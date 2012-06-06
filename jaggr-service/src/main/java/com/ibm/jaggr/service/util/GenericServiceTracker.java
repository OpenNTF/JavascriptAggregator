/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.util;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class GenericServiceTracker<T> extends ServiceTracker {

	public GenericServiceTracker(BundleContext arg0, Filter arg1,
			ServiceTrackerCustomizer arg2) {
		super(arg0, arg1, arg2);
	}

	public GenericServiceTracker(BundleContext arg0, ServiceReference arg1,
			ServiceTrackerCustomizer arg2) {
		super(arg0, arg1, arg2);
	}

	public GenericServiceTracker(BundleContext arg0, String arg1,
			ServiceTrackerCustomizer arg2) {
		super(arg0, arg1, arg2);
	}

	@SuppressWarnings("unchecked")
	@Override
	public T getService() {
		return (T)super.getService();
	}

	@SuppressWarnings("unchecked")
	@Override
	public T getService(ServiceReference arg0) {
		return (T)super.getService(arg0);
	}

	@SuppressWarnings("unchecked")
	@Override
	public T[] getServices() {
		return (T[])super.getServices();
	}

	@SuppressWarnings("unchecked")
	@Override
	public T waitForService(long arg0) throws InterruptedException {
		return (T) super.waitForService(arg0);
	}
	
	

}
