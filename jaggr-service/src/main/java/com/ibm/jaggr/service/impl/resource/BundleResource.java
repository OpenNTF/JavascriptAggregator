/*
 * (C) Copyright IBM Corp. 2012, 2016
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
import com.ibm.jaggr.core.resource.AbstractResourceBase;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;
import com.ibm.jaggr.core.util.PathUtil;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.logging.Logger;

public class BundleResource extends AbstractResourceBase {
	static final Logger log = Logger.getLogger(BundleResource.class.getName());

	final String symname;
	final BundleContext context;

	public BundleResource(URI uri, BundleContext context) {
		super(uri);
		this.context = context;
		Bundle bundle = null;
		if (!uri.getScheme().equals("bundleresource") && !uri.getScheme().equals("bundleentry")) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new IllegalArgumentException(uri.toString());
		}
		String host = uri.getHost();
		try {
			int bundleid = Integer.parseInt(host.split("[\\-\\.]")[0]); //$NON-NLS-1$
			bundle = context.getBundle(bundleid);
		} catch (NumberFormatException ignore) {
		}
		symname = (bundle != null) ? bundle.getSymbolicName() : null;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#lastModified()
	 */
	@Override
	public long lastModified() {
		long lastmod = 0L;
		try {
			lastmod = getURI().toURL().openConnection().getLastModified();
		} catch (IOException ignore) {}
		return lastmod;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#isFolder()
	 */
	@Override
	public boolean isFolder() {
		return getURI().getPath().endsWith("/"); //$NON-NLS-1$
	}

	@Override
	public long getSize() throws IOException {
		return getURI().toURL().openConnection().getContentLength();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#getReader()
	 */
	@Override
	public Reader getReader() throws IOException {
		return new InputStreamReader(getURI().toURL().openConnection().getInputStream(), "UTF-8"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#getInputStream()
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return getURI().toURL().openConnection().getInputStream();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#walkTree(com.ibm.jaggr.service.resource.IResourceVisitor)
	 */
	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
		if (!exists() || symname == null) {
			throw new IOException(getURI().toString());
		}
		Bundle bundle = Platform.getBundle(symname);
		recurse(bundle, getURI().getPath(), visitor, ""); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#asVisitorResource()
	 */
	@Override
	public Resource asVisitorResource() {
		URL url = null;
		try {
			url = getURI().toURL();
		} catch (MalformedURLException ignore) {
		}
		return new VisitorResource(url, ""); //$NON-NLS-1$
	}

	/**
	 * Internal method for recursing sub directories.
	 *
	 * @param file
	 *            The file object
	 * @param visitor
	 *            The {@link IResourceVisitor} to call for non-folder resources
	 * @throws IOException
	 */
	private void recurse(Bundle bundle, String path, IResourceVisitor visitor, String relPathName) throws IOException {
		Enumeration<?> entries = bundle.findEntries(path, "*", false); //$NON-NLS-1$
		if (entries != null) {
			while (entries.hasMoreElements()) {
				URL child = (URL)entries.nextElement();
				String childPath = child.getPath();
				String temp = childPath;
				if (temp.endsWith("/")) { //$NON-NLS-1$
					temp = temp.substring(0, temp.length()-1);
				}
				int idx = temp.lastIndexOf("/"); //$NON-NLS-1$
				String childName = temp.substring(idx+1);
				String relPath = relPathName
						+ (relPathName.length() == 0 ? "" : "/") //$NON-NLS-1$ //$NON-NLS-2$
						+ childName;
				boolean result = visitor.visitResource(
						new VisitorResource(child, relPath), relPath);
				if (result && child.getPath().endsWith("/")) { //$NON-NLS-1$
					recurse(bundle, childPath, visitor, relPath);
				}
			}
		}
	}

	@Override
	public String getPath() {
		return getReferenceURI().getPath();
	}

	@Override
	public String toString() {
		return super.toString() + ": " +  (symname != null ? symname  : "null") + " - " + getURI().toString(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private class VisitorResource implements IResourceVisitor.Resource {

		final URL url;
		long lastModified;
		final String path;

		private VisitorResource(URL url, String path) {
			this.url = url;
			this.path = path;
			if (url != null) {
				try {
					URLConnection connection = url.openConnection();
					this.lastModified = connection.getLastModified();
				} catch (IOException ex) {
					this.lastModified = 0;
				}
			}
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceVisitor.Resource#isFolder()
		 */
		@Override
		public boolean isFolder() {
			return url.getPath().endsWith("/"); //$NON-NLS-1$
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.modules.ResourceVisitor.Resource#getURI()
		 */
		@Override
		public URI getURI() {
			URI uri = null;
			try {
				uri = PathUtil.url2uri(url);
			} catch (URISyntaxException ignore) {}
			return uri;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.modules.ResourceVisitor.Resource#lastModified()
		 */
		@Override
		public long lastModified() {
			return lastModified;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#newResource(com.ibm.jaggr.core.IAggregator)
		 */
		@Override
		public IResource newResource(IAggregator aggregator) {
			if (path == null) {
				throw new UnsupportedOperationException();
			}
			BundleResource result = new BundleResource(getURI(), context);
			if (!BundleResource.this.getURI().equals(BundleResource.this.getReferenceURI())) {
				result.setReferenceURI(BundleResource.this.resolve(path));
			}
			return aggregator.runConverters(result);
		}
	}
}
