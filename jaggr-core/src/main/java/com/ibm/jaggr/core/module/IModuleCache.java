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

package com.ibm.jaggr.core.module;

import com.ibm.jaggr.core.cache.IGenericCache;
import com.ibm.jaggr.core.readers.ModuleBuildReader;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

/**
 * Interface for module cache object
 */
public interface IModuleCache extends Serializable, IGenericCache {

	public static final String MODULECACHEINFO_PROPNAME = IModuleCache.class.getName() + ".MODULE_CACHEINFO"; //$NON-NLS-1$

	/**
	 * Returns a future to the build reader for requested module.  If the build
	 * already exists in the cache, then a completed future for the reader to the
	 * cached build is returned.  Otherwise, a new build is started, and a future
	 * that will complete when the build has finished is returned.
	 *
	 * @param request
	 *            the request object
	 * @param module
	 *            the module to build
	 * @return a {@link Future} to the build reader for the module
	 * @throws IOException
	 */
	Future<ModuleBuildReader> getBuild(HttpServletRequest request, IModule module) throws IOException;

}
