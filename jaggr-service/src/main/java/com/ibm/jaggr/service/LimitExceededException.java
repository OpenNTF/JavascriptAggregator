/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;


/**
 * Extneds RuntimeException for limit Aggregator limit exceeded errors
 */
public class LimitExceededException extends RuntimeException {

	private static final long serialVersionUID = 3005341596518701343L;
	
	/**
	 * @param msg the error message
	 */
	public LimitExceededException(String msg) {
		super(msg);
	}
	
}
