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

import java.net.URI;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;


/**
 * Default implementation for {@link IResourceFactory} that currently supports
 * only file resources.
 */
public class FileResourceFactory implements IResourceFactory {
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modules.ResourceFactory#create(java.net.URI)
	 */
	@Override
	public IResource newResource(URI uri) {
		IResource result = null;
		String scheme = uri.getScheme();
		if ("file".equals(scheme) || scheme == null) { //$NON-NLS-1$
			result = new FileResource(uri);
		} else {
			throw new UnsupportedOperationException(uri.getScheme()); 
		}
		return result;
	}

	@Override
	public boolean handles(URI uri) {
		return "file".equals(uri.getScheme()); //$NON-NLS-1$
	}
}
