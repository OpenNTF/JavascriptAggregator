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
package com.ibm.jaggr.core.modulebuilder;

import java.io.Serializable;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * A module build renderer that adds support for retrieving source map information
 * associated with the build.
 */
public class SourceMappedBuildRenderer implements IModuleBuildRenderer, Serializable {
	private static final long serialVersionUID = -7172681051632926609L;

	final Object build;
	final SourceMap smap;

	public SourceMappedBuildRenderer(Object build, SourceMap smap) {
		this.build = build;
		this.smap = smap;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.modulebuilder.IModuleBuildRenderer#renderBuild(javax.servlet.http.HttpServletRequest, java.util.Set)
	 */
	@Override
	public String renderBuild(HttpServletRequest request, Set<String> dependentFeatures) {
		String result;
		if (build instanceof IModuleBuildRenderer) {
			result = ((IModuleBuildRenderer)build).renderBuild(request, dependentFeatures);
		} else {
			result = build.toString();
		}
		return result;
	}

	/**
	 * @return the source map associated with this module build
	 */
	public SourceMap getSourceMap() {
		return smap;
	}

}
