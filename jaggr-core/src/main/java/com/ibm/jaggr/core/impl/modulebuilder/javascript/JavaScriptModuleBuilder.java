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

package com.ibm.jaggr.core.impl.modulebuilder.javascript;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;


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
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.cachekeygenerator.ExportNamesCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.FeatureSetCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.PlatformAggregatorFactory;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.OptimizationLevel;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.RequestUtil;
import com.ibm.jaggr.core.util.StringUtil;

/**
 * This class minifies a javacript module.  The modules is assumed to use the AMD
 * loader format.  Modules are minified sing the Google Closure compiler,
 * and module builds differ according to the requested compilation level and has-filtering
 * condiftions.  The requested compilation level and has-filtering conditions are 
 * specified as attributes in the http request when calling {@link #build}.
 *
 */
public class JavaScriptModuleBuilder implements IModuleBuilder, IExtensionInitializer, ILayerListener, IShutdownListener {
	private static final Logger log = Logger.getLogger(JavaScriptModuleBuilder.class.getName());
	
	private static final List<JSSourceFile> externs = Collections.emptyList();

	/**
	 * Name of the request attribute containing the expanded dependencies for
	 * the layer.  This is the list of module dependencies for all of the modules
	 * in the layer, plus the expanded dependencies for those modules.  The value
	 * is of type {@link ModuleDeps}.
	 */
	static final String EXPANDED_DEPENDENCIES = ILayer.class.getName() + ".layerdeps"; //$NON-NLS-1$

	private static final ICacheKeyGenerator exportNamesCacheKeyGenerator = 
		new ExportNamesCacheKeyGenerator();

	static {
		Logger.getLogger("com.google.javascript.jscomp.Compiler").setLevel(Level.WARNING); //$NON-NLS-1$
		Logger.getLogger("com.google.javascript.jscomp.PhaseOptimizer").setLevel(Level.WARNING); //$NON-NLS-1$
		Compiler.setLoggingLevel(Level.WARNING);
	}

	private List<Object> registrations = new LinkedList<Object>();
	
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
	
	@Override
	public void initialize(IAggregator aggregator,
			IAggregatorExtension extension, IExtensionRegistrar registrar) {
		Dictionary<String,String> props = new Hashtable<String,String>();
		props.put("name", aggregator.getName()); //$NON-NLS-1$		
		registrations.add(PlatformAggregatorFactory.getPlatformAggregator().registerService(ILayerListener.class.getName(), this, props));
		props = new Hashtable<String,String>();
		props.put("name", aggregator.getName()); //$NON-NLS-1$
		registrations.add(PlatformAggregatorFactory.getPlatformAggregator().registerService(IShutdownListener.class.getName(), this, props));
	}

