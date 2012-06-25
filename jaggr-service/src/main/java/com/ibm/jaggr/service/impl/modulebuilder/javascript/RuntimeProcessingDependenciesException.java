/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.modulebuilder.javascript;

import com.ibm.jaggr.service.ProcessingDependenciesException;

public class RuntimeProcessingDependenciesException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	/**
	 * This class is thrown by custom compiler pass modules when a
	 * {@link ProcessingDependenciesException is caught. The interface for
	 * custom compiler pass modules doesn't allow them to throw checked
	 * exceptions, so this class inherits from RuntimeException. Instances of
	 * this exception are caught by the JavaScript module builder which in turn
	 * throws {@link ProcessingDependenciesException}.
	 */
	RuntimeProcessingDependenciesException(ProcessingDependenciesException cause) {
		super(cause);
	}
}
