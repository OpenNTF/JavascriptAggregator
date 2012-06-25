/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.modulebuilder.text;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ExportNamesCacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.service.modulebuilder.ModuleBuild;
import com.ibm.jaggr.service.readers.JavaScriptEscapingReader;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.TypeUtil;

/**
 * This class specializes JavaScriptModuleBuilder for text modules.  Text modules are returned as 
 * javascript modules wrapped in a define() statement.
 */
public class TextModuleBuilder implements IModuleBuilder {
	
	static private final ICacheKeyGenerator[] s_cacheKeyGenerators = 
		new ICacheKeyGenerator[]{new ExportNamesCacheKeyGenerator()};

	/**
	 * Returns the compiler input source for the text module as the text stream
	 * wrapped in an AMD define() function.
	 */
	/* (non-Javadoc)
	 * @see com.ibm.domino.servlets.aggrsvc.modules.JsModule#getSourceFile()
	 */
	@Override
	public ModuleBuild build(
			String mid, 
			IResource resource, 
			HttpServletRequest request,
			ICacheKeyGenerator[] keyGens
			) 
	throws Exception {
		StringBuffer sb = new StringBuffer();
		Boolean exportMid = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME));
		if (exportMid != null && exportMid.booleanValue()) {
			sb.append("define(\"").append(mid).append("\",'"); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			sb.append("define('"); //$NON-NLS-1$
		}
		StringWriter writer = new StringWriter();
		CopyUtil.copy(
				new JavaScriptEscapingReader(
						getContentReader(mid, resource, request, keyGens)
				), 
				writer
		);
		sb.append(writer.toString());
		sb.append("');"); //$NON-NLS-1$
		return new ModuleBuild(sb.toString(), keyGens, false);
	}
	
	protected Reader getContentReader(
			String mid,
			IResource resource, 
			HttpServletRequest request,
			ICacheKeyGenerator[] keyGens) 
	throws IOException {
		return resource.getReader();
	}
	
	@Override
	public ICacheKeyGenerator[] getCacheKeyGenerators(IAggregator aggregator) {
		return s_cacheKeyGenerators;
	}

	@Override
	public boolean handles(String mid, IResource resource) {
		return true;
	}
}
