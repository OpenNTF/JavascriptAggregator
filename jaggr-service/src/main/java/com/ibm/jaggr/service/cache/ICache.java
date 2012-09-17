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

package com.ibm.jaggr.service.cache;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.ibm.jaggr.service.layer.ILayer;
import com.ibm.jaggr.service.module.IModule;

/**
 * The aggregator cache is the repository for cached module builds and layers.
 */
public interface ICache extends Serializable, Cloneable {

	/**
	 * Returns a map of the layer build cache entries.
	 * 
	 * @return The layer build cache entries
	 */
	public ConcurrentMap<String, ILayer> getLayers();
	
	/**
	 * Returns a map of the module build cache entries
	 * 
	 * @return The module build cache entries
	 */
	public ConcurrentMap<String, IModule> getModules();
		
	/**
	 * Returns the date and time that this cache object was created.
	 * 
	 * @return The creation date of this cache
	 */
	public long getCreated();
	
	/**
	 * Outputs a string representation of this cache object. The filter string
	 * is just passed along to the module and layer caches and is not otherwise
	 * used by this method.
	 * 
	 * @param writer 
	 *            The writer object to output the cache dump to
	 * @param filter
	 *            A regular expression to filter the output. Passed along to the
	 *            module and layer cache object toString() methods.
	 * @throws IOException
	 */
    public void dump(Writer writer, Pattern filter) throws IOException;

}