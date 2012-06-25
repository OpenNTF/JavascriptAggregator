/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.deps;

/**
 * Listener interface for dependencies changes.  To receive notification 
 * of change events, register the implementing class as an OSGi service,
 * specifying the servlet name under the <code>name</code> property in 
 * the service properties.
 */
public interface IDependenciesListener {
	/**
	 * This method is called when dependencies are loaded/reloaded/validated.
	 * Listeners may track the value returned by
	 * {@link IDependencies#getLastModified()} to detect when the dependencies
	 * have changed.
	 * 
	 * @param deps
	 *            The dependencies object. Note that changes to the dependencies
	 *            may occur in-place, using the same IDependencies object with
	 *            updated values, or may result in a new IDependencies object.
	 *            This interface does not specify which approach is used by an
	 *            implementation.
	 * @param sequence
	 *            The sequence number.  Notifications for different listener
	 *            events (options, config, dependencies) that have the same cause
	 *            have the same sequence number.  Notifications resulting from 
	 *            servlet initialization have the sequence number 1.  The sequence
	 *            number is incremented for subsequent event causes, but there
	 *            is no guarantee about the the values for subsequent notifications
	 *            other than that they will be increasing.
	 */
	public void dependenciesLoaded(IDependencies deps, long sequence);
}
