/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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
 * Extends ServletException for incorrect or improperly formated requests.
 * Results in a HTTP 400 Status (Bad Request) returned to the client.
 */
public class BadRequestException extends IOException {
	private static final long serialVersionUID = -7233292670300274166L;

	/**
	 * Constructs a new exception.
	 */
	public BadRequestException() {
		super();
	}

	/**
	 * Constructs a new servlet exception with the specified message.
	 * 
	 * @param message
	 *            a <code>String</code> specifying the text of the exception
	 *            message
	 */
	public BadRequestException(String message) {
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
	public BadRequestException(Throwable rootCause) {
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
	public BadRequestException(String message, Throwable rootCause) {
		super(message, rootCause);
	}
}
