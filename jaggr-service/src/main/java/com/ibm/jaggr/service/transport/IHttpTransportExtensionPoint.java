/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.transport;


public interface IHttpTransportExtensionPoint {

	/**
	 * The extension point name. This value is combined with the namespace for
	 * the extension point to form the extension point id.
	 */
	public static final String NAME = "httptransport"; //$NON-NLS-1$

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
	 * Name of {@code httptransport} extension point {@code path} attribute.
	 * Specifies the module path to the transport provided resources such as the
	 * loader extension JavaScript as well as any supporting resources.
	 * <p>
	 * This is a required attribute.
	 */
	public static final String PATH_ATTRIBUTE = "path"; //$NON-NLS-1$

	/**
	 * List of required extension attributes for the {@code httptransport} extension
	 * point.
	 */
	public static final String[] REQUIRED_ATTRIBUTES = { PATH_ATTRIBUTE };

	/**
	 * A reference to the {@link IHttpTransport} interface which is the
	 * interface that plugin extensions of the {@code httptransport} extension
	 * point must implement.
	 */
	public static final Class<?> INTERFACE = IHttpTransport.class;
}
