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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;

import com.ibm.jaggr.core.options.IOptions;

/**
 *
 * The cache manager for the aggregator service.
 * <ul>  
 * <li>Implements functionality used by console commands for dumping and clearing the cache</li>
 * <li>Provides a thread executor service for the module builders</li>
 * <li>Provides utility methods for asynchronously creating and deleting cache files</li>
 * <li>Provides access to the {@link ICache} object</li>
 * </ul>
 */
public interface ICacheManager {
    
    /**
     * Clears the cache
     */
    public void clearCache();
    
	/**
	 * Outputs the string representation of the cache to the specified 
	 * writer, filtered using the specified regular expression pattern
	 * 
	 * @param writer
	 *            The writer object to output to
	 * @param filter
	 *            The regular expression filter, or null
	 * @throws IOException
	 */
    public void dumpCache(Writer writer, Pattern filter) throws IOException;
    
	/**
	 * Returns the current cache object
	 * 
	 * @return The cache object
	 */
	public ICache getCache();
	
	/**
	 * Utility method to create a cache file on an asynchronous thread.
	 * 
	 * @param fileName
	 *            The filename of the cache file.  The cache file with the
	 *            specified name will be created in the cache directory.  The 
	 *            filename may not include a path component
	 * @param content
	 *            The {@link InputStream} to read the file contents from
	 * @param callback
	 *            The completion callback
	 */
	public void createNamedCacheFileAsync(String fileName, InputStream content, CreateCompletionCallback callback);
	
	/**
	 * Utility method to create a cache file on an asynchronous thread.
	 * 
	 * @param fileNamePrefix
	 *            The prefix to use for the generated file name
	 * @param content
	 *            The {@link InputStream} to read the file contents from
	 * @param callback
	 *            The completion callback
	 */
	public void createCacheFileAsync(String fileNamePrefix, InputStream content, CreateCompletionCallback callback);
	
	/**
	 * Utility method to create a cache file on an asynchronous thread.
	 * 
	 * @param filename
	 *            The filename of the cache file.  The cache file with the
	 *            specified name will be created in the cache directory.  The 
	 *            filename may not include a path component
	 * @param content
	 *            The {@link Reader} to read the file contents from
	 * @param callback
	 *            The completion callback
	 */
	public void createNamedCacheFileAsync(String filename, Reader content, CreateCompletionCallback callback);

	/**
	 * Utility method to create a cache file on an asynchronous thread.
	 * 
	 * @param fileNamePrefix
	 *            The prefix to use for the generated file name
	 * @param content
	 *            The {@link Reader} to read the file contents from
	 * @param callback
	 *            The completion callback
	 */
	public void createCacheFileAsync(String fileNamePrefix, Reader content, CreateCompletionCallback callback);

	/**
	 * Utility method to externalize an object on an asynchronous thread.
	 * 
	 * @param filename
	 *            The prefix to use for the generated file name
	 * @param object
	 *            The object to externalize
	 * @param callback
	 *            The completion callback
	 */
	public void externalizeCacheObjectAsync(String filename, Object object, CreateCompletionCallback callback);
	
	/**
	 * Utility method to asynchronously delete cache files after a delay 
	 * period specified by {@link IOptions#getDeleteDelay()}.  The idea is
	 * to delay deleting the cache file long enough so that any threads 
	 * which may be using the file at the time this method is called have
	 * finished with it and no longer need it.  Note that the file may be
	 * deleted before the delay time has expired if the aggregator is 
	 * shutdown before the delay time has expired
	 * 
	 * @param filename The name of the cache file to delete.
	 */
	public void deleteFileDelayed(String filename);

	/**
	 * Returns the {@link File} object for the cache directory.
	 * 
	 * @return The cache directory
	 */
	public File getCacheDir();

	/**
	 * Interface used to provide a file creation callback. Instances of this
	 * class are specified when calling
	 * {@link ICacheManager#createCacheFileAsync(String, InputStream, CreateCompletionCallback)}
	 * and
	 * {@link ICacheManager#createCacheFileAsync(String, Reader, CreateCompletionCallback)}
	 * .
	 */
	public interface CreateCompletionCallback {
		/**
		 * This method is called when a cache file has been created or an
		 * exception occurred while trying to creat it.
		 * 
		 * @param filename
		 *            The file name of the cache file, not including path
		 *            information, or null if an exception occurred. The file is
		 *            located in the directory specified by
		 *            {@link ICacheManager#getCacheDir()}.
		 * @param e
		 *            The exception object if an exception occurred while trying
		 *            to create the file, or null if the file was successfully
		 *            created.
		 */
		void completed(String filename, Exception e);
	}
	
}
