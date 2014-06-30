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

import com.ibm.jaggr.core.IServiceRegistration;

/**
 * OSGi platform implementation for {@code com.ibm.jaggr.core.ServiceRegistration} interface.
 * This class acts as a wrapper for {@code org.osgi.framework.ServiceRegistration} and uses this class
 * as service registration on OSGi platform.
 *
 */

public class ServiceRegistrationOSGi implements IServiceRegistration {

	private org.osgi.framework.ServiceRegistration serviceRegistrationOSGi;

	public ServiceRegistrationOSGi(org.osgi.framework.ServiceRegistration serviceRegistration){
		serviceRegistrationOSGi = serviceRegistration;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.code.ServiceRegistration.unregister()
	 */
	@Override
	public void unregister() {
		serviceRegistrationOSGi.unregister();
	}

	@Override
	public String toString() {
		return serviceRegistrationOSGi.toString();
	}

}
