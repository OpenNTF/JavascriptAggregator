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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Dictionary;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.service.impl.AggregatorImpl;
import com.ibm.jaggr.service.util.ConsoleService;

/**
 * This class provides the OSGi implementation of the
 * {@link IPlatformServices} interface.
 */
public class PlatformServicesImpl implements IPlatformServices {

	private BundleContext bundleContext;
	private static final Logger log = Logger.getLogger(PlatformServicesImpl.class
			.getName());
	public static int resolved = Bundle.RESOLVED;
	public static int active = Bundle.ACTIVE;
	public static int stopping = Bundle.STOPPING;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.BundleContext#registerService(String clazz,
	 * Object service, Dictionary properties)
	 */
	@Override
	public ServiceRegistration registerService(String clazz, Object service,
			Dictionary<String, String> properties) {
		ServiceRegistration serviceRegistration = null;
		if (bundleContext != null) {
			serviceRegistration = bundleContext.registerService(clazz, service,
					properties);
		}
		return serviceRegistration;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.ServiceRegistration#unRegisterService()
	 */
	@Override
	public void unRegisterService(Object serviceRegistration) {
		((ServiceRegistration) serviceRegistration).unregister();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.BundleContext#getServiceReferences(String clazz,
	 * String filter)
	 */
	@Override
	public ServiceReference[] getServiceReferences(String clazz, String filter) {
		ServiceReference[] refs = null;
		try {
			if (bundleContext != null) {
				refs = bundleContext.getServiceReferences(clazz, filter);
			}
		} catch (InvalidSyntaxException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		return refs;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.BundleContext#getService(ServiceReference
	 * reference)
	 */
	@Override
	public Object getService(Object serviceReference) {
		if (bundleContext != null) {
			return bundleContext
					.getService((ServiceReference) serviceReference);
		} else {
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.BundleContext#unGetService(ServiceReference
	 * reference)
	 */
	@Override
	public boolean ungetService(Object serviceReference) {
		if (bundleContext != null) {
			return bundleContext
					.ungetService((ServiceReference) serviceReference);
		} else {
			return false;
		}
	}

	/**
	 * This method sets the bundleContext reference that is used by the other
	 * methods in this class. The method is called by the
	 * {@link AggregatorImpl#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, String, Object)}
	 * to set the bundleContext.
	 * 
	 * @param bc
	 */
	public void setBundleContext(BundleContext bc) {
		bundleContext = bc;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.Bundle#getResource(String name)
	 */
	@Override
	public URL getResource(String resourceName) {
		if (bundleContext != null) {
			return bundleContext.getBundle().getResource(resourceName);
		} else {
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.framework.Bundle#getHeaders()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Dictionary<String, String> getHeaders() {
		if (bundleContext != null) {
			return bundleContext.getBundle().getHeaders();
		} else {
			return null;
		}
	}

	@Override
	public void println(String msg) {
		new ConsoleService().println(msg);
	}

	@Override
	public boolean isShuttingdown() {
		if (bundleContext != null) {
			int bundleState = bundleContext.getBundle().getState();
			if (bundleState == Bundle.ACTIVE || bundleState == Bundle.STOPPING) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}

	@Override
	public URI getConfigURL(String configName) throws URISyntaxException {
		URI uri = null;
		uri = new URI("namedbundleresource://" +  //$NON-NLS-1$
					bundleContext.getBundle().getSymbolicName()  +
					(configName.startsWith("/") ? "" : "/") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
					configName);		
		return uri;
	}

}
