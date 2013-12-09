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

package com.ibm.jaggr.core.cache;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.regex.Pattern;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.layer.ILayerCache;
import com.ibm.jaggr.core.module.IModuleCache;

/**
 * The aggregator cache is the repository for cached module builds and layers.
 */
public interface ICache extends Serializable {

	/**
	 * Returns the cache of layer build cache entries.
	 * 
	 * @return The layer build cache entries
	 */
	public ILayerCache getLayers();
	
	/**
	 * Returns the cache of module build cache entries
	 * 
	 * @return The module build cache entries
	 */
	public IModuleCache getModules();
		
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

    /**
     * Called for newly created (or de-serialized) caches to set the aggregator
     * instance for the cache.  Note that the aggregator may not be fully 
     * initialized at the time that this method is called and some aggregator
     * methods (like {@link IAggregator#getConfig()} may return null if called
     * from within this method.
     * 
     * @param aggregator The aggregator instance for this cache object
     */
    public void setAggregator(IAggregator aggregator);
}
