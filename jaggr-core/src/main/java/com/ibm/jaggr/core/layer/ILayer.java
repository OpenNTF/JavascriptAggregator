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

package com.ibm.jaggr.core.layer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.jaggr.core.cache.ICache;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.transport.IHttpTransport;

/**
 * A layer is an aggregation of modules. An ILayer object organizes a collection
 * of layer builds, where each layer build is an aggregation of module builds
 * for the same modules, with the layer builds varying according to the features
 * specified in the request and other request parameters.
 * <p>
 * Collections of ILayer objects and {@link IModule} objects together constitute
 * the cache metadata encapsulated by an {@link ICache} object. Instances of
 * this object are serialized, through serialization of the containing
 * {@link ICache} object, by the cache manager when it periodically saves a
 * snapshot of the cache metadata to disk. Serialization is actually performed
 * on clones of the objects in the cache in order to avoid performance
 * degradation which would be caused by locking of critical sections during file
 * I/O.
 * <p>
 * Instances of ILayer are cloneable and serializable.
 */
public interface ILayer extends Serializable {
	
	/**
	 * Name of request attribute indicating that the response should not be
	 * cached on the browser.  Used in development mode when error modules contain 
	 * JavaScript code to invoke the console logger on the browser.
	 */
	public static final String NOCACHE_RESPONSE_REQATTRNAME = ILayer.class.getName() + ".nocache"; //$NON-NLS-1$
	
	/**
	 * Name of the request attribute containing the queue of module build
	 * futures. This queue is used to add additional modules specified by
	 * builders to the response. Note that this attribute may be null if the
	 * request does not support adding modules (i.e. if
	 * {@link IHttpTransport#NOADDMODULES_REQATTRNAME} is true);
	 */
	public static final String BUILDFUTURESQUEUE_REQATTRNAME = ILayer.class.getName() + ".buildQueue"; //$NON-NLS-1$

	/**
	 * Returns the {@link InputStream} for the assembled and gzipped layer build
	 * which was generated using the compilation level, has-conditions and
	 * options that are specified in the request.
	 * <p>
	 * Has the side effect of setting the appropriate Content-Length,
	 * Content-Type and Content-Encoding headers in the response.
	 * 
	 * @param request
	 *            The request object
	 * @param response
	 *            The response object
	 * @return LayerInputStream The object encapsulating the built layer
	 * @throws Exception
	 */
	public InputStream getInputStream(HttpServletRequest request,
			HttpServletResponse response) throws IOException;

	/**
	 * Returns the lastModified time of the layer based on the latest last
	 * modified time of each of the component source files. This method assumes
	 * that it will be called multiple times for the same request and so uses
	 * the request attributes namespace to cache results and avoid duplicate
	 * processing.
	 * <p>
	 * In production mode, the last modified times of the source files are
	 * checked when the layer is initially created and the first time the layer
	 * is accessed after a server restart. In development mode, we determine the
	 * last modified time of the layer from the last modified times of the
	 * component source files on every request.
	 * 
	 * @param request
	 *            The http request object
	 * @throws IOException
	 */
	public long getLastModified(HttpServletRequest request)
			throws IOException;

	/**
	 * Returns the cache key that this layer is associated with in the
	 * layer cache.
	 * 
	 * @return this layer's cache key
	 */
	public String getKey();
}
