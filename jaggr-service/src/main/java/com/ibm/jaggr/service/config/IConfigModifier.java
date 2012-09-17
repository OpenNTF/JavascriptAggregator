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

package com.ibm.jaggr.service.config;

import java.util.Map;

import com.ibm.jaggr.service.IAggregator;

/**
 * Interface for config modifier class. Instances of this class are registered
 * as an OSGi service with the name property set to the name of the aggregator
 * that the config object is associated with. The
 * {@link #modifyConfig(IAggregator, Map)} method is called whenever the
 * config is loaded/reloaded, before any config listeners are called.
 */
public interface IConfigModifier {
	/**
	 * Called whenever the config is loaded/reloaded
	 * 
	 * @param aggregator
	 *            The aggregator that this config is associated with
	 * @param rawConfig
	 *            The raw config object from the config JSON. See
	 *            {@link IConfig#getRawConfig()} for a description of this
	 *            object. Implementors of this method may modify the config
	 *            object as needed. Objects added to the raw config should be
	 *            limited to Maps, Lists and Strings, Numbers and Booleans, or
	 *            JSON proxy objects. All elements must be serializable and
	 *            cloneable and support element comparison using the the
	 *            equals() method.
	 */
	void modifyConfig(IAggregator aggregator, Map<String, Object> rawConfig);
}
