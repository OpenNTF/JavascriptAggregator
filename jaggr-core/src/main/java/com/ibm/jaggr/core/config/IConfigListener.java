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

package com.ibm.jaggr.core.config;

/**
 * Listener interface for config changes.  To receive notification 
 * of config events, register the implementing class as an OSGi service,
 * specifying the servlet name under the <code>name</code> property in 
 * the service properties.
 */
public interface IConfigListener {
	/**
	 * This method is called when the config is loaded/reloaded, after the
	 * config has been modified by any registered {@link IConfigModifier}
	 * services.
	 * 
	 * @param config
	 *            The new config object. In order to detect and react to config
	 *            changes, you can save the value returned from
	 *            {@link IConfig#toString()} and compare it with the value
	 *            returned in subsequent configLoaded events.
	 * @param sequence
	 *            The sequence number. Notifications for different listener
	 *            events (options, config, dependencies) that have the same
	 *            cause have the same sequence number. Notifications resulting
	 *            from servlet initialization have the sequence number 1. The
	 *            sequence number is incremented for subsequent event causes,
	 *            but there is no guarantee about the the values for subsequent
	 *            notifications other than that they will be increasing.
	 */
	public void configLoaded(IConfig config, long sequence);
}
