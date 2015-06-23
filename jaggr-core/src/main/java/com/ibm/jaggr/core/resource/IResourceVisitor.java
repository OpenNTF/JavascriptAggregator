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

package com.ibm.jaggr.core.resource;

import com.ibm.jaggr.core.IAggregator;

import java.io.IOException;
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
		 * Return an IResource object for this resource. Implementors are responsible for calling
		 * {@link IAggregator#runConverters(IResource)} against the new resource before returning.
		 *
		 * @param aggregator
		 *            the aggregator instance
		 *
		 * @return an IResource object for this resource.
		 * @throws UnsupportedOperationException
		 *             for instances of this class returned by {@link IResource#asVisitorResource()}
		 */
		public IResource newResource(IAggregator aggregator);

	}
}
