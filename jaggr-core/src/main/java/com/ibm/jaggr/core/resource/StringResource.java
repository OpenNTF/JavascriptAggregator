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

	@Override
	public IResource resolve(String relative) {
		return new StringResource("", uri.resolve(relative)); //$NON-NLS-1$
	}
	
}
