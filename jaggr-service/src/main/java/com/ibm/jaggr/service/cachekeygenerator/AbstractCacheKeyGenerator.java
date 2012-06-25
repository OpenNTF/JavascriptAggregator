/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.cachekeygenerator;

import javax.servlet.http.HttpServletRequest;


/**
 * Abstract implementation of {@code ICacheKeyGenerator}.
 * <p>
 * Implements default cache key generator behavior for module builders that d
 * don't need provisional cache key generators and don't need combine logic.
 */
@SuppressWarnings("serial")
public abstract class AbstractCacheKeyGenerator implements
		ICacheKeyGenerator {

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator#generateKey(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public abstract String generateKey(HttpServletRequest request);
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ibm.jaggr.service.modules.Module.CacheKeyGenerator#
	 * combine
	 * (com.ibm.jaggr.service.modules.Module.CacheKeyGenerator)
	 */
	@Override
	public ICacheKeyGenerator combine(ICacheKeyGenerator other) {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator#getCacheKeyGenerators(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public ICacheKeyGenerator[] getCacheKeyGenerators(HttpServletRequest request) {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.ICacheKeyGenerator#isProvisional()
	 */
	@Override
	public boolean isProvisional() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.ICacheKeyGenerator#toString()
	 */
	public abstract String toString();
}