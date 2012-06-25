/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.jaggr.service.transport.IHttpTransport;

/**
 * Listener interface for HTTP request processing.  The start method
 * is called for all registered listeners by the aggregator
 * at the start of request processing.
 * <p>
 * Listeners are registered as an OSGi service using the name of 
 * the aggregator as the name attribute of the listener object.
 */
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
