/*
 * (C) Copyright 2014, IBM Corporation
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

package com.ibm.jaggr.core.impl.modulebuilder.less;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessException;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder;
import com.ibm.jaggr.core.resource.IResource;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * This class compiles LESS resources that are loaded by the AMD aggregator.
 */
public class LessModuleBuilder extends CSSModuleBuilder {

	public static final LessEngine LESS_ENGINE = new LessEngine();

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.modulebuilder.impl.text.TextModuleBuilder#getContentReader(java.lang.String,
	 * com.ibm.jaggr.service.resource.IResource,
	 * javax.servlet.http.HttpServletRequest,
	 * com.ibm.jaggr.service.module.ICacheKeyGenerator)
	 */
	@Override
	protected Reader getContentReader(String mid, IResource resource, HttpServletRequest request, List<ICacheKeyGenerator> keyGens) throws IOException {
		String less = readToString(resource.getReader());
		try {
			return processCss(resource, request, LESS_ENGINE.compile(less, resource.getURI().toString()));
		} catch (LessException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.modulebuilder.IModuleBuilder#handles(java.lang
	 * .String, com.ibm.jaggr.service.resource.IResource)
	 */
	@Override
	public boolean handles(String mid, IResource resource) {
		return mid.endsWith(".less"); //$NON-NLS-1$
	}
}
