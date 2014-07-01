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
package com.ibm.jaggr.service;

import com.ibm.jaggr.core.IServiceReference;

import org.osgi.framework.ServiceReference;

/**
 * Implements the {@link IServiceReference} interface for OSGi using
 * {@link ServiceReference}
 */
public class ServiceReferenceOSGi implements IServiceReference {

	final private ServiceReference ref;

	public ServiceReferenceOSGi(ServiceReference ref) {
		this.ref = ref;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IServiceReference#getPlatformObject()
	 */
	@Override
	public Object getPlatformObject() {
		return ref;
	}

	@Override
	public String toString() {
		return ref.toString();
	}

}
