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
package com.ibm.jaggr.core.impl.cache;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.IGenericCache;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericCacheImpl <T> implements IGenericCache<T>, Serializable {
	private static final long serialVersionUID = 7063606966797433122L;

	protected ConcurrentMap<String, T> cacheMap;

	public GenericCacheImpl() {
		cacheMap = new ConcurrentHashMap<String, T>();
	}

	protected GenericCacheImpl(GenericCacheImpl<T> other) {
		cacheMap = other.cacheMap;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#get(java.lang.String)
	 */
	@Override
	public T get(String key) {
		return cacheMap.get(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#size()
	 */
	@Override
	public int size() {
		return cacheMap.size();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String key) {
		return cacheMap.containsKey(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#getKeys()
	 */
	@Override
	public Set<String> getKeys() {
		return cacheMap.keySet();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#clear()
	 */
	@Override
	public void clear() {
		cacheMap.clear();

	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#setAggregator(com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void setAggregator(IAggregator aggregator) {
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#dump(java.io.Writer, java.util.regex.Pattern)
	 */
	@Override
	public void dump(Writer writer, Pattern filter) throws IOException {
		String linesep = System.getProperty("line.separator"); //$NON-NLS-1$
		for (Map.Entry<String, T> entry : cacheMap.entrySet()) {
			if (filter != null) {
				Matcher m = filter.matcher(entry.getKey());
				if (!m.find())
					continue;
			}
			String typeName = entry.getValue().getClass().getSimpleName();
			writer.append(typeName + " key: ").append(entry.getKey()).append(linesep); //$NON-NLS-1$
			writer.append(entry.getValue().toString()).append(linesep).append(linesep);
		}
	}

}
