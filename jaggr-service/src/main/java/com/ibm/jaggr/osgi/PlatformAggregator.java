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

package com.ibm.jaggr.osgi;

import java.net.URL;
import java.util.Dictionary;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import com.ibm.jaggr.osgi.service.util.ConsoleService;
import com.ibm.jaggr.service.impl.IPlatformAggregator;

public class PlatformAggregator implements IPlatformAggregator {
	
	private BundleContext bundleContext = null;
	private static final Logger log = Logger.getLogger(PlatformAggregator.class.getName());
	public static int resolved = Bundle.RESOLVED;
	public static int active = Bundle.ACTIVE;
	public static int stopping = Bundle.STOPPING;
	
	public ServiceRegistration registerService(String clazz, Object service, Dictionary properties){
		ServiceRegistration serviceRegistration = null;
		if(bundleContext != null){
			serviceRegistration = bundleContext.registerService(clazz, service, properties);
		}
			return serviceRegistration;		
	}
	
	public void unRegisterService(Object service){
		((ServiceRegistration)service).unregister();		
	}
	
	public ServiceReference[] getServiceReferences(String clazz, String filter){		
		ServiceReference[] refs = null;
		try {
			if(bundleContext != null){
				refs = bundleContext.getServiceReferences(clazz, filter);
			}
		} catch (InvalidSyntaxException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}	
		}
		return refs;
		
	}

	public Object getService(Object serviceReference){
		if(bundleContext != null){
			return bundleContext.getService((ServiceReference)serviceReference);
		}else{
			return null;
		}
	}
	
	public boolean unGetService(Object serviceReference, String clazz){
		if(bundleContext != null){
			return bundleContext.ungetService((ServiceReference)serviceReference);
		}else{
			return false;
		}
	}	
	
	public void setBundleContext(BundleContext bc){
		bundleContext = bc;
	}
	
	public URL getResource(String resourceName){		
		if(bundleContext != null){
			return bundleContext.getBundle().getResource(resourceName);
		}else{
			return null;
		}
	}
	
	public Dictionary<String, String> getHeaders(){
		if(bundleContext != null){
			return bundleContext.getBundle().getHeaders();
		}else{
			return null;
		}
	}
	
	
	
	public void println(String msg){		
		new ConsoleService().println(msg);
	}

	public boolean initiateShutdown(){
		if(bundleContext != null){
			int bundleState = bundleContext.getBundle().getState();		
			if(bundleState == Bundle.ACTIVE || bundleState == Bundle.STOPPING){
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
	}	
	
}
