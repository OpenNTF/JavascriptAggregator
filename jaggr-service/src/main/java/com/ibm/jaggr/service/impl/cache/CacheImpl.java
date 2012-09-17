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

package com.ibm.jaggr.service.impl.cache;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.jaggr.service.cache.ICache;
import com.ibm.jaggr.service.layer.ILayer;
import com.ibm.jaggr.service.module.IModule;

public class CacheImpl implements ICache {
	private static final long serialVersionUID = 3919790021174908549L;

	/// The caches
    private ConcurrentMap<String, ILayer> _layerCache;
	private ConcurrentMap<String, IModule> _moduleCache;
	
	// The options associated with this cache instance
    private volatile Map<String, String> _optionsMap;
    
	private volatile String _rawConfig;
	
	private volatile long _depsLastMod;
	
	private final long _created;
	
	/**
	 * @param initialSize The initial size of the cache
	 */
	public CacheImpl(int initialSize, String rawConfig, long depsLastMod, Map<String, String> options) {
		_layerCache = new ConcurrentHashMap<String, ILayer>(initialSize);
		_moduleCache = new ConcurrentHashMap<String, IModule>(initialSize);
		_optionsMap = options;
		_rawConfig = rawConfig;
		_depsLastMod = depsLastMod;
		_created = new Date().getTime();
	}
	
	/**
	 * @return The ILayer cache
	 */
	@Override
	public ConcurrentMap<String, ILayer> getLayers() {
		return _layerCache;
	}
	
	/**
	 * @return The IModule cache
	 */
	@Override
	public ConcurrentMap<String, IModule> getModules() {
		return _moduleCache;
	}
	
	public Map<String, String> getOptionsMap() {
		return _optionsMap;
	}

	/**
	 * @return The last modified date of the cache wide dependencies for this
	 *         cache (config file, expanded dependencies list). 
	 */
	public String getRawConfig() {
		return _rawConfig;
	}

	public long getDepsLastModified() {
		return _depsLastMod;
	}
	
	@Override
	public long getCreated() {
		return _created;
	}
	
	public void setOptionsMap(Map<String, String> optionsMap) {
		_optionsMap = optionsMap;
	}
	
	public void setRawConfig(String rawConfig) {
		_rawConfig = rawConfig;
	}
	
	public void setDepsLastModified(long depsLastMod) {
		_depsLastMod = depsLastMod;
	}
	
	/**
	 * Returns a clone of this object.  Note that although cloning is thread-safe,
	 * it is NOT atomic.  This means that the object being cloned can be changing
	 * while we are in the process of cloning it (e.g. items being added/removed
	 * from the Maps) so there is no guaranty that the cloned object is an exact
	 * copy of the object being cloned at any one point in time.
	 */
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public synchronized Object clone() throws CloneNotSupportedException {
		
		CacheImpl clone = (CacheImpl)super.clone();
		// ConcurrentHashMap doesn't implement a clone method so create a clone by
		// using the copy constructor.
		clone._layerCache = new ConcurrentHashMap<String, ILayer>(_layerCache);
		clone._moduleCache = new ConcurrentHashMap<String, IModule>(_moduleCache);
		// The copy constructor for ConcurrentHashMap creates a shallow copy, so we
		// we need to clone the individual values.  Keys don't need to be cloned since
		// strings are immutable.
		for (Map.Entry<String, ILayer> layer : clone.getLayers().entrySet()) {
			layer.setValue((ILayer)layer.getValue().clone());
		}
		for (Map.Entry<String, IModule> module : clone.getModules().entrySet()) {
			module.setValue((IModule)module.getValue().clone());
		}
		return clone;
	}

    /**
     * Help out the GC by clearing out the cache maps.  
     */
    public synchronized void clear() {
		_layerCache.clear();
		_moduleCache.clear();
    }
    
    /* (non-Javadoc)
     * @see com.ibm.jaggr.service.cache.ICache#dump(java.io.Writer, java.util.regex.Pattern)
     */
    @Override
    public void dump(Writer writer, Pattern filter) throws IOException {
    	String linesep = System.getProperty("line.separator"); //$NON-NLS-1$
    	for (Map.Entry<String, ILayer> entry : _layerCache.entrySet()) {
    		if (filter != null) {
    			Matcher m = filter.matcher(entry.getKey());
    			if (!m.find())
    				continue;
    		}
    		writer.append("ILayer key: ").append(entry.getKey()).append(linesep); //$NON-NLS-1$
    		writer.append(entry.getValue().toString()).append(linesep).append(linesep);
    	}
    	for (Map.Entry<String, IModule> entry : _moduleCache.entrySet()) {
    		if (filter != null) {
    			Matcher m = filter.matcher(entry.getKey());
    			if (!m.find())
    				continue;
    		}
    		writer.append("IModule key: ").append(entry.getKey()).append(linesep); //$NON-NLS-1$
    		writer.append(entry.getValue().toString()).append(linesep).append(linesep);
    	}
    }
}
