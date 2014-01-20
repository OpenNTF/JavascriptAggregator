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

package com.ibm.jaggr.core.resource;

import java.net.URI;
import java.util.Properties;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.IExtensionInitializer.IExtensionRegistrar;

/**
 * This interface is implemented by resource factories which create instances of
 * {@link IResource} objects.
 * <p>
 * Instances of this interface are created by the eclipse extension framework
 * for Aggregator extensions that implement the
 * {@code com.ibm.jaggr.core.resourcefactory} extension point.
 * Aggregator extensions may also register resource factories by calling
 * {@link IExtensionRegistrar#registerExtension(Object, Properties, String, String, IAggregatorExtension)}
 * when the extension's
 * {@link IExtensionInitializer#initialize(IAggregator, IAggregatorExtension, IExtensionRegistrar)}
 * method is called (assuming the extension implements the 
 * {@link IExtensionInitializer} interface).
 * <p>
 * The extension point defines the {@code scheme} attribute which is used by the
 * aggregator to select the extension implementation based on the scheme of the
 * URI for which the {@code IResource} is being requested. See
 * {@link IAggregator#newResource(URI)} for a description of how the aggregator
 * selects an {@code IResourceFactory} for a URI.
 */
public interface IResourceFactory {

	/**
	 * Creates a {@link IResource} object for the specified URI. This method
	 * must not fail due to non-existence of the resource specified by the URL.
	 * If the resource doesn't exist, a valid resource object that returns
	 * <code>exists() == false</code> and throws an IOException for other
	 * methods that require existence of the resource should be returned.
	 * <p>
	 * This method is free to convert the provided URI as needed so that the URI
	 * returned by {@link IResource#getURI()} for the returned object does not
	 * need to be the same as the provided URI. For example, the factory may
	 * convert a <code>bundleentry</code> URI into a <code>file</code> uri, or
	 * else it may need to convert a URI using a pseudo scheme such as
	 * <code>namedbundleentry</code> into a URI that is supported by the
	 * platform.
	 * 
	 * @param uri
	 *            The resource URI. The concrete implementation of the returned
	 *            type is determined by the URI scheme.
	 * 
	 * @return A {@link IResource} object for the specified URI. The returned
	 *         object MUST be serialiable.
	 */
	public IResource newResource(URI uri);
	
	public boolean handles(URI uri);

}
