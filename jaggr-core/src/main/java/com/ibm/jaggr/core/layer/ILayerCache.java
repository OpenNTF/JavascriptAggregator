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

package com.ibm.jaggr.core.layer;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;


/**
 * Interface for layer cache object
 */
public interface ILayerCache extends Serializable {
	
	/**
	 * Returns a layer object for the specified request.  If a layer is already in 
	 * the cache that can satisfy the request, it is returned.  Otherwise, a new 
	 * layer is created and added to the cache before returning it.
	 * 
	 * @param request the request object
	 * @return the layer object
	 */
	public ILayer getLayer(HttpServletRequest request);
	
	/**
	 * Returns the layer with the specified key, or null if the layer with the
	 * specified key is not in the cache. The cache key for the layer can be
	 * determined by calling {@link ILayer#getKey()}
	 * 
	 * @param key
	 *            the layer key
	 * @return the requested layer or null
	 */
	public ILayer get(String key);
	
	/**
	 * Returns true if the layer with the specified key is in the 
	 * cache
	 * 
	 * @param key the layer key
	 * @return true if the specified layer is in the cache
	 */
	public boolean contains(String key);
	
	/**
	 * Removes all layer objects in the cache
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
