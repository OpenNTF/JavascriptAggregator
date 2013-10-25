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

package com.ibm.jaggr.service.impl;

import java.net.URL;
import java.util.Dictionary;

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
	 * @param service
	 * @param properties
	 * @return A pointer to the service registration
	 */
	public Object registerService(String clazz, Object service,
			Dictionary<String, String> properties);

	/**
	 * Removes the service registration from the platform.
	 * 
	 * @param service
	 *            A pointer to the service registration
	 */
	public void unRegisterService(Object service);

	/**
	 * Returns an array of service registration pointers for the services that
	 * were registered under the specified class and filter.
	 * 
	 * @param clazz
	 * @param filter
	 * @return A array of pointers to service registrations
	 */
	public Object[] getServiceReferences(String clazz, String filter);

	/**
	 * Returns the service implementation corresponding to the service
	 * registration pointer.
	 * 
	 * @param serviceReference
	 * @return Object implementing the service.
	 */
	public Object getService(Object serviceReference);

	/**
	 * Releases the service object from the service registration.
	 * 
	 * @param serviceReference
	 * @param clazz
	 * @return true if the operation was successful else false
	 */
	// TODO: Check if we can optimise this
	public boolean unGetService(Object serviceReference, String clazz);

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
