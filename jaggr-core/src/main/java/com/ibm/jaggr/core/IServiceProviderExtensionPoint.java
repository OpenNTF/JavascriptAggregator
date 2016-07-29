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
 * This interface defines constants for the {@code serviceprovider} extension
 * point
 */
public interface IServiceProviderExtensionPoint {
	/**
	 * The extension point name. This value is combined with the namespace for
	 * the extension point to form the extension point id.
	 */
	public static final String NAME = "serviceprovider"; //$NON-NLS-1$

	/**
	 * The extension point namespace. This value is combined with the extension
	 * point name to form the extension point id.
	 */
	public static final String NAMESPACE = "com.ibm.jaggr.service"; //$NON-NLS-1$

	/**
	 * Name extension point id. This is the combination of the extension point
	 * namespace plus the extension point name.
	 */
	public static final String ID = NAMESPACE + "." + NAME; //$NON-NLS-1$

	/**
	 * Name of {@code serviceprovider} extension point {@code service}
	 * attribute. Specifies the interface class name of the service that
	 * should be registered.  If specified, the extension point class must
	 * implement this interface.
	 * <p>
	 * This is an optional attribute.
	 */
	public static final String SERVICE_ATTRIBUTE = "service"; //$NON-NLS-1$

	/**
	 * List of required extension attributes for the {@code serviceprovider} extension
	 * point.
	 */
	public static final String[] REQUIRED_ATTRIBUTES = {};

	/**
	 * A reference to the {@link IAggregatorExtension} interface which is the
	 * interface that plugin extensions of the {@code serviceprovider} extension
	 * point must implement.
	 */
	public static final Class<?> INTERFACE = IAggregatorExtension.class;

}
