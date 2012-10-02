/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;

import java.util.Properties;

import com.ibm.jaggr.service.IExtensionInitializer.IExtensionRegistrar;

/**
 * Interface for a registered aggregator extension.  An {@link Iterable} of
 * {@code IAggregatorExtension} objects is returned by the method
 * {@link IAggregator#getResourceFactoryExtensions()} and 
 * {@link IAggregator#getModuleBuilderExtensions()}, 
 * 
 * @author chuckd@us.ibm.com
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