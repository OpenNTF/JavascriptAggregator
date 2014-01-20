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

import org.mozilla.javascript.Scriptable;

import com.ibm.jaggr.core.IAggregator;

/**
 * Interface for config modifier class. Instances of this class are registered
 * as an OSGi service with the name property set to the name of the aggregator
 * that the config object is associated with. The
 * {@link #modifyConfig(IAggregator, Scriptable)} method is called whenever the
 * config is loaded/reloaded, before any config listeners are called.
 */
public interface IConfigModifier {
	/**
	 * Called whenever the config is loaded/reloaded
	 * 
	 * @param aggregator
	 *            The aggregator that this config is associated with
	 * @param rawConfig
	 *            The raw config object from the config JavaScript. See
	 *            {@link IConfig#getRawConfig()} for a description of this
	 *            object. Implementors of this method may modify the config
	 *            object as needed.
	 */
	void modifyConfig(IAggregator aggregator, Scriptable rawConfig);
}
