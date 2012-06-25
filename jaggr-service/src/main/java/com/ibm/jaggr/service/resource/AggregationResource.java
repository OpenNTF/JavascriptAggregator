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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.input.ReaderInputStream;

import com.ibm.jaggr.service.readers.AggregationReader;

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
	 * @see com.ibm.jaggr.service.resource.IResource#getURI()
	 */
	@Override
	public URI getURI() {
		return facadeUri;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#exists()
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
	 * @see com.ibm.jaggr.service.resource.IResource#lastModified()
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
	 * @see com.ibm.jaggr.service.resource.IResource#getReader()
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
	 * @see com.ibm.jaggr.service.resource.IResource#getInputStream()
	 */
	@Override
	public InputStream getInputStream() throws IOException {
		return new ReaderInputStream(getReader(), "UTF-8"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#walkTree(com.ibm.jaggr.service.resource.IResourceVisitor)
	 */
	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResource#asVisitorResource()
	 */
	@Override
	public IResourceVisitor.Resource asVisitorResource() throws IOException {
		return this;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResourceVisitor.Resource#isFolder()
	 */
	@Override
	public boolean isFolder() {
		return false;
	}
}
