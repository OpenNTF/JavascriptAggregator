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

package com.ibm.jaggr.core.impl.modulebuilder.text;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ExportNamesCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.readers.JavaScriptEscapingReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.TypeUtil;

/**
 * This class specializes JavaScriptModuleBuilder for text modules.  Text modules are returned as 
 * javascript modules wrapped in a define() statement.
 */
public class TextModuleBuilder implements IModuleBuilder {
	
	static protected final List<ICacheKeyGenerator> s_cacheKeyGenerators = 
		Collections.unmodifiableList(Arrays.asList(new ICacheKeyGenerator[]{new ExportNamesCacheKeyGenerator()}));

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
			List<ICacheKeyGenerator> keyGens
			) 
	throws Exception {
		StringBuffer sb = new StringBuffer();
		Boolean exportMid = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME));
		Boolean noTextAdorn = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOTEXTADORN_REQATTRNAME));
		if (noTextAdorn) {
			sb.append("'"); //$NON-NLS-1$
		} else if (exportMid != null && exportMid.booleanValue()) {
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
		sb.append(noTextAdorn ? "'" : "');"); //$NON-NLS-1$ //$NON-NLS-2$
		return new ModuleBuild(sb.toString(), keyGens, false);
	}
	
	protected Reader getContentReader(
			String mid,
			IResource resource, 
			HttpServletRequest request,
			List<ICacheKeyGenerator> keyGens) 
	throws IOException {
		return resource.getReader();
	}
	
	@Override
	public List<ICacheKeyGenerator> getCacheKeyGenerators(IAggregator aggregator) {
		return s_cacheKeyGenerators;
	}

	@Override
	public boolean handles(String mid, IResource resource) {
		return true;
	}
}
