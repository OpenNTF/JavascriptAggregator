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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
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

		Set<FileVisitOption> options = new HashSet<FileVisitOption>();
		options.add(FileVisitOption.FOLLOW_LINKS);
		java.nio.file.Files.walkFileTree(file.toPath(),  options, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)	throws IOException {
				String name = NIOFileResource.this.file.toPath().relativize(dir).toString();
				boolean cont = true;
				if (name.length() > 0) { // No need to visit root dir.
					String path = NIOFileResource.this.file.toPath().relativize(dir).toString().replace("\\", "/"); //$NON-NLS-1$ //$NON-NLS-2$
					cont = visitor.visitResource(getResource(dir.toFile(), attrs, path), path);
				}
				return cont ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
			}

			@Override
			public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
				String path = NIOFileResource.this.file.toPath().relativize(file).toString().replace("\\", "/"); //$NON-NLS-1$ //$NON-NLS-2$
				visitor.visitResource(getResource(file.toFile(), attrs, path), path);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private IResourceVisitor.Resource getResource(final File file, final BasicFileAttributes attrs, String path) {
		return new FileResource.VisitorResource(file, 0, path) {
			@Override
			public long lastModified() {
				return attrs.lastModifiedTime().to(TimeUnit.MILLISECONDS);
			}
			@Override
			public boolean isFolder() {
				return attrs.isDirectory() || attrs.isSymbolicLink() && file.isDirectory();
			}
			@Override
			public URI getURI() {
				return FileResource.getURI(file);
			}
		};
	}
}