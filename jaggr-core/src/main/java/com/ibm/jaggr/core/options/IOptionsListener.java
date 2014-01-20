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

package com.ibm.jaggr.core.options;

/**
 * Listener interface for options changes. To receive notification of options
 * changes, register an instance of the implementing class as an OSGi service.
 * The listener registration should specify a filter using the name property 
 * with the value obtained by calling {@link IOptions#getName()} for the 
 * options object who's changes you want to track.
 */
public interface IOptionsListener {
	/**
	 * This method is called when the options are loaded/changed.
	 * 
	 * @param options
	 *            The new options. Note that changes to the options may occur
	 *            in-place, using the same IOptions object with updated values,
	 *            or may result in a new object instance. This interface does not
	 *            specify which approach is used by an implementation.
	 * @param sequence
	 *            The sequence number. Notifications for different listener
	 *            events (options, config, dependencies) that have the same
	 *            cause have the same sequence number. The sequence number is
	 *            incremented for subsequent event causes, but there is no
	 *            guarantee about the the values for subsequent notifications
	 *            other than that they will be increasing.
	 */
	public void optionsUpdated(IOptions options, long sequence);
}
