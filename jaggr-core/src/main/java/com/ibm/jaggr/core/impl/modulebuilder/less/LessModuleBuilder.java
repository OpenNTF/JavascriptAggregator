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
import com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder;
import com.ibm.jaggr.core.resource.IResource;

import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * This class compiles LESS resources that are loaded by the AMD aggregator.
 */
public class LessModuleBuilder extends CSSModuleBuilder implements IExtensionSingleton {
	static final String sourceClass = LessModuleBuilder.class.getName();
	static final Logger log = Logger.getLogger(sourceClass);

	private static final String LESS_JS_RES = "less-rhino-1.7.0.js"; //$NON-NLS-1$

	private static final String LESS_COMPILER_VAR = "lessCompiler"; //$NON-NLS-1$

	private static final Pattern HANDLES_PATTERN = Pattern.compile("\\.(css)|(less)$"); //$NON-NLS-1$

	private static final String compilerString = new StringBuffer()
		.append("var ").append(LESS_COMPILER_VAR).append(" = function(input, options) {") //$NON-NLS-1$ //$NON-NLS-2$
		.append("    var result;") //$NON-NLS-1$
		.append("    new less.Parser(options).parse(input, function (e, root) {") //$NON-NLS-1$
		.append("    	if (e) {throw e;}") //$NON-NLS-1$
		.append("        result = root.toCSS(options);") //$NON-NLS-1$
		.append("	});") //$NON-NLS-1$
		.append("	return result;") //$NON-NLS-1$
		.append("}") //$NON-NLS-1$
		.toString();

	Script lessJsScript = null;
	Script compilerScript = null;

	public LessModuleBuilder() {
		super();
		init();
	}

	// for unit tests
	protected LessModuleBuilder(IAggregator aggregator) {
		super(aggregator);
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
			InputStream in = CSSModuleBuilder.class.getClassLoader().getResourceAsStream(LESS_JS_RES);
			if (in == null) {
				throw new NotFoundException(LESS_JS_RES);
			}
			String lessJsSource = IOUtils.toString(in);
			lessJsScript = cx.compileString(lessJsSource,  LESS_JS_RES, 1, null);
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
		lessJsScript.exec(cx, threadScope);
		compilerScript.exec(cx, threadScope);

		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, threadScope);
		}
		return threadScope;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder#postcss(java.lang.String, com.ibm.jaggr.core.resource.IResource)
	 */
	@Override
	protected String postcss(String css, IResource resource) throws IOException {
		final String sourceMethod = "postcss"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{css, resource});
		}
		if (resource.getPath().endsWith(".less")) { //$NON-NLS-1$
			css = processLess(resource.getURI().toString(), css);
		}
		css = super.postcss(css, resource);

		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, css);
		}
		return css;
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
			scope.setParentScope(threadScope);
			Scriptable options = cx.newObject(threadScope);
			options.put("filename", options, filename); //$NON-NLS-1$
        	Function compiler = (Function)threadScope.get(LESS_COMPILER_VAR, threadScope);
            css = compiler.call(cx, scope, null, new Object[] {css, options}).toString();

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
