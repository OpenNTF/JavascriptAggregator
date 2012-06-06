/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.Date;

import org.apache.commons.io.input.ReaderInputStream;

public class StringResource implements IResource, IResourceVisitor.Resource {
	final String content;
	final URI uri;
	final long lastModified;
	
	public StringResource(String content, URI uri) {
		this.content = content;
		this.uri = uri;
		this.lastModified = new Date().getTime();
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
		return new StringReader(content);
	}

	@Override
	public InputStream getInputStream() throws IOException {
		return new ReaderInputStream(getReader());
	}

	@Override
	public void walkTree(IResourceVisitor visitor) throws IOException {
	}

	@Override
	public IResourceVisitor.Resource asVisitorResource() throws IOException {
		return this;
	}

	@Override
	public boolean isFolder() {
		return false;
	}
	
}
