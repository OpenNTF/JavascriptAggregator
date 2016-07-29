/*
 * (C) Copyright IBM Corp. 2012, 2016
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

package com.ibm.jaggr.core.impl.layer;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;

/**
 * Class to encapsulate operations on layer builds.  Uses {@link ExecutorService}
 * objects to asynchronously create and delete cache files.  Cache files are deleted
 * using a {@link ScheduledExecutorService} with a delay to allow sufficient time
 * for any threads that may still be using the file to be done with them.
 * <p>
 * This class avoids synchronization by declaring the instance variables volatile and being
 * careful about the order in which variables are assigned and read.  See comments in the
 * various methods for details.
 */
class CacheEntry implements Serializable {
	private static final long serialVersionUID = 6165683127051059701L;

	private transient volatile byte[] bytes = null;
	private transient volatile byte[] sourceMap = null;
	private volatile String filename = null;
	private volatile int size;
	private volatile int sourceMapSize;
	private volatile boolean delete = false;
	final int layerId;
	final String layerKey;
	final long lastModified;

	/* Copy constructor */
	CacheEntry(CacheEntry other) {
		layerId = other.layerId;
		layerKey = other.layerKey;
		lastModified = other.lastModified;
		bytes = other.bytes;
		sourceMap = other.sourceMap;
		filename = other.filename;
		size = other.size;
		sourceMapSize = other.sourceMapSize;
		delete = other.delete;
	}

	CacheEntry(int layerId, String layerKey, long lastModified) {
		this.layerId = layerId;
		this.layerKey = layerKey;
		this.lastModified = lastModified;
	}
	/**
	 * Return an input stream to the layer.  Has side effect of setting the
	 * appropriate Content-Type, Content-Length and Content-Encoding headers
	 * in the response.
	 *
	 * @param request
	 *            the request object
	 * @param sourceMapResult
	 *            (Output) mutable object reference to the source map.  May be null
	 *            if source maps are not being requested.
	 * @return The InputStream for the built layer
	 * @throws IOException
	 */
	public InputStream getInputStream(HttpServletRequest request, MutableObject<byte[]> sourceMapResult) throws IOException {
		// Check bytes before filename when reading and reverse order when setting.
		// The following local variables intentionally hide the instance variables.
		byte[] bytes = this.bytes;
		byte[] sourceMap = this.sourceMap;
		String filename = this.filename;

		InputStream result = null;
		if (bytes != null) {
			// Cache data is already in memory.  Don't need to de-serialize it.
			result = new ByteArrayInputStream(bytes);
			if (sourceMapResult != null && sourceMapSize > 0) {
				sourceMapResult.setValue(sourceMap);
			}
		} else if (filename != null){
			// De-serialize data from cache
			ICacheManager cmgr = ((IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME)).getCacheManager();
			File file = new File(cmgr.getCacheDir(), filename);
			if (sourceMapSize == 0) {
				// No source map data in cache entry so just stream the file.
				result = new FileInputStream(file);
			} else {
				// Entry contains source map data so that means it's a serialized CacheData
				// instance.  De-serialize the object and extract the data.
				CacheData data;
				ObjectInputStream is = new ObjectInputStream(
						new FileInputStream(file));
				try {
					data = (CacheData)is.readObject();
				} catch (ClassNotFoundException e) {
					throw new IOException(e.getMessage(), e);
				} finally {
					IOUtils.closeQuietly(is);
				}
				bytes = data.bytes;
				sourceMap = data.sourceMap;
				if (sourceMapResult != null) {
					sourceMapResult.setValue(sourceMap);
				}
				result = new ByteArrayInputStream(bytes);
			}
		} else {
			throw new IOException();
		}
		return result;
	}

	/**
	 * Can fail by returning null, but won't throw an exception.
	 *
	 * @param request
	 *             the request object
	 * @param sourceMapResult
	 *             (Output) mutable object reference to the source map.  May be null
	 *             if source maps are not being requested.
	 * @return The LayerInputStream, or null if data is not available
	 * @throws IOException
	 */
	public InputStream tryGetInputStream(HttpServletRequest request, MutableObject<byte[]> sourceMapResult) throws IOException {
		InputStream result = null;
		// Check bytes before filename when reading and reverse order when setting
		if (bytes != null || filename != null) {
			try {
				result = getInputStream(request, sourceMapResult);
			} catch (Exception e) {
				if (LayerImpl.log.isLoggable(Level.SEVERE)) {
					LayerImpl.log.log(Level.SEVERE, e.getMessage(), e);
				}
				// just return null
			}
		}
		return result;
	}

