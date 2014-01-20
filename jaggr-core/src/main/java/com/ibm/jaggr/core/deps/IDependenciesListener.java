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

package com.ibm.jaggr.core.deps;

/**
 * Listener interface for dependencies changes.  To receive notification 
 * of change events, register the implementing class as an OSGi service,
 * specifying the servlet name under the <code>name</code> property in 
 * the service properties.
 */
public interface IDependenciesListener {
	/**
	 * This method is called when dependencies are loaded/reloaded/validated.
	 * Listeners may track the value returned by
	 * {@link IDependencies#getLastModified()} to detect when the dependencies
	 * have changed.
	 * 
	 * @param deps
	 *            The dependencies object. Note that changes to the dependencies
	 *            may occur in-place, using the same IDependencies object with
	 *            updated values, or may result in a new IDependencies object.
	 *            This interface does not specify which approach is used by an
	 *            implementation.
	 * @param sequence
	 *            The sequence number.  Notifications for different listener
	 *            events (options, config, dependencies) that have the same cause
	 *            have the same sequence number.  Notifications resulting from 
	 *            servlet initialization have the sequence number 1.  The sequence
	 *            number is incremented for subsequent event causes, but there
	 *            is no guarantee about the the values for subsequent notifications
	 *            other than that they will be increasing.
	 */
	public void dependenciesLoaded(IDependencies deps, long sequence);
}
