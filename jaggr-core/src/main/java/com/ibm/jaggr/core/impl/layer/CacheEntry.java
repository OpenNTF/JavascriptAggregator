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

package com.ibm.jaggr.core.impl.layer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;

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
	private static final long serialVersionUID = -2129350665073838766L;

	private transient volatile byte[] bytes = null;
	private volatile String filename = null;
	private volatile int size;
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
		filename = other.filename;
		size = other.size;
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
	 * @return The InputStream for the built layer
	 * @throws IOException
	 */
	public InputStream getInputStream(HttpServletRequest request) throws IOException {
		// Check bytes before filename when reading and reverse order when setting
		byte[] bytes = this.bytes;
		String filename = this.filename;
		InputStream in = null;
		if (bytes != null) {
			in = new ByteArrayInputStream(bytes);
		} else if (filename != null){
			ICacheManager cmgr = ((IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME)).getCacheManager();
			File file = new File(cmgr.getCacheDir(), filename);
			in = new FileInputStream(file);
		}
		return in;
	}
	
	/**
	 * Can fail by returning null, but won't throw an exception.  Will also return null
	 * if no data is available after waiting for 10 seconds.
	 * 
	 * @return The LayerInputStream, or null if data is not available 
	 */
	public InputStream tryGetInputStream(HttpServletRequest request) throws IOException {
		InputStream in = null;
		// Check bytes before filename when reading and reverse order when setting
		if (bytes != null || filename != null) {
			try {
				in = getInputStream(request);
			} catch (Exception e) {
				if (LayerImpl.log.isLoggable(Level.SEVERE)) {
					LayerImpl.log.log(Level.SEVERE, e.getMessage(), e);
				}
				// just return null
			}
		}
		return in;
	}
	
	/**
	 * @param bytes
	 */
	public void setBytes(byte[] bytes) {
		this.size = bytes.length;
		this.bytes = bytes;
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
	 */
	public void persist(final ICacheManager mgr) throws IOException {
		if (delete) return;
		mgr.createCacheFileAsync("layer.", //$NON-NLS-1$
				new ByteArrayInputStream(bytes),
				new ICacheManager.CreateCompletionCallback() {
			@Override
			public void completed(final String fname, Exception e) {
				if (e == null) {
					synchronized (this) {
						if (!delete) {
							if (e == null) {
				                // Set filename before clearing bytes 
				                filename = fname;
				                // Free up the memory for the content now that we've written out to disk
				                // TODO:  Determine a size threshold where we may want to keep the contents
				                // of small files in memory to reduce disk i/o.
				                bytes = null;
							}
						} else {
			    			mgr.deleteFileDelayed(fname);
						}
					}
				}
			}
		});
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
}