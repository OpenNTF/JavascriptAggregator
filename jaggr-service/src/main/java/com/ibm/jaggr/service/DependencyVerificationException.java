/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;

import java.io.IOException;

/**
 * An exception that's thrown by the JavaScript optimizer when
 * it detects that the dependency list of a module has changed from
 * what was used to generate the dependency graph and development
 * mode is enabled
 * 
 * @author chuckd@us.ibm.com
 */
public class DependencyVerificationException extends IOException {
	private static final long serialVersionUID = 824963277533958913L;

	public DependencyVerificationException(String message) {
		super(message);
	}
	
}
