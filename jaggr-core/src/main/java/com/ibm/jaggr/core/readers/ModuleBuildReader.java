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

import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.modulebuilder.ModuleBuildFuture;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

/**
 * Objects of this class are returned in a {@link Future} from
 * {@link IModule#getBuild(HttpServletRequest)}.  This class is merely
 * a wrapper for the items that, taken together, represent a module build.
 */
public class ModuleBuildReader extends Reader {
	private Reader reader;
	private List<ModuleBuildFuture> extraBuilds;
	private List<ICacheKeyGenerator> keyGenerators;
	private final boolean isScript;
	private final String error;

	/**
	 * Constructor for a Build object specifying a reader, key generator
	 * and error flag.
	 *
	 * @param reader A {@link Reader} to the build content
	 * @param isScript true if the reader content is executable script code
	 * @param keyGens The {@link ICacheKeyGenerator} list for this IModule
	 * @param error If not null, then an message describing the error
	 */
	public ModuleBuildReader(Reader reader, boolean isScript, List<ICacheKeyGenerator> keyGens, String error) {
		this.reader = reader;
		this.isScript = isScript;
		this.keyGenerators = keyGens;
		this.error = error;
		extraBuilds = null;
		if (keyGenerators != null && KeyGenUtil.isProvisional(keyGenerators)) {
			throw new IllegalStateException();
		}
	}

	/**
	 * Consturctor for a reader with no cache key generator and no error
	 *
	 * @param reader A {@link Reader} to the build content
	 * @param isScript true if the reader content is executable script code
	 */
	public ModuleBuildReader(Reader reader, boolean isScript) {
		this(reader, isScript, null, null);
	}

	/**
	 * Constructor for a build reader from a string
	 *
	 * @param str the string
	 * @param isScript true if the string contains executable script code
	 */
	public ModuleBuildReader(String str, boolean isScript) {
		this(new StringReader(str), isScript);
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
		return error != null;
	}

	public String getErrorMessage() {
		return error;
	}

	/**
	 * @return true if the content is scirpt code
	 */
	public boolean isScript() {
		return isScript;
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
	 * Adds the specified future to the list of extra builds.
	 * <p>
	 *
	 * @param future The future to add
	 */
	public void addExtraBuild(ModuleBuildFuture future) {
		if (extraBuilds == null) {
			extraBuilds = new LinkedList<ModuleBuildFuture>();
		}
		extraBuilds.add(future);
	}

	/**
	 * Returns the list of additional futures that should be processed along with this build
	 * this future
	 *
	 * @return The list of extra build futures.
	 */
	public List<ModuleBuildFuture> getExtraBuilds() {
		return extraBuilds == null ? Collections.<ModuleBuildFuture>emptyList() : Collections.unmodifiableList(extraBuilds);
	}
}