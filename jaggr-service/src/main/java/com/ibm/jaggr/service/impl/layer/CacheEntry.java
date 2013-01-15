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

package com.ibm.jaggr.service.impl.layer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.util.CopyUtil;

/**
 * Class to encapsulate operations on layer builds.  Uses {@link ExecutorService}
 * objects to asynchronously create and delete cache files.  ICache files are deleted
 * using a {@link ScheduledExecutorService} with a delay to allow sufficient time
 * for any threads that may still be using the file to be done with them. 
 * <p>
 * This class avoids synchronization by declaring the instance variables volatile and being
 * careful about the order in which variables are assigned and read.  See comments in the 
 * various methods for details.
 */
class CacheEntry implements Serializable {
	private static final long serialVersionUID = -2129350665073838766L;

	private transient volatile byte[] zippedBytes = null;
	private transient volatile int expandedSize = -1;
	volatile String filename = null;
	private volatile boolean delete = false;
	final int layerId;
	final String layerKey;
	final long lastModified;
	
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
	public InputStream getInputStream(HttpServletRequest request, 
			HttpServletResponse response) throws IOException {
		// Check bytes before filename when reading and reverse order when setting
		byte[] bytes = this.zippedBytes;
		String filename = this.filename;
		InputStream in, logInputStream = null;
		long size;
		// determine if response is to be gzipped
		boolean isGzipped = false;
		String accept = request.getHeader("Accept-Encoding"); //$NON-NLS-1$
        if (LayerImpl.log.isLoggable(Level.FINE)) {
        	LayerImpl.log.fine("Accept-Encoding = " + (accept != null ? accept : "null"));
        }
        if (accept != null)
        	accept = accept.toLowerCase();
        if (accept != null && accept.contains("gzip") && !accept.contains("gzip;q=0")) { //$NON-NLS-1$ //$NON-NLS-2$
        	isGzipped = true;
        }
        
		if (bytes != null) {
			in = new ByteArrayInputStream(bytes);
			size = bytes.length;
			if (!isGzipped) {
				in = new GZIPInputStream(in);
            	size = expandedSize;
			}
			if (LayerImpl.log.isLoggable(Level.FINEST)) {
				logInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
			}
		} else {
			ICacheManager cmgr = ((IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME)).getCacheManager();
			if (isGzipped) {
				filename += ".zip";
			}
			File file = new File(cmgr.getCacheDir(), filename);
			in = new FileInputStream(file);
			if (LayerImpl.log.isLoggable(Level.FINEST)) {
				logInputStream = new GZIPInputStream(new FileInputStream(file));
			}
			size = file.length();
		}
        response.setContentType("application/x-javascript; charset=utf-8"); //$NON-NLS-1$
        if (isGzipped) {
        	response.setHeader("Content-Encoding", "gzip"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (LayerImpl.log.isLoggable(Level.FINE)) {
        	LayerImpl.log.fine("Returning " + (isGzipped ? "" : "un-") + "gzipped response: size=" + size); //$NON-NLS-1$
        }
    	response.setHeader("Content-Length", Long.toString(size)); //$NON-NLS-1$
        if (logInputStream != null) {
        	ByteArrayOutputStream bos = new ByteArrayOutputStream();
        	CopyUtil.copy(logInputStream, bos);
        	LayerImpl.log.log(Level.FINEST, "Response: " + bos.toString("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
        }
		return in;
	}
	
	/**
	 * Can fail by returning null, but won't throw an exception.  Will also return null
	 * if no data is available after waiting for 10 seconds.
	 * 
	 * @return The LayerInputStream, or null if data is not available 
	 */
	public InputStream tryGetInputStream(HttpServletRequest request, 
			HttpServletResponse response) throws IOException {
		InputStream in = null;
		// Check bytes before filename when reading and reverse order when setting
		if (zippedBytes != null || filename != null) {
			try {
				in = getInputStream(request, response);
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
	public void setBytes(byte[] bytes, int expandedSize) {
		this.zippedBytes = bytes;
		this.expandedSize = expandedSize;
	}
	
	/**
	 * Returns the expanded (unzipped) size of this cached entry
	 * 
	 * @return the expanded (unzipped) size
	 */
	public int getExpandedSize() {
		return expandedSize;
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
			mgr.deleteFileDelayed(filename + ".zip");
		} 
	}
	
	/**
	 * Asynchronously write the layer build content to disk and set filename to the 
	 * name of the cache files when done.  Save the unzipped contents first, and then
	 * once we have the cache filename, save the zipped contents to a file with the
	 * same name with a .zip extension.
	 * 
	 * @param mgr The cache manager
	 */
	public void persist(final ICacheManager mgr) throws IOException {
		if (delete) return;
		mgr.createCacheFileAsync("layer.", //$NON-NLS-1$
				new GZIPInputStream(new ByteArrayInputStream(zippedBytes)),
				new ICacheManager.CreateCompletionCallback() {
			@Override
			public void completed(final String fname, Exception e) {
				if (e == null) {
					mgr.createNamedCacheFileAsync(fname + ".zip", new ByteArrayInputStream(zippedBytes), 
							new ICacheManager.CreateCompletionCallback() {
						@Override
						public void completed(String zippedFname, Exception e) {
							synchronized (this) {
								if (!delete) {
									if (e == null) {
										// now that we have the filename for the non-zipped result,
										// save the zipped result using the same filename with a .zip
										// extension
						                // Set filename before clearing bytes 
						                filename = fname;
						                // Free up the memory for the content now that we've written out to disk
						                // TODO:  Determine a size threshold where we may want to keep the contents
						                // of small files in memory to reduce disk i/o.
						                zippedBytes = null;
									}
								} else {
					    			mgr.deleteFileDelayed(fname);
					    			mgr.deleteFileDelayed(fname + ".zip");
								}
							}
						}
					});				
				}
			}
		});
	}
}