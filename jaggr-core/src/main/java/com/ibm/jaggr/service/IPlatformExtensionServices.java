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

import java.net.URL;
import java.util.Dictionary;
import java.util.Properties;

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
public interface IPlatformExtensionServices {
	/**
	 * Returns a properties object for the associated
	 * <code>IHttpTransport</code> implementation. The object has a key to
	 * define the loader extension config property, a class key which refers to
	 * the <code>IHttpTransport</code> implementation class name which
	 * implements this interface. This is useful for environments which do not
	 * support eclipse extension point and their schemas and it helps in getting
	 * the attributes of the extensions.
	 * 
	 * @return A Properties object for the associated
	 *         <code>IHttpTransport</code> implementation.
	 */
	public Properties getProperties();

	/**
	 * This method is called when the implementation of the IHttpTransport is
	 * initialized. This is useful for non-osgi environments which do not
	 * support eclipse extension point where there is a
	 * <code>setInitializationData()</code> callback performed by the OSGi
	 * framework .
	 * 
	 */
	public void setInitializationData();

}
