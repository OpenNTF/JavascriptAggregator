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
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.PlatformServicesException;

import com.ibm.jaggr.service.impl.Activator;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides the OSGi implementation of the
 * {@link IPlatformServices} interface.
 */
public class PlatformServicesImpl implements IPlatformServices {
	private static final Logger log = Logger.getLogger(PlatformServicesImpl.class.getName());

	private final Bundle contributingBundle;

	public PlatformServicesImpl(Bundle contributingBundle){
		final String sourceMethod = "<ctor>"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(PlatformServicesImpl.class.getName(), sourceMethod, new Object[]{contributingBundle});
		}
		this.contributingBundle = contributingBundle;
		if (isTraceLogging) {
			log.exiting(PlatformServicesImpl.class.getName(), sourceMethod);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.osgi.framework.BundleContext#registerService(String clazz,
	 * Object service, Dictionary properties)
	 */
	@Override
	public IServiceRegistration registerService(String clazz, Object service,
			Dictionary<String, String> properties) {
		final String sourceMethod = "registerService"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(PlatformServicesImpl.class.getName(), sourceMethod, new Object[]{clazz, service, properties});
		}
		ServiceRegistrationOSGi serviceRegistrationOSGi = null;
		ServiceRegistration serviceRegistration = null;
		BundleContext bundleContext = Activator.getBundleContext();
		if (bundleContext != null) {
			serviceRegistration = bundleContext.registerService(clazz, service,
					properties);
			serviceRegistrationOSGi = new ServiceRegistrationOSGi(serviceRegistration);
		}
		if (isTraceLogging) {
			log.exiting(PlatformServicesImpl.class.getName(), sourceMethod, serviceRegistrationOSGi);
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
	public IServiceReference[] getServiceReferences(String clazz, String filter) throws PlatformServicesException {
		final String sourceMethod = "getServiceReferences"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(PlatformServicesImpl.class.getName(), sourceMethod, new Object[]{clazz, filter});
		}
		ServiceReferenceOSGi[] refs = null;
		try {
			BundleContext bundleContext = Activator.getBundleContext();
			if (bundleContext != null) {
				ServiceReference[] platformRefs = bundleContext.getServiceReferences(clazz, filter);
				if (platformRefs != null) {
					refs = new ServiceReferenceOSGi[platformRefs.length];
					for (int i = 0; i < platformRefs.length; i++) {
						refs[i] = new ServiceReferenceOSGi(platformRefs[i]);
					}
				}
			}
		} catch (InvalidSyntaxException e) {
			throw new PlatformServicesException(e);
		}
		if (isTraceLogging) {
			log.exiting(PlatformServicesImpl.class.getName(), sourceMethod, refs != null ? Arrays.asList(new IServiceReference[refs.length]) : null);
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
	public Object getService(IServiceReference serviceReference) {
		final String sourceMethod = "getService"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(PlatformServicesImpl.class.getName(), sourceMethod, serviceReference);
		}
		Object result = null;
		BundleContext bundleContext = Activator.getBundleContext();
		if (bundleContext != null) {
			result = bundleContext
					.getService((ServiceReference) serviceReference.getPlatformObject());
		}
		if (isTraceLogging) {
			log.exiting(PlatformServicesImpl.class.getName(), sourceMethod, result);
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.osgi.framework.BundleContext#unGetService(ServiceReference
	 * reference)
	 */
	@Override
	public boolean ungetService(IServiceReference serviceReference) {
		final String sourceMethod = "ungetService"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(PlatformServicesImpl.class.getName(), sourceMethod, new Object[]{serviceReference});
		}
		boolean result = false;
		BundleContext bundleContext = Activator.getBundleContext();
		if (bundleContext != null) {
			result = bundleContext
					.ungetService((ServiceReference) serviceReference.getPlatformObject());
		}
		if (isTraceLogging) {
			log.exiting(PlatformServicesImpl.class.getName(), sourceMethod, result);
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.osgi.framework.Bundle#getResource(String name)
	 */
	@Override
	public URL getResource(String resourceName) {
		final String sourceMethod = "getResource"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(PlatformServicesImpl.class.getName(), sourceMethod, new Object[]{resourceName});
		}
		URL result = null;
		if (contributingBundle != null) {
			result = contributingBundle.getResource(resourceName);
		}
		if (isTraceLogging) {
			log.exiting(PlatformServicesImpl.class.getName(), sourceMethod, result);
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.osgi.framework.Bundle#getHeaders()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Dictionary<String, String> getHeaders() {
		final String sourceMethod = "getHeaders"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(PlatformServicesImpl.class.getName(), sourceMethod);
		}
		Dictionary<String, String> result = null;
		if (contributingBundle != null) {
			result = contributingBundle.getHeaders();
		}
		if (isTraceLogging) {
			log.exiting(PlatformServicesImpl.class.getName(), sourceMethod, result);
		}
		return result;
	}

	@Override
	public URI getAppContextURI() throws URISyntaxException {
		final String sourceMethod = "getAppContextURI"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(PlatformServicesImpl.class.getName(), sourceMethod);
		}
		URI result = new URI("namedbundleresource://" +  //$NON-NLS-1$
				contributingBundle.getSymbolicName()  + "/"); //$NON-NLS-1$

		if (isTraceLogging) {
			log.exiting(PlatformServicesImpl.class.getName(), sourceMethod, result);
		}
		return result;
	}

}
