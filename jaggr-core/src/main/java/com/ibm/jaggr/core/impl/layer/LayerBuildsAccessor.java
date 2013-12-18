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

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;

import com.ibm.jaggr.core.cache.ICacheManager;

/**
 * Wrapper for the layer builds map to help maintain a layer specific view
 * of the map (which contains CacheEntry objects for all layers). Maintains a
 * counter for the number of builds in the map that belong to this layer,
 * and a flag to indicate that the layer is expired when the map no longer
 * contains any builds belonging to this layer. Expired layers are removed
 * from the layer cache.
 * <p>
 * This wrapper tags entries that are added to the cache with the id for the
 * owning layer so that entries belonging to the layer can be retrieved (see
 * {@link LayerBuildsAccessor#entrySet()}).
 */
class LayerBuildsAccessor {

	/**
	 * 
	 */
	private final ConcurrentMap<String, CacheEntry> map;
	private final ICacheManager cacheMgr;
	private final LatchingCounter evictionLatch;

	/**
	 * ReadWriteLock used to quell modifications when cloning.  Write lock is
	 * obtained to clone, read lock is obtained for any other operation than can 
	 * modify the internal state of this object.  Since the lock object is shared
	 * by all layers in the layer cache, this allows the layer cached to be cloned in a 
	 * consistent state for serialization.
	 */
	private final ReadWriteLock cloneLock;
	private final int layerId;
	private final String keyPrefix;
	private final String keyPrefixUpperBound;
	private final WeakReference<LayerCacheImpl> layerCacheRef;
	
	LayerBuildsAccessor(
			int layerId, 
			ConcurrentMap<String, CacheEntry> map, 
			ICacheManager cacheMgr, 
			ReadWriteLock cloneLock, 
			NavigableSet<String> sortedKeys,
			LayerCacheImpl layerCache) {
		this.layerId = layerId;
		this.keyPrefix = Integer.toString(layerId) + "-";	//$NON-NLS-1$
		this.keyPrefixUpperBound = Integer.toString(layerId) + ".";	//$NON-NLS-1$
		this.map = map;
		this.cacheMgr = cacheMgr;
		this.cloneLock = cloneLock;
		this.layerCacheRef = new WeakReference<LayerCacheImpl>(layerCache);
		int count = 0;
		if (sortedKeys != null) {
			count = sortedKeys.subSet(keyPrefix, true, keyPrefixUpperBound, false).size();
		}
		this.evictionLatch = new LatchingCounter(count);
	}
	
	/**
	 * Retrieves the cache entry with the specified key
	 * 
	 * @param key The map key without the layer identifier prefix
	 * @return the value for the key
	 */
	public CacheEntry get(String key) {
		return map.get(keyPrefix + key);
	}
	
	/**
	 * Replaces the value associated with the key only if the existing value is
	 * equal to <code>oldValue</code>
	 * 
	 * @param key the cache key
	 * @param oldValue the expected value
	 * @param newValue the new value
	 * @return true if the value was replace
	 */
	public boolean replace(String key, CacheEntry oldValue, CacheEntry newValue) {
		boolean replaced = false;
		cloneLock.readLock().lock();
		try {
			replaced = map.replace(keyPrefix + key, oldValue, newValue);
			if (replaced && oldValue != newValue) {
				oldValue.delete(cacheMgr);
			}
		} finally {
			cloneLock.readLock().unlock();
		}
		return replaced;
	}
	/**
	 * Works like {@link ConcurrentMap#putIfAbsent(Object, Object)} with the 
	 * added semantic that if <code>checkLastMod</code> is true, then the existing value
	 * will be replaced with the specified value if the specified value's lastModified
	 * date is later than the existing value's lastModified date.  If the value is 
	 * replace, then the return value is null, indicating that the specified 
	 * value was added to the map, and the previous entry's cache file is deleted.  
	 * <p>If this method is called for a layer that has been evicted, then the
	 * method returns null without adding the value to the cache.
	 * <p>
	 * Bumps the count if the value is added to the cache.
	 * 
	 * @param key
	 *            The map key without the layer identifier prefix
	 * @param value
	 *            The value to add to the map
	 * @param update
	 *            If true, then the specified value will replace the existing value
	 *            if the last-modified time of the specified value is greater than
	 *            the last-modified time of the existing value.
	 * @return The existing value or null if the value was added or the
	 *         layer has been evicted
	 */
	public CacheEntry putIfAbsent(String key, CacheEntry value, boolean update) {
		key = keyPrefix + key;
		CacheEntry existingValue = null;
		boolean incrementCount = false;
		cloneLock.readLock().lock();
		try {
			while (true) {
				if (evictionLatch.isLatched()) {
					value.delete(cacheMgr);
					return null;
				}
				existingValue = map.putIfAbsent(key, value);
				if (existingValue == null) {
					incrementCount = true;
				} else {
					if (update && 
							value.lastModified > existingValue.lastModified) {
						/*
						 * Replace the expired value with the new value.  If the replace fails,
						 * then another thread replaced or removed the value between the time 
						 * putIfAbsent returned and the time we called replace.  In that case,
						 * continue in the while loop and try calling putIfAbsent again to get
						 * the updated entry.
						 */
						if (map.replace(key, existingValue, value)) {
							existingValue.delete(cacheMgr);
							existingValue = null;
						} else {
							continue;
						}
					}
				}
				break;
			}
			if (incrementCount) {
				// value was added to the cache.  Increment the counter
				Boolean evicted = evictionLatch.increment();
				if (evicted) {
					map.remove(key,  value);
					value.delete(cacheMgr);
				}
			}
		} finally {
			cloneLock.readLock().unlock();
		}
		return existingValue;
	}
	
