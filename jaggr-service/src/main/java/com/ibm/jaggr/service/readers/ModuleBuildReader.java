/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.readers;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.module.IModule;

/**
 * Objects of this class are returned in a {@link Future} from 
 * {@link IModule#getBuild(HttpServletRequest)}.  This class is merely 
 * a wrapper for the items that, taken together, represent a module build.
 */
public class ModuleBuildReader extends Reader {
	private Reader reader;
	private ICacheKeyGenerator[] keyGenerators;
	private boolean error;
	
	/**
	 * Constructor for a Build object specifying a reader, key generator
	 * and error flag.
	 * 
	 * @param reader A {@link Reader} to the build content
	 * @param keyGens The {@link ICacheKeyGenerator} array for this IModule
	 * @param error True if this module build contains an error response
	 */
	public ModuleBuildReader(Reader reader, ICacheKeyGenerator[] keyGens, boolean error) {
		this.reader = reader;
		this.keyGenerators = keyGens;
		this.error = error;
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
	public ICacheKeyGenerator[] getCacheKeyGenerators() {
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
}