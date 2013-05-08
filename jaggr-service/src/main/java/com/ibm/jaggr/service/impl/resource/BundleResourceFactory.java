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

package com.ibm.jaggr.service.impl.resource;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.service.urlconversion.URLConverter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IAggregatorExtension;
import com.ibm.jaggr.service.IExtensionInitializer;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.IResourceFactory;
import com.ibm.jaggr.service.util.PathUtil;

public class BundleResourceFactory implements IResourceFactory, IExecutableExtension, IExtensionInitializer {
	static final Logger log = Logger.getLogger(BundleResourceFactory.class.getName());
	
	private BundleContext context;
	private ServiceReference urlConverterSR;

	public BundleResourceFactory() {
	}

	@Override
	public IResource newResource(URI uri) {
		
		IResource result = null;
		String scheme = uri.getScheme();
		if ("bundleresource".equals(scheme) || "bundleentry".equals(scheme)) { //$NON-NLS-1$ //$NON-NLS-2$
			URLConverter converter = (urlConverterSR != null) 
					? (URLConverter)context.getService(urlConverterSR) : null;
			if (converter != null) {
				URL fileUrl = null;
				try {
					fileUrl = converter.toFileURL(uri.toURL());
					result = new FileResource(uri, this, PathUtil.url2uri(fileUrl));
				} catch (FileNotFoundException e) {
					if (log.isLoggable(Level.FINE)) {
						log.log(Level.FINE, uri.toString(), e);
					}
					result = new BundleResource(uri, context);
				} catch (Throwable t) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, uri.toString(), t);
					}
					result = new BundleResource(uri, context);
				}
			} else {
				result = new BundleResource(uri, context);
			}
		} else if ("namedbundleresource".equals(scheme)) { //$NON-NLS-1$
			// Support aggregator specific URI scheme for named bundles
			String bundleSymbolicName = uri.getHost();
			Bundle bundle = Platform.getBundle(bundleSymbolicName);
			if (bundle != null) {
				URL url = bundle.getEntry(uri.getPath());
				if (url != null) {
					try {
						uri = PathUtil.url2uri(url);
					} catch (URISyntaxException e) {
						if (log.isLoggable(Level.WARNING)) {
							log.log(Level.WARNING, e.getMessage(), e);
						}
					}
					result = newResource(uri);
				} else {
					result = new NotFoundResource(uri);
				}
			} else {
				result = new NotFoundResource(uri);
			}
		} else {
			throw new UnsupportedOperationException(uri.getScheme()); 
		}
		return result;
	}

	@Override
	public void setInitializationData(IConfigurationElement config, String arg1,
			Object arg2) throws CoreException {
		context = Platform.getBundle(config.getNamespaceIdentifier()).getBundleContext();
		this.urlConverterSR = context.getServiceReference(org.eclipse.osgi.service.urlconversion.URLConverter.class.getName());
	}

	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension extension, IExtensionRegistrar registrar) {
	}
	
	@Override
	public boolean handles(URI uri) {
		String scheme = uri.getScheme();
		return scheme.equals("bundleentry") ||  //$NON-NLS-1$
		       scheme.equals("bundleresource") || //$NON-NLS-1$
		       scheme.equals("namedbundleresource"); //$NON-NLS-1$
	}
}