	/**
	 * Sets the contents of the cache entry
	 *
	 * @param bytes the layer content
	 */
	public void setBytes(byte[] bytes) {
		this.size = bytes.length;
		this.bytes = bytes;
	}

	/**
	 * Sets the contents of the cache entry with source map info.
	 *
	 * @param bytes The layer content
	 * @param sourceMap The source map for the layer
	 */
	public void setData(byte[] bytes, byte[] sourceMap) {
		this.sourceMap = sourceMap;
		sourceMapSize = sourceMap != null ? sourceMap.length : 0;
		setBytes(bytes);
	}

	/**
	 * Delete the cached build after the specified delay in minues
	 *
	 * @param mgr
	 *            The cache manager. May be null if persist hasn't yet been
	 *            called, in which case, persist will have no effect when it
	 *            is called.
	 */
	public synchronized void delete(final ICacheManager mgr) {
		delete = true;
		if (filename != null) {
			mgr.deleteFileDelayed(filename);
		}
	}

	/**
	 * @return True if the file for this entry has been deleted.
	 */
	public boolean isDeleted() {
		return delete;
	}

	/**
	 * @return The name of the cache file for this entry
	 */
	public String getFilename() {
		return filename;
	}

	/**
	 * @return The size of the data for this cache entry
	 */
	public int getSize() {
		return size;
	}


	/**
	 * Asynchronously write the layer build content to disk and set filename to the
	 * name of the cache files when done.
	 *
	 * @param mgr The cache manager
	 * @throws IOException
	 */
	public void persist(final ICacheManager mgr) throws IOException {
		if (delete) return;
		if (sourceMapSize == 0) {
			// No source map.  Just stream the file
			mgr.createCacheFileAsync("layer.", //$NON-NLS-1$
					new ByteArrayInputStream(bytes),
					new CreateCompletionCallback(mgr));
		} else {
			// cache entry contains source map info.  Create a CacheData instance
			// and serialize object.
			Object data = new CacheData(bytes, sourceMap);
			mgr.externalizeCacheObjectAsync("layer.", //$NON-NLS-1$
					data,
					new CreateCompletionCallback(mgr));
		}
	}

	@Override
	public String toString() {
		return new StringBuffer("CacheEntry(") //$NON-NLS-1$
		.append("layerId:").append(layerId).append(",") //$NON-NLS-1$ //$NON-NLS-2$
		.append("layerKey:").append(layerKey).append(",") //$NON-NLS-1$ //$NON-NLS-2$
		.append("lastMod:").append(lastModified).append(",") //$NON-NLS-1$ //$NON-NLS-2$
		.append("file:").append(filename).append(",") //$NON-NLS-1$ //$NON-NLS-2$
		.append("size:").append(size).append(",") //$NON-NLS-1$ //$NON-NLS-2$
		.append("deleted:").append(delete).append(")").toString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Serializable cache data object for cache entries that include source map data
	 */
	static final class CacheData implements Serializable {
		private static final long serialVersionUID = -3247844042962099916L;

		public final byte[] bytes;
		public final byte[] sourceMap;

		public CacheData(byte[] bytes, byte[] sourceMap) {
			this.bytes = bytes;
			this.sourceMap = sourceMap;
		}
	}

	/**
	 * Common create completion callback used by {@link CacheEntry#persist(ICacheManager)}
	 * for serializing both types of cache data (file stream and serialized object).
	 */
	class CreateCompletionCallback implements ICacheManager.CreateCompletionCallback {
		private final ICacheManager mgr;

		private CreateCompletionCallback(ICacheManager mgr) {
			this.mgr = mgr;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.cache.ICacheManager.CreateCompletionCallback#completed(java.lang.String, java.lang.Exception)
		 */
		@Override
		public void completed(String fname, Exception e) {
			synchronized(this) {
				if (!delete) {
					if (e == null) {
						// Must set filename before clearing content
						// since we don't synchronize.
						filename = fname;
						// Free up the memory for the content now that
						// we've written out to disk
						// TODO: Determine a size threshold where we may
						// want to keep the contents
						// of small files in memory to reduce disk i/o.
						sourceMap = null;
						bytes = null;
					}
				} else {
					mgr.deleteFileDelayed(fname);
				}
			}
		}
	}
}