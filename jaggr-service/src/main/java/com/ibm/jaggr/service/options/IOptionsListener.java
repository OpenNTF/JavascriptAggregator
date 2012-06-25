/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.options;

/**
 * Listener interface for options changes. To receive notification of options
 * changes, register an instance of the implementing class as an OSGi service.
 * The listener registration should specify a filter using the name property 
 * with the value obtained by calling {@link IOptions#getName()} for the 
 * options object who's changes you want to track.
 */
public interface IOptionsListener {
	/**
	 * This method is called when the options are loaded/changed.
	 * 
	 * @param options
	 *            The new options. Note that changes to the options may occur
	 *            in-place, using the same IOptions object with updated values,
	 *            or may result in a new object instance. This interface does not
	 *            specify which approach is used by an implementation.
	 * @param sequence
	 *            The sequence number. Notifications for different listener
	 *            events (options, config, dependencies) that have the same
	 *            cause have the same sequence number. The sequence number is
	 *            incremented for subsequent event causes, but there is no
	 *            guarantee about the the values for subsequent notifications
	 *            other than that they will be increasing.
	 */
	public void optionsUpdated(IOptions options, long sequence);
}
