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

package com.ibm.jaggr.core;

import java.net.URL;
import java.util.Dictionary;

import javax.imageio.spi.ServiceRegistry;

/**
 * Interface for all platform dependent functionalities used by the aggregator.
 * The functionalities have been collated in this interface. This interface has
 * been implemented by platform dependent implementations. For example, there is
 * a OSGi specific implementation which provides the OSGi way for implementing
 * these functionalities. The implementations are kept in the platform specific
 * bundles. For OSGi, the implementation of this interface is location in
 * {@code jaggr-service} bundle.
 * 
 */
public interface IPlatformServices {
	/**
	 * Registers the specified service object with the specified properties
	 * under the specified class name with the platform.
	 * 
	 * @param clazz
	 *            The class name under which the service is registered
	 * @param service
	 *            The service object
	 * @param properties
	 *            The properties for this service.
	 * @return service registration object. For OSGi implementation, this object
	 *         refers to implementation of {@code org.osgi.framework.ServiceRegistration}
	 *         interface.
	 * 
	 */
	public Object registerService(String clazz, Object service,
			Dictionary<String, String> properties);

	/**
	 * Removes the service registration from the platform.
	 * 
	 * @param serviceRegistration
	 *            Service registration object. For OSGi implementation, this
	 *            object refers to implementation of
	 *            {@code org.osgi.framework.ServiceRegistration} interface.
	 */
	public void unRegisterService(Object serviceRegistration);

	/**
	 * Returns an array of service references for the services that were
	 * registered under the specified class and filter.
	 * 
	 * @param clazz
	 *            The class name under which the service is registered
	 * @param filter
	 *            The specified filter expression is used to select the
	 *            registered services whose service properties contain keys and
	 *            values which satisfy the filter expression
	 * @return A array of objects of service references. For OSGi
	 *         implementation, this object refers to implementation of
	 *         {@code org.osgi.framework.ServiceReference} interface.
	 */
	public Object[] getServiceReferences(String clazz, String filter);

	/**
	 * Returns the service object corresponding to the service registration.
	 * 
	 * @param serviceReference
	 *            Service reference object for the desired service object. For
	 *            OSGi implementation, this object refers to implementation of
	 *            {@code org.osgi.framework.ServiceReference} interface.
	 * @return service object
	 */
	public Object getService(Object serviceReference);

	/**
	 * Removes the service reference from the platform
	 * 
	 * @param serviceReference
	 *            Service reference object. For OSGi implementation, this object
	 *            refers to implementation of {@code org.osgi.framework.ServiceReference}
	 *            interface.
	 * @return true if the operation was successful else false
	 */

	public boolean ungetService(Object serviceReference);

	/**
	 * A method to access a resource within an application
	 * 
	 * @param resourceName
	 * @return {@code URL} of the resource
	 */
	public URL getResource(String resourceName);

	/**
	 * An interface method to get the headers from the application context. For
	 * OSGi, it could be extracting the headers from the bundle context.
	 * 
	 * @return A {@code Dictionary} of key-value pairs depicting the headers.
	 */

	public Dictionary<String, String> getHeaders();

	/**
	 * A method to print messages.
	 * 
	 * @param msg
	 */
	public void println(String msg);

	/**
	 * This method returns true when the platform is shutting down.
	 * 
	 * @return
	 */
	public boolean isShuttingdown();

}
