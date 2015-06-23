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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IServiceProviderExtensionPoint;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.core.resource.IResourceConverter;
import com.ibm.jaggr.core.resource.IResourceConverterExtensionPoint;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransportExtensionPoint;

import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation  for {@link IAggregatorExtension} interface.
 */
public class AggregatorExtension  implements IAggregatorExtension {
	private static final Logger log = Logger.getLogger(AggregatorExtension.class.getName());

	private String extensionPointId;
	private String uniqueId;
	private String contributorId;
	private Object instance;
	private Properties attributes;
	private InitParams initParams;
	private IAggregator aggregator;

	/**
	 * Constructs a new AggregatorExtension object from an object instance and
	 * and the specified extension point id
	 * @param instance
	 *            The instantiated object for this extension
	 * @param attributes
	 *            The attributes for this extension
	 * @param initParams
	 *            The init-params for this extension
	 * @param extensionPointId
	 *            the extension point id
	 * @param uniqueId
	 *            the extension unique id
	 * @param aggregator
	 *            the aggregator
	 */
	public AggregatorExtension(Object instance, Properties attributes, InitParams initParams, String extensionPointId, String uniqueId, IAggregator aggregator) {
		final String sourceMethod = "<ctor>"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorExtension.class.getName(), sourceMethod, new Object[]{instance, attributes, extensionPointId, uniqueId, aggregator});
		}
		this.extensionPointId = extensionPointId;
		this.uniqueId = uniqueId;
		this.contributorId = null;
		this.instance = instance;
		this.attributes = attributes;
		this.initParams = initParams;
		this.aggregator = aggregator;
		validate();
		if (isTraceLogging) {
			log.exiting(AggregatorExtension.class.getName(), sourceMethod);
		}
	}

	public AggregatorExtension(Object instance, Properties attributes, InitParams initParams, String extensionPointId, String uniqueId) {
		this(instance, attributes, initParams, extensionPointId, uniqueId, null);
	}

	private void validate() {
		final String sourceMethod = "validate"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorExtension.class.getName(), sourceMethod);
		}
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
		} else if (IResourceConverterExtensionPoint.ID.equals(extensionPointId)) {
			if (!(instance instanceof IResourceConverter)) {
				throw new IllegalArgumentException(instance.getClass().getName());
			}
			// Validate required attributes
			for (String attr : IResourceConverterExtensionPoint.REQUIRED_ATTRIBUTES) {
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
		} else if (!IServiceProviderExtensionPoint.ID.equals(extensionPointId)) {
			throw new IllegalArgumentException(instance.getClass().getName());
		}
		if (isTraceLogging) {
			log.exiting(AggregatorExtension.class.getName(), sourceMethod);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getExtensionPointId()
	 */
	@Override
	public String getExtensionPointId() {
		final String sourceMethod = "getExtensionPointId"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorExtension.class.getName(), sourceMethod);
			log.exiting(AggregatorExtension.class.getName(), sourceMethod, extensionPointId);
		}
		return extensionPointId;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getInstance()
	 */
	@Override
	public Object getInstance() {
		final String sourceMethod = "getInstance"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorExtension.class.getName(), sourceMethod);
			log.exiting(AggregatorExtension.class.getName(), sourceMethod, instance);
		}
		return instance;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getAttribute(java.lang.String)
	 */
	@Override
	public String getAttribute(String name) {
		final String sourceMethod = "getAttribute"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorExtension.class.getName(), sourceMethod, new Object[]{name});
		}
		String result = attributes.getProperty(name);
		if (result != null && aggregator != null) {
			result = aggregator.substituteProps(result);
		}
		if (isTraceLogging) {
			log.exiting(AggregatorExtension.class.getName(), sourceMethod, result);
		}
		return result;
	}

	@Override
	public Set<String> getAttributeNames() {
		final String sourceMethod = "getAttributeNames"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorExtension.class.getName(), sourceMethod);
		}
		Set<String> result = attributes.stringPropertyNames();
		if (isTraceLogging) {
			log.exiting(AggregatorExtension.class.getName(), sourceMethod, result);
		}
		return result;

	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getUniqueId()
	 */
	@Override
	public String getUniqueId() {
		final String sourceMethod = "getUniqueId"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorExtension.class.getName(), sourceMethod);
			log.exiting(AggregatorExtension.class.getName(), sourceMethod, uniqueId);
		}
		return uniqueId;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator.ILoadedExtension#getContributorId()
	 */
	@Override
	public String getContributorId() {
		final String sourceMethod = "getContributorId"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorExtension.class.getName(), sourceMethod);
			log.exiting(AggregatorExtension.class.getName(), sourceMethod, contributorId);
		}
		return contributorId;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return new StringBuffer()
		    .append("{extensionPointId:").append(extensionPointId) //$NON-NLS-1$
		    .append(", uniqueId:").append(uniqueId) //$NON-NLS-1$
		    .append(", contributorId:").append(contributorId) //$NON-NLS-1$
		    .append(", instance:").append(instance) //$NON-NLS-1$
		    .append(", attributes:").append(attributes) //$NON-NLS-1$
		    .append(", initParams:").append(initParams) //$NON-NLS-1$
		    .append("}").toString(); //$NON-NLS-1$
	}

	@Override
	public InitParams getInitParams() {
		return initParams;
	}
}
