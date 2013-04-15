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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.IResourceVisitor;

/**
 * An implementation of {@link IResource} for File resources
 */
public class FileResource implements IResource {
	static final Logger log = Logger.getLogger(FileResource.class.getName());

	File file;

	/**
	 * Public constructor used by factory
	 * 
	 * @param uri
	 *            the resource URI
	 */
	public FileResource(URI uri) {
		if (uri.getAuthority() != null && File.separatorChar == '\\') {
			// Special case for UNC filenames on Windows
			file = new File("\\\\" + uri.getAuthority() + '/' + uri.getPath()); //$NON-NLS-1$
		} else {
			file = new File(uri);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ibm.jaggr.service.modules.Resource#getURI()
	 */
	@Override
	public URI getURI() {
		URI uri = file.toURI();
		if (uri.toString().startsWith("file:////")) { //$NON-NLS-1$
			// Special case for UNC filenames on Windows.  Convert back
			// to authority based URI due to issues with URI.resolve()
			// when using UNC form of the URI.
			try {
				uri = new URI("file:" + uri.getPath()); //$NON-NLS-1$
			} catch (URISyntaxException e) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, e.getMessage(), e);
				}
			}
		}
		return uri;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ibm.jaggr.service.modules.Resource#exists()
	 */
	@Override
	public boolean exists() {
		return file.exists();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ibm.jaggr.service.modules.Resource#lastModified()
	 */
	@Override
	public long lastModified() {
		return file.lastModified();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ibm.jaggr.service.modules.Resource#getReader()
	 */
	@Override
	public Reader getReader() throws IOException {
		return new InputStreamReader(new FileInputStream(file), "UTF-8"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#getInputStream()
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return new FileInputStream(file);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ibm.jaggr.service.modules.Resource#walkTree(com.ibm.jaggr.modules
	 * .ResourceVisitor, boolean)
	 */
	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
		if (!exists()) {
			throw new FileNotFoundException(file.getAbsolutePath());
		}
		if (!file.isDirectory()) {
			return;
		}
		recurse(file, visitor, ""); //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ibm.jaggr.service.resource.IResource#asVisitorResource()
	 */
	@Override
	public VisitorResource asVisitorResource() throws IOException {
		if (!exists()) {
			throw new IOException(this.file.getAbsolutePath());
		}
		return new VisitorResource(file, file.lastModified());
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
	private void recurse(File file, IResourceVisitor visitor, String path)
			throws IOException {
		File[] files = file.listFiles();
		if (files == null) {
			return;
		}
		for (File child : files) {
			String childName = path + (path.length() == 0 ? "" : "/") //$NON-NLS-1$ //$NON-NLS-2$
					+ child.getName();
			boolean result = visitor.visitResource(new VisitorResource(child,
					child.lastModified()), childName);
			if (child.isDirectory() && result) {
				recurse(child, visitor, childName);
			}
		}
	}

	/**
	 * Implementation of {@link IResourceVisitor.Resource} for files.
	 * <p>
	 * TODO: Using {@link File} objects returned from {@link File#listFiles()}
	 * is disappointingly slow for determining file last modified times. This is
	 * because the {@link File} object encapsulates only the file name and does
	 * not contain any of the directory listing meta-data (such as last-modified
	 * time) that is generally available from a file directory listing,
	 * necessitating additional file I/O in order to get the last-modified time
	 * of each file. Java 7 improves on this with the java.nio.file package.
	 * This implementation should be changed to use
	 * {@link java.noi.file.Files#walkFileTree()} instead of
	 * {@link File#listFiles()} as soon as Java 7 is available for use, in order
	 * to improve startup performance when validating a previously serialized
	 * dependency tree.
	 */
	private static class VisitorResource implements IResourceVisitor.Resource {

		File file;
		long lastModified;

		private VisitorResource(File file, long lastModified) {
			this.file = file;
			this.lastModified = lastModified;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.ibm.jaggr.service.resource.IResourceVisitor.Resource
		 * #isFolder()
		 */
		@Override
		public boolean isFolder() {
			return this.file.isDirectory();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.ibm.jaggr.service.modules.ResourceVisitor.Resource#
		 * getURI()
		 */
		@Override
		public URI getURI() {
			URI uri = file.toURI();
			if (uri.toString().startsWith("file:////")) { //$NON-NLS-1$
				// Special case for UNC filenames on Windows.  Convert back
				// to authority based URI due to issues with URI.resolve()
				// when using UNC form of the URI.
				try {
					uri = new URI("file:" + uri.getPath()); //$NON-NLS-1$
				} catch (URISyntaxException e) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, e.getMessage(), e);
					}
				}
			}
			return uri;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.ibm.jaggr.service.modules.ResourceVisitor.Resource#
		 * lastModified()
		 */
		@Override
		public long lastModified() {
			return lastModified;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * com.ibm.jaggr.service.modules.ResourceVisitor.Resource#
		 * getReader()
		 */
		@Override
		public Reader getReader() throws IOException {
			return new InputStreamReader(new FileInputStream(file), "UTF-8"); //$NON-NLS-1$
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceVisitor.Resource#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return new FileInputStream(file);
		}
	}
}
