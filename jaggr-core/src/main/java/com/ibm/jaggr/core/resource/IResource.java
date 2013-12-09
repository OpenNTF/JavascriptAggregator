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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;

/**
 * Defines the interface for AMD module resources. Implementations can provide
 * support for resources on the file system, in zip and jar files, or any other
 * type of repository that can support this interface.
 * <p>
 * Instances of <code>IResource</code> are created by an associated 
 * {@link IResourceFactory} object.
 * <p>
 * Implementors of this interface also implement the
 * {@link IResourceVisitor.Resource} interface.
 */
public interface IResource {
	
	/**
	 * Returns the URI for this resource.  Note that there is no requirement
	 * that the value returned be the same value that was provided to the
	 * {@link IResourceFactory} object used to create this instance.
	 * 
	 * @return The resource URI
	 */
	public URI getURI();

	/**
	 * Returns true if the resource exists.
	 * 
	 * @return True if the resource exists
	 */
	public boolean exists();
	
	/**
	 * Returns the last-modified date of the resource.
	 * 
	 * @return The last-modified date
	 */
	public long lastModified();
	
	/**
	 * Returns an IResource for the resource obtained by resolving the URI of
	 * this resource with the specified relative URI. Use this method instead of
	 * calling {@link URI#resolve(String)} on the value returned by
	 * {@link #getURI()} to ensure that the cached resource for the URI
	 * exists in the event that resource URIs are derived from a service such as
	 * {@link URLConverter#toFileURL(java.net.URL)}.
	 */
	public IResource resolve(String relative);
	
	/**
	 * Returns a {@link Reader} object for the resource if the resource is not a
	 *         directory/folder resource.
	 *         
	 * @return The resource reader        
	 * @throws IOException if the resource is a folder, or an I/O error occurred
	 */
	public Reader getReader() throws IOException;
	
	/**
	 * Returns a {@link InputStream} object for the resource if the resource
	 * is not a directory/folder resource.
	 * 
	 * @return The resource input stream
	 * @throws IOException if the resource is a folder, or an I/O error occurred
	 */
	public InputStream getInputStream() throws IOException;

	/**
	 * Walks the folder tree rooted at the current resource, calling the
	 * {@link IResourceVisitor#visitResource(IResourceVisitor.Resource, String)} 
	 * method for each resource or folder resource encountered.
	 * <p>
	 * If this resource is not a folder, then <code>visitor's</code>
	 * {@link IResourceVisitor#visitResource(IResourceVisitor.Resource, String)} 
	 * method is called once for this resource.
	 * 
	 * @param visitor
	 *            An instance of {@link IResourceVisitor}
	 * @throws IOException
	 */
	public void walkTree(IResourceVisitor visitor) throws IOException;

	/**
	 * This is a convenience method to return the current resource as a
	 * {@link IResourceVisitor.Resource} object. This is useful for when you
	 * want to invoke a resource visitor for a resource that was obtained by
	 * means other than {@link #walkTree(IResourceVisitor)}.  The resource
	 * must exist.
	 * 
	 * @return An {@link IResourceVisitor.Resource} for current resource
	 * @throws IOException if the resource does not exist
	 */
	public IResourceVisitor.Resource asVisitorResource() throws IOException;
}
