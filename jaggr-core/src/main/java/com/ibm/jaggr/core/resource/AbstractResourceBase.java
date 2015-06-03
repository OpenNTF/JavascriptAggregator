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

import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;

import java.io.IOException;
import java.net.URI;

/**
 * Base class implementation for IResource.  Provide functionality
 * common to most implementations.
 */
public abstract class AbstractResourceBase implements IResource {

	private final URI uri;
	private URI referenceUri = null;

	public AbstractResourceBase(URI uri) {
		this.uri = uri;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#getURI()
	 */
	@Override
	public URI getURI() {
		return uri;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#getPath()
	 */
	@Override
	public String getPath() {
		return getReferenceURI().getPath();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#getReferenceURI()
	 */
	@Override
	public URI getReferenceURI() {
		return referenceUri != null ? referenceUri : getURI();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#setReferenceURI(java.net.URI)
	 */
	@Override
	public void setReferenceURI(URI uri) {
		referenceUri = uri;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#exists()
	 */
	@Override
	public boolean exists() {
		return lastModified() != 0;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#lastModified()
	 */
	@Override
	public long lastModified() {
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#isFolder()
	 */
	@Override
	public boolean isFolder() {
		return false;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#getSize()
	 */
	@Override
	public long getSize() throws IOException {
		throw new IOException("Not supported"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#resolve(java.lang.String)
	 */
	@Override
	public URI resolve(String relative) {
		return getReferenceURI().resolve(relative);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#walkTree(com.ibm.jaggr.core.resource.IResourceVisitor)
	 */
	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#asVisitorResource()
	 */
	@Override
	public Resource asVisitorResource() {
		return new IResourceVisitor.Resource() {

			/* (non-Javadoc)
			 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#newResource()
			 */
			@Override
			public IResource newResource() {
				throw new UnsupportedOperationException();
			}

			/* (non-Javadoc)
			 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#lastModified()
			 */
			@Override
			public long lastModified() {
				return AbstractResourceBase.this.lastModified();
			}

			/* (non-Javadoc)
			 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#isFolder()
			 */
			@Override
			public boolean isFolder() {
				return AbstractResourceBase.this.isFolder();
			}

			/* (non-Javadoc)
			 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#getURI()
			 */
			@Override
			public URI getURI() {
				return AbstractResourceBase.this.getURI();
			}
		};
	}
}
