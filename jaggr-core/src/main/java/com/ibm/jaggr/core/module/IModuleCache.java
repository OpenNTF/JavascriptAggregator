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

package com.ibm.jaggr.core.module;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.readers.ModuleBuildReader;

/**
 * Interface for module cache object
 */
public interface IModuleCache extends Serializable {
	
	public static final String MODULECACHEINFO_PROPNAME = IModuleCache.class.getName() + ".MODULE_CACHEINFO"; //$NON-NLS-1$

	
	/**
	 * Returns the module with the specified key, or null if the 
	 * module with the specified key is not in the cache
	 * 
	 * @param key the module key
	 * @return the requested module or null
	 */
	IModule get(String key);
	
	/**
	 * Returns a future to the build reader for requested module.  If the build
	 * already exists in the cache, then a completed future for the reader to the
	 * cached build is returned.  Otherwise, a new build is started, and a future
	 * that will complete when the build has finished is returned.
	 */
	Future<ModuleBuildReader> getBuild(HttpServletRequest request, IModule module) throws IOException;
	
	/**
	 * Returns true if the module with the specified key is in the 
	 * cache
	 * 
	 * @param key the module key
	 * @return true if the specified module is in the cache
	 */
	boolean contains(String key);

	/**
	 * Remove all modules in the cache 
	 */
	public void clear();
	
	/**
	 * Returns the number of entries in the layer cache
	 * 
	 * @return the number of entries
	 */
	public int size();
	
	/**
	 * Returns the set of keys associated with entries in the cache
	 * 
	 * @return the key set
	 */
	public Set<String> getKeys();
	
    /**
     * Output the cache info to the specified Writer
     * 
     * @param writer the target Writer
     * @param filter Optional filter argument
     * @throws IOException
     */
	public void dump(Writer writer, Pattern filter) throws IOException;

	/**
	 * Called for newly created (or de-serialized) caches to set the 
	 * aggregator instance for this cache.  Note that the aggregator may not be fully 
     * initialized at the time that this method is called and some aggregator
     * methods (like {@link IAggregator#getConfig()} may return null if called
     * from within this method.
	 * 
	 * @param aggregator The aggregator instance for the cache
	 */
	public void setAggregator(IAggregator aggregator);
}
