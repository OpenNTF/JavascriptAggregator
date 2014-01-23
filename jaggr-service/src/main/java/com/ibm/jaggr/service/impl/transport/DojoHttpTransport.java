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
package com.ibm.jaggr.service.impl.transport;

import com.ibm.jaggr.service.impl.Activator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;

/**
 * Extends {@link AbstractDojoHttpTransport} for OSGi platform
 *
 */
public class DojoHttpTransport extends AbstractDojoHttpTransport implements IExecutableExtension {

    static final String comboUriStr = "namedbundleresource://" + Activator.BUNDLE_NAME + "/WebContent/"; //$NON-NLS-1$ //$NON-NLS-2$

    private String resourcePathId = null;

    private URI comboUri = null;

    private String pluginUniqueId = null;

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.AbstractHttpTransport#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
	 */
	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName,
			Object data) throws CoreException {

		// Make sure the contributing bundle is started
		Bundle contributingBundle = Platform.getBundle(config.getNamespaceIdentifier());
		if (contributingBundle.getState() != Bundle.ACTIVE) {
			try {
				contributingBundle.start();
			} catch (BundleException e) {
				throw new CoreException(
						new Status(Status.ERROR, config.getNamespaceIdentifier(),
								e.getMessage(), e)
					);
			}
		}

		// Get the resource (combo) path name from the this extension's
		// path attribute
		resourcePathId = config.getAttribute(PATH_ATTRNAME);
		if (resourcePathId == null) {
			throw new CoreException(
					new Status(Status.ERROR, config.getNamespaceIdentifier(),
						MessageFormat.format(
							Messages.AbstractHttpTransport_1,
							new Object[]{config.getDeclaringExtension().getUniqueIdentifier()}
						)
					)
				);
		}
		// Save this extension's pluginUniqueId
		pluginUniqueId = config.getDeclaringExtension().getUniqueIdentifier();

		// Initialize the combo uri
		try {
			comboUri = new URI(comboUriStr);
		} catch (URISyntaxException e) {
			throw new CoreException(
					new Status(Status.ERROR, config.getNamespaceIdentifier(),
							e.getMessage(), e)
				);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.impl.transport.AbstractHttpTransport#getComboUri()
	 */
	@Override
	protected URI getComboUri() {
		return comboUri;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.impl.transport.AbstractHttpTransport#getTransportId()
	 */
	@Override
	protected String getTransportId() {
		return pluginUniqueId;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.impl.transport.AbstractHttpTransport#getResourcePathId()
	 */
	@Override
	protected String getResourcePathId() {
		return resourcePathId;
	}

}
