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
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.cache.IGenericCache;
import com.ibm.jaggr.core.cache.IGzipCache;
import com.ibm.jaggr.core.cache.IGzipCache.ICacheEntry;
import com.ibm.jaggr.core.impl.layer.VariableGZIPOutputStream;
import com.ibm.jaggr.core.util.CopyUtil;

import org.apache.commons.lang3.mutable.MutableInt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;

public class GzipCacheImpl extends GenericCacheImpl<ICacheEntry> implements Serializable,
		IGzipCache {
	private static final long serialVersionUID = 9075726057738934199L;

	private static final Logger log = Logger.getLogger(GzipCacheImpl.class.getName());
	private static final String sourceClass = GzipCacheImpl.class.getName();

	private transient ICacheManager cacheManager;

	/**
	 * The cache entry object for the gzip cache. In order to minimize thread contention, the code
	 * in this module follows certain conventions regarding the order in which fields are set and
	 * which fields may be reset (described in comments for each field). This allows the fields to
	 * be accessed in ways that don't require synchronization for the most common cases.
	 */
	private static class CacheEntry implements Serializable, ICacheEntry {
		private static final long serialVersionUID = -1613609199037083555L;

		/**
		 * Holds the gzipped bytes while waiting for writing of the gzip cache file to complete.
		 * Reset to null (un-synchronized) after {@link #file} has been set or by
		 * serialization/deserialization.
		 */
		volatile transient private byte[] bytes;

		/**
		 * The last-modified date of the source resource when the cache entry was created. Set after
		 * {@link #bytes} is set and does not get reset except through
		 * serialization/deserialization.
		 */
		volatile transient private long lastModified;

		/**
		 * If an exception occurred trying to generate the gzipped content, the exception is saved
		 * in this field and the cache entry is removed from the cache. This field does not get
		 * reset except through serialization/deserialization.
		 */
		volatile transient private IOException ex;

		/**
		 * Persistent {@link File} object referencing the gzipped cache file. Set (un-synchronized)
		 * after cache file has been fully written and flushed and cache file last-modified has been
		 * updated with the last-modified time of the source. Following setting of this field,
		 * {@link bytes} will be reset to null (un-synchronized).
		 */
		volatile private File file;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.core.cache.IGzipCache#getInputStream(java.lang.String, java.net.URI,
	 * org.apache.commons.lang3.mutable.MutableInt)
	 */
	@Override
	public InputStream getInputStream(final String key, final URI source, final MutableInt retLength)
			throws IOException {
		final String sourceMethod = "getInputStream"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[] { key, source, retLength });
		}

		InputStream in = null, result = null;
		CacheEntry tryCacheEntry = (CacheEntry) super.get(key);
		URLConnection connection = source.toURL().openConnection();

		try {
			long lastModified = connection.getLastModified();
			if (tryCacheEntry != null) {
				// Make local copies of volatile CacheEntry fields
				byte[] bytes = tryCacheEntry.bytes;
				File file = tryCacheEntry.file;
				if (bytes != null) {
					// Important - CacheEntry.lastModified is set before CacheEntry.bytes so we can
					// safely
					// check CacheEntry.lastModified here even though we're not synchronized.
					if (lastModified != tryCacheEntry.lastModified) {
						// stale cache entry. Remove it and create a new one below
						cacheMap.remove(key, tryCacheEntry);
					} else {
						retLength.setValue(tryCacheEntry.bytes.length);
						result = new ByteArrayInputStream(tryCacheEntry.bytes);
					}
				} else if (file != null) {
					// Some platforms round file last modified times to nearest second.
					if (Math.abs(lastModified - file.lastModified()) > 1000) {
						// Stale cache entry, remove it and create a new one below
						cacheMap.remove(key, tryCacheEntry);
						// also delete the associated cache file asynchronously.
						cacheManager.deleteFileDelayed(file.getName());
					} else {
						try {
							retLength.setValue(file.length());
							result = new FileInputStream(file);
						} catch (FileNotFoundException ex) {
							// File doesn't exist (was probably deleted outside this program)
							// Not fatal, just fall through and create it again.
							cacheMap.remove(key, tryCacheEntry);
						}
					}
				}
				if (result != null) {
					// found result in cache. Return it.
					log.exiting(sourceClass, sourceMethod, result);
					return result;
				}
			}

			// Result not in cache (or we removed it). Try to create a new cache entry.
			CacheEntry newCacheEntry = new CacheEntry();
			CacheEntry oldCacheEntry = (CacheEntry) cacheMap.putIfAbsent(key, newCacheEntry);
			final CacheEntry cacheEntry = oldCacheEntry != null ? oldCacheEntry : newCacheEntry;

			// Synchronize on the cache entry so that more than one thread won't try to create the
			// zipped content.
			synchronized (cacheEntry) {
				if (cacheEntry.ex != null) {
					// An exception occurred trying to create the gzip response in another thread.
					// Re-throw the exception here.
					throw cacheEntry.ex;
				}
				// First, check to make sure that another thread didn't beat us to the punch.
				// Even though we're synchronized on the cacheEntry object, cacheEntry.bytes can be
				// cleared by the createCacheFileAsync callback, so we need to copy this volatile
				// field
				// to a local variable and access it from there.
				byte[] bytes = cacheEntry.bytes;
				if (bytes != null) {
					retLength.setValue(bytes.length);
					result = new ByteArrayInputStream(bytes);
				} else if (cacheEntry.file != null) { // once set, cacheEntry.file does not change
														// by convention
					retLength.setValue(cacheEntry.file.length());
					result = new FileInputStream(cacheEntry.file);
				} else {
					// Gzip encode the resource and save the result in the cache entry until the
					// cache
					// file is written asynchronously.
					try {
						in = connection.getInputStream();
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						VariableGZIPOutputStream compress = new VariableGZIPOutputStream(bos, 10240);
						compress.setLevel(Deflater.BEST_COMPRESSION);
						CopyUtil.copy(in, compress);

						// Important - CacheEntry.lastModified must be set before cacheEntry.bytes
						cacheEntry.lastModified = lastModified;
						cacheEntry.bytes = bos.toByteArray();
						result = new ByteArrayInputStream(cacheEntry.bytes);
						retLength.setValue(cacheEntry.bytes.length);

						// Call the cache manager to asynchronously save the gzipped response to
						// disk
						// Include the filename part of the source URI in the cached filename
						String path = source.getPath();
						int idx = path.lastIndexOf("/"); //$NON-NLS-1$
						String fname = (idx != -1) ? path.substring(idx + 1) : path;
						cacheManager.createCacheFileAsync(
								fname + ".gzip.", //$NON-NLS-1$
								new ByteArrayInputStream(cacheEntry.bytes),
								new ICacheManager.CreateCompletionCallback() {
									@Override
									public void completed(String filename, Exception e) {
										if (e != null && log.isLoggable(Level.SEVERE)) {
											// Exception occurred saving file. Not much we can do
											// except log the error
											log.logp(Level.SEVERE, sourceClass, sourceMethod,
													e.getMessage(), e);
											return;
										}
										File cacheFile = new File(cacheManager.getCacheDir(),
												filename);
										cacheFile.setLastModified(cacheEntry.lastModified);
										// Important - cacheEntry.file must be set before clearing
										// cacheEntry.bytes
										cacheEntry.file = cacheFile;
										cacheEntry.bytes = null;
									}
								});
					} catch (Throwable t) {
						cacheEntry.ex = (t instanceof IOException) ? (IOException) t
								: new IOException(t);
						cacheMap.remove(key, cacheEntry);
						throw cacheEntry.ex;
					}
				}
			}
		} finally {
			// URLConnection doesn't have a close method. The only way to make sure a connection is
			// closed is to close the input or output stream which is obtained from the connection.
			if (in != null) {
				in.close();
			} else {
				connection.getInputStream().close();
			}
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceClass, result);
		}
		return result;
	}

	@Override
	public void setAggregator(IAggregator aggregator) {
		cacheManager = aggregator.getCacheManager();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#newInstance()
	 */
	@Override
	public IGenericCache newInstance() {
		return new GzipCacheImpl();
	}
}
