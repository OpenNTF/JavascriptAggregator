/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;

import javax.servlet.http.HttpServlet;

/**
 * Listener interface for shutdown notification. To receive shutdown
 * notification, register the implementing class as an OSGi service, specifying
 * the name of the aggregator under the <code>name</code> property in the
 * service properties.
 */
public interface IShutdownListener {

	/**
	 * This method is called from within the aggregator's
	 * {@link HttpServlet#destroy()} method to notify listeners that the servlet
	 * is shutting down.
	 * 
	 * @param aggregator
	 *            The aggregator that is shutting down
	 */
	public void shutdown(IAggregator aggregator);
}
