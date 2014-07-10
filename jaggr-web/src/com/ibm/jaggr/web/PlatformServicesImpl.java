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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.IServiceRegistration;

public class PlatformServicesImpl implements IPlatformServices {

	private Map<String, Object> serviceReferenceMap;

	public PlatformServicesImpl() {
		//But this will be a problem if we ever try to support a command console 
		//because the console communicates with the aggregator instances that are registered in the service registry, 
		//and for that to work, the service registry has to be a global object.
		serviceReferenceMap = new ConcurrentHashMap<String, Object>();
	}

	@Override
	public IServiceRegistration registerService(String clazz, Object service, Dictionary<String, String> properties) {
		StringBuilder sb = new StringBuilder();		
		String uniqueKey = sb.append(clazz).append("_") //$NON-NLS-1$
				.append(service.getClass().getName()).toString();
		if (!serviceReferenceMap.containsKey(uniqueKey)) {			
			Object[]  serviceDetails = new Object[3];
			serviceDetails[0] = clazz;
			serviceDetails[1] = service;
			serviceDetails[2] = properties;			
			serviceReferenceMap.put(uniqueKey, serviceDetails);
		}
		IServiceRegistration serviceRegistation = new ServiceRegistrationWeb(uniqueKey, this);
		
		return serviceRegistation;
	}

	
	public void unRegisterService(Object serviceRegistration) {
		if (serviceReferenceMap.containsKey((String) serviceRegistration)) {			
			serviceReferenceMap.remove((String) serviceRegistration);
			return;
		}			
	}

	@Override
	public IServiceReference[] getServiceReferences(String clazz, String filter) {
		return getServiceReferences(clazz);
	}
	
	public IServiceReference[] getServiceReferences(String clazz) {		
		ArrayList<IServiceReference> serviceReferences = new ArrayList<IServiceReference>();
		Set<String> srs = serviceReferenceMap.keySet();
		for(String sr : srs){
			int index = ((String) sr).indexOf("_"); //$NON-NLS-1$
			String clz = ((String) sr).substring(0, index);
			if(clz.equalsIgnoreCase(clazz) || clazz == null){
				serviceReferences.add(new ServiceReferenceWeb(sr));
			}
		}
		IServiceReference[] serviceRefs = new IServiceReference[serviceReferences.size()];
		for(int i = 0; i < serviceReferences.size(); i++){
			serviceRefs[i] = serviceReferences.get(i);
		}
		return  serviceRefs;
	}

	@Override
	public Object getService(IServiceReference serviceReference) {
		Object[] serviceDetails = null;
		serviceDetails = (Object[])(serviceReferenceMap.get((String)serviceReference.getPlatformObject()));
		return serviceDetails[1];
	}

	@Override
	public synchronized boolean ungetService(IServiceReference serviceReference) {
		boolean status = false;
		if(serviceReferenceMap.containsKey((String)serviceReference.getPlatformObject())){
			serviceReferenceMap.remove((String)serviceReference.getPlatformObject());
			status = true;
			return status;
		}
		return status;
	}

	@Override
	public URL getResource(String resourceName) {		
		return null;	
	}

	@Override
	public Dictionary<String, String> getHeaders() {
		return null;
	}

	@Override
	public URI getAppContextURI() throws URISyntaxException {		
		return null;
	}

}
