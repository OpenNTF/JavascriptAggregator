/*
 * (C) Copyright 2014, IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.jaggr.core.impl.modulebuilder.less;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IExtensionSingleton;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.cachekeygenerator.FeatureSetCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.rhino.HasFunction;
import com.ibm.jaggr.core.util.rhino.ReadFileExtFunction;

import com.google.common.collect.ImmutableList;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

/**
 * This class compiles LESS resources that are loaded by the AMD aggregator.
 */
public class LessModuleBuilder extends CSSModuleBuilder implements IExtensionSingleton {
	static final String sourceClass = LessModuleBuilder.class.getName();
	static final Logger log = Logger.getLogger(sourceClass);

	private static final ImmutableList<String> LESS_JS_RES = new ImmutableList.Builder<String>()
		.add("less-rhino-1.7.0.js") //$NON-NLS-1$
		.add("lessFileLoader.js") //$NON-NLS-1$
		.build();

	private static final String LESS_COMPILER_VAR = "lessCompiler"; //$NON-NLS-1$

	private static final Pattern HANDLES_PATTERN = Pattern.compile("\\.(css)|(less)$"); //$NON-NLS-1$

	private static final String GLOBAL_VARS = "globalVars"; //$NON-NLS-1$

	private static final String LESS_SUFFIX = ".less"; //$NON-NLS-1$

	private static final String compilerString = new StringBuffer()
		.append("var ").append(LESS_COMPILER_VAR).append(" = function(input, options, additionalData) {") //$NON-NLS-1$ //$NON-NLS-2$
		.append("	var result;") //$NON-NLS-1$
		.append("	new less.Parser(options).parse(input, function (e, root) {") //$NON-NLS-1$
		.append("		if (e) {throw e;}") //$NON-NLS-1$
		.append("		result = root.toCSS(options);") //$NON-NLS-1$
		.append("	}, additionalData);") //$NON-NLS-1$
		.append("	return result;") //$NON-NLS-1$
		.append("}") //$NON-NLS-1$
		.toString();

	private static final ThreadLocal<HttpServletRequest> threadLocalRequest = new ThreadLocal<HttpServletRequest>();
	private static final ThreadLocal<Set<String>> threadLocalDependentFeatures = new ThreadLocal<Set<String>>();

	ArrayList<Script> lessJsScript = new ArrayList<Script>();
	Script compilerScript = null;
	Object lessGlobals = null;
	boolean isFeatureDependent = false;

	public LessModuleBuilder() {
		super();
		init();
	}

	// for unit tests
	protected LessModuleBuilder(IAggregator aggregator, ExecutorService es, int scopePoolSize) {
		super(aggregator, es, scopePoolSize);
		init();
	}

