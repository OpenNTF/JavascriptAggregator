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

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;

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
	 * Name of the request attribute specifying the ordered
	 * <code>Collection&lt;String&gt;</code> specifying the list of modules
	 * requested.
	 * <p>
	 * The collection's toString() method must return a string representation of
	 * the list that can be used to uniquely identify the order and items in the
	 * list for use as a cache key. Implementors may choose to provide a
	 * condensed presentation for the sake of efficiency, however,
	 * non-displayable characters should be avoided.
	 */
	public static final String REQUESTEDMODULES_REQATTRNAME = IHttpTransport.class
			.getName() + ".REQUESTEDMODULELIST"; //$NON-NLS-1$

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
	 * Name of the request attribute specifying the Boolean flag indicating if
	 * debug logging output about require list expansion should be displayed in
	 * the browser console
	 */
	public static final String EXPANDREQLOGGING_REQATTRNAME = IHttpTransport.class
			.getName() + ".ExpandReqLogging"; //$NON-NLS-1$

	/**
	 * Name of the request attribute specifying the Boolean flag indicating if
	 * has! plugin branching should be performed during require list expansion.
	 */
	public static final String HASPLUGINBRANCHING_REQATTRNAME = IHttpTransport.class
			.getName() + ".HasPluginBranching"; //$NON-NLS-1$
	
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
	 * Name of the request attribute specifying a {@code Set<String>} of
	 * modules ids that, together with their expanded dependencies, will be included
	 * in the response in addition to the list of modules specified by
	 * {@link #REQUESTEDMODULES_REQATTRNAME}.
	 * <p>
	 * This feature is typically used to load a bootstrap layer of modules using
	 * the same request that is used to load the loader and the loader config.
	 * It is not used for aggregator generated requests. In this scenario, the
	 * loader together with any non-AMD modules like the client-side loader
	 * config are specified using {@link #REQUESTEDMODULES_REQATTRNAME} and the
	 * top level AMD modules are specified using {@link #REQUIRED_REQATTRNAME}.
	 * <p>
	 * Note: Modules specifying a loader plugin will be included in the response
	 * only if the loader plugin is specified in the
	 * {@link IConfig#TEXTPLUGINDELEGATORS_CONFIGPARAM} config param, or the
	 * {@link IConfig#JSPLUGINDELEGATORS_CONFIGPARAM} config param. For the
	 * {@code has} loader plugin, the module will be included only if the
	 * feature specified by the plugin is defined in the feature set specified
	 * in the request.
	 */
	public static final String REQUIRED_REQATTRNAME = IHttpTransport.class
			.getName() + ".Required"; //$NON-NLS-1$

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
		 * Before any required module builds (the module(s) specified by the
		 * {@code required} request parameter, plus any of its expanded
		 * dependencies) have been written to the response.
		 */
		BEGIN_REQUIRED_MODULES,

		/**
		 * Before the first required module is written to the response.
		 */
		BEFORE_FIRST_REQUIRED_MODULE,

		/**
		 * Before subsequent required module builds are written to the response.
		 */
		BEFORE_SUBSEQUENT_REQUIRED_MODULE,

		/**
		 * After a required module build has been written to the response. The
		 * distinction between the first and subsequent module builds is made to
		 * facilitate the placing of commas between list items.
		 */
		AFTER_REQUIRED_MODULE,

		/**
		 * After a required module build has been written to the response.
		 */
		END_REQUIRED_MODULES,

		/**
		 * After all normal and required modules have been written to the
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
	 *            This parameter specifies the module id as a string for the following
	 *            {@code type} values:
	 *            <ul>
	 *            <li>{@link LayerContributionType#BEFORE_FIRST_MODULE}</li>
	 *            <li>{@link LayerContributionType#BEFORE_SUBSEQUENT_MODULE}</li>
	 *            <li>{@link LayerContributionType#AFTER_MODULE}</li>
	 *            <li>{@link LayerContributionType#BEFORE_FIRST_REQUIRED_MODULE}
	 *            </li>
	 *            <li>
	 *            {@link LayerContributionType#BEFORE_SUBSEQUENT_REQUIRED_MODULE}
	 *            </li>
	 *            <li>{@link LayerContributionType#AFTER_REQUIRED_MODULE}</li>
	 *            </ul>
	 *            For the following values of {@code type}, {@code arg} specifies
	 *            a {@code Set<String>} of required modules specified in the
	 *            request via the {@link #REQUIRED_REQATTRNAME} request parameter.
	 *            <ul>
	 *            <li>{@link LayerContributionType#BEGIN_REQUIRED_MODULES}</li>
	 *            <li>{@link LayerContributionType#END_REQUIRED_MODULES}</li>
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
	 * layer (i.e. a layer constructed from the modules specified using by
	 * {@link #REQUIRED_REQATTRNAME} plus it's dependencies).
	 * <p>
	 * Excluded modules include those that specify a loader plugin that is not
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
}
