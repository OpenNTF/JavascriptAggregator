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

/**
 * This interface defines constants for the {@code resourcefactory} extension
 * point
 */
public interface IResourceFactoryExtensionPoint {

	/**
	 * The extension point name. This value is combined with the namespace for
	 * the extension point to form the extension point id.
	 */
	public static final String NAME = "resourcefactory"; //$NON-NLS-1$

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
	 * Name for {@code resourcefactory} extension point {@code scheme}
	 * attribute. Specifies the URI scheme that this factory supports, or
	 * {@code *} if the factory can support all schemes. This is a required
	 * attribute.
	 */
	public static final String SCHEME_ATTRIBUTE = "scheme"; //$NON-NLS-1$

	/**
	 * List of required extension attributes for the {@code resourcefactory}
	 * extension point.
	 */
	public static final String[] REQUIRED_ATTRIBUTES = { SCHEME_ATTRIBUTE };

	/**
	 * A reference to the {@link IResourceFactory} interface which is the
	 * interface that plugin extensions of the {@code resourcefactory} extension
	 * point must implement.
	 */
	public static final Class<?> INTERFACE = IResourceFactory.class;
}
