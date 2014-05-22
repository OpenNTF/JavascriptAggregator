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

/**
 * Interface for variables resolver.  Variables of the form ${varname} which appear in the
 * server-side config JavaScript will be replaced by the values returned from the
 * <code>resolve</code> method.  The default variable resolver uses Java system properties.
 * <p>
 * Variable resolvers are registered with the service registry using the interface name
 * and the <code>name</code> property set to the name of the aggregator instance.
 */
public interface IVariableResolver {

	/**
	 * Returns the value to use in place of the variable name.  If a resolver cannot
	 * resolve a variable name, it should return null.
	 *
	 * @param variableName
	 *            The variable name
	 * @return The value to use, or null.
	 */
	String resolve(String variableName);
}
