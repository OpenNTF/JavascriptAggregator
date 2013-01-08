package com.ibm.jaggr.service.test;

import java.io.IOException;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.impl.cache.CacheManagerImpl;

public class TestCacheManager extends CacheManagerImpl {

	public TestCacheManager(IAggregator aggregator, long stamp) throws IOException {
		super(aggregator, stamp);
	}
	
	@Override
	public void serializeCache() {
		super.serializeCache();
	}
}
