/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;

import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.IResourceVisitor;
import com.ibm.jaggr.service.resource.IResourceVisitor.Resource;

public class NotFoundResource implements IResource {
	private final URI uri;
	public NotFoundResource(URI uri) {
		this.uri = uri;
	}

	@Override
	public URI getURI() {
		return uri;
	}

	@Override
	public boolean exists() {
		return false;
	}

	@Override
	public long lastModified() {
		return 0;
	}

	@Override
	public Reader getReader() throws IOException {
		throw new IOException(uri.toString());
	}
	
	@Override 
	public InputStream getInputStream() throws IOException {
		throw new IOException(uri.toString());
	}

	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
		throw new IOException(uri.toString());
	}

	@Override
	public Resource asVisitorResource() throws IOException {
		throw new IOException(uri.toString());
	}

}