	/**
	 * Removes the entry for the key only if currently mapped to the given
	 * value. If the entry is removed, then the entry count for this layer
	 * is decremented. If <code>evict</code> is true, then the
	 * <code>_evicted</code> flag will be set if the count is decremented to
	 * zero.  Either <code>key</code> and/or <code>value</code> may be null, in 
	 * which case, nothing is removed, but if the entry count is zero, then
	 * <code>_evicted</code> is set to true.
	 * 
	 * @param key
	 *            The map key without the layer identifier prefix
	 * @param value
	 *            The value expected to be associated with the specified
	 *            key.
	 * @return true if the entry was removed
	 */
	public boolean remove(String key, CacheEntry value) {
		boolean removed = false;
		cloneLock.readLock().lock();
		try {
			if (key != null && value != null) {
				removed = map.remove(keyPrefix + key, value);
			}
			if (removed) {
				evictionLatch.decrement();
				value.delete(cacheMgr);
			} else {
				evictionLatch.latchIfZero();
			}
		} finally {
			cloneLock.readLock().unlock();
		}
		return removed;
	}
	
	/**
	 * Like {@link Map#entrySet()} except that the returned entrySet
	 * contains only entries belonging to this layer
	 * 
	 * @return entry set of map entries belonging to this layer
	 */
	public Set<Map.Entry<String, CacheEntry>> entrySet() {
		Set<Map.Entry<String, CacheEntry>> result = Collections.emptySet();
		NavigableMap<String, CacheEntry> navMap = new TreeMap<String, CacheEntry>(map);
		String from = navMap.ceilingKey(keyPrefix), to = navMap.lowerKey(keyPrefixUpperBound);
		if (from != null) {
			result = navMap.subMap(from, true, to, true).entrySet();
		}
		return result;
	}
	
	/**
	 * Handles map entry eviction notifications. Decrements the entry
	 * counter for the layer. If the counter is decremented to zero, then
	 * the <code>_evicted</code> flag is set.
	 * 
	 * @param cacheEntry
	 *            the cache entry being evicted
	 * @return true if the <code>_evicted</code> flag was set
	 */
	public boolean cacheEntryEvicted(CacheEntry cacheEntry) {
		boolean evicted = false;
		cloneLock.readLock().lock();
		try {
			if (cacheEntry.layerId == layerId) {
				evicted = evictionLatch.decrement();
			}
		} finally {
			cloneLock.readLock().unlock();
		}
		return evicted;
	}
	
	public boolean isLayerEvicted() {
		return evictionLatch.isLatched();
	}
	
	public int getCount() {
		return evictionLatch.getCount();
	}
	
	public Map<String, CacheEntry> getMap() {
		return Collections.unmodifiableMap(map);
	}
	
	/**
	 * Convenience method to remove the layer that this class is associated with
	 * from the layer cache.
	 * 
	 * @param layer
	 *            The layer to remove. The id of the specified layer must be the
	 *            same as <code>layerId</code> or else a
	 *            {@link IllegalStateException} is thrown
	 */
	public void removeLayerFromCache(LayerImpl layer) {
		if (layer.getId() != layerId) {
			throw new IllegalStateException();
		}
		LayerCacheImpl layerCache = layerCacheRef.get();
		if (layerCache != null) {
			layerCache.remove(layer.getKey(), layer);
		}
	}
}