/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;

/**
 * Defines the interface for a resource visitor called by the tree walker.
 */
public interface IResourceVisitor {

	/**
	 * This method is called by {@link IResource#walkTree(IResourceVisitor)} 
	 * for both folder and non-folder resources encountered while walking the
	 * folder tree rooted at resource that walkTree was called on.
	 * 
	 * @param resource
	 *            The non-folder resource being visited.
	 * @param pathName
	 *            The path name of this resource relative to the location of the
	 *            resource that walkTree was called on.
	 * @return True if the resource is a folder resource and the treeWalker
	 *         should recurse into the folder.
	 * @throws IOException
	 */
	public boolean visitResource(IResourceVisitor.Resource resource,
			String pathName) throws IOException;

	/**
	 * Defines the interface for a resource item that is visited by the tree
	 * walker. This interface should be implemented using the folder tree
	 * meta-data available to the tree walker, particularly for the
	 * {@link #getURI()}, {@link #isFolder()} and {@link #lastModified()}
	 * methods, avoiding additional I/O calls to retrieve this information to
	 * the extent practical.
	 * <p>
	 * Implementors of this interface also implement {@link IResource}
	 * interface.
	 */
	public interface Resource {

		/**
		 * @return The URI for this resource
		 */
		public URI getURI();

		/**
		 * @return True if the resource is a folder
		 */
		public boolean isFolder();

		/**
		 * @return The last modified date of the resource
		 */
		public long lastModified();

		/**
		 * @return A {@link Reader} object for the resource.
		 * 
		 * @throws IOException
		 */
		public Reader getReader() throws IOException;
		
		/**
		 * @return A {@link InputStream} object for the resource.
		 * 
		 * @throws IOException
		 */
		public InputStream getInputStream() throws IOException;
	}
}
