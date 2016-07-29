/*
 * (C) Copyright IBM Corp. 2012, 2016
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

/**
 * Interface to depict service registration functionality. This is implemented by classes which may
 * leverage platform provided features for service registration. For eg. {@code com.ibm.jaggr.service.ServiceRegistrationOSGi}
 * implements this interface for OSGi platform.
 *
 */

public interface IServiceRegistration {
	/**
	 * Removes this service registration from the platform.
	 *
	 */

	public void	unregister();
}