	@Override
	public String layerBeginEndNotifier(EventType type, HttpServletRequest request, List<IModule> modules, Set<String> dependentFeatures) {
		String result = null;
		if (type == EventType.BEGIN_LAYER) {
			// Check to see if optimization is disabled for this request and if so,
			// then disable module name exporting since we can't export names of 
			// javascript modules without the compiler.
			CompilationLevel level = getCompilationLevel(request);
			if (level  == null) {
				// optimization is disabled, so disable exporting of module name
				request.setAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.FALSE);
			}
			
			// If we're doing require list expansion, then set the EXPANDED_DEPENDENCIES attribute
			// with the set of expanded dependencies for the layer.  This will be used by the 
			// build renderer to filter layer dependencies from the require list expansion.
			if (RequestUtil.isExplodeRequires(request)) {
				boolean isReqExpLogging = RequestUtil.isRequireExpLogging(request);
				List<String> moduleIds = new ArrayList<String>(modules.size());
				for (IModule module : modules) {
					moduleIds.add(module.getModuleId());
				}
				IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
	    		Features features = (Features)request.getAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME);
	    		DependencyList configDepList = new DependencyList(
	    				aggr.getConfig().getDeps(),
	    				aggr.getConfig(),
	    				aggr.getDependencies(),
	    				features,
	    				isReqExpLogging, false);
	    				
	    		DependencyList layerDepList = new DependencyList(
	    				moduleIds,
	    				aggr.getConfig(),
	    				aggr.getDependencies(),
	    				features,
	    				isReqExpLogging, false);
	    		
	    		ModuleDeps configDeps = new ModuleDeps();
	    		ModuleDeps layerDeps = new ModuleDeps();
	    		try {
	    			configDeps.addAll(configDepList.getExplicitDeps());
	    			configDeps.addAll(configDepList.getExpandedDeps());
		    		layerDeps.addAll(layerDepList.getExplicitDeps());
		    		layerDeps.addAll(layerDepList.getExpandedDeps());
					dependentFeatures.addAll(configDepList.getDependentFeatures());
					dependentFeatures.addAll(layerDepList.getDependentFeatures());
	    		} catch (IOException e) {
	    			throw new RuntimeException(e.getMessage(), e);
	    		}
				
				if (isReqExpLogging) {
					StringBuffer sb = new StringBuffer();
					sb.append("console.log(\"%c" + Messages.JavaScriptModuleBuilder_2 + "\", \"color:blue\");") //$NON-NLS-1$ //$NON-NLS-2$
					  .append("console.log(\"%c"); //$NON-NLS-1$
					for (Map.Entry<String, String> entry : configDeps.getModuleIdsWithComments().entrySet()) {
						sb.append("\t" + entry.getKey() + " (" + entry.getValue() + ")\\r\\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					sb.append("\", \"font-size:x-small\");"); //$NON-NLS-1$
					sb.append("console.log(\"%c" + Messages.JavaScriptModuleBuilder_3 + "\", \"color:blue\");") //$NON-NLS-1$ //$NON-NLS-2$
					  .append("console.log(\"%c"); //$NON-NLS-1$
					for (Map.Entry<String, String> entry : layerDeps.getModuleIdsWithComments().entrySet()) {
						sb.append("\t" + entry.getKey() + " (" + entry.getValue() + ")\\r\\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					sb.append("\", \"font-size:x-small\");"); //$NON-NLS-1$
					result = sb.toString();
				}
				layerDeps.addAll(configDeps);
				request.setAttribute(EXPANDED_DEPENDENCIES, layerDeps);
			}
		}
		return result;
	}
	
	@Override
	public void shutdown(IAggregator aggregator) {
		for (Object reg : registrations) {
			PlatformAggregatorFactory.getPlatformAggregator().unRegisterService(reg);			
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
    	boolean isHasFiltering = RequestUtil.isHasFiltering(request);
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
		if (features == null || level == null || !RequestUtil.isHasFiltering(request)) {
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

		boolean isReqExpLogging = RequestUtil.isRequireExpLogging(request);
		List<ModuleDeps> expandedDepsList = null; 
		if (RequestUtil.isExplodeRequires(request) && level != null) {
			expandedDepsList = new ArrayList<ModuleDeps>();
			/*
			 * Register the RequireExpansionCompilerPass if we're exploding 
			 * require lists to include nested dependencies
			 */
			RequireExpansionCompilerPass recp = 
				new RequireExpansionCompilerPass(
						aggr, 
						features,
						discoveredHasConditionals,
						expandedDepsList,
						(String)request.getAttribute(IHttpTransport.CONFIGVARNAME_REQATTRNAME),
						isReqExpLogging,
						RequestUtil.isPerformHasBranching(request));
			
			compiler_options.customPasses.put(CustomPassExecutionTime.BEFORE_CHECKS, recp);
		}
		if (RequestUtil.isExportModuleName(request) && level != null) {
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
				expandedDepsList == null || expandedDepsList.size() == 0 ? 
						output : new JavaScriptBuildRenderer(output, expandedDepsList, isReqExpLogging),
				createNewKeyGen ? 
						getCacheKeyGenerators(discoveredHasConditionals) : 
						keyGens,
				false);
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
		 * the {@code 0|1} is the value returned by {@link RequestUtil#isExplodeRequires(HttpServletRequest)}. The
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
			boolean requireListExpansion = RequestUtil.isExplodeRequires(request);
			boolean reqExpLogging = RequestUtil.isRequireExpLogging(request);
			boolean hasFiltering = RequestUtil.isHasFiltering(request);
			
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
					RequestUtil.isHasFiltering(request) && 
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
			return eyecatcher + (featureKeyGen != null ? (":(" + featureKeyGen.toString()) + ")" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		@Override
		public List<ICacheKeyGenerator> getCacheKeyGenerators(
				HttpServletRequest request) {
			if (featureKeyGen == null) {
				return Arrays.asList(new ICacheKeyGenerator[]{this});
			}
			List<ICacheKeyGenerator> result = new LinkedList<ICacheKeyGenerator>();
			result.add(new CacheKeyGenerator(null));
			if (RequestUtil.isHasFiltering(request) && getCompilationLevel(request) != null) {
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
