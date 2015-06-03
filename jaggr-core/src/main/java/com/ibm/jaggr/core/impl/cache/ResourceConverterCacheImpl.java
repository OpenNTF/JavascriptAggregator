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
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.cache.IGenericCache;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a generic cache for resource converters.  Clients provide the implementation
 * for converting a resource and writing it to a cache file, and this class handles the management
 * of the cache file in the aggregator cache.
 */
public class ResourceConverterCacheImpl extends GenericCacheImpl<ResourceConverterCacheImpl.CacheEntry> implements Serializable {

	private static final long serialVersionUID = 5441922319434222745L;

	private static final String sourceClass = ResourceConverterCacheImpl.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);

	public interface IConverter extends Serializable {
		void generateCacheContent(IResource source, File cacheFile) throws IOException;
	}

	private final IConverter converter;

	private final String suffix;
	private final String prefix;

	// The following transient fields are initialized in setAggregator()
	private transient IAggregator aggregator;
	private transient IResourceFactory fileResourceFactory;

	/**
	 * The cache entry object. In order to minimize thread contention.
	 */
	static class CacheEntry implements Serializable {
		private static final long serialVersionUID = -1613609199037083555L;

		/**
		 * If an exception occurred trying to generate the converted content, the exception is saved
		 * in this field and the cache entry is removed from the cache. This field does not get
		 * reset except through serialization/deserialization.
		 */
		volatile transient private IOException ex;

		/**
		 * Persistent {@link File} object referencing the cache file.
		 */
		volatile private File file;

		/**
		 * The reference uri from the original source resource.
		 */
		volatile private URI referenceUri;
	}

	/**
	 * @param converter the converter used to create new cache entries.
	 * @param prefix the prefix to use for cache file names.
	 * @param suffix the suffix to use for cache file names.
	 */
	public ResourceConverterCacheImpl(IConverter converter, String prefix, String suffix) {
		this.converter = converter;
		this.prefix = prefix;
		this.suffix = suffix;
	}

	/**
	 * @return the converter this cache object was initialized with
	 */
	public IConverter getConverter() {
		return this.converter;
	}

	/**
	 * @return The prefix this cache object was initialized with
	 */
	public String getPrefix() {
		return this.prefix;
	}

	/**
	 * @return The suffix this cache object was initialized with
	 */
	public String getSuffix() {
		return this.suffix;
	}

	/**
	 * Returns the cache file for the specified key. If the cache file does not exist, then a new
	 * cache file is created by calling the resource converter that was provided in the constructor
	 * for this cache instance with the specified source resource.
	 *
	 * @param key
	 *            the cache key
	 * @param source
	 *            the resource to be converted (may be null). If null, the result will be non-null
	 *            only if the cache entry already exists.
	 * @return the cached resource object, or null if {@code source} is null and the cache entry
	 *         does not already exist.
	 * @throws IOException
	 */
	public IResource convert(String key, IResource source) throws IOException {
		final String sourceMethod = "getInputStream"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[] { key, source });
		}

		IResource result = null;
		File cacheFile = null;
		URI referenceUri = null;
		CacheEntry tryCacheEntry = (CacheEntry) get(key);

		if (tryCacheEntry != null) {
			// Make local copies of volatile CacheEntry fields
			cacheFile = tryCacheEntry.file;
			referenceUri = tryCacheEntry.referenceUri;
			if (cacheFile != null) {

				// Some platforms round file last modified times to nearest second.
				if (source != null && Math.abs(source.lastModified() - cacheFile.lastModified()) > 1000) {
					// Stale cache entry, remove it and create a new one below
					cacheMap.remove(key, tryCacheEntry);
					// also delete the associated cache file asynchronously.
					aggregator.getCacheManager().deleteFileDelayed(cacheFile.getName());
				} else {
					// found result in cache. Return it.
					result = fileResourceFactory.newResource(cacheFile.toURI());
					result.setReferenceURI(referenceUri);
					log.exiting(sourceClass, sourceMethod, result);
					return result;
				}
			}
		}

		// Result not in cache (or we removed it). Try to create a new cache entry.
		CacheEntry newCacheEntry = new CacheEntry();
		CacheEntry oldCacheEntry = (CacheEntry) cacheMap.putIfAbsent(key, newCacheEntry);
		final CacheEntry cacheEntry = oldCacheEntry != null ? oldCacheEntry : newCacheEntry;

		// Synchronize on the cache entry so that more than one thread won't try to create the
		// file.
		synchronized (cacheEntry) {
			if (cacheEntry.ex != null) {
				// An exception occurred trying to create the file in another thread.
				// Re-throw the exception here.
				throw cacheEntry.ex;
			}
			// First, check to make sure that another thread didn't beat us to the punch.
			if (cacheEntry.file != null) {
				cacheFile = cacheEntry.file;
				referenceUri = cacheEntry.referenceUri;
			} else if (source != null) {
				try {
					// Include the filename part of the source URI in the cached filename
					String path = source.getPath();
					int idx = path.lastIndexOf("/"); //$NON-NLS-1$
					String fname = (idx != -1) ? path.substring(idx + 1) : path;
					cacheFile = File.createTempFile(prefix, "." + fname + suffix, aggregator.getCacheManager().getCacheDir()); //$NON-NLS-1$
					converter.generateCacheContent(source, cacheFile);
					cacheFile.setLastModified(source.lastModified());
					cacheFile = cacheEntry.file = cacheFile;
					referenceUri = cacheEntry.referenceUri = source.getReferenceURI();
				} catch (Throwable t) {
					cacheEntry.ex = (t instanceof IOException) ? (IOException) t
							: new IOException(t);
					cacheMap.remove(key, cacheEntry);
					if (cacheFile != null) {
						aggregator.getCacheManager().deleteFileDelayed(cacheFile.getName());
					}
					throw cacheEntry.ex;
				}
			}
		}
		if (cacheFile != null) {
			result = fileResourceFactory.newResource(cacheFile.toURI());
			result.setReferenceURI(referenceUri);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceClass, result);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.cache.GenericCacheImpl#setAggregator(com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void setAggregator(IAggregator aggregator) {
		this.aggregator = aggregator;
		// get the file resource factory
		Iterable<IAggregatorExtension> iter = aggregator.getExtensions(IResourceFactoryExtensionPoint.ID);
		for (IAggregatorExtension ext : iter) {
			if ("file".equals(ext.getAttribute("scheme"))) { //$NON-NLS-1$ //$NON-NLS-2$
				fileResourceFactory = (IResourceFactory)ext.getInstance();
				break;
			}
		}
		if (fileResourceFactory == null) {
			throw new IllegalStateException("Missing file resource factory"); //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.IGenericCache#newInstance()
	 */
	@Override
	public IGenericCache newInstance() {
		return new ResourceConverterCacheImpl(converter, prefix, suffix);
	}
}
