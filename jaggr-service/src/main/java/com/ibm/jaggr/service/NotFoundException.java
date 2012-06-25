/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;

import java.io.IOException;

/**
 * Extends ServletException for resource not found errors.  Results in a
 * HTTP Status 404 (Not Found) returned to the client.
 */
public class NotFoundException extends IOException {

	private static final long serialVersionUID = 4090848192443486793L;

	/**
	 * Constructs a new exception.
	 */
	public NotFoundException() {
	}

	/**
	 * Constructs a new servlet exception with the specified message.
	 * 
	 * @param message
	 *            a <code>String</code> specifying the text of the exception
	 *            message
	 */
	public NotFoundException(String message) {
		super(message);
	}

	/**
	 * Constructs a new exception when the servlet needs to throw an exception
	 * and include a message about the "root cause" exception that interfered
	 * with its normal operation. The exception's message is based on the
	 * localized message of the underlying exception.
	 * 
	 * @param rootCause
	 *            the <code>Throwable</code> exception that interfered with the
	 *            servlet's normal operation, making this exception necessary
	 */
	public NotFoundException(Throwable rootCause) {
		super(rootCause);
	}

	/**
	 * Constructs a new exception when the servlet needs to throw an exception
	 * and include a message about the "root cause" exception that interfered
	 * with its normal operation, including a description message.
	 * 
	 * @param message
	 *            a <code>String</code> specifying the text of the exception
	 *            message
	 * @param rootCause
	 *            the <code>Throwable</code> exception that interfered with the
	 *            servlet's normal operation, making this exception necessary
	 */
	public NotFoundException(String message, Throwable rootCause) {
		super(message, rootCause);
	}

}