	protected void init() {
		final String sourceMethod = "init"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod);
		}
		Context cx = Context.enter();
		try {
			cx.setOptimizationLevel(9);
			for (String fname : LESS_JS_RES) {
				InputStream in = CSSModuleBuilder.class.getClassLoader().getResourceAsStream(fname);
				if (in == null) {
					throw new NotFoundException(fname);
				}
				String source = IOUtils.toString(in);
				lessJsScript.add(cx.compileString(source,  fname, 1, null));
			}
			compilerScript = cx.compileString(compilerString, BLANK, 1, null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			Context.exit();
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.IModuleBuilder#getCacheKeyGenerator(com.ibm.jaggr.service.IAggregator)
	 */
	@Override	public List<ICacheKeyGenerator> getCacheKeyGenerators(IAggregator aggregator) {
		final String sourceMethod = "getCacheKeyGenerators"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{aggregator});
		}
		List<ICacheKeyGenerator> result = super.getCacheKeyGenerators(aggregator);
		if (isFeatureDependent) {
			// If responses are feature dependent, add a FeatueCacheKeyGenerator to the list of
			// cache key generators for this module builder
			result = new ArrayList<ICacheKeyGenerator>(super.getCacheKeyGenerators(aggregator));
			if (result.size() != 2) {
				throw new IllegalStateException("Unexpected list size = " + result.size()); //$NON-NLS-1$
			}
			result.add(new FeatureSetCacheKeyGenerator(new HashSet<String>(), true));
			result = Collections.unmodifiableList(result);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, KeyGenUtil.toString(result));
		}
		return result;
	}



	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder#createThreadScope(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable)
	 */
	@Override
	public Scriptable createThreadScope(Context cx, Scriptable protoScope) {
		final String sourceMethod = "createThreadScope"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{cx, protoScope});
		}
		Scriptable threadScope = super.createThreadScope(cx, protoScope);
		for (Script script : lessJsScript) {
			script.exec(cx, threadScope);
		};
		compilerScript.exec(cx, threadScope);
		ReadFileExtFunction readFileFn = new ReadFileExtFunction(threadScope, getAggregator());
		ScriptableObject.putProperty(threadScope, ReadFileExtFunction.FUNCTION_NAME, readFileFn);


		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, threadScope);
		}
		return threadScope;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder#inlineImports(javax.servlet.http.HttpServletRequest, java.lang.String, com.ibm.jaggr.core.resource.IResource, java.lang.String)
	 */
	@Override
	protected String inlineImports(HttpServletRequest req, String css, IResource res, String path)
			throws IOException {
		/*
		 * For the LESS builder, we want the LESS compiler to process &#64imports so that variable
		 * substitution in import names can take place.  We override this method and do nothing because
		 * the CSS module builder calls it before the LESS compiler runs.  Instead, we inline imports
		 * that weren't processed by the LESS compiler (i.e. CSS imports) after compiling by calling
		 * _inlineImports().
		 */
		if (!res.getPath().toLowerCase().endsWith(LESS_SUFFIX)) {
			css = super.inlineImports(req, css, res, path);
		}
		return css;
	}

	/**
	 * Called by our <code>postcss</code> method to inline imports not processed by the LESS compiler
	 * (i.e. CSS imports) <b>after</b> the LESS compiler has processed the input.
	 *
	 * @param req
	 *            The request associated with the call.
	 * @param css
	 *            The current CSS containing &#064;import statements to be
	 *            processed
	 * @param res
	 *            The resource for the CSS file.
	 * @param path
	 *            The path, as specified in the &#064;import statement used to
	 *            import the current CSS, or null if this is the top level CSS.
	 *
	 * @return The input CSS with &#064;import statements replaced with the
	 *         contents of the imported files.
	 *
	 * @throws IOException
	 */
	protected String _inlineImports(HttpServletRequest req, String css, IResource res, String path) throws IOException {
		return super.inlineImports(req, css, res, path);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder#postcss(java.lang.String, com.ibm.jaggr.core.resource.IResource)
	 */
	@Override
	protected String postcss(HttpServletRequest request, String css, IResource resource) throws IOException {
		final String sourceMethod = "postcss"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{css, resource});
		}
		if (resource.getPath().toLowerCase().endsWith(LESS_SUFFIX)) {
			css = processLess(resource.getURI().toString(), css);
			if (inlineImports) {
				css = _inlineImports(request, css, resource, ""); //$NON-NLS-1$
			}
		}
		css = super.postcss(request, css, resource);

		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, css);
		}
		return css;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.modulebuilder.text.TextModuleBuilder#build(java.lang.String, com.ibm.jaggr.core.resource.IResource, javax.servlet.http.HttpServletRequest, java.util.List)
	 */
	@Override
	public ModuleBuild build(String mid, IResource resource, HttpServletRequest request, List<ICacheKeyGenerator> inKeyGens) throws Exception {

		// Manage life span of thread locals used by this module builder
		if (isFeatureDependent) {
			threadLocalRequest.set(request);
			threadLocalDependentFeatures.set(null);
		}
		try {
			return super.build(mid, resource, request, inKeyGens);
		}	finally {
			if (isFeatureDependent) {
				threadLocalRequest.remove();
				threadLocalDependentFeatures.remove();
			}
		}
	}



	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder#getContentReader(java.lang.String, com.ibm.jaggr.core.resource.IResource, javax.servlet.http.HttpServletRequest, org.apache.commons.lang3.mutable.MutableObject)
	 */
	@Override
	protected Reader getContentReader(String mid, IResource resource, HttpServletRequest request,
			MutableObject<List<ICacheKeyGenerator>> keyGensRef) throws IOException {

		final String sourceMethod = "getContentReader"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{mid, resource, request, keyGensRef});
		}

		// Call super class implementation and determine if we need to update the cache key generators
		Reader result = super.getContentReader(mid, resource, request, keyGensRef);
		if (isFeatureDependent) {
			List<ICacheKeyGenerator> keyGens = keyGensRef.getValue();
			if (resource.getPath().toLowerCase().endsWith(LESS_SUFFIX)) {
				Set<String> dependentFeatures = threadLocalDependentFeatures.get();
				if (keyGens == null) {
					keyGens = getCacheKeyGenerators(getAggregator());
				}
				FeatureSetCacheKeyGenerator fsKeyGen = (FeatureSetCacheKeyGenerator)keyGens.get(2);
				// Cache key generators need to be updated if existing one is provisional or if there are new dependent features
				if (fsKeyGen.isProvisional() || !fsKeyGen.getFeatureSet().containsAll(dependentFeatures)) {
					List<ICacheKeyGenerator> newKeyGens = new ArrayList<ICacheKeyGenerator>();
					newKeyGens.add(keyGens.get(0));
					newKeyGens.add(keyGens.get(1));
					FeatureSetCacheKeyGenerator newFsKeyGen = new FeatureSetCacheKeyGenerator(dependentFeatures, false);
					newKeyGens.add(newFsKeyGen.combine(fsKeyGen));
					keyGensRef.setValue(newKeyGens);
					if (isTraceLogging) {
						log.logp(Level.FINER, sourceClass, sourceMethod, "Key generators updated: " + KeyGenUtil.toString(newKeyGens)); //$NON-NLS-1$
					}
				}
			} else {
				// We're processing a CSS file.  See if we need to provide key gens
				if (keyGens == null) {
					keyGensRef.setValue(super.getCacheKeyGenerators(getAggregator()));
				}
			}
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, result);
		}
		return result;
	}

	protected String processLess(String filename, String css) throws IOException {
		final String sourceMethod = "processLess"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{filename, css});
		}
		Context cx = Context.enter();
		Scriptable threadScope = null;
		try {
			threadScope = getThreadScopes().poll(SCOPE_POOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (threadScope == null) {
				throw new TimeoutException("Timeout waiting for thread scope"); //$NON-NLS-1$
			}
			Scriptable scope = cx.newObject(threadScope);
			scope.setPrototype(threadScope);
			scope.setParentScope(null);
			Scriptable options = cx.newObject(threadScope);
			options.put("filename", options, filename); //$NON-NLS-1$

			// Evaluate global variables
			Object additionalData = Undefined.instance;
			if (lessGlobals != null) {
				additionalData = cx.newObject(threadScope);
				if (lessGlobals instanceof Function) {
					// Evaluate the global vars in the context of the request
					HttpServletRequest request = threadLocalRequest.get();
					Features features = (Features) request.getAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME);
					HasFunction hasFn = new HasFunction(scope, features);
					ScriptableObject.putProperty(scope, "has", hasFn); //$NON-NLS-1$
					Object obj = ((Function) lessGlobals).call(cx, scope, null, null);
					((Scriptable) additionalData).put(GLOBAL_VARS, (Scriptable) additionalData,	obj);
					Set<String> dependentFeatures = hasFn.getDependentFeatures();
					if (!dependentFeatures.isEmpty()) {
						threadLocalDependentFeatures.set(dependentFeatures);
					}
				} else {
					((Scriptable) additionalData).put(GLOBAL_VARS, (Scriptable) additionalData,
							lessGlobals);
				}
				if (isTraceLogging) {
					log.logp(Level.FINER, sourceClass, sourceMethod, "lessGlobals = " + Context.toString(lessGlobals)); //$NON-NLS-1$
				}
			}
			Function compiler = (Function) threadScope.get(LESS_COMPILER_VAR, threadScope);
			css = compiler.call(cx, scope, null, new Object[] { css, options, additionalData }).toString();
		} catch (JavaScriptException e) {
			// Add module info
			String message = "Error parsing " + filename + "\r\n" + e.getMessage(); //$NON-NLS-1$ //$NON-NLS-2$
			throw new IOException(message, e);
		} catch (InterruptedException e) {
			throw new IOException(e);
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		} finally {
			if (threadScope != null) {
				getThreadScopes().add(threadScope);
			}
			Context.exit();
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, css);
		}
		return css;
	}

	@Override
	public void configLoaded(IConfig conf, long sequence) {
		final String sourceMethod = "configLoaded"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass,  sourceMethod, new Object[]{conf, sequence});
		}
		super.configLoaded(conf, sequence);
		lessGlobals = conf.getProperty("lessGlobals", null); //$NON-NLS-1$
		if (lessGlobals == IConfig.NOT_FOUND) {
			lessGlobals = null;
		}
		isFeatureDependent = lessGlobals != null && (lessGlobals instanceof Function);
		if (isTraceLogging) {
			log.logp(Level.FINER, sourceClass, sourceMethod, "lessGlobals = " + lessGlobals); //$NON-NLS-1$
			log.logp(Level.FINER, sourceClass, sourceMethod, "isFeatureDependent = " + isFeatureDependent); //$NON-NLS-1$
			log.exiting(sourceClass, sourceMethod);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.modulebuilder.IModuleBuilder#handles(java.lang.String, com.ibm.jaggr.service.resource.IResource)
	 */
	@Override
	public boolean handles(String mid, IResource resource) {
		final String sourceMethod = "handles"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{mid, resource});
		}
		boolean result = HANDLES_PATTERN.matcher(mid).find();
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, result);
		}
		return result;
	}
}
