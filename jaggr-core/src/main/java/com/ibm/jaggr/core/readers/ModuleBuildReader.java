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

package com.ibm.jaggr.core.readers;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.impl.layer.ModuleBuildFuture;
import com.ibm.jaggr.core.module.IModule;

/**
 * Objects of this class are returned in a {@link Future} from 
 * {@link IModule#getBuild(HttpServletRequest)}.  This class is merely 
 * a wrapper for the items that, taken together, represent a module build.
 */
public class ModuleBuildReader extends Reader {
	private Reader reader;
	private List<ModuleBuildFuture> before;
	private List<ModuleBuildFuture> after;
	private List<ICacheKeyGenerator> keyGenerators;
	private boolean error;
	
	/**
	 * Constructor for a Build object specifying a reader, key generator
	 * and error flag.
	 * 
	 * @param reader A {@link Reader} to the build content
	 * @param keyGens The {@link ICacheKeyGenerator} list for this IModule
	 * @param error True if this module build contains an error response
	 */
	public ModuleBuildReader(Reader reader, List<ICacheKeyGenerator> keyGens, boolean error) {
		this.reader = reader;
		this.keyGenerators = keyGens;
		this.error = error;
		before = after = null;
		if (keyGenerators != null && KeyGenUtil.isProvisional(keyGenerators)) {
			throw new IllegalStateException();
		}
	}
	
	/**
	 * Consturctor for a reader with no cache key generator and no error
	 * 
	 * @param reader A {@link Reader} to the build content
	 */
	public ModuleBuildReader(Reader reader) {
		this(reader, null, false);
	}
	
	/**
	 * Constructor for a build reader from a string
	 * 
	 * @param str the string
	 */
	public ModuleBuildReader(String str) {
		this(new StringReader(str));
	}
		
	/**
	 * Returns the cache key generator for this module
	 * 
	 * @return The cache key generator
	 */
	public List<ICacheKeyGenerator> getCacheKeyGenerators() {
		return keyGenerators;
	}
	
	/**
	 * Returns the error flag for this build. If true, an error occurred
	 * while generating the build. Responses containing build errors are not
	 * cached by the layer cache manager, and HTTP responses for layers that
	 * include build errors include cache control headers to prevent the
	 * response from being cached by the browser or proxy caches.
	 * 
	 * @return The error flag for the build
	 */
	public boolean isError() {
		return error;
	}

	/* (non-Javadoc)
	 * @see java.io.Reader#read(char[], int, int)
	 */
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		return reader.read(cbuf, off, len);
	}

	/* (non-Javadoc)
	 * @see java.io.Reader#close()
	 */
	@Override
	public void close() throws IOException {
		reader.close();
	}
	
	/**
	 * Adds the specified future to the list of before futures.
	 * <p>
	 * Before futures are processed ahead of this future.
	 * 
	 * @param future The future to add to the before list
	 */
	public void addBefore(ModuleBuildFuture future) {
		if (before == null) {
			before = new LinkedList<ModuleBuildFuture>();
		}
		before.add(future);
	}
	
	/**
	 * Adds the specified future to the list of after futures.
	 * <p>
	 * After futures are processed following this future.
	 * 
	 * @param future The future to add to the before after
	 */
	public void addAfter(ModuleBuildFuture future) {
		if (after == null) {
			after = new LinkedList<ModuleBuildFuture>();
		}
		after.add(future);
	}
	
	/**
	 * Returns the list of additional futures that should be processed ahead 
	 * of this future
	 * 
	 * @return The list of before futures.
	 */
	public List<ModuleBuildFuture> getBefore() {
		return before == null ? Collections.<ModuleBuildFuture>emptyList() : Collections.unmodifiableList(before);
	}
	
	/**
	 * Returns the list of additional futures that should be processed following 
	 * this future
	 * 
	 * @return The list of after futures.
	 */
	public List<ModuleBuildFuture> getAfter() {
		return after == null ? Collections.<ModuleBuildFuture>emptyList() : Collections.unmodifiableList(after);
	}
}