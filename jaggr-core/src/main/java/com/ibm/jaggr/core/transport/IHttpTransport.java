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

package com.ibm.jaggr.core.transport;

import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.transport.IRequestedModuleNames;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Instances of IHttpTransport are responsible for parsing the HTTP request to
 * extract request information such as module list, feature lists and other
 * request parameters, and then 'decorating' the request with the processed
 * values as defined types in request attributes.
 * <p>
 * This interface also provides the AMD loader extension JavaScript used to
 * format and send the requests to the aggregator.
 */
public interface IHttpTransport extends IExtensionInitializer {

	/**
	 * Name of the request attribute specifying the requested module names.
	 * This is an instance of {@link IRequestedModuleNames}.
	 * <p>
	 * The object's toString() method must return a string representation of
	 * the list that can be used to uniquely identify the order and items in the
	 * list for use as a cache key. Implementors may choose to provide a
	 * condensed presentation for the sake of efficiency, however,
	 * non-displayable characters should be avoided.
	 */
	public static final String REQUESTEDMODULENAMES_REQATTRNAME = IHttpTransport.class
			.getName() + ".REQUESTEDMODULENAMES"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the <code>Feature</code> object
	 * for the feature set specified in the request
	 */
	public static final String FEATUREMAP_REQATTRNAME = IHttpTransport.class
			.getName() + ".FEATUREMAP"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the {@link OptimizationLevel}
	 * specifying the requested optimization level for module builds. Not all
	 * module builders may support all (or any) levels.
	 */
	public static final String OPTIMIZATIONLEVEL_REQATTRNAME = IHttpTransport.class
			.getName() + ".OptimizationLevel"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the Boolean flag indicating if
	 * dependency lists in require() calls should be expanded to include nested
	 * dependencies.
	 */
	public static final String EXPANDREQUIRELISTS_REQATTRNAME = IHttpTransport.class
			.getName() + ".ExpandRequireLists"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the Boolean flag indicating if
	 * the aggregator should export the name of the requested module in the
	 * define() functions of anonymous modules.
	 */
	public static final String EXPORTMODULENAMES_REQATTRNAME = IHttpTransport.class
			.getName() + ".ExportModuleNames"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying a {@code Collection;&lt;String&gt;}
	 * containing set of requested locales.  How the requested locales are determined
	 * is implementation specific.
	 */
	public static final String REQUESTEDLOCALES_REQATTRNAME = IHttpTransport.class
			.getName() + ".RequestedLocales"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the Boolean flag indicating if debug logging output
	 * about require list expansion should be displayed in the browser console
	 *
	 * @deprecated this constant is deprecated in favor of the
	 *             {@link #DEPENDENCYEXPANSIONLOGGING_REQATTRNAME} constant
	 */
	public static final String EXPANDREQLOGGING_REQATTRNAME = IHttpTransport.class
			.getName() + ".ExpandReqLogging"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the Boolean flag indicating if debug logging output
	 * about dependency expansion should be displayed in the browser console
	 */
	public static final String DEPENDENCYEXPANSIONLOGGING_REQATTRNAME = IHttpTransport.class
			.getName() + ".DependencyExpansionLogging"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the Boolean flag indicating if
	 * the response should be annotated with comments indicating the names of
	 * the module source files.
	 */
	public static final String SHOWFILENAMES_REQATTRNAME = IHttpTransport.class
			.getName() + ".ShowFilenames"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the Boolean flag indicating if
	 * the responses should not be cached. This flag affects caching of
	 * responses on the client as well as caching of module and layer builds on
	 * the server.
	 */
	public static final String NOCACHE_REQATTRNAME = IHttpTransport.class
			.getName() + ".NoCache"; //$NON-NLS-1$

	/**
	 * Name of the request attributes containing any recognized cache bust
	 * argument from the request.
	 */
	public static final String CACHEBUST_REQATTRNAME = IHttpTransport.class
			.getName() + ".CacheBust"; //$NON-NLS-1$
	/**
	 * Specifies that the text module builder should not wrap the text
	 * in an AMD define(...) function call, and instead should return
	 * the text content as an unadorned string.  Some use cases (e.g.
	 * Dojo's layer builder) require this functionality.
	 */
	public static final String NOTEXTADORN_REQATTRNAME = IHttpTransport.class
			.getName() + ".NoTextAdorn"; //$NON-NLS-1$

	/**
	 * Specifies that the module builders may not add module resources that were
	 * not explicitly requested by the loader to the response. This option may
	 * be set by the HTTP transport based on loader or compiler limitations. For
	 * example, if module name exporting is needed to add unrequested modules to
	 * the response but module name exporting is not available due to other
	 * request parameters (e.g. {@link #EXPORTMODULENAMES_REQATTRNAME} or
	 * {@link #OPTIMIZATIONLEVEL_REQATTRNAME}) then module expansion may not be
	 * supported for the current request. Module builders may specify additional
	 * modules to be included in the response using the {@link ModuleBuild}
	 * constructor that accepts a list of {@link IModule} objects. If this
	 * request attribute is true, then the list of additional modules specified
	 * in the {@link ModuleBuild} constructor will be ignored.
	 */
	public static final String NOADDMODULES_REQATTRNAME = IHttpTransport.class
			.getName() + ".NoExpandModules"; //$NON-NLS-1$
	/**
	 * Name of the request attribute specifying the config var name used to
	 * configure the loader on the client.  The default value is "require".
	 * This parameter may be specified if a different var name is used to
	 * configure the loader
	 * <p>
	 * This information is used by the javascript module builder to locate
	 * the loader config {@code deps} property in order to expand the
	 * modules specified by that property to include nested dependencies.
	 * <p>
	 * This request attribute is not required to be present if no config var
	 * name was specified in the request.
	 */
	public static final String CONFIGVARNAME_REQATTRNAME = IHttpTransport.class
			.getName() + ".ConfigVarName"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying whether or not dependencies specified in require
	 * calls that are contained within AMD modules should be expanded when performing server-side
	 * expansion of dependent modules. Used only for application generated requests specifying the
	 * <code>deps</code> and/or <code>preloads</code> request parameters.
	 * <p>
	 * This request attribute corresponds to the <code>includeRequireDeps</code> request parameter
	 * and is set by the transport based on the value of the request parameter.
	 */
	public static final String INCLUDEREQUIREDEPS_REQATTRNAME = IHttpTransport.class
			.getName() + ".IncludeRequireDeps";  //$NON-NLS-1$


	/**
	 * Name of the request attribute specifying whether or not dependencies specified using has!
	 * loader plugin conditionals for undefined features (either in define or require dependencies)
	 * should be included when performing server-side expansion of dependent modules. Used only for
	 * application generated requests specifying the <code>deps</code> and/or <code>preloads</code>
	 * request parameters.
	 * <p>
	 * If this attribute is true, then the modules specified by both branches of a has! plugin
	 * conditional for an undefined feature will be added to the layer being built, along with both
	 * of those modules' dependencies.
	 * <p>
	 * This request attribute corresponds to the <code>includeUndefinedFeatureDeps</code> request
	 * parameter and is set by the transport based on the value of the request parameter.
	 */
	public static final String INCLUDEUNDEFINEDFEATUREDEPS_REQATTRNAME = IHttpTransport.class
			.getName() + ".IncludeUndefinedFeatureDeps"; //$NON-NLS-1$


	/**
	 * Name of the request attribute specifying if the server should assert that the modules
	 * specified by the <code>deps</code> and/or <code>preloads</code>
	 * request parameters, plus their nested dependencies, include no nls modules.  This is
	 * helpful for keeping nls resources out of the bootstrap layer in cases where code needs
	 * to run on the client to determine the user's preferred locale.  If this options is
	 * specified and the response would contain nls resources, then a 400 - Bad Request
	 * response status is returned.
	 * <p>
	 * This request attribute corresponds to the <code>assertNoNLS</code> request
	 * parameter and is set by the transport based on the value of the request parameter.
	 */
	public static final String ASSERTNONLS_REQATTRNAME = IHttpTransport.class
			.getName() + "AssertNoNLS"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying if server expansion of dependencies for
	 * requested modules should be performed.  If true, then the expanded dependencies
	 * of the modules specified in the request (the modules returned by
	 * {@link IRequestedModuleNames#getModules()}) will be included in the response, excluding
	 * the modules specified by {@link IRequestedModuleNames#getExcludes()}
	 * and their dependencies.
	 * <p>
	 * This options take precedence over {@link IHttpTransport#EXPANDREQUIRELISTS_REQATTRNAME}
	 * in the event that both are specified.
	 */
	public static final String SERVEREXPANDLAYERS_REQATTRNAME = IHttpTransport.class
			.getName() + "ServerExpandLayers";  //$NON-NLS-1$

	/**
	 * Supported optimization levels. Module builders are not required to
	 * support all, or any, of these.
	 */
	public enum OptimizationLevel {
		NONE, WHITESPACE, SIMPLE, ADVANCED
	};

	/**
	 * Called to parse the HTTP request and decorate the request with the
	 * request attributes defined in this interface.
	 *
	 * @param request
	 *            The HTTP request
	 * @throws IOException
	 */
	public void decorateRequest(HttpServletRequest request) throws IOException;

	/**
	 * Called by aggregator extensions to contribute JavaScript that will be
	 * included in the loader extension JavaScript module that is loaded by the
	 * client.
	 * <p>
	 * The loader extension JavaScript is loaded before the AMD loader. Code
	 * contributed to the loader extension JavaScript may modify or augment the
	 * loader config, such as adding paths or aliases, etc.
	 * <p>
	 * In addition, this interface defines the property
	 * <code>urlProcessors</code> which is in scope when the JavaScript
	 * contributed by this method is run. This property is an array of functions
	 * that are each called just before a request is sent to the aggregator. The
	 * function takes a single parameter which is the aggregator URL and it
	 * returns the same or updated URL. Extensions wishing to contribute query
	 * args to aggregator URLs, or otherwise modify the URL, may do so by adding
	 * a url processor function to this array in the JavaScript contribution as
	 * shown in the following example:
	 * <p>
	 * <code>urlProcessors.push(function(url) {return url+'&foo=bar'});</code>
	 * <p>
	 * The mechanism by which the loader extension JavaScript is delivered to
	 * the client is outside the scope of this interface.  A typical implementation
	 * will register an {@link IResourceFactory} which returns an {@link IResource}
	 * object that will deliver the loader extension JavaScript when the AMD module
	 * which has been mapped to the resource URI for the loader extension JavaScript
	 * is requested.
	 *
	 * @param contribution
	 *            The JavaScript being contributed.
	 */
	public void contributeLoaderExtensionJavaScript(String contribution);

	/**
	 * Enum defining the constants for the various layer contribution types.
	 */
	public enum LayerContributionType {

		/**
		 * Before anything has been written to the response.
		 */
		BEGIN_RESPONSE,

		/**
		 * Before any modules build outputs have been written to the response
		 */
		BEGIN_MODULES,

		/**
		 * Before the first module build is written to the response.
		 */
		BEFORE_FIRST_MODULE,

		/**
		 * Before subsequent module builds are written to the response. The
		 * distinction between the first and subsequent module builds is made to
		 * facilitate the placing of commas between list items.
		 */
		BEFORE_SUBSEQUENT_MODULE,

		/**
		 * After the module build as been written to the response.
		 */
		AFTER_MODULE,

		/**
		 * After all module builds have beeen written to the respnose
		 */
		END_MODULES,

		/**
		 * Before any layer module builds (the module(s) specified by the
		 * {@code required} request parameter, plus any of its expanded
		 * dependencies) have been written to the response.
		 */
		BEGIN_LAYER_MODULES,

		/**
		 * Before the first layer module is written to the response.
		 */
		BEFORE_FIRST_LAYER_MODULE,

		/**
		 * Before subsequent layer module builds are written to the response.
		 */
		BEFORE_SUBSEQUENT_LAYER_MODULE,

		/**
		 * After a layer module build has been written to the response. The
		 * distinction between the first and subsequent module builds is made to
		 * facilitate the placing of commas between list items.
		 */
		AFTER_LAYER_MODULE,

		/**
		 * After a layer module build has been written to the response.
		 */
		END_LAYER_MODULES,

		/**
		 * After all normal and layer modules have been written to the
		 * response.
		 */
		END_RESPONSE
	}

	/**
	 * Returns a string value that will be added to the layer in the location
	 * specified by {@code type}, or null if the transport has no contribution
	 * to make. This method is provided to allow the transport to inject
	 * scaffolding JavaScript that may be required by the AMD loader.
	 *
	 * @param request
	 *            The request object
	 * @param type
	 *            The layer contribution type
	 * @param arg
	 *            This parameter specifies a {@link ModuleInfo} object for the following
	 *            {@code type} values:
	 *            <ul>
	 *            <li>{@link LayerContributionType#BEFORE_FIRST_MODULE}</li>
	 *            <li>{@link LayerContributionType#BEFORE_SUBSEQUENT_MODULE}</li>
	 *            <li>{@link LayerContributionType#AFTER_MODULE}</li>
	 *            <li>{@link LayerContributionType#BEFORE_FIRST_LAYER_MODULE}
	 *            </li>
	 *            <li>
	 *            {@link LayerContributionType#BEFORE_SUBSEQUENT_LAYER_MODULE}
	 *            </li>
	 *            <li>{@link LayerContributionType#AFTER_LAYER_MODULE}</li>
	 *            </ul>
	 *            For the following values of {@code type}, {@code arg} specifies
	 *            a {@code Set<String>} of modules which are to be required by the
	 *            loader via a synthetically generated require() call.
	 *            <ul>
	 *            <li>{@link LayerContributionType#BEGIN_LAYER_MODULES}</li>
	 *            <li>{@link LayerContributionType#END_LAYER_MODULES}</li>
	 *            </ul>
	 *            For all other values of {@code type}, {@code mid} is null.
	 *
	 * @return A string value that is to be added to the layer in the location
	 *         specified by {@code type}.
	 */
	public String getLayerContribution(HttpServletRequest request,
			LayerContributionType type, Object arg);

	/**
	 * Returns true if the specified module can be included in a server expanded
	 * layer (i.e. a bootstrap layer).
	 * <p>
	 * Excluded modules consist of those that specify a loader plugin that is not
	 * included in {@link IConfig#TEXTPLUGINDELEGATORS_CONFIGPARAM} or
	 * {@link IConfig#JSPLUGINDELEGATORS_CONFIGPARAM}, and modules that specify
	 * an absolute or server relative URL.
	 *
	 * @param request
	 *            The request object
	 * @param mid
	 *            The module id
	 * @return True if the specified module can be included in a server expanded
	 *         layer
	 */
	public boolean isServerExpandable(HttpServletRequest request, String mid);


	/**
	 * Returns a cache key generator for the JavaScript contained in the
	 * loader extension JavaScript and output by
	 * {@link #getLayerContribution(HttpServletRequest, LayerContributionType, Object)}
	 * . If the output JavaScript is invariant with regard to the request for
	 * the same set of modules, then this function may return null.
	 *
	 * @return The cache key generator for the JavaScript output by this
	 *         transport.
	 */
	public List<ICacheKeyGenerator> getCacheKeyGenerators();

	/**
	 * Returns a map of module name to module id pairs that can be used to encode the module names
	 * when requesting modules.
	 *
	 * @return an unmodifiable collection of module name to module id pairs or null if the transport
	 *         doesn't support id mapping of module names.
	 */
	public Map<String, Integer> getModuleIdMap();

	/**
	 * Returns the name of the client side registration function used to register module-name/module-id
	 * mappings.  The function takes a single 2-element array parameter, the first element being an
	 * array of module name arrays and the second element being the corresponding array of module ids
	 * arrays for the modules named by the first array.
	 * <p><pre>
	 * [
	 *     [
	 *         ["module1", "module2", "module3"],
	 *         ["module4"],
	 *         ["module5", "module6"]
	 *     ],
	 *     [
	 *         [1,2,3],
	 *         [4],
	 *         [5,6]
	 *     ]
	 * ]
	 * </pre>
	 * <p>The JavaScript module builder emits code to register module-ids for expanded dependencies on the
	 * client, using the string arrays (the first element) both to specify the expanded dependencies and
	 * as part of the id mappings.
	 * <p>The id mappings are used by the client to encode requested module names when requesting
	 * modules from the aggregator in order to minimize URL lengths.
	 *
	 * @return the client-side registration function name, or null if the transport doesn't support
	 *         id mapping of module names.
	 */
	public String getModuleIdRegFunctionName();

	/**
	 * Returns the plugin name used by the transport for text resources (e.g. combo/text).
	 *
	 * @return the plugin name
	 */
	public String getAggregatorTextPluginName();

	public class ModuleInfo {
		private final String mid;
		private final boolean isScript;
		public ModuleInfo(String mid, boolean isScript) {
			this.mid = mid;
			this.isScript = isScript;
		}

		/**
		 * @return the module id
		 */
		public String getModuleId() { return mid; }

		/**
		 * @return true if the module is a script module
		 */
		public boolean isScript() { return isScript; }
	}

}
