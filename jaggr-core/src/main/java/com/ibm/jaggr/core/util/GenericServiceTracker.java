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

package com.ibm.jaggr.core.util;

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
