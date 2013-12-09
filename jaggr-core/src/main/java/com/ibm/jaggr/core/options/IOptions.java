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

package com.ibm.jaggr.core.options;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import com.ibm.jaggr.core.IAggregator;

/**
 * This interface defines property names for standard aggregator options
 * properties. It also provides convenience getters for those properties.
 * <p>
 * The options are backed by a {@link Properties} object, so any property,
 * including properties not defined by this interface, can be read using
 * {@link #getOption(String)} and set using
 * {@link #setOption(String, String)}.
 * <p>
 * The options are read on bundle activation from aggregator.properties located
 * in the home directory of the user that started the server.
 * <p>
 * Aggregator options are server-wide, meaning the each aggregator servlet
 * running on a given server shares the same options. A single OSGi service is
 * registered using the IOptions interface during bundle activation for the
 * aggregator bundle. Calls to {@link IAggregator#getOptions()} return this
 * single instance of the options object.
 */
public interface IOptions {

	/**
	 * Name of aggregator propertis file located in the home directory
	 * of the user that launched the server.  Instances of IOptions
	 * are initialized using this properties file.
	 */
	public static final String propsFilename = "aggregator.properties"; //$NON-NLS-1$

	/**
	 * Name of property that specifies if the aggregator is running in
	 * development mode. When running in development mode, the last-modified
	 * dates of source files comprising an aggregated response are checked
	 * against the if-modified-since date in the request for every request.
	 * <p>
	 * Development mode also enables enhanced error reporting, with information
	 * about errors that occur on the server displayed in the browser console
	 * using console.error() calls.
	 * <p>
	 * Development mode also enables all debug mode features
	 * <p>
	 * Valid values: <code>true/false</code>
	 */
	public static final String DEVELOPMENT_MODE = "developmentMode"; //$NON-NLS-1$
	
	/**
	 * Name of property that specifies if the aggregator is running in debug
	 * mode. Debug mode features are useful for diagnosing problems and include
	 * enabling of URL query args to disable optimization, enable require list
	 * expansion logging, and emitting of module names in aggregated responses.
	 * <p>
	 * Debug mode features do not negatively impact overall server performance 
	 * like some development mode features do.
	 * <p>
	 * All features enabled by debug mode are also enabled by development mode.
	 * Valid values: <code>true/false</code>
	 */
	public static final String DEBUG_MODE = "debugMode"; //$NON-NLS-1$

	/**
	 * Name of property that specifies if the aggregator should not perform
	 * has.js feature trimming of javascript code.
	 * <p>
	 * Valid values: <code>true/false</code>
	 */
	public static final String DISABLE_HASFILTERING = "disableHasFiltering"; //$NON-NLS-1$
	
	/**
	 * Name of property that specifies if the dependency lists in require()
	 * calls should not be expanded to include nested dependencies.
	 * <p>
	 * Valid values: <code>true/false</code>
	 */
	public static final String DISABLE_REQUIRELISTEXPANSION = "disableRequireListExpansion"; //$NON-NLS-1$
	
	/**
	 * Name of property that specifies if has! plugin branching should be
	 * disabled during require list expansion.
	 */
	public static final String DISABLE_HASPLUGINBRANCHING = "disableHasPluginBranching"; //$NON-NLS-1$
	
	/**
	 * Name of property that specifies a cache bust string. This is an arbitrary
	 * string that is associated with the serialized meta-data for aggregator
	 * caches and the module dependency maps. When these data structures are
	 * de-serialized on server restarts, the saved value is compared against the
	 * value that is read from the current options, and if the values don't
	 * match, then the de-serialized data is discarded and the caches and
	 * dependency maps are deleted and rebuilt.
	 * <p>
	 * Valid values: <code>String</code>
	 */
	public static final String CACHEBUST = "cacheBust"; //$NON-NLS-1$

	/**
	 * Name of property that specifies the delay in seconds to wait before
	 * asynchronously deleting a cache file that has been queued for deletion.
	 * <p>
	 * Valid values: Integer value >= 0
	 * @see #DEFAULT_DELETE_DELAY
	 */
	public static final String DELETE_DELAY = "deleteDelay"; //$NON-NLS-1$

	/**
	 * Name of property to specify if the dependency list of required modules
	 * specified in the define() function of AMD modules should be validated,
	 * during module minification, against the the dependency list that was used
	 * to create the dependency graph for require list expansion. The default
	 * value, if not specified, is true. This option has meaning only when
	 * developmentMode is enabled.
	 * <p>
	 * A dependency verification failure results in discarding of the response
	 * being processed and replacement with a response that invokes a JavaScript
	 * alert message on the browser indicating the module that caused the
	 * failure, and instructions to clear the browser cache and reload the page.
	 * It also causes dependencies to be revalidated on the server (the
	 * equivilent if issuing a 'aggregator validatedeps' console command).
	 * <p>
	 * Valid values: <code>true/false</code>
	 */
	public static final String VERIFY_DEPS = "verifyDeps"; //$NON-NLS-1$
	
