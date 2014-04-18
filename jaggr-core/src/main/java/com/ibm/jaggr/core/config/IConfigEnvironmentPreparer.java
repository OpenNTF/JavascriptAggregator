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

import com.ibm.jaggr.core.IAggregator;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * Implementations of this interface are called just before the server-side config JavaScript is
 * evaluated on the server. Implementors may add functions and/or properties to the provided scope,
 * which may be referenced by the config JavaScript.
 * <p>
 * Instances of this interface are registered as an OSGi service with the name property set to the
 * name of the aggregator. The {@link #prepare(IAggregator, Context, Scriptable)} method is called
 * just before the config JavaScript is evaluated.
 */
public interface IConfigEnvironmentPreparer {

	/**
	 * Called prior to evaluating the config JavaScript
	 *
	 * @param aggregator
	 *            The aggregator object
	 * @param context
	 *            The JavaScript context
	 * @param scope
	 *            the JavaScript scope object.  Add functions and/or properties to the scope
	 *            to make them available to the config JavaScript.
	 */
	public void prepare(IAggregator aggregator, Context context, Scriptable scope);

}
