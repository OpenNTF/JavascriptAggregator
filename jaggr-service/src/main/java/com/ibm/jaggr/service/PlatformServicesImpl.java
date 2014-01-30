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

import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.PlatformServicesException;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Dictionary;

/**
 * This class provides the OSGi implementation of the
 * {@link IPlatformServices} interface.
 */
public class PlatformServicesImpl implements IPlatformServices {

	private final BundleContext bundleContext;
	public static int resolved = Bundle.RESOLVED;
	public static int active = Bundle.ACTIVE;
	public static int stopping = Bundle.STOPPING;

	public PlatformServicesImpl(BundleContext bc){
		bundleContext = bc;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.osgi.framework.BundleContext#registerService(String clazz,
	 * Object service, Dictionary properties)
	 */
	@Override
	public com.ibm.jaggr.core.ServiceRegistration registerService(String clazz, Object service,
			Dictionary<String, String> properties) {
		ServiceRegistrationOSGi serviceRegistrationOSGi = null;
		ServiceRegistration serviceRegistration = null;
		if (bundleContext != null) {
			serviceRegistration = bundleContext.registerService(clazz, service,
					properties);
			serviceRegistrationOSGi = new ServiceRegistrationOSGi(serviceRegistration);
		}
		return serviceRegistrationOSGi;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.osgi.framework.BundleContext#getServiceReferences(String clazz,
	 * String filter)
	 */
	@Override
	public ServiceReference[] getServiceReferences(String clazz, String filter) throws PlatformServicesException {
		ServiceReference[] refs = null;
		try {
			if (bundleContext != null) {
				refs = bundleContext.getServiceReferences(clazz, filter);
			}
		} catch (InvalidSyntaxException e) {
			throw new PlatformServicesException(e);
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
		if (bundleContext != null && bundleContext.getBundle() != null) {
			return bundleContext.getBundle().getHeaders();
		} else {
			return null;
		}
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
	public URI getAppContextURI() throws URISyntaxException {
		URI uri = null;
		uri = new URI("namedbundleresource://" +  //$NON-NLS-1$
				bundleContext.getBundle().getSymbolicName()  + "/"); //$NON-NLS-1$
		return uri;
	}

}
