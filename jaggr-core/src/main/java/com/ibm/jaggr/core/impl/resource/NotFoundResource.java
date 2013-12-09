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

package com.ibm.jaggr.core.impl.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;

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

	@Override
	public IResource resolve(String relative) {
		throw new UnsupportedOperationException();
	}

}
