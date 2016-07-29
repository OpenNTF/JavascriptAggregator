/*
 * (C) Copyright IBM Corp. 2012, 2016
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

package com.ibm.jaggr.core.impl.cache;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICache;
import com.ibm.jaggr.core.cache.IGenericCache;
import com.ibm.jaggr.core.cache.IGzipCache;
import com.ibm.jaggr.core.layer.ILayerCache;
import com.ibm.jaggr.core.module.IModuleCache;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

public class CacheImpl implements ICache {
	private static final long serialVersionUID = -4409057458310867441L;
	/// The caches
	private ILayerCache _layerCache;
	private IModuleCache _moduleCache;
	private IGzipCache _gzipCache;
	private ConcurrentMap<String, IGenericCache> _namedCaches;

	private Object _control;	// used by cache manager to control cache life span

	private final long _created;

	public CacheImpl(IAggregator aggregator, Object control, ICache oldCache) {
		_layerCache = aggregator.newLayerCache();
		_moduleCache = aggregator.newModuleCache();
		_gzipCache = aggregator.newGzipCache();
		_namedCaches = new ConcurrentHashMap<String, IGenericCache>();
		// If there are any generic cache objects in the old cache , then create new instances
		// of those in the new cache.
		if (oldCache != null) {
			ConcurrentMap<String, IGenericCache> old = ((CacheImpl)oldCache)._namedCaches;
			for (Map.Entry<String, IGenericCache> entry : old.entrySet()) {
				_namedCaches.put(entry.getKey(), entry.getValue().newInstance());
			}
		}
		_control = control;
		_created = new Date().getTime();
	}

	/**
	 * @return The ILayer cache
	 */
	@Override
	public ILayerCache getLayers() {
		return _layerCache;
	}

	/**
	 * @return The IModule cache
	 */
	@Override
	public IModuleCache getModules() {
		return _moduleCache;
	}

	@Override
	public IGzipCache getGzipCache() {
		return _gzipCache;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.ICache#getCache(java.lang.String)
	 */
	@Override
	public IGenericCache getCache(String key) {
		return _namedCaches.get(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.ICache#putIfAbsent(java.lang.String, com.ibm.jaggr.core.cache.IGenericCache)
	 */
	@Override
	public IGenericCache putIfAbsent(String key, IGenericCache cache) {
		return _namedCaches.putIfAbsent(key, cache);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.ICache#remove(java.lang.String)
	 */
	@Override
	public boolean remove(String key) {
		return _namedCaches.remove(key) != null;
	}
	@Override
	public long getCreated() {
		return _created;
	}

	public Object getControlObj() {
		return _control;
	}

	/**
	 * Help out the GC by clearing out the cache maps.
	 */
	public synchronized void clear() {
		_layerCache.clear();
		_moduleCache.clear();
		_gzipCache.clear();
		for (IGenericCache cache : _namedCaches.values()) {
			cache.clear();
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICache#dump(java.io.Writer, java.util.regex.Pattern)
	 */
	@Override
	public void dump(Writer writer, Pattern filter) throws IOException {
		_layerCache.dump(writer, filter);
		_moduleCache.dump(writer, filter);
		_gzipCache.dump(writer, filter);
		for (IGenericCache cache : _namedCaches.values()) {
			cache.dump(writer, filter);
		}
	}

	@Override
	public void setAggregator(IAggregator aggregator) {
		_layerCache.setAggregator(aggregator);
		_moduleCache.setAggregator(aggregator);
		_gzipCache.setAggregator(aggregator);
		for (IGenericCache cache : _namedCaches.values()) {
			cache.setAggregator(aggregator);
		}
	}

}
