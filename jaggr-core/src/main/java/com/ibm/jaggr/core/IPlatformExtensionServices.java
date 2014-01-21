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

import java.util.Properties;

/**
 * Interface to support eclipse extension point implementation for non-osgi
 * environment. This interface has been implemented by classes providing
 * implementation to extension points.
 */
public interface IPlatformExtensionServices {
	/**
	 * Returns a properties object for the associated extension implementation.
	 * The properties file replaces any schema definitions for extension point
	 * implementations. This is useful for environments which do not support
	 * eclipse extension point and their schemas and it helps in getting the
	 * attributes of the extensions.
	 * 
	 * @return A Properties object for the associated extension point
	 *         implementation.
	 */
	public Properties getProperties();

	/**
	 * This method is called during the initialization of eclipse extension
	 * point. This is useful for non-osgi environments which do not support
	 * eclipse extension point and this method replaces the 
	 * <code>setInitializationData()</code> callback performed by the OSGi
	 * framework .
	 * 
	 */
	public void setInitializationData();

}
