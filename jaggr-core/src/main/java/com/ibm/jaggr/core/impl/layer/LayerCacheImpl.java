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

package com.ibm.jaggr.core.impl.layer;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.io.Writer;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.EvictionListener;
import com.googlecode.concurrentlinkedhashmap.Weigher;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.layer.ILayerCache;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.RequestUtil;
import com.ibm.jaggr.core.util.TypeUtil;

/**
 * This class implements the {@link ILayerCache} interface by using
 * {@link ConcurrentLinkedHashMap} to create a map that supports a maximum
 * number of cached entries with eviction notification of LRU entries. It also
 * adds methods for cloning and dumping the cache contents.
 * <p>
 * The cache actually consists of two maps.  The layerMap is a map of 
 * {@link ILayer} objects associated with keys that identify the layer 
 * according to the modules contained in the layer.  The layerBuildMap
 * is a map of {@link CacheEntry} objects associated with keys that 
 * identify the layer as well as build specific identifiers.  There is 
 * a one-to-many association of layerMap entries to layerBuildMap 
 * entries.  Each {@link CacheEntry} object identifies the filename(s)
 * of the files that contain the built content for the layer build.
 * <p>
 * The layerBuildMap can have a size limit.  When the limit is reached
 * LRU entries will be evicted by calling the eviction listener specified
 * when the map was created.  The eviction listener removes the association
 * between the evicted entry and the ILayer object.  When a ILayer object
 * no longer has any CacheEntry objects in the layerBuildMap, it is 
 * removed from the layerMap.
 */
public class LayerCacheImpl implements ILayerCache, Serializable {
	private static final long serialVersionUID = -3231549218609175774L;

	static final int DEFAULT_MAXLAYERCACHECAPACITY_MB = 500;
	
	private ConcurrentMap<String, LayerImpl> layerMap;
	
	private ConcurrentLinkedHashMap<String, CacheEntry> layerBuildMap;
	
	private IAggregator aggregator;
	
	private AtomicInteger newLayerId = new AtomicInteger(0);
	
	private int maxCapacity = DEFAULT_MAXLAYERCACHECAPACITY_MB;
	
	private AtomicInteger numEvictions = new AtomicInteger(0);
	
	private ReadWriteLock cloneLock = new ReentrantReadWriteLock();
	
	// Used by Serialization proxy
	protected LayerCacheImpl() {}
	
	/**
	 * Copy constructor.  Used by sub-classes that need to override writeReplace
	 *  
	 * @param layerCache
	 */
	protected LayerCacheImpl(LayerCacheImpl layerCache) {
		layerMap = layerCache.layerMap;
		layerBuildMap = layerCache.layerBuildMap;
		aggregator = layerCache.aggregator;
		newLayerId = layerCache.newLayerId;
		maxCapacity = layerCache.maxCapacity;
		numEvictions = layerCache.numEvictions;
		cloneLock = layerCache.cloneLock;
	}
	
	public LayerCacheImpl(IAggregator aggregator) {
		maxCapacity = getMaxCapacity(aggregator);
		layerMap = new ConcurrentHashMap<String, LayerImpl>();
		layerBuildMap = new ConcurrentLinkedHashMap.Builder<String, CacheEntry>()
				.maximumWeightedCapacity(maxCapacity)
				.listener(newEvictionListener())
				.weigher(newWeigher()).build();
	}
	
