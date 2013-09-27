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

package com.ibm.jaggr.osgi.service.impl.transport;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;


import com.ibm.jaggr.service.impl.transport.DojoHttpTransport;
import com.ibm.jaggr.service.impl.transport.Messages;


public class OSGiDojoHttpTransport extends DojoHttpTransport implements IExecutableExtension{
	
	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName,
			Object data) throws CoreException {
		
		pluginUniqueId = config.getDeclaringExtension().getUniqueIdentifier();
		
		Bundle contributingBundle = Platform.getBundle(config.getNamespaceIdentifier());
		if (contributingBundle.getState() != Bundle.ACTIVE) {
			try {
				contributingBundle.start();
			} catch (BundleException e) {
				throw new CoreException(new Status(Status.ERROR, config.getNamespaceIdentifier(), e.getMessage(), e));
			}
		}
		
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
		
		try {
			System.err.println("OSGiDojoHttpTransport.java : combouri here was "+comboUri);
			comboUri = new URI(getComboUriStr()); 
		} catch (URISyntaxException e) {
			throw new CoreException(
					new Status(Status.ERROR, config.getNamespaceIdentifier(), 
							e.getMessage(), e)
				);
		}
		
	}
	
	

}
