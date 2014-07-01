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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.impl.resource.FileResource;
import com.ibm.jaggr.core.impl.resource.FileResourceFactory;
import com.ibm.jaggr.core.impl.resource.NotFoundResource;
import com.ibm.jaggr.core.impl.resource.ResolverResource;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.util.PathUtil;

import com.ibm.jaggr.service.impl.Activator;
import com.ibm.jaggr.service.impl.AggregatorImpl;

import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.service.urlconversion.URLConverter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BundleResourceFactory extends FileResourceFactory implements IExtensionInitializer {
	static final Logger log = Logger.getLogger(BundleResourceFactory.class.getName());

	private Bundle contributingBundle;
	private ServiceReference urlConverterSR;
	private Object resolver;
	private Method resolverGetBundleMethod;

	public BundleResourceFactory() {
		super();
	}

	/**
	 * Constructor for tests
	 * @param classLoader
	 */
	protected BundleResourceFactory(ClassLoader classLoader) {
		super(classLoader);
	}

	@Override
	public IResource newResource(URI uri) {
		BundleContext context = Activator.getBundleContext();
		IResource result = null;
		String scheme = uri.getScheme();
		if ("bundleresource".equals(scheme) || "bundleentry".equals(scheme)) { //$NON-NLS-1$ //$NON-NLS-2$
			URLConverter converter = (urlConverterSR != null)
					? (URLConverter)context.getService(urlConverterSR) : null;

			if (converter != null) {
				URI fileUri = null;
				try {
					fileUri = PathUtil.url2uri(converter.toFileURL(toURL(uri)));
					FileResource fileResource = null;
					Constructor<?> constructor = getNIOFileResourceConstructor(URI.class);
					try {
						fileResource = (FileResource)getNIOInstance(constructor, fileUri);
					} catch (Throwable t) {
						if (log.isLoggable(Level.SEVERE)) {
							log.log(Level.SEVERE, t.getMessage(), t);
						}
					}

					if (fileResource == null) {
						fileResource = new FileResource(fileUri);
					}
					// Wrap the result in a ResolverResource so that this resource factory object
					// will be used to construct new, resolved resources.  This is necessary since
					// URLConverter.toFileURL needs to be invoked on any resolved resources to
					// ensure that the resource the resolved URL points to exists.
					result = new ResolverResource(fileResource, uri, this);
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
				} finally {
					context.ungetService(urlConverterSR);
				}
			} else {
				result = new BundleResource(uri, context);
			}
		} else if ("namedbundleresource".equals(scheme)) { //$NON-NLS-1$
			// Support aggregator specific URI scheme for named bundles
			String bundleName = getNBRBundleName(uri);
			Bundle bundle = getBundle(bundleName);
			if (bundle != null) {
				URL url = bundle.getEntry(getNBRPath(bundleName, uri));
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

	/*
	 * Package-private initializer for unit testing
	 */
	void setInitializationData(Bundle contributingBundle, ServiceReference urlConverterSR) {
		if (this.contributingBundle != null || this.urlConverterSR != null) {
			throw new IllegalStateException();
		}
		this.contributingBundle = contributingBundle;
		this.urlConverterSR = urlConverterSR;
	}

	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension extension, IExtensionRegistrar registrar) {
		contributingBundle = ((AggregatorImpl)aggregator).getContributingBundle();
		urlConverterSR = Activator.getBundleContext().getServiceReference(org.eclipse.osgi.service.urlconversion.URLConverter.class.getName());

		// Try to load the BundleResolver class (will fail if not on OSGi 4.3).
		try {
			Class<?> resolverClass = Class.forName("com.ibm.jaggr.service.impl.resource.BundleResolver"); //$NON-NLS-1$
			Constructor<?> ctor = resolverClass.getConstructor(new Class[]{Bundle.class});
			resolver = ctor.newInstance(new Object[]{contributingBundle});
			resolverGetBundleMethod = resolverClass.getMethod("getBundle", new Class[]{String.class}); //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			if (log.isLoggable(Level.FINE)) {
				log.fine("Down level version of OSGi.  Bundle wiring support not available."); //$NON-NLS-1$
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
	}

	@Override
	public boolean handles(URI uri) {
		String scheme = uri.getScheme();
		return scheme.equals("bundleentry") ||  //$NON-NLS-1$
				scheme.equals("bundleresource") || //$NON-NLS-1$
				scheme.equals("namedbundleresource"); //$NON-NLS-1$
	}


	/**
	 * Extracts the bundle's symbolic name from a uri with the <code>namedbundleresource<code> scheme.
	 *
	 * Supports backwards compatibility for names from 1.0.0 release (for now).
	 *
	 * @param uri The uri with a <code>namedbundleresource<code> scheme.
	 * @return The bundle's symbolic name within the uri.
	 */
	protected String getNBRBundleName(URI uri) {
		String ret = null;

		String host = uri.getHost();
		String authority;
		if (host != null) {
			ret = host;
		} else if ((authority = uri.getAuthority()) != null) {
			ret = authority;
		} else {
			String path = uri.getPath();
			if (path.startsWith("/")) //$NON-NLS-1$
				path = path.substring(1);
			ret = path.substring(0, path.indexOf('/'));
		}

		return ret;
	}

	/**
	 * Extracts the path of the file resource from a uri with the <code>namedbundleresource<code> scheme.
	 *
	 * Supports backwards compatibility for names from 1.0.0 release (for now).
	 *
	 * @param bundle The name of the bundle within the uri from {@link BundleResourceFactory#getNBRBundleName(URI)}
	 * @param uri The uri with a <code>namedbundleresource<code> scheme.
	 * @return The path of the file resource within the uri.
	 */
	protected String getNBRPath(String bundle, URI uri) {
		String path = uri.getPath();
		return path.startsWith("/" + bundle) ? path.substring(bundle.length() + 1) : path; //$NON-NLS-1$
	}

	/*
	 * So that unit test cases can provide alternative implementation
	 */
	protected URL toURL(URI uri) throws IOException {
		return uri.toURL();
	}

	/*
	 * So that unit test cases can provide alternative implementation
	 */
	protected Bundle getBundle(String bundleName) {
		Bundle result = null;
		if (resolverGetBundleMethod != null) {
			try {
				result = (Bundle) resolverGetBundleMethod.invoke(resolver, new Object[]{bundleName});
			} catch (Exception e) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, e.getMessage(), e);
				}
				result = Platform.getBundle(bundleName);
			}
		} else {
			result = Platform.getBundle(bundleName);
		}
		return result;
	}
}
