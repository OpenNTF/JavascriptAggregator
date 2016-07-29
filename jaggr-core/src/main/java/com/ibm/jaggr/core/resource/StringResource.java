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

import org.apache.commons.io.input.ReaderInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.Date;

public class StringResource extends AbstractResourceBase {
	final String content;
	final long lastModified;

	public StringResource(String content, URI uri) {
		super(uri);
		this.content = content;
		this.lastModified = new Date().getTime();
	}

	public StringResource(String content, URI uri, long lastModified) {
		super(uri);
		this.content = content;
		this.lastModified = lastModified;
	}

	@Override
	public long lastModified() {
		return lastModified;
	}

	@Override
	public long getSize() {
		return content.length();
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
	public String toString() {
		return super.toString() + " - " + getURI().toString(); //$NON-NLS-1$
	}

}
