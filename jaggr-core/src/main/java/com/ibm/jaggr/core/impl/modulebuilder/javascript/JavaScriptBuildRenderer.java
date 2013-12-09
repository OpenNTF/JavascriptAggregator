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

package com.ibm.jaggr.core.impl.modulebuilder.javascript;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.modulebuilder.IModuleBuildRenderer;

class JavaScriptBuildRenderer implements Serializable, IModuleBuildRenderer {
	
	/**
	 * Format string for the place holder module name used to stand in for the
	 * expanded require list. Note that the punctuation characters (including
	 * space and curly braces) in the string prevents the compiler from trying
	 * to optimize the module list array by replacing it with a single string
	 * for the form "moduleA moduleB moduleC".split(' '), where the separator
	 * character can be any of the punctuation characters that are included in
	 * the place holder string.
	 */
	static final String REQUIRE_EXPANSION_PLACEHOLDER_FMT = "$/{jaggr expand require,%1$d};/$"; //$NON-NLS-1$
	
	/**
	 * Format string for the place holder module name used to stand in for the
	 * expanded require list in log messages.
	 */
	static final String REQUIRE_EXPANSION_LOG_PLACEHOLDER_FMT = "$/{jaggr expand require,%1$d}log;/$"; //$NON-NLS-1$

	/**
	 * Regular expression pattern for locating the place holder module name used
	 * to stand in for the expanded require list.
	 */
	static final Pattern REQUIRE_EXPANSION_PLACEHOLDER_PAT = 
			Pattern.compile("(,\"\\$/\\{jaggr expand require,([0-9]+)\\};/\\$\")|(\\$/\\{jaggr expand require,([0-9]+)\\}log;/\\$)", Pattern.MULTILINE); //$NON-NLS-1$
	
	private static final long serialVersionUID = -2475505194723490517L;

	/**
	 * The compiled module fragments.  The compiled module is split into
	 * fragments at the points where expanded dependencies will be 
	 * inserted into the end of require calls.
	 */
	private List<String> contentFragments = new ArrayList<String>();
	
	/**
	 * The list of expended dependencies for require calls within the
	 * compiled modules. 
	 */
	private List<ModuleDeps> expandedDeps = new ArrayList<ModuleDeps>();
	
	private List<Boolean> isLogOutput = null;
	
	public JavaScriptBuildRenderer(String content, List<ModuleDeps> depsList, boolean isReqExpLogging) {
		if (isReqExpLogging) {
			isLogOutput = new ArrayList<Boolean>();
		}
		Matcher m = REQUIRE_EXPANSION_PLACEHOLDER_PAT.matcher(content);
		// Note that the number of matches can be less than the number of
		// elements in depsList due to dead code removal by the optimizer
		while (m.find()) {
			String strIdx = m.group(2);
			boolean isLog = false;
			if (strIdx == null) {
				strIdx = m.group(4);
				isLog = true;
			}
			int depIdx = Integer.parseInt(strIdx);
			StringBuffer sb = new StringBuffer();
			m.appendReplacement(sb, ""); //$NON-NLS-1$
			contentFragments.add(sb.toString());
			expandedDeps.add(depsList.get(depIdx));
			if (isReqExpLogging) {
				isLogOutput.add(isLog);
			}
		}
		StringBuffer sb = new StringBuffer();
		m.appendTail(sb);
		contentFragments.add(sb.toString());
	}
	
	
	public String renderBuild(HttpServletRequest request) {
		ModuleDeps enclosingDeps = (ModuleDeps)request.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		if (expandedDeps == null || expandedDeps.size() == 0) {
			return contentFragments.get(0);
		}
		StringBuffer sb = new StringBuffer();
		int i;
		for (i = 0; i < expandedDeps.size(); i++) {
			ModuleDeps expanded = new ModuleDeps(expandedDeps.get(i));
			if (enclosingDeps != null) {
				expanded.subtractAll(enclosingDeps);
			}
			expanded.simplify();
			Set<String> moduleIds = expanded.getModuleIds();
			sb.append(contentFragments.get(i));
			boolean isLog = isLogOutput != null && isLogOutput.get(i);
			int j = 0;
			if (!moduleIds.isEmpty()) {
				for (String s : moduleIds) {
					if (isLog) {
						sb.append(j++ > 0 ? ", " : "").append(s); //$NON-NLS-1$ //$NON-NLS-2$
					} else {
						sb.append(",\"").append(s).append("\""); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
			}
		}
		sb.append(contentFragments.get(i));
		return sb.toString();
	}
}