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

package com.ibm.jaggr.core;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpServlet;

import com.ibm.jaggr.core.IExtensionInitializer.IExtensionRegistrar;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigListener;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.layer.ILayerCache;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.transport.IHttpTransport;

/**
 * Interface for the AMD Aggregator. Provides accessors to other aggregator
 * components and some service methods. Implementors of this interface extend
 * {@link HttpServlet}, so they are created by the framework when a servlet
 * implementing this interface is instanciated.
 * <p>
 * The aggregator registers as an OSGi service using this interface and the
 * servlet alias as the <code>name</code> service property.
 * <p>
 * When servicing requests, a reference to the aggregator may be obtained from
 * the request attribute named {@link #AGGREGATOR_REQATTRNAME}. It may also be
 * obtained from the session context attributes using the same name.
 */
public interface IAggregator {

	/**
	 * HTTP request attribute name for the {@link IAggregator} instance
	 * associated with a given request
	 */
	public static final String AGGREGATOR_REQATTRNAME = IAggregator.class
			.getPackage().getName() + ".Aggregator"; //$NON-NLS-1$

	/**
	 * Name of the request attribute which holds an instance of a
	 * {@link ConcurrentMap} which can be used by module builders to store
	 * request specific properties in a thread-safe way.
	 */
	public static final String CONCURRENTMAP_REQATTRNAME = IAggregator.class
			.getName() + ".ConcurrentMap"; //$NON-NLS-1$

	/**
	 * Returns the name of the servlet implementing this interface. This is same
	 * as the value of the alias attribute in the &lt;servlet&gt; element of the
	 * plugin.xml for the bundle that defines the servlet.
	 * 
	 * @return The name of the servlet.
	 */
	public String getName();

	/**
	 * Returns the config object for this aggregator. The aggregator config is
	 * specified using the <code>config</code> servlet init-param. The URI
	 * specified by this init-param points to a server side AMD config file that
	 * specifies the aggregator config propertes using JSON format in the same
	 * way that the client side loader config JSON specifies the AMD
	 * configuration for the loader on the client. \
	 * 
	 * @return The config object for this aggregator
	 */
	public IConfig getConfig();

	/**
	 * Returns the options object for this aggregator. Options are specified in
	 * the server wide aggregator.properties file, located in the home directory
	 * of the user that launched the server.
	 * 
	 * @return The current aggregator options.
	 */
	public IOptions getOptions();

	public IExecutors getExecutors();
	/**
	 * Returns the cache manager object for this aggregator. The cache manager
	 * provides methods for asynchronously maintaining cached resources.
	 * 
	 * @return The cache manager for this aggregator
	 */
	public ICacheManager getCacheManager();

	/**
	 * Returns the dependencies object for this aggregator. Used for expanding
	 * module dependencies.
	 * 
	 * @return The dependencies object for this aggregator.
	 */
	public IDependencies getDependencies();

	/**
	 * Returns the HTTP transport in use by this object
	 * 
	 * @return the HTTP transport
	 */
	public IHttpTransport getTransport();

	/**
	 * Returns a new {@link IResource} for the specified URI. The aggregator
	 * will create the new resource using one of the registered resource
	 * factories.
	 * <p>
	 * The aggregator will select the factory from among the registered
	 * {@link IResourceFactory} extensions by testing the provided {@code uri}
	 * against the scheme attribute of each of the registered resource factories
	 * as follows:
	 * <p>
	 * Iterate through the registered resource factory extensions looking for
	 * extensions that specify a <code>scheme</code> attribute matching the
	 * scheme of <code>uri</code> or a <code>scheme</code> attribute of
	 * <code>*</code>. For each matching extension, call the
	 * {@link IResourceFactory#handles(URI)} method and if the method returns
	 * true, then call the {@link IResourceFactory#newResource(URI)} method to
	 * create the new resource.
	 * <p>
	 * The iteration order of the resource factories is determined by the order
	 * that <code>resourcefactories</code> init-params are declared within the
	 * <code>servlet</code> element defining the aggregator servlet, the order
	 * that the <code>factory</code> elements are declared in the resource
	 * factory extensions within the plugin.xml{s}, and on the insertion
	 * positions of extensions registered programatically using
	 * {@link IExtensionRegistrar#registerExtension}.
	 * <p>
	 * The registered resource factory extension may be obtained by calling
	 * {@link IAggregator#getResourceFactoryExtensions()};
	 * <p>
	 * If a satisfactory resource factory cannot be found, then the unchecked
	 * {@link UnsupportedOperationException} is thrown.
	 * 
	 * @param uri
	 *            The URI for the resource
	 * @return The newly created resource object.
	 * 
	 * @throws UnsupporteOperationException
	 *             if there is no {@link IResourceFactory} registered to handle the
	 *             specified uri.
	 */
	public IResource newResource(URI uri);

