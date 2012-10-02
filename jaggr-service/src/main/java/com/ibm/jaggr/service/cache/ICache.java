/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.cache;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.regex.Pattern;

import com.ibm.jaggr.service.layer.ILayerCache;
import com.ibm.jaggr.service.module.IModuleCache;

/**
 * The aggregator cache is the repository for cached module builds and layers.
 * 
 * @author chuckd@us.ibm.com
 */
public interface ICache extends Serializable, Cloneable {

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

}