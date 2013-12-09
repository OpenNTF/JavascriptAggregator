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

package com.ibm.jaggr.core.impl.module;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.module.ModuleIdentifier;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.util.RequestUtil;

/**
 * This class implements the {@link IModuleCache} interface by extending {@link ConcurrentHashMap}
 * and adds methods for cloning and dumping the cache contents.
 */
public class ModuleCacheImpl implements IModuleCache, Serializable {
	private static final long serialVersionUID = 1739429773011306523L;

	private ConcurrentMap<String, IModule> cacheMap = new ConcurrentHashMap<String, IModule>();

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.module.IModuleCache#dump(java.io.Writer, java.util.regex.Pattern)
	 */
	@Override
	public void dump(Writer writer, Pattern filter) throws IOException {
    	String linesep = System.getProperty("line.separator"); //$NON-NLS-1$
    	for (Map.Entry<String, IModule> entry : cacheMap.entrySet()) {
    		if (filter != null) {
    			Matcher m = filter.matcher(entry.getKey());
    			if (!m.find())
    				continue;
    		}
    		writer.append("IModule key: ").append(entry.getKey()).append(linesep); //$NON-NLS-1$
    		writer.append(entry.getValue().toString()).append(linesep).append(linesep);
    	}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.module.IModuleCache#getBuild(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.core.module.IModule)
	 */
	@Override
	public Future<ModuleBuildReader> getBuild(HttpServletRequest request,
			IModule module) throws IOException {
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		@SuppressWarnings("unchecked")
		Map<String, String> moduleCacheInfo = (Map<String, String>)request.getAttribute(IModuleCache.MODULECACHEINFO_PROPNAME);
		IOptions options = aggr.getOptions();
		IResource resource = module.getResource(aggr);
		String cacheKey = new ModuleIdentifier(module.getModuleId()).getModuleName();
		// Try to get the module from the module cache first
		IModule cachedModule = null;
		if (!resource.exists()) {
			// Source file doesn't exist.
			if (!options.isDevelopmentMode()) {
				// Avoid the potential for DoS attack in production mode by throwing
				// an exceptions instead of letting the cache grow unbounded
				throw new NotFoundException(resource.getURI().toString());
			}
			// NotFound modules are not cached.  If the module is in the cache (because a 
			// source file has been deleted), then remove the cached module.
    		cachedModule = cacheMap.remove(cacheKey);
    		if (cachedModule != null) {
	        	if (moduleCacheInfo != null) {
	        		moduleCacheInfo.put(cacheKey, "remove"); //$NON-NLS-1$
	        	}
	        	cachedModule.clearCached(aggr.getCacheManager());
    		}
			// create a new NotFoundModule
			module = new NotFoundModule(module.getModuleId(), module.getURI());
	    	request.setAttribute(ILayer.NOCACHE_RESPONSE_REQATTRNAME, Boolean.TRUE);
		} else {
			// add it to the module cache if not already there
			if (!RequestUtil.isIgnoreCached(request)) {
				cachedModule = cacheMap.putIfAbsent(cacheKey, module);
			}
        	if (moduleCacheInfo != null) {
				moduleCacheInfo.put(cacheKey, (cachedModule != null) ? "hit" : "add"); //$NON-NLS-1$ //$NON-NLS-2$
        	}
			module = cachedModule != null ? cachedModule : module;
		}
		return module.getBuild(request);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.module.IModuleCache#get(java.lang.String)
	 */
	@Override
	public IModule get(String key) {
		return cacheMap.get(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.module.IModuleCache#size()
	 */
	@Override
	public int size() {
		return cacheMap.size();
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.module.IModuleCache#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String key) {
		return cacheMap.containsKey(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.module.IModuleCache#getKeys()
	 */
	@Override
	public Set<String> getKeys() {
		return cacheMap.keySet();
	}

	@Override
	public void clear() {
		cacheMap.clear();
		
	}

	@Override
	public void setAggregator(IAggregator aggregator) {
		// Not currently used by this implementation
	}
}
