/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.cachekeygenerator;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.TypeUtil;

/**
 * Simple cache key generator for export names option. Used by multiple
 * builders.
 */
public final class ExportNamesCacheKeyGenerator extends
		AbstractCacheKeyGenerator {
	private static final long serialVersionUID = -1888066677992730471L;
	
	private static final String eyecatcher = "expn"; //$NON-NLS-1$

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cachekeygenerator.AbstractCacheKeyGenerator#generateKey(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public String generateKey(HttpServletRequest request) {
		boolean exportNames = TypeUtil.asBoolean(request
				.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME));
		return eyecatcher + ":" + (exportNames ? "1" : "0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	@Override 
	public String toString() {
		return eyecatcher;
	}

}
