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

public class ExceptionResource implements IResource, IResourceVisitor.Resource {
	private URI uri;
	private long lastModified;
	private IOException ex;

	public ExceptionResource(URI uri, long lastModified, IOException ex) {
		this.uri = uri;
		this.lastModified = lastModified;
		this.ex = ex;
	}
	
	@Override
	public URI getURI() {
		return uri;
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public long lastModified() {
		return lastModified;
	}

	@Override
	public Reader getReader() throws IOException {
		throw ex;
	}
	
	public InputStream getInputStream() throws IOException {
		throw ex;
	}

	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
	}

	@Override
	public Resource asVisitorResource() throws IOException {
		return this;
	}

	@Override
	public boolean isFolder() {
		return false;
	}

}
