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