	/**
	 * Name of property to specify the directory to use for cache files. If not
	 * specified, then the plugin state area for the bundle is used (i.e. the
	 * value returned by
	 * {@link Platform#getStateLocation(org.osgi.framework.Bundle)}).
	 */
	public static final String CACHE_DIRECTORY = "cacheDirectory"; //$NON-NLS-1$
	
	/** The default value returned by {@link #getDeleteDelay()} */
	public static final int DEFAULT_DELETE_DELAY = 3*60; // 3 minutes

	
	
	/**
	 * Convenience method for reading the {@link #VERIFY_DEPS} 
	 * options property.
	 * 
	 * @return The value of the {@link #VERIFY_DEPS} property 
	 * as a boolean
	 */
	public boolean isVerifyDeps();

	/**
	 * Convenience method for reading the {@link #DISABLE_REQUIRELISTEXPANSION} 
	 * options property.
	 * 
	 * @return The value of the {@link #DISABLE_REQUIRELISTEXPANSION} property 
	 * as a boolean
	 */
	public boolean isDisableRequireListExpansion();

	/**
	 * Convenience method for reading the {@link #DEVELOPMENT_MODE} 
	 * options property.
	 * 
	 * @return The value of the {@link #DEVELOPMENT_MODE} property 
	 * as a boolean
	 */
	public boolean isDevelopmentMode();

	/**
	 * Convenience method for reading the {@link #DEBUG_MODE} 
	 * options property.
	 * 
	 * @return The value of the {@link #DEBUG_MODE} property 
	 * as a boolean
	 */
	public boolean isDebugMode();

	/**
	 * Convenience method for reading the {@link #DISABLE_HASFILTERING} 
	 * options property.
	 * 
	 * @return The value of the {@link #DISABLE_HASFILTERING} property 
	 * as a boolean
	 */
	public boolean isDisableHasFiltering();
	
	
	/**
	 * Convenience method for reading the {@link #DISABLE_HASPLUGINBRANCHING} 
	 * options property.
	 * 
	 * @return The value of the {@link #DISABLE_HASPLUGINBRANCHING} property 
	 * as a boolean
	 */
	public boolean isDisableHasPluginBranching();

	/**
	 * Convenience method for reading the {@link #CACHEBUST} options
	 * property.
	 * 
	 * @return The value of the {@link #CACHEBUST} options property.
	 */
	public String getCacheBust();
	
	/**
	 * Convenience method for reading the {@link #DELETE_DELAY} 
	 * options property.
	 * 
	 * @return The value of the {@link #DELETE_DELAY} property 
	 * as an int.  If the property is not set, then 
	 * {@link #DEFAULT_DELETE_DELAY} is returned.
	 */
	public int getDeleteDelay();
	
    /**
     * Convenience method for reading the {@link #CACHE_DIRECTORY}
     * options property.
     * 
     * @return The value of the {@link #CACHE_DIRECTORY} property
     */
	
    public String getCacheDirectory();
    
    /**
     * Returns the value of the specified option
     * 
     * @param name The option name
     * @return The option value
     */
    public String getOption(String name);
    
	/**
	 * Sets the named option to the specified value.
	 * <p>
	 * Use this method sparingly. Calling this method results in options
	 * listeners being called to inform them that the options have changed. Some
	 * listeners react to this by performing actions that can have significant
	 * adverse effects on performance. The cache manager, for example, purges
	 * the cache of all cached responses.
	 * <p>
	 * This method should not generally be called as part of normal operation of
	 * the aggregator. I may be called, for example, by a console command
	 * handler that allows admins or developers to set properties while the
	 * server is running. Changes to options made using this method are
	 * persisted to the properties file.
	 * 
	 * @param name
	 *            The name of the option to set
	 * @param value
	 *            The new option value.  If null, then {@code name} is
	 *            reset to its default value.
	 * @throws IOException
	 *             If an error occurs persisting the updated options to the
	 *             aggregator properties file.
	 */
    public void setOption(String name, String value) throws IOException;
    
	/**
	 * Convenience method for setting options with boolean values. Calling this
	 * method is equivalent to calling
	 * {@code setOption(name, Boolean.toString(value));}.
	 * 
	 * @param name
	 *            The name of the option to set
	 * @param value
	 *            The boolean value.
	 * @throws IOException
	 *             If an error occurs persisting the updated options to the
	 *             aggregator properties file.
	 */
    public void setOption(String name, boolean value) throws IOException;
    
	/**
	 * Returns an immutable map of the current options and their values.
	 * 
	 * @return A map of the current option name/value pairs.
	 */
    public Map<String, String> getOptionsMap();
    
    /**
     * Returns the name that can be used to track this options object using the IOptionsListener
     * interface.
     * 
     * @return the options name
     */
    public String getName();
}
