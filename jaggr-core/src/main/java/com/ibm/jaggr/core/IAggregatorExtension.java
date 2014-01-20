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

import com.ibm.jaggr.core.IExtensionInitializer.IExtensionRegistrar;

/**
 * Interface for a registered aggregator extension.  An {@link Iterable} of
 * {@code IAggregatorExtension} objects is returned by the method
 * {@link IAggregator#getResourceFactoryExtensions()} and 
 * {@link IAggregator#getModuleBuilderExtensions()}, 
 */
public interface IAggregatorExtension {
	/**
	 * Returns the extension point id that this extension object implements
	 * 
	 * @return The extension point id
	 */
	public String getExtensionPointId();
	
	/**
	 * Returns the unique id for this extension
	 * 
	 * @return the extension unique id
	 */
	public String getUniqueId();
	
	/**
	 * Returns the bundle symbolic name for the bundle that contributed this
	 * extension. If the extension was registered through the
	 * {@link IExtensionRegistrar} interface, then the value is null.
	 * 
	 * @return The contributor id
	 */
	public String getContributorId();
	
	/**
	 * Returns the object instance implementing this extension
	 * 
	 * @return the object instance 
	 */
	public Object getInstance();
	
	/**
	 * Returns the value of the specified attribute. These are the same as the
	 * attribute values specified in the xml defining the extension, or in the
	 * case of extensions registered through
	 * {@link IExtensionRegistrar#registerExtension(Object, Properties, String, String, IAggregatorExtension)},
	 * they are the attributes provided in the properties object.
	 * 
	 * @param name
	 *            the attribute name
	 * @return the attribute value
	 */
	public String getAttribute(String name);
}
