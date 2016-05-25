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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.AbstractCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.readers.JavaScriptEscapingReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.util.CopyUtil;

import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.mutable.MutableObject;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

/**
 * This class specializes JavaScriptModuleBuilder for text modules.  Text modules are returned as
 * javascript modules wrapped in a define() statement.
 */
public class TextModuleBuilder implements IModuleBuilder {

	static private final AbstractCacheKeyGenerator s_cacheKeyGenerator = new AbstractCacheKeyGenerator() {
		private static final long serialVersionUID = 3835177199729110434L;
		// This is a singleton, so default equals() is sufficient
		private final String eyecatcher = "txt"; //$NON-NLS-1$
		@Override
		public String generateKey(HttpServletRequest request) {
			StringBuffer sb = new StringBuffer(eyecatcher).append(":0"); //$NON-NLS-1$
			return sb.toString();
		}
		@Override
		public String toString() {
			return eyecatcher;
		}
	};
	static protected final List<ICacheKeyGenerator> s_cacheKeyGenerators = ImmutableList.<ICacheKeyGenerator>of(s_cacheKeyGenerator);


	/**
	 * Returns the compiler input source for the text module as the text stream
	 * wrapped in an AMD define() function.
	 */
	/* (non-Javadoc)
	 * @see com.ibm.domino.servlets.aggrsvc.modules.JsModule#getSourceFile()
	 */
	@Override
	public ModuleBuild build(String mid, IResource resource, HttpServletRequest request, List<ICacheKeyGenerator> keyGens) throws Exception {
		StringBuffer sb = new StringBuffer();
		sb.append("'"); //$NON-NLS-1$
		StringWriter writer = new StringWriter();
		MutableObject<List<ICacheKeyGenerator>> keyGensRef = new MutableObject<List<ICacheKeyGenerator>>(keyGens);
		CopyUtil.copy(
				new JavaScriptEscapingReader(
						getContentReader(mid, resource, request, keyGensRef)
						),
						writer
				);
		sb.append(writer.toString());
		sb.append("'"); //$NON-NLS-1$
		return new ModuleBuild(sb.toString(), keyGensRef.getValue(), null);
	}

	protected Reader getContentReader(String mid,	IResource resource,	HttpServletRequest request,	MutableObject<List<ICacheKeyGenerator>> keyGensRef)	throws IOException {
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

	@Override
	public boolean isScript(HttpServletRequest request) {
		return  false;
	}
}