	/* (non-Javadoc)
	 * @see java.util.concurrent.ConcurrentHashMap#clear()
	 */
	@Override
	public void clear() {
		cloneLock.readLock().lock();
		try {
			layerMap.clear();
			layerBuildMap.clear();
		} finally {
			cloneLock.readLock().unlock();
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.layer.ILayerCache#dump(java.io.Writer, java.util.regex.Pattern)
	 */
	@Override
	public void dump(Writer writer, Pattern filter) throws IOException {
    	String linesep = System.getProperty("line.separator"); //$NON-NLS-1$
    	for (Map.Entry<String, LayerImpl> entry : layerMap.entrySet()) {
    		if (filter != null) {
    			Matcher m = filter.matcher(entry.getKey());
    			if (!m.find())
    				continue;
    		}
    		writer.append("ILayer key: ").append(entry.getKey()).append(linesep); //$NON-NLS-1$
    		writer.append(entry.getValue().toString()).append(linesep).append(linesep);
    	}
    	writer.append("Number of layer cache entires = ").append(Integer.toString(layerMap.size())).append(linesep); //$NON-NLS-1$
    	writer.append("Number of layer cache evictions = ").append(Integer.toString(numEvictions.get())).append(linesep); //$NON-NLS-1$
	}

	@Override
	public ILayer getLayer(HttpServletRequest request) {
		cloneLock.readLock().lock();
		try {
			String key = request
	        		.getAttribute(IHttpTransport.REQUESTEDMODULES_REQATTRNAME)
	        		.toString(); 
	        
			Object requiredModules = request.getAttribute(IHttpTransport.REQUIRED_REQATTRNAME);
			if (requiredModules != null) {
				key += requiredModules.toString();
			}
			ILayer result = null;
			boolean ignoreCached = RequestUtil.isIgnoreCached(request);
			if (!ignoreCached) {
				result = layerMap.get(key);
			}
			if (result == null) {
				int id = newLayerId.incrementAndGet();
				LayerImpl newLayer = new LayerImpl(key, id);
				if (!ignoreCached) {
					result = layerMap.putIfAbsent(key, newLayer);
				}
				if (result == null) {
					newLayer.setLayerBuildsAccessor(new LayerBuildsAccessor(id, layerBuildMap, aggregator.getCacheManager(), cloneLock, null, this));
					result = newLayer;
				}
			}
			return result;
		} finally {
			cloneLock.readLock().unlock();
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.layer.ILayerCache#get(java.lang.String)
	 */
	@Override
	public ILayer get(String key) {
		return layerMap.get(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.layer.ILayerCache#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String key) {
		return layerMap.containsKey(key);
	}

	boolean remove(String key, ILayer layer) {
		cloneLock.readLock().lock();
		try {
			return layerMap.remove(key, layer);
		} finally {
			cloneLock.readLock().unlock();
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.layer.ILayerCache#getKeys()
	 */
	@Override
	public Set<String> getKeys() {
		return layerMap.keySet();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.layer.ILayerCache#size()
	 */
	@Override
	public int size() {
		return layerMap.size();
	}
	
	Map<String, CacheEntry> getLayerBuildMap() {
		return layerBuildMap;
	}
	
	Collection<String> getLayerBuildKeys() {
		return layerBuildMap.ascendingKeySet();
	}
	
	int getNumEvictions() {
		return numEvictions.get();
	}
	
	int getMaxCapacity() {
		return maxCapacity;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.layer.ILayerCache#setAggregator(com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void setAggregator(IAggregator aggregator) {
		if (this.aggregator != null) {
			throw new IllegalStateException();
		}
		this.aggregator = aggregator;
		
		// See if the max cache entries init-param has changed and
		int newMaxCapacity = getMaxCapacity(aggregator);

		// If the maximum size has changed, then create a new layerBuildMap with the new
		// max size and copy the entries from the existing map to the new map
		ConcurrentLinkedHashMap<String, CacheEntry> oldLayerBuildMap = null;
															// have no layer builds in the layerBuildMap
		if (maxCapacity != newMaxCapacity) {
			maxCapacity = newMaxCapacity;
			oldLayerBuildMap = layerBuildMap;
			layerBuildMap = new ConcurrentLinkedHashMap.Builder<String, CacheEntry>()
					.maximumWeightedCapacity(maxCapacity)
					.listener(newEvictionListener())
					.weigher(newWeigher())
					.build();
			// Need to call setLayerBuildAccessors BEFORE calling putAll because
			// it might result in the eviction handler being called.
			setLayerBuildAccessors(oldLayerBuildMap.keySet());
			layerBuildMap.putAll(oldLayerBuildMap.ascendingMap());
			oldLayerBuildMap.clear();
		} else {
			setLayerBuildAccessors(layerBuildMap.keySet());
		}
	}
	
	/**
	 * Calls setLayerBuildAccessor for each layer in <code>layerMap</code>.
	 * 
	 * @param buildKeys
	 *            Set of keys in the build map. This is not necessarily the
	 *            same as <code>layerBuildMap.keySet()</code> because we may be
	 *            migrating th builds to a new map in the event that the maximum
	 *            size has changed.
	 */
	private void setLayerBuildAccessors(Set<String> buildKeys) {
		NavigableSet<String> sorted = new TreeSet<String>(buildKeys);
		Set<String> evictionKeys = new HashSet<String>();	// list of layer keys to remove because they
															// have no layer builds in the layerBuildMap
		for (Map.Entry<String, LayerImpl> entry : layerMap.entrySet()) {
			LayerImpl layer = entry.getValue();
			LayerBuildsAccessor accessor = new LayerBuildsAccessor(layer.getId(), layerBuildMap, aggregator.getCacheManager(),  cloneLock, sorted, this);
			if (accessor.getCount() > 0) {
				layer.setLayerBuildsAccessor(accessor);
			} else {
				evictionKeys.add(entry.getKey());
			}
		}
		// Now remove the layers that are missing layer builds in the layerBuildMap
		for (String key : evictionKeys) {
			layerMap.remove(key);
		}
	}

	protected EvictionListener<String, CacheEntry> newEvictionListener() {
		return new EvictionListener<String, CacheEntry>() { 
			@Override
			public void onEviction(String layerKey, CacheEntry cacheEntry) {
				LayerImpl layer = layerMap.get(cacheEntry.layerKey);
				if (layer != null) {
					numEvictions.incrementAndGet();
					if (layer.cacheEntryEvicted(cacheEntry)) {
						layerMap.remove(layer.getKey(), layer);
					}
				}
				// Delete the cache entry persistent storage.
				cacheEntry.delete(aggregator.getCacheManager());
			}
		};
	}
	
	protected Weigher<CacheEntry> newWeigher() {
		return new Weigher<CacheEntry>() {
			@Override
			public int weightOf(CacheEntry entry) {
				// ConcurrentLinkedHashMap barfs on size == 0
				return entry.getSize() > 0 ? entry.getSize() : 1;
			}
		};
	}
	
	protected int getMaxCapacity(IAggregator aggregator) {
		InitParams initParams =  aggregator.getInitParams();
		int result = DEFAULT_MAXLAYERCACHECAPACITY_MB * 1024 * 1024;
		if (initParams != null) {
			List<String> values = initParams.getValues(InitParams.MAXLAYERCACHECAPACITY_MB_INITPARAM);
			result = (TypeUtil.asInt(values.size()  > 0 ? values.get(values.size()-1) : null,  DEFAULT_MAXLAYERCACHECAPACITY_MB) * 1024 * 1024);
		}
		return result;
	}
	
	/* ---------------- Serialization Support -------------- */
	/*
	 *  ConcurrentLinkedHashMap serialization doesn't maintain LRU ordering of entries,
	 *  so use a serialization proxy that will allow us to work around that problem. 
	 *  We also clone the LayerImpl objects in the serialization proxy constructor 
	 *  (while owning the write-lock on the cache) so that we can maintain the 
	 *  integrity of the serialized version of the cache without needing to own
	 *  the write-lock while doing disk-io. 
	 */
	protected Object writeReplace() throws ObjectStreamException {
		return new SerializationProxy(this);
	}

	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
	    throw new InvalidObjectException("Proxy required"); //$NON-NLS-1$
	}

	protected static class SerializationProxy implements Serializable {
		private static final long serialVersionUID = 1956233862324653291L;
		
		private final int newLayerId;
		private final int maxCapacity;
		private final int numEvictions;
		private final Class<?> clazz;
		private final ConcurrentMap<String, LayerImpl> layerMap;
		private final Map<String, CacheEntry> layerBuildMap;

		protected SerializationProxy(LayerCacheImpl cache) throws InvalidObjectException {
			cache.cloneLock.writeLock().lock();
			try {
				clazz = cache.getClass();
				newLayerId = cache.newLayerId.get();
				maxCapacity = cache.maxCapacity;
				numEvictions = cache.numEvictions.get();
				layerMap = new ConcurrentHashMap<String, LayerImpl>();
				for (Map.Entry<String, LayerImpl> entry : cache.layerMap.entrySet()) {
					layerMap.put(entry.getKey(), (LayerImpl)entry.getValue().cloneForSerialization());
				}
				
				layerBuildMap = cache.layerBuildMap.ascendingMap();
			} finally {
				cache.cloneLock.writeLock().unlock();
			}
	    }

	    protected Object readResolve() {
	    	LayerCacheImpl cache;
			try {
				cache = (LayerCacheImpl)clazz.newInstance();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
	    	cache.layerMap = layerMap;
	    	cache.newLayerId = new AtomicInteger(newLayerId);
	    	cache.maxCapacity = maxCapacity;
	    	cache.numEvictions = new AtomicInteger(numEvictions);
	    	cache.cloneLock = new ReentrantReadWriteLock();
			cache.layerBuildMap = new ConcurrentLinkedHashMap.Builder<String, CacheEntry>()
					.maximumWeightedCapacity(maxCapacity)
					.listener(cache.newEvictionListener())
					.weigher(cache.newWeigher())
					.build();
			cache.layerBuildMap.putAll(layerBuildMap);
	    	return cache;
	    }
	}
}
