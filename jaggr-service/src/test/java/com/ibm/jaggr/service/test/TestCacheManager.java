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

package com.ibm.jaggr.service.test;

import java.io.IOException;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICache;
import com.ibm.jaggr.service.impl.cache.CacheManagerImpl;

public class TestCacheManager extends CacheManagerImpl {
	
	private ICache cache = null;

	public TestCacheManager(IAggregator aggregator, long stamp) throws IOException {
		super(aggregator, stamp);
	}
	
	@Override
	public void serializeCache() {
		super.serializeCache();
	}
	
	public void setCache(ICache cache) {
		this.cache = cache;
	}
	
	@Override 
	public ICache getCache() {
		return cache == null ? super.getCache() : cache;
	}
}
