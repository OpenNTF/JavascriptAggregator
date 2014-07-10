/*
 * © Copyright IBM Corp. 2013
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */

package com.ibm.jaggr.web;

import com.ibm.jaggr.core.IServiceRegistration;

public class ServiceRegistrationWeb implements IServiceRegistration {

	String ServiceRegistration = null;
	PlatformServicesImpl platformServices = null;
	
	public ServiceRegistrationWeb(String serviceRegistrationWeb, PlatformServicesImpl platformServicesWeb){
		ServiceRegistration = serviceRegistrationWeb;
		platformServices = platformServicesWeb;
	}
	
	@Override
	public void unregister() {
		platformServices.unRegisterService(ServiceRegistration);
	}

}
