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
package com.ibm.jaggr.core.cache;

/**
 * OSGi service listener interface that is called by the cache manager when it has been
 * initialized.  Aggregator extension may register service listeners for this interface
 * to know when it is safe to call cache manager methods for adding named caches,
 * etc.  To receive listener events, register the implementing class as an OSGi service,
 * specifying the servlet name in the <code>name</code> service property.
 */
public interface ICacheManagerListener {
	/**
	 * Called when the cache manager has been initialized.
	 *
	 * @param cacheManager the cache manager instance.
	 */
	void initialized(ICacheManager cacheManager);
}