/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;

import java.io.IOException;


/**
 * Thrown when the aggregator is servicing a request the dependency graph is
 * in the process of being built/validated, and development mode is enabled.
 * 
 * @author chuckd@us.ibm.com
 */
public class ProcessingDependenciesException extends IOException {
	private static final long serialVersionUID = -8334623620467466846L;

}
