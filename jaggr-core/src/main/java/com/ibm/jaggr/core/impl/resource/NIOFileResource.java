/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.resource.IResourceVisitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class NIOFileResource extends FileResource {
	static final Logger log = Logger.getLogger(NIOFileResource.class.getName());

	public NIOFileResource(URI uri) {
		super(uri);
	}

	@Override
	protected FileResource newInstance(URI uri) {
		return new NIOFileResource(uri);
	}

	@Override
	public void walkTree(final IResourceVisitor visitor) throws IOException {
		if (!exists()) {
			throw new FileNotFoundException(file.getAbsolutePath());
		}
		if (!file.isDirectory()) {
			return;
		}

		java.nio.file.Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)	throws IOException {
				String name = NIOFileResource.this.file.toPath().relativize(dir).toString();
				boolean cont = true;
				if (name.length() > 0) { // No need to visit root dir.
					cont = visitor.visitResource(
						getResource(dir.toFile(), attrs),
						NIOFileResource.this.file.toPath().relativize(dir).toString().replace("\\", "/") //$NON-NLS-1$ //$NON-NLS-2$
					);
				}
				return cont ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
			}

			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
				visitor.visitResource(
					getResource(file.toFile(), attrs),
					NIOFileResource.this.file.toPath().relativize(file).toString().replace("\\", "/") //$NON-NLS-1$ //$NON-NLS-2$
				);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private IResourceVisitor.Resource getResource(final File file, final BasicFileAttributes attrs) {
		return new IResourceVisitor.Resource() {
			@Override
			public long lastModified() {
				return attrs.lastModifiedTime().to(TimeUnit.MICROSECONDS);
			}
			@Override
			public boolean isFolder() {
				return attrs.isDirectory();
			}
			@Override
			public URI getURI() {
				return FileResource.getURI(file);
			}
			@Override
			public Reader getReader() throws IOException {
				return new InputStreamReader(new FileInputStream(file), "UTF-8"); //$NON-NLS-1$
			}
			@Override
			public InputStream getInputStream() throws IOException {
				return new FileInputStream(file);
			}
		};
	}
}