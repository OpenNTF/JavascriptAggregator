/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl;

import java.util.Properties;

import org.eclipse.core.runtime.IExtension;

import com.ibm.jaggr.service.IAggregatorExtension;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.service.resource.IResourceFactory;
import com.ibm.jaggr.service.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.transport.IHttpTransportExtensionPoint;

/**
 * Implementation  for {@link IAggregatorExtension} interface.
 */
class AggregatorExtension  implements IAggregatorExtension {
	private String extensionPointId;
	private String uniqueId;
	private String contributorId;
	private Object instance;
	private Properties attributes;
	
	/**
	 * Constructs a new AggregatorExtension object from an object instance and
	 * an {@link IExtension}
	 * 
	 * @param extension
	 *            The IExtension object
	 * @param instance
	 *            The instantiated object for this extension
	 * @param attributes
	 *            The attributes for this extension
	 */
	AggregatorExtension(IExtension extension, Object instance, Properties attributes) {
		this.extensionPointId = extension.getExtensionPointUniqueIdentifier();
		this.uniqueId = extension.getUniqueIdentifier();
		this.contributorId = extension.getContributor().getName();
		this.instance = instance;
		this.attributes = attributes;
		validate();
	}
	
	/**
	 * Constructs a new AggregatorExtension object from an object instance and 
	 * and the specified extension point id
	 * @param instance
	 *            The instantiated object for this extension
	 * @param attributes
	 *            The attributes for this extension
	 * @param extensionPointId
	 *            the extension point id 
	 * @param uniqueId
	 *            the extension unique id
	 */
	AggregatorExtension(Object instance, Properties attributes, String extensionPointId, String uniqueId) {
		this.extensionPointId = extensionPointId;
		this.uniqueId = uniqueId;
		this.contributorId = null;
		this.instance = instance;
		this.attributes = attributes;
		validate();
	}
	
	private void validate() {
		if (IResourceFactoryExtensionPoint.ID.equals(extensionPointId)) {
			if (!(instance instanceof IResourceFactory)) {
				throw new IllegalArgumentException(instance.getClass().getName());
			}
			// Validate required attributes
			for (String attr : IResourceFactoryExtensionPoint.REQUIRED_ATTRIBUTES) {
				String value = attributes.getProperty(attr);
				if (value == null || value.length() == 0) {
					throw new IllegalArgumentException(attr);
				}
			}
		} else if (IModuleBuilderExtensionPoint.ID.equals(extensionPointId)) {
			if (!(instance instanceof IModuleBuilder)) {
				throw new IllegalArgumentException(instance.getClass().getName());
			}
			// Validate required attributes
			for (String attr : IModuleBuilderExtensionPoint.REQUIRED_ATTRIBUTES) {
				String value = attributes.getProperty(attr);
				if (value == null || value.length() == 0) {
					throw new IllegalArgumentException(attr);
				}
			}
		} else if (IHttpTransportExtensionPoint.ID.equals(extensionPointId)) {
			if (!(instance instanceof IHttpTransport)) {
				throw new IllegalArgumentException(instance.getClass().getName());
			}
			// Validate required attributes
			for (String attr : IHttpTransportExtensionPoint.REQUIRED_ATTRIBUTES) {
				String value = attributes.getProperty(attr);
				if (value == null || value.length() == 0) {
					throw new IllegalArgumentException(attr);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getExtensionPointId()
	 */
	@Override
	public String getExtensionPointId() {
		return extensionPointId;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getInstance()
	 */
	@Override
	public Object getInstance() {
		return instance;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getAttribute(java.lang.String)
	 */
	@Override
	public String getAttribute(String name) {
		return attributes.getProperty(name);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getUniqueId()
	 */
	@Override
	public String getUniqueId() {
		return uniqueId;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getContributorId()
	 */
	@Override
	public String getContributorId() {
		return contributorId;
	}
}