	/**
	 * Returns an {@link IModuleBuilder} for the specified arguments. The
	 * aggregator will select the builder from among the registered module
	 * builder extensions by testing the provided <code>mid</code> and
	 * <code>res</code> arguments against the attributes for each of the
	 * registered extensions as follows:
	 * <p>
	 * Iterate through the registered module builder extensions looking for
	 * extensions that specify an <code>extension</code> attribute that matches
	 * the file extension of the resource specified by <code>res</code> or an
	 * <code>extension</code> attribute of <code>*</code>. For each matching
	 * module builder extension, if the extension does not specify a
	 * <code>plugin</code> attribute or the value of the <code>plugin</code>
	 * attribute matches the plugin specified by <code>mid</code> (if any), then
	 * call the module builder's
	 * {@link IModuleBuilder#handles(String, IResource)} method. If the method
	 * returns true, then the module builder is returned to the caller.
	 * <p>
	 * The iteration order of the module builders is determined by the order
	 * that <code>modulebuilders</code> init-params are declared within the
	 * <code>servlet</code> element defining the aggregator servlet, the order
	 * that the <code>builder</code> elements are declared in the module
	 * builder extensions within the plugin.xml{s}, and on the insertion
	 * positions of extensions registered programatically using
	 * {@link IExtensionRegistrar#registerExtension}.
	 * <p>
	 * The registered module builder extensions may be obtained by calling
	 * {@link #getModuleBuilderExtensions()}.
	 * <p>
	 * If a satisfactory module builder still cannot be found, then the
	 * unchecked {@link UnsupportedOperationException} is thrown.
	 * 
	 * @param mid
	 *            The module id for the module to be built
	 * @param res
	 *            The resource for the module
	 * @return The module builder for the source
	 */
	public IModuleBuilder getModuleBuilder(String mid, IResource res);

	/**
	 * Returns the servlet init-params for this aggregator. The servlet
	 * init-params are defined using &lt;init-param&gt; elements within the
	 * &lt;servlet&gt; element in the plugin.xml
	 * 
	 * @return The servlet init-params
	 */
	public InitParams getInitParams();

	/**
	 * Returns this aggregator's working directory. This is the location on the
	 * file system where the aggregator stores cache files and serialized
	 * objects.
	 * 
	 * @return The working directory
	 */
	public File getWorkingDirectory();

	/**
	 * Reloads the config data using the latest values in the config file
	 * specified in the <code>config</code> init-param of the servlet.
	 * <p>
	 * Any services registered using the {@link IConfigListener} interface are
	 * notified.
	 * 
	 * @return True if the config has been modified since it was last loaded.
	 * @throws IOException
	 */
	public boolean reloadConfig() throws IOException;

	/**
	 * Returns this instance of the aggregator as a HttpServlet.
	 * 
	 * @return The aggregator as a HttpServlet
	 */
	public HttpServlet asServlet();

	/**
	 * Returns an {@link Iterable} for the collection of resource factory
	 * extensions that have been registered for this aggregator. This includes
	 * extensions obtained from the eclipse extension registry, as well as
	 * extensions registered though the {@link IExtensionRegistrar} interface.
	 * 
	 * @return An Iterable to the collection of resource factory extensions for
	 *         this aggregator
	 */
	public Iterable<IAggregatorExtension> getResourceFactoryExtensions();

	/**
	 * Returns an {@link Iterable} for the collection of module builder
	 * extensions that have been registered for this aggregator. This includes
	 * extensions obtained from the eclipse extension registry, as well as
	 * extensions registered though the {@link IExtensionRegistrar} interface.
	 * 
	 * @return An Iterable to the collection of module builder extensions for
	 *         this aggregator
	 */
	public Iterable<IAggregatorExtension> getModuleBuilderExtensions();

	/**
	 * Returns the {@code IAggregatorExtension} for the HttpTransport
	 * for this aggregator.
	 * 
	 * @return The IAggregatorExtension for the http transport.
	 */
	public IAggregatorExtension getHttpTransportExtension();
	/**
	 * Factory method for IModule instances.
	 * 
	 * @param mid
	 *            the module id
	 * @param uri
	 *            the URI to the module source
	 * @return the new IModule instance
	 */
	public IModule newModule(String mid, URI uri);

	/**
	 * Factory method for ILayerCache
	 * 
	 * @return the new ILayerCache instance
	 */
	public ILayerCache newLayerCache();
	
	/**
	 * Factory method for IModuleCache
	 * 
	 * @return the new IModuleCache instance
	 */
	public IModuleCache newModuleCache();

	/**
	 * Performs substitution of variable names of the form ${name} with the values
	 * defined in java system properties, or with the value returned by a registered
	 * variable resolver that implements the interface {@link IVariableResolver}.
	 * Variable resolvers are registered in the OSGi service registry.  If more than
	 * one resolver is registered, the first one that returns a value for the variable
	 * will be used.  If no value is found for a variable, then the variable
	 * identifier is unmodified in the returned string.
	 * 
	 * @param str The string for which variables should be substituted
	 * @return The input string with variables of the form ${name} replaced with
	 * the variable values.
	 */
	public String substituteProps(String str);
	
	/**
	 * Like {@link #substituteProps(String)}, but allows the caller to specify
	 * a transformer class to process the substitution values.
	 * 
	 * @param str The input string
	 * @param transformer An instance of {@link SubstitutionTransformer}
	 * @return The transformed string
	 */
	public String substituteProps(String str, SubstitutionTransformer transformer);
	
	/**
	 * Transformer interface used by 
	 * {@link IAggregator#substituteProps(String, SubstitutionTransformer)}. 
	 */
	public interface SubstitutionTransformer {
		/**
		 * Transforms the string property being substituted.
		 * 
		 * @param name the property name
		 * @param value the property value
		 * @return the transformed property value
		 */
		String transform(String name, String value);
	};
}
