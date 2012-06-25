/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.modulebuilder;

/**
 * This interface defines constants for the {@code modulebuilder} extension
 * point
 */
public interface IModuleBuilderExtensionPoint {

	/**
	 * The extension point name. This value is combined with the namespace for
	 * the extension point to form the extension point id.
	 */
	public static final String NAME = "modulebuilder"; //$NON-NLS-1$

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
	 * Name of {@code modulebuilder} extension point {@code extension}
	 * attribute. Specifies the file extension (.js, .html, etc.), that this
	 * builder supports, or {@code *} if this builder can support all
	 * extensions.
	 * <p>
	 * This is a required attribute.
	 */
	public static final String EXTENSION_ATTRIBUTE = "extension"; //$NON-NLS-1$

	/**
	 * List of required extension attributes for the {@code modulebuilder}
	 * extension point.
	 */
	public static final String[] REQUIRED_ATTRIBUTES = { EXTENSION_ATTRIBUTE };

	/**
	 * A reference to the {@link IModuleBuilder} interface which is the
	 * interface that plugin extensions of the {@code modulebuilder} extension
	 * point must implement.
	 */
	public static final Class<?> INTERFACE = IModuleBuilder.class;
}
