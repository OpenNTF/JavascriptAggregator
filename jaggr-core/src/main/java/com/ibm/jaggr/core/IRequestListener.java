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

package com.ibm.jaggr.core;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.transport.IHttpTransport;

/**
 * @deprecated as of 1.1.7, replaced by {@link ILayerListener}
 * 
 * Listener interface for HTTP request processing.  The start method
 * is called for all registered listeners by the aggregator
 * at the start of request processing.
 * <p>
 * Listeners are registered as an OSGi service using the name of 
 * the aggregator as the name attribute of the listener object.
 */
@Deprecated
public interface IRequestListener {
	/**
	 * Called by the aggregator at the start of request processing.
	 * The transport has already set the request attributes defined in
	 * {@link IHttpTransport}.
	 * <p>
	 * This method is called by the main request processing thread, so 
	 * the request object can be updated without worrying about 
	 * threading issues.
	 * 
	 * @param request the request object
	 * @param response the response object
	 */
	void startRequest(HttpServletRequest request, HttpServletResponse response);
	
	/**
	 * Called when processing of a request completes normally. All worker
	 * threads that have been started for module builders on this request have
	 * finished and the response is ready to be returned to the client.
	 * <p>
	 * This method is not guaranteed to be called following a call to
	 * {@link #startRequest} if, for example, an exception is thrown while
	 * processing the request.
	 * 
	 * @param request the request object
	 * @param response the response object
	 */
	void endRequest(HttpServletRequest request, HttpServletResponse response);
}
