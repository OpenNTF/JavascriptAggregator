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

import org.apache.commons.lang3.mutable.MutableInt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

/**
 * Interface for a cache of gzipped modules
 */
public interface IGzipCache extends IGenericCache {

	/**
	 * The type of the cache entry is opaque to the user.
	 * {@link IGzipCache#getInputStream(String, URI, MutableInt)} should be used as the sole
	 * interface for accessing the cache.
	 */
	public interface ICacheEntry {
	};

	/**
	 * Returns an input stream to the gzipped contents of the resource specified by
	 * <code>source</code>
	 *
	 * @param key
	 *            the cache key (typically the resource path from the request)
	 * @param source
	 *            the URI to the resource on the server
	 * @param retSize
	 *            Returned - the length of the gzipped content
	 * @return an input stream to the gzipped content for the resource
	 * @throws IOException
	 */
	InputStream getInputStream(String key, URI source, MutableInt retSize) throws IOException;

}
