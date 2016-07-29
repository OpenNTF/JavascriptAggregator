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

package com.ibm.jaggr.core.resource;

import com.ibm.jaggr.core.IAggregator;

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
	 * Returns the URI for this resource. Note that there is no requirement that the value returned
	 * be the same value that was provided to the {@link IResourceFactory} object used to create
	 * this instance. Neither is there any requirement that the path/filename of the URI have any
	 * relationship to the URI originally specified in {@link IAggregator#newResource(URI)}. The
	 * method may return a URI to a file in a cache directory with an arbitrary name as a result of
	 * bundle extraction and/or running resource converters. The file extension portion of the URI
	 * will be the same as the originally requested URI, however.
	 * <p>
	 * Use {@link #getReferenceURI()} to obtain the originally requested URI.
	 * <p>
	 * Because instances of this object can refer to cache resources, you should not hold on to
	 * references to IResource objects long term (more than the duration of a request).
	 *
	 * @return The resource URI
	 */
	public URI getURI();

	/**
	 * Returns the reference path of this resource. This is equivalent to calling
	 * {@code getReferenceURI().getPath()}.
	 *
	 * @return the reference path of the resource
	 */
	public String getPath();

	/**
	 * Returns the reference URI. This is the URI that was passed to
	 * {@link IAggregator#newResource(URI)}, before any conversions by resource factories or
	 * resource converters. This URI may or may not be the same value that is returned by
	 * {@link #getURI()} and it may be a pseudo URI specifying a scheme that is not directly
	 * supported by the platform (e.g. namedbundleresource).
	 *
	 * @return the reference URI.
	 */
	public URI getReferenceURI();

	/**
	 * Sets the reference URI.  This method may be called only once.  If the reference
	 * URI has been set and this method is called again an {@link IllegalStateException}
	 * is thrown.
	 *
	 * @param referenceUri the reference uri
	 * throws IllegalStateException
	 */
	public void setReferenceURI(URI referenceUri);

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
	 * @return true if the resource is a folder.
	 */
	public boolean isFolder();

	/**
	 * Returns the size of the content for this resource. Note that not all implementations are able
	 * to support this method. If the size cannot be provided, a
	 * {@link IOException} is thrown.
	 *
	 * @return the size of the content for the resource
	 * @throws IOException
	 */
	public long getSize() throws IOException;

	/**
	 * This is equivalent to calling
	 * {@code relative.getReferenceURI().resolve()}
	 *
	 * @param relative
	 *            the path to resolve against
	 * @return
	 *        the resolved path
	 */
	public URI resolve(String relative);

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
	 * {@link IResourceVisitor.Resource} object. This is useful for when you want to invoke a
	 * resource visitor for a resource that was obtained by means other than
	 * {@link #walkTree(IResourceVisitor)}. The resource must exist.
	 * <p>
	 * Note that calling {@link IResourceVisitor.Resource#newResource(IAggregator)} on objects
	 * returned from this method will generally throw {@link UnsupportedOperationException}
	 *
	 * @return An {@link IResourceVisitor.Resource} for current resource
	 */
	public IResourceVisitor.Resource asVisitorResource();
}
