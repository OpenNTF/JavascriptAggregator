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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.input.ReaderInputStream;

import com.ibm.jaggr.core.readers.AggregationReader;

/**
 * This class implements an aggregating resource that combines the 
 * contents of one or more component resources.
 */
public class AggregationResource implements IResource, IResourceVisitor.Resource {
	
	private final URI facadeUri;
	private final List<IResource> resources; 

	public AggregationResource(URI facadeUri, List<IResource> resources) {
		this.facadeUri = facadeUri;
		this.resources = resources;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#getURI()
	 */
	@Override
	public URI getURI() {
		return facadeUri;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#exists()
	 */
	@Override
	public boolean exists() {
		// Return false if any of the resources in this aggregated resource
		// does not exist
		boolean result = true;
		for (IResource resource : resources) {
			if (!resource.exists()) {
				result = false;
				break;
			}
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#lastModified()
	 */
	@Override
	public long lastModified() {
		// return the latest of each of the resources in this aggregated
		// resource
		long result = -1;
		for (IResource resource : resources) {
			result = Math.max(result, resource.lastModified());
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#getReader()
	 */
	@Override
	public Reader getReader() throws IOException {
		// Return an Aggregation reader for all the resources in this
		// aggregated resource
		List<Reader> readers = new ArrayList<Reader>(resources.size());
		for (IResource resource : resources) {
			readers.add(resource.getReader());
		}
		return new AggregationReader(readers);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#getInputStream()
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return new ReaderInputStream(getReader(), "UTF-8"); //$NON-NLS-1$
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
	public IResourceVisitor.Resource asVisitorResource() throws IOException {
		return this;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#isFolder()
	 */
	@Override
	public boolean isFolder() {
		return false;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResource#resolve(java.lang.String)
	 */
	@Override
	public IResource resolve(String relative) {
		throw new UnsupportedOperationException();
	}
}
