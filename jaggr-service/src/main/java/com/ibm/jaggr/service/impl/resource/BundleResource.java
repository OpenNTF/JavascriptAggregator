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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;
import com.ibm.jaggr.core.util.PathUtil;

public class BundleResource implements IResource {
	final URI uri;
	final String symname;
	
	public BundleResource(URI uri, BundleContext context) {
		this.uri = uri;
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
	
	protected BundleResource(URI uri, String symname) {
		this.uri = uri;
		this.symname = symname;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#getURI()
	 */
	@Override
	public URI getURI() {
		return uri;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#exists()
	 */
	@Override
	public boolean exists() {
		return lastModified() != 0;
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
	 * @see com.ibm.jaggr.service.resource.IResource#resolve(java.lang.String)
	 */
	@Override
	public IResource resolve(String relative) {
		return new BundleResource(getURI().resolve(relative), symname);
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#getReader()
	 */
	@Override
	public Reader getReader() throws IOException {
		return new InputStreamReader(uri.toURL().openConnection().getInputStream(), "UTF-8"); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#getInputStream()
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return uri.toURL().openConnection().getInputStream();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#walkTree(com.ibm.jaggr.service.resource.IResourceVisitor)
	 */
	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
		if (!exists() || symname == null) {
			throw new IOException(uri.toString());
		}
		Bundle bundle = Platform.getBundle(symname);
		recurse(bundle, uri.getPath(), visitor, ""); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#asVisitorResource()
	 */
	@Override
	public Resource asVisitorResource() throws IOException {
		Resource resource = null;
		if (!exists()) {
			throw new IOException(uri.toString());
		}
		try {
			resource = new VisitorResource(uri.toURL());
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
		return resource;
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
						new VisitorResource(child), relPath);
				if (result && child.getPath().endsWith("/")) { //$NON-NLS-1$
					recurse(bundle, childPath, visitor, relPath);
				}
			}
		}
	}
	
	private static class VisitorResource implements IResourceVisitor.Resource {

		URL url;
		long lastModified;
		
		private VisitorResource(URL url) throws IOException {
			this.url = url;
			this.lastModified = url.openConnection().getLastModified();
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
		 * @see com.ibm.jaggr.service.modules.ResourceVisitor.Resource#getReader()
		 */
		@Override
		public Reader getReader() throws IOException {
			return new InputStreamReader(url.openConnection().getInputStream(), "UTF-8"); //$NON-NLS-1$
		}
		
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceVisitor.Resource#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return url.openConnection().getInputStream();
		}
	}
}
