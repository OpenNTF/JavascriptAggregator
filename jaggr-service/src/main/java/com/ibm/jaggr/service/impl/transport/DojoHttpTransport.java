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

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Properties;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IAggregatorExtension;
import com.ibm.jaggr.service.impl.transport.Messages;
import com.ibm.jaggr.service.resource.IResourceFactoryExtensionPoint;

/**
 * This class implements the functions of Dojo HTTP transport for OSGi platform 
 *
 */
public class DojoHttpTransport extends AbstractDojoHttpTransport implements IExecutableExtension{
	
	protected static String comboUriStr = "namedbundleresource://" + "com.ibm.jaggr.core" + "/WebContent/dojo"; //$NON-NLS-1$ //$NON-NLS-2$
	protected static String loaderExtensionPath  = "/WebContent/dojo/loaderExt.js"; //$NON-NLS-1$	
	protected static final String textPluginProxyUriStr = comboUriStr + "/text"; //$NON-NLS-1$
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
	 */
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
			comboUri = new URI(getComboUriStr()); 
		} catch (URISyntaxException e) {
			throw new CoreException(
					new Status(Status.ERROR, config.getNamespaceIdentifier(), 
							e.getMessage(), e)
				);
		}
		
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.AbstractHttpTransport#initialize(com.ibm.jaggr.service.IAggregator, com.ibm.jaggr.service.IAggregatorExtension, com.ibm.jaggr.service.IExtensionInitializer.IExtensionRegistrar)
	 */
	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension extension, IExtensionRegistrar reg) {		
		
		super.initialize(aggregator, extension, reg);
		// Get first resource factory extension so we can add to beginning of list
    	Iterable<IAggregatorExtension> resourceFactoryExtensions = aggregator.getResourceFactoryExtensions();
    	IAggregatorExtension first = resourceFactoryExtensions.iterator().next();
    	
    	// Register the loaderExt resource factory
    	Properties attributes = new Properties();   
    	// this scheme is specific to the OSGi platform.
    	attributes.put("scheme", "namedbundleresource"); //$NON-NLS-1$ //$NON-NLS-2$
    	
		reg.registerExtension(
				new LoaderExtensionResourceFactory(), 
				attributes,
				IResourceFactoryExtensionPoint.ID,
				getPluginUniqueId(),
				first);
		
	}	
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.AbstractDojoHttpTransport#getComboUriStr()
	 */
	@Override
	protected String getComboUriStr() {
    	return comboUriStr;
    }
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.AbstractDojoHttpTransport#getTextPluginProxyUriStr()
	 */
	@Override
	protected String getTextPluginProxyUriStr(){
	    return textPluginProxyUriStr;
	 }
	 
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.AbstractDojoHttpTransport#getLoaderExtensionPath()
	 */
	@Override
	 protected String getLoaderExtensionPath() {
	    return loaderExtensionPath;
	 }

	// this method is for non-osgi platforms to write logic during initialization. Hence, keeping a blank implementation here.
	@Override
	public void setInitializationData() {
		
	}

}
