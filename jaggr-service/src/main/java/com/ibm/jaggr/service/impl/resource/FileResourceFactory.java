/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.resource;

import java.net.URI;

import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.IResourceFactory;


/**
 * Default implementation for {@link IResourceFactory} that currently supports
 * only file resources.
 * 
 * @author chuckd@us.ibm.com
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
