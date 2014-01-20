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

package com.ibm.jaggr.core.cachekeygenerator;

import java.util.List;

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
	 * @see com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator#generateKey(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public abstract String generateKey(HttpServletRequest request);
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator#combine(com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator)
	 */
	@Override
	public ICacheKeyGenerator combine(ICacheKeyGenerator other) {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator#getCacheKeyGenerators(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public List<ICacheKeyGenerator> getCacheKeyGenerators(HttpServletRequest request) {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.module.ICacheKeyGenerator#isProvisional()
	 */
	@Override
	public boolean isProvisional() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.module.ICacheKeyGenerator#toString()
	 */
	public abstract String toString();
}
