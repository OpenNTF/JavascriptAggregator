/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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

package com.ibm.jaggr.core.impl.module;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.impl.cache.GenericCacheImpl;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.util.RequestUtil;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

/**
 * This class implements the {@link IModuleCache} interface by extending {@link ConcurrentHashMap}
 * and adds methods for cloning and dumping the cache contents.
 */
public class ModuleCacheImpl extends GenericCacheImpl<IModule> implements IModuleCache, Serializable {
	private static final long serialVersionUID = 6091565036994759152L;

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.IModuleCache#getBuild(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.service.module.IModule)
	 */
	@Override
	public Future<ModuleBuildReader> getBuild(HttpServletRequest request,
			IModule module) throws IOException {
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		@SuppressWarnings("unchecked")
		Map<String, String> moduleCacheInfo = (Map<String, String>)request.getAttribute(IModuleCache.MODULECACHEINFO_PROPNAME);
		IResource resource = module.getResource(aggr);
		String cacheKey = module.getModuleId();
		// Try to get the module from the module cache first
		IModule cachedModule = null;
		if (!resource.exists()) {
			// Source file doesn't exist.
			// NotFound modules are not cached.  If the module is in the cache (because a
			// source file has been deleted), then remove the cached module.
			cachedModule = cacheMap.remove(cacheKey);
			if (cachedModule != null) {
				if (moduleCacheInfo != null) {
					moduleCacheInfo.put(cacheKey, "remove"); //$NON-NLS-1$
				}
				cachedModule.clearCached(aggr.getCacheManager());
			}
			throw new NotFoundException(resource.getURI().toString());
		} else {
			// add it to the module cache if not already there
			if (!RequestUtil.isIgnoreCached(request)) {
				cachedModule = cacheMap.putIfAbsent(cacheKey, module);
				if (cachedModule != null && aggr.getOptions().isDevelopmentMode()) {
					// If the uri for the source resource has changed (can happen with resource converters)
					// discard the cached module and use the new one.
					IResource cachedResource = cachedModule.getResource(null);
					if (cachedResource != null && !cachedResource.getURI().equals(resource.getURI())) {
						cacheMap.replace(cacheKey, module);
						cachedModule = null;
					}
				}
			}
			if (moduleCacheInfo != null) {
				moduleCacheInfo.put(cacheKey, (cachedModule != null) ? "hit" : "add"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			module = cachedModule != null ? cachedModule : module;
		}
		return module.getBuild(request);
	}
}
