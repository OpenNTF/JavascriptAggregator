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

package com.ibm.jaggr.core.impl;

import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransportExtensionPoint;

import java.util.Properties;

/**
 * Implementation  for {@link IAggregatorExtension} interface.
 */
public class AggregatorExtension  implements IAggregatorExtension {
	private String extensionPointId;
	private String uniqueId;
	private String contributorId;
	private Object instance;
	private Properties attributes;

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
	public AggregatorExtension(Object instance, Properties attributes, String extensionPointId, String uniqueId) {
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
