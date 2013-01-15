/*
 * (C) Copyright 2012, IBM Corporation
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

package com.ibm.jaggr.service.impl.modulebuilder.javascript;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.google.common.collect.HashMultimap;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.Result;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IAggregatorExtension;
import com.ibm.jaggr.service.IExtensionInitializer;
import com.ibm.jaggr.service.IRequestListener;
import com.ibm.jaggr.service.IShutdownListener;
import com.ibm.jaggr.service.NotFoundException;
import com.ibm.jaggr.service.cachekeygenerator.ExportNamesCacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.FeatureSetCacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.service.modulebuilder.ModuleBuild;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.transport.IHttpTransport.OptimizationLevel;
import com.ibm.jaggr.service.util.DependencyList;
import com.ibm.jaggr.service.util.Features;
import com.ibm.jaggr.service.util.StringUtil;
import com.ibm.jaggr.service.util.TypeUtil;

/**
 * This class minifies a javacript module.  The modules is assumed to use the AMD
 * loader format.  Modules are minified sing the Google Closure compiler,
 * and module builds differ according to the requested compilation level and has-filtering
 * conditions.  The requested compilation level and has-filtering conditions are 
 * specified as attributes in the http request when calling {@link #build}.
 *
 */
public class JavaScriptModuleBuilder implements IModuleBuilder, IExtensionInitializer, IRequestListener, IShutdownListener {
	private static final Logger log = Logger.getLogger(JavaScriptModuleBuilder.class.getName());
	
	private static final List<JSSourceFile> externs = Collections.emptyList();
	
	private static final String EXPANDEDCONFIGDEPS_THREADSAFEREQATTRNAME = JavaScriptModuleBuilder.class.getName() + ".expandedConfigDeps"; //$NON-NLS-1$
	
	private static final ICacheKeyGenerator exportNamesCacheKeyGenerator = 
		new ExportNamesCacheKeyGenerator();

	static {
		Compiler.setLoggingLevel(Level.WARNING);
	}

	private List<ServiceRegistration> registrations = new LinkedList<ServiceRegistration>();
	
	public static CompilationLevel getCompilationLevel(HttpServletRequest request) {
        CompilationLevel level = CompilationLevel.SIMPLE_OPTIMIZATIONS;
		IAggregator aggregator = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggregator.getOptions();
        if (options.isDevelopmentMode() || options.isDebugMode()) {
			OptimizationLevel optimize = (OptimizationLevel)request.getAttribute(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME);
	        if (optimize == OptimizationLevel.WHITESPACE) {
	            level = CompilationLevel.WHITESPACE_ONLY;
	        } else if (optimize == OptimizationLevel.ADVANCED) {
	            level = CompilationLevel.ADVANCED_OPTIMIZATIONS;
	        } else if (optimize == OptimizationLevel.NONE) {
	            level = null;
	        }
        }
        return level;
	}
	/**
	 * Static class method for determining if require list explosion should be performed.
	 *  
	 * @param options
	 * @return True if require list explosion should be performed.
	 */
	public static boolean s_isExplodeRequires(HttpServletRequest request) {
		boolean result = false;
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		Boolean reqattr = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME));
		result = (options == null || !options.isSkipRequireListExpansion()) 
				&& reqattr != null && reqattr
				&& aggr.getDependencies() != null;
		return result;
	}
	
	
	/**
	 * Static method for determining if has filtering should be performed.
	 * 
	 * @param options
	 * @return True if has filtering should be performed
	 */
	public static boolean s_isHasFiltering(HttpServletRequest request) {
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		return (options != null) ? !options.isSkipHasFiltering() : true;
	}
	
	public static boolean s_isExportModuleName(HttpServletRequest request) {
		Boolean value = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME));
		return value != null ? value : false;
	}
	
	public static boolean s_isRequireExpLogging(HttpServletRequest request) {
		boolean result = false;
		if (s_isExplodeRequires(request) ) {
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			IOptions options = aggr.getOptions();
			if (options.isDebugMode() || options.isDevelopmentMode()) {
				result = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPANDREQLOGGING_REQATTRNAME));
			}
		}
		return result;
	}
	
	
	@Override
	public void initialize(IAggregator aggregator,
			IAggregatorExtension extension, IExtensionRegistrar registrar) {
		BundleContext context = aggregator.getBundleContext();
		Properties props = new Properties();
		props.put("name", aggregator.getName()); //$NON-NLS-1$
		registrations.add(context.registerService(IRequestListener.class.getName(), this, props));
		props = new Properties();
		props.put("name", aggregator.getName()); //$NON-NLS-1$
		registrations.add(context.registerService(IShutdownListener.class.getName(), this, props));
	}

	@Override
	public void startRequest(HttpServletRequest request, HttpServletResponse response) { 
		// Check to see if optimization is disabled for this request and if so,
		// then disable module name exporting since we can't export names of 
		// javascript modules without the compiler.
		CompilationLevel level = getCompilationLevel(request);
		if (level  == null) {
			// optimization is disabled, so disable exporting of module name
			request.setAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.FALSE);
		} else if (s_isExplodeRequires(request)) {
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			Features features = (Features)request.getAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME);
			if (features == null) {
				features = Features.emptyFeatures;
			}
			DependencyList expandedConfigDeps = 
				new DependencyList(
						aggr.getConfig().getDeps(), 
						aggr.getConfig(), 
						aggr.getDependencies(), 
						features, 
						s_isRequireExpLogging(request));
			expandedConfigDeps.setLabel("require.deps"); //$NON-NLS-1$
			request.setAttribute(EXPANDEDCONFIGDEPS_THREADSAFEREQATTRNAME, expandedConfigDeps);
		}
	}
	
	@Override
	public void endRequest(HttpServletRequest request, HttpServletResponse response) {
	}

	@Override
	public void shutdown(IAggregator aggregator) {
		for (ServiceRegistration reg : registrations) {
			reg.unregister();
		}
	}

	@Override
	public ModuleBuild build(
			String mid, 
			IResource resource, 
			HttpServletRequest request,
			List<ICacheKeyGenerator> keyGens
			) 
	throws Exception {
		final IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		// Get the parameters from the request
        CompilationLevel level = getCompilationLevel(request);
		boolean createNewKeyGen = (keyGens == null); 
    	boolean isHasFiltering = s_isHasFiltering(request);
		// If the source doesn't exist, throw an exception.
		if (!resource.exists()) {
			if (log.isLoggable(Level.WARNING)) {
				log.warning(
					MessageFormat.format(
						Messages.JavaScriptModuleBuilder_0,
						new Object[]{resource.getURI().toString()}
					)
				);
			}
			throw new NotFoundException(resource.getURI().toString());
		}

		List<JSSourceFile> sources = this.getJSSource(mid, resource, request, keyGens);
		
		// If optimization level is none, then just return the source, unless
		// we were given a null keyGens array, in which case we
		// need to process the source to be able to provide a
		// cache key generator.
		if (level == null && keyGens != null) {
			StringBuffer code = new StringBuffer();
			for (JSSourceFile sf : sources) {
				code.append(sf.getCode());
			}
			return new ModuleBuild(
				code.toString(),
				keyGens,
				false
			); 
		}
		boolean coerceUndefinedToFalse = aggr.getConfig().isCoerceUndefinedToFalse();
		Features features = (Features)request.getAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME);
		if (features == null || level == null || !s_isHasFiltering(request)) {
			// If no features specified or we're only processing features to
			// get the dependency list for the cache key generator, then use
			// an empty feature set.
			features = Features.emptyFeatures;
			coerceUndefinedToFalse = false;
		}
		
		
		Set<String> discoveredHasConditionals = new LinkedHashSet<String>();
		String output = null;
	
		Compiler compiler = new Compiler();
		CompilerOptions compiler_options = new CompilerOptions();
    	compiler_options.customPasses = HashMultimap.create();
		if (isHasFiltering && (level != null || keyGens == null)) {
			// Run has filtering compiler pass if we are doing has filtering, or if this
			// is the first build for this module (keyGens == null) so that we can get 
			// the dependent features for the module to include in the cache key generator.
			HasFilteringCompilerPass hfcp = new HasFilteringCompilerPass(
					features, 
					keyGens == null ? discoveredHasConditionals : null, 
					coerceUndefinedToFalse
			);
   			compiler_options.customPasses.put(CustomPassExecutionTime.BEFORE_CHECKS, hfcp);
		}

		boolean isReqExpLogging = s_isRequireExpLogging(request);
		if (s_isExplodeRequires(request) && level != null) {
			DependencyList expandedConfigDeps = (DependencyList)request.getAttribute(EXPANDEDCONFIGDEPS_THREADSAFEREQATTRNAME);
			/*
			 * Register the RequireExpansionCompilerPass if we're exploding 
			 * require lists to include nested dependencies
			 */
			if (expandedConfigDeps != null) {
				discoveredHasConditionals.addAll(expandedConfigDeps.getDependentFeatures());
			}
			RequireExpansionCompilerPass recp = 
				new RequireExpansionCompilerPass(
						aggr, 
						features,
						discoveredHasConditionals,
						expandedConfigDeps,
						(String)request.getAttribute(IHttpTransport.CONFIGVARNAME_REQATTRNAME),
						isReqExpLogging);
			
			compiler_options.customPasses.put(CustomPassExecutionTime.BEFORE_CHECKS, recp);
		}
		if (s_isExportModuleName(request) && level != null) {
			compiler_options.customPasses.put(
					CustomPassExecutionTime.BEFORE_CHECKS,
					new ExportModuleNameCompilerPass()
			);
		}
		
		if (level != null && level != CompilationLevel.WHITESPACE_ONLY) {
			level.setOptionsForCompilationLevel(compiler_options);
		} else {
			// If CompilationLevel is WHITESPACE_ONLY, then don't call 
			// setOptionsForCompilationLevel because it disables custom
			// compiler passes and we want them to run.
			
		    // Allows annotations that are not standard.
		    compiler_options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
		        CheckLevel.OFF);
		}
		
		// we do our own threading, so disable compiler threads.
		compiler.disableThreads();
		
		// compile the module
		Result result = compiler.compile(externs, sources, compiler_options);
		if (result.success) {
			if (keyGens != null) {
				// Determine if we need to update the cache key generator.  Updating may be 
				// necessary due to require list expansion as a result of different 
				// dependency path traversals resulting from the specification of different
				// feature sets in the request.
				CacheKeyGenerator keyGen = (CacheKeyGenerator)keyGens.get(1);
				if (keyGen.featureKeyGen == null || 
						keyGen.featureKeyGen.getFeatureSet() == null ||
						!keyGen.featureKeyGen.getFeatureSet().containsAll(discoveredHasConditionals)) {
					discoveredHasConditionals.addAll(keyGen.featureKeyGen.getFeatureSet());
					createNewKeyGen = true;
				}
			}
			if (level == null) {
				// If optimization level is null, then we compiled only to discover
				// the dependent features for the cache key generator.  Set the 
				// response output to the un-modified source code.
				StringBuffer code = new StringBuffer();
				for (JSSourceFile sf : sources) {
					code.append(sf.getCode());
				}
				output = code.toString();
			} else {
				// Get the compiler output and set the data in the ModuleBuild 
				output = compiler.toSource();
			}
		} else {
			// Got a compiler error.  Output a warning message to the browser console
			StringBuffer sb = new StringBuffer(
				MessageFormat.format(
					Messages.JavaScriptModuleBuilder_1,
					new Object[] {resource.getURI().toString()}
				)
			);
			for (JSError error : result.errors) {
				sb.append("\r\n\t").append(error.description) //$NON-NLS-1$
				   .append(" (").append(error.lineNumber).append(")."); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (aggr.getOptions().isDevelopmentMode() || aggr.getOptions().isDebugMode()) {
				// In development mode, return the error console output
				// together with the uncompressed source.
				String escaped = StringUtil.escapeForJavaScript(sb.toString());
				// Reuse the string buffer and output a console error message
				// along with the uncompiled source files as the build output.
				sb.replace(0, sb.length(), "\r\nconsole.error(\"") //$NON-NLS-1$
				  .append(escaped)
				  .append("\");\r\n"); //$NON-NLS-1$
				for (JSSourceFile sf : sources) {
					sb.append(sf.getCode());
				}
				return new ModuleBuild(sb.toString(), null, true);
			} else {
				throw new Exception(sb.toString());
			}
		}
		return new ModuleBuild(
				output,
				createNewKeyGen ? 
						getCacheKeyGenerators(discoveredHasConditionals) : 
						keyGens,
				isReqExpLogging);
	}		
	
	/**
	 * Overrideable method for getting the source modules to compile
	 * 
	 * @param mid 
	 * @param resource
	 * @param request
	 * @param keyGens
	 * @return
	 * @throws IOException
	 */
	protected List<JSSourceFile> getJSSource(String mid, IResource resource, HttpServletRequest request, List<ICacheKeyGenerator> keyGens) throws IOException  {
		
		List<JSSourceFile> result = new LinkedList<JSSourceFile>();
		InputStream in = resource.getInputStream();
		JSSourceFile sf = JSSourceFile.fromInputStream(mid, in);
		sf.setOriginalPath(resource.getURI().toString());
		in.close();
		result.add(sf);
		return result;
	}

	@Override
	public List<ICacheKeyGenerator> getCacheKeyGenerators(IAggregator aggregator) {
		return getCacheKeyGenerators((Set<String>)null);
	}
	
	@Override
	public boolean handles(String mid, IResource resource) {
		return resource.getURI().getPath().endsWith(".js"); //$NON-NLS-1$
	}

	protected List<ICacheKeyGenerator> getCacheKeyGenerators(Set<String> dependentFeatures) {
		ArrayList<ICacheKeyGenerator> keyGens = new ArrayList<ICacheKeyGenerator>();
		keyGens.add(exportNamesCacheKeyGenerator);
		keyGens.add(new CacheKeyGenerator(dependentFeatures, dependentFeatures == null));
		return keyGens;
	}
	
	static final class CacheKeyGenerator implements ICacheKeyGenerator {

		private static final long serialVersionUID = -3344636280865415030L;

		private static final String eyecatcher = "js"; //$NON-NLS-1$

		private final FeatureSetCacheKeyGenerator featureKeyGen;
		
		CacheKeyGenerator(Set<String> depFeatures, boolean provisional) {
			featureKeyGen = new FeatureSetCacheKeyGenerator(depFeatures, provisional);
		}
		
		private CacheKeyGenerator(FeatureSetCacheKeyGenerator featureKeyGen) {
			this.featureKeyGen = featureKeyGen; 
		}
		
		/**
		 * Calculates a string in the form
		 * <code>&lt;<i>level</i>&gt;:&lt;0|1&gt;{&lt;<i>has-conditions</i>&gt;}</code>
		 * where <i>level</i> is the {@link CompilationLevel} specified by the
		 * request attribute {@link #COMPILATION_LEVEL} or {@code NONE} if null, and
		 * the {@code 0|1} is the value returned by {@link #s_isExplodeRequires(HttpServletRequest)}. The
		 * has-conditions are listed in alphabetical order. The key will contain
		 * only has-conditions from {@code hasMap} that are also members of
		 * {@code includedHas} (i.e. only has-conditions that are relevant to this
		 * module).
		 * 
		 * @param request
		 *            The request
		 * @param includedHas
		 *            Set of has-conditionals actually tested in the module, or null
		 * @return The cache key
		 */
		@Override
		public String generateKey(HttpServletRequest request) {
	        CompilationLevel level = getCompilationLevel(request);
			boolean requireListExpansion = s_isExplodeRequires(request);
			boolean reqExpLogging = s_isRequireExpLogging(request);
			boolean hasFiltering = s_isHasFiltering(request);
			
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Creating cache key: level=" +  //$NON-NLS-1$
						(level != null ? level.toString() : "null") + ", " + //$NON-NLS-1$ //$NON-NLS-2$
						"requireListExpansion=" + Boolean.toString(requireListExpansion) + ", " + //$NON-NLS-1$ //$NON-NLS-2$
						"requireExpLogging=" + Boolean.toString(reqExpLogging) + "," + //$NON-NLS-1$ //$NON-NLS-2$
						"hasFiltering=" + Boolean.toString(hasFiltering) //$NON-NLS-1$
				);
						
			}
			StringBuffer sb = new StringBuffer(eyecatcher).append(":"); //$NON-NLS-1$
				sb.append((level != null ? level.toString() : "NONE").substring(0,1))  //$NON-NLS-1$
				  .append(requireListExpansion ? ":1" : ":0") //$NON-NLS-1$ //$NON-NLS-2$
				  .append(reqExpLogging ? ":1" : ":0") //$NON-NLS-1$ //$NON-NLS-2$
				  .append(hasFiltering ? ":1" : ":0"); //$NON-NLS-1$ //$NON-NLS-2$
			
			if (featureKeyGen != null && 
					s_isHasFiltering(request) && 
					getCompilationLevel(request) != null) 
			{
				String s = featureKeyGen.generateKey(request);
				if (s.length() > 0) {
					sb.append(";").append(s); //$NON-NLS-1$
				}
			}
			if (log.isLoggable(Level.FINEST))
				log.finest("Created cache key: " + sb.toString()); //$NON-NLS-1$
			return sb.toString();
		}

		@Override
		public ICacheKeyGenerator combine(ICacheKeyGenerator otherKeyGen) {
			if (this.equals(otherKeyGen)) {
				return this;
			}
			CacheKeyGenerator other = (CacheKeyGenerator)otherKeyGen;
			FeatureSetCacheKeyGenerator combined = null;
			if (featureKeyGen != null && other.featureKeyGen != null) {
				combined = (FeatureSetCacheKeyGenerator)featureKeyGen.combine(other.featureKeyGen);
			} else if (featureKeyGen != null) {
				combined = featureKeyGen;
			} else if (other.featureKeyGen != null) {
				combined = other.featureKeyGen;
			}
			return new CacheKeyGenerator(combined);
		}

		@Override
		public boolean isProvisional() {
			return featureKeyGen != null ? featureKeyGen.isProvisional() : false;
		}
		
		@Override
		public String toString() {
			return eyecatcher + (featureKeyGen != null ? (":(" + featureKeyGen.toString()) + ")" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-2$
		}

		@Override
		public List<ICacheKeyGenerator> getCacheKeyGenerators(
				HttpServletRequest request) {
			if (featureKeyGen == null) {
				return Arrays.asList(new ICacheKeyGenerator[]{this});
			}
			List<ICacheKeyGenerator> result = new LinkedList<ICacheKeyGenerator>();
			result.add(new CacheKeyGenerator(null));
			if (s_isHasFiltering(request) && getCompilationLevel(request) != null) {
				List<ICacheKeyGenerator> gens = featureKeyGen.getCacheKeyGenerators(request);
				if (gens == null) {
					result.add(featureKeyGen);
				} else {
					result.addAll(gens);
				}
			}
			return result;
		}
		
		@Override
		public boolean equals(Object other) {
			return other != null && getClass().equals(other.getClass()) && 
					featureKeyGen.equals(((CacheKeyGenerator)other).featureKeyGen);
		}
		
		@Override
		public int hashCode() {
			return getClass().hashCode() * 31 + featureKeyGen.hashCode();
		}
	}
}	
