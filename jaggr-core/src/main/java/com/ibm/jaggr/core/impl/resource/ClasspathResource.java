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

package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An implementation of {@link IResource} for classpath resources
 */
public class ClasspathResource implements IResource {
	static final Logger log = Logger.getLogger(ClasspathResource.class.getName());


	final String zipEntryUri;
	final ArrayList<String> zipFileEntries;
	 URL classpathUrl;
	 URI classpathUri;
	 String classpathName;
	 String zipFileEntry;
	 ZipEntry zipEntry;
	 ZipFile zipFile;
	 String scheme;



	/**
	 * Public constructor used by factory
	 *
	 * @param entry
	 *            the entry within the jar
	 * @param entries
	 * 			  list of all entries within the jar
	 * @param scheme Scheme associated with the resource
	 */
	public ClasspathResource(String entry, ArrayList<String> entries, String scheme) {
		setScheme(scheme);
		zipEntryUri = entry;
		zipFileEntries = entries;
		try {
			if (entry.startsWith("file:") && entry.contains("!")) { //$NON-NLS-1$ //$NON-NLS-2$
				String[] split = entry.split("!"); //$NON-NLS-1$
				classpathUrl = new URL(split[0]);
				classpathUri = new URI(split[0]);
				classpathName = (split[0].lastIndexOf("/") != -1) ? split[0].substring(split[0].lastIndexOf("/") + 1) : split[0]; //$NON-NLS-1$ //$NON-NLS-2$
				zipFileEntry = split[1].startsWith("/") ?  split[1].substring(1) : split[1]; //$NON-NLS-1$
				// need to remove the forward slash

				zipFile = new ZipFile(new File(classpathUri));
				zipEntry = zipFile.getEntry(zipFileEntry);
			} else {
				throw new ClassPathResourceException("Improper classpath resource"); //$NON-NLS-1$
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.modules.Resource#getURI()
	 */
	@Override
	public URI getURI() {
		return URI.create("jar:" + zipEntryUri); //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.modules.Resource#getPath()
	 */
	@Override
	public String getPath() {
		if(!zipFileEntry.startsWith("/")){//$NON-NLS-1$ // return the path preceded with /
			return "/".concat(zipFileEntry); //$NON-NLS-1$
		}else
			return zipFileEntry;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.modules.Resource#exists()
	 */
	@Override
	public boolean exists() {
		return lastModified() != 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.modules.Resource#lastModified()
	 */
	@Override
	public long lastModified() {
		long lastmod = 0L;
		try {
			lastmod = getURI().toURL().openConnection().getLastModified();
		} catch (IOException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		return lastmod;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#resolve(java.net.URI)
	 */
	@Override
	public IResource resolve(String relative) {
		URI finalFileUri = null;
		ClasspathResource classpathResource = null;
		try {
			URI pathOnlyUri = new URI(getPath()); // should start with '/');
			URI resolved = pathOnlyUri.resolve(relative);
			int idx = getURI().toString().indexOf("!/"); //$NON-NLS-1$
			finalFileUri = new URI(getURI().toString().substring(0, idx+1) + resolved.getPath());
			classpathResource = newInstance(finalFileUri);
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		return classpathResource;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.modules.Resource#getReader()
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
			throw new FileNotFoundException(zipEntry.getName());
		}
		if (!zipEntry.isDirectory()) {
			return;
		}
		recurse(zipEntry, visitor);
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
			throw new IOException(zipEntry.getName());
		}
		return new VisitorResource(zipEntry, zipEntryUri);
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
	private void recurse(ZipEntry file, IResourceVisitor visitor)
			throws IOException {
		ArrayList<ZipEntry> files = getChildZipEntries(file);

		if (files == null) {
			return;
		}
		for (ZipEntry child : files) {
			String childName = child.getName().substring(file.getName().length());
			String childEntryUri = zipEntryUri + childName;
			visitor.visitResource(new VisitorResource(child,
					childEntryUri), childName);
		}
	}

	protected ClasspathResource newInstance(URI uri) {
		return (ClasspathResource) new ClasspathResourceFactory().newResource(uri);
	}

	private ArrayList<ZipEntry> getChildZipEntries(ZipEntry entry){
		String entryName = entry.getName();
		ArrayList<ZipEntry> files = new ArrayList<ZipEntry>();
		for (int i = 0; i < zipFileEntries.size(); i ++){
			if(zipFileEntries.get(i).startsWith(entryName) && !(zipFileEntries.get(i).equalsIgnoreCase(entryName)) && zipFileEntries.get(i).endsWith(".js")){ //$NON-NLS-1$
				files.add(zipFile.getEntry(zipFileEntries.get(i)));
			}
		}

		return files;
	}

	/**
	 * Implementation of {@link IResourceVisitor.Resource} for classpath resources.
	 */
	private static class VisitorResource implements IResourceVisitor.Resource {

		ZipEntry entry;
		String ZipFileUri;


		private VisitorResource(ZipEntry entry, String uri) {
			this.entry = entry;
			this.ZipFileUri = uri;

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
			if (entry == null) {
				return false;
			} else {
				return entry.isDirectory();
			}
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
			return URI.create("jar:" + ZipFileUri); //$NON-NLS-1$
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see
		 * com.ibm.jaggr.service.modules.ResourceVisitor.Resource#
		 * getPath()
		 */
		@Override
		public String getPath() {
			return ZipFileUri;
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
			long lastmodified = 0;
			try {
				lastmodified = getURI().toURL().openConnection().getLastModified();
			} catch (IOException e) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, e.getMessage(), e);
				}
			}
			return lastmodified;
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
			return new InputStreamReader(getURI().toURL().openConnection().getInputStream(), "UTF-8"); //$NON-NLS-1$
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceVisitor.Resource#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return getURI().toURL().openConnection().getInputStream();
		}
	}

	public class ClassPathResourceException extends Exception {

		public ClassPathResourceException(String string) {
			super(string);
		}

		private static final long serialVersionUID = 1L;

	}

	/**
	 * A utility method to return the scheme associated with this resource
	 * @return Scheme associated with the resource.
	 */
	protected String getScheme(){
		return scheme;
	}

	/**
	 * A utility method to set the scheme associated with this resource
	 * @param sch Scheme for this resource.
	 */
	protected void setScheme(String sch){
		scheme = sch;
	}


}
