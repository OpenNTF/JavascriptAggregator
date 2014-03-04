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

import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.modulebuilder.IModuleBuildRenderer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

public class JavaScriptBuildRenderer implements Serializable, IModuleBuildRenderer {

	private static final Logger log = Logger.getLogger(JavaScriptBuildRenderer.class.getName());

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
			Pattern.compile("(,\\s*\"\\$/\\{jaggr expand require,([0-9]+)\\};/\\$\")|(\\$/\\{jaggr expand require,([0-9]+)\\}log;/\\$)", Pattern.MULTILINE); //$NON-NLS-1$

	private static final long serialVersionUID = -2475505194723490517L;

	/**
	 * The compiled module fragments.  The compiled module is split into
	 * fragments at the points where expanded dependencies will be
	 * inserted into the end of require calls.
	 */
	private List<String> contentFragments = new ArrayList<String>();

	/**
	 * The module id
	 */
	private final String mid;

	/**
	 * The list of expended dependencies for require calls within the
	 * compiled modules.
	 */
	private List<ModuleDeps> expandedDeps = new ArrayList<ModuleDeps>();

	private List<Boolean> isLogOutput = null;

	public JavaScriptBuildRenderer(String mid, String content, List<ModuleDeps> depsList, boolean isReqExpLogging) {
		final String methodName = "<ctor>"; //$NON-NLS-1$
		final boolean isDebugLogging = log.isLoggable(Level.FINER);
		final boolean isTraceLogging = log.isLoggable(Level.FINEST);
		if (isDebugLogging) {
			log.entering(JavaScriptBuildRenderer.class.getName(), methodName, new Object[]{mid, content, depsList, isReqExpLogging});
		}
		this.mid = mid;
		if (isReqExpLogging) {
			isLogOutput = new ArrayList<Boolean>();
		}
		Matcher m = REQUIRE_EXPANSION_PLACEHOLDER_PAT.matcher(content);
		// Note that the number of matches can be less than the number of
		// elements in depsList due to dead code removal by the optimizer
		while (m.find()) {
			String strIdx = m.group(2);
			if (isDebugLogging) {
				List<String> groups = new ArrayList<String>(m.groupCount());
				for (int i = 0; i < m.groupCount(); i++) {
					groups.add(m.group(i));
				}
				log.finer("matched groups =" + groups.toString()); //$NON-NLS-1$
			}
			boolean isLog = false;
			if (strIdx == null) {
				strIdx = m.group(4);
				isLog = true;
				if (isDebugLogging) {
					log.finer("Console logging is enabled"); //$NON-NLS-1$
				}
			}
			int depIdx = Integer.parseInt(strIdx);
			StringBuffer sb = new StringBuffer();
			m.appendReplacement(sb, ""); //$NON-NLS-1$
			contentFragments.add(sb.toString());
			if (isTraceLogging) {
				log.finest("Adding to contentFragments - " + sb.toString()); //$NON-NLS-1$
			}
			ModuleDeps expandedDep = depsList.get(depIdx);
			expandedDeps.add(expandedDep);
			if (isTraceLogging) {
				log.finest("Adding to expandedDeps - " + expandedDeps); //$NON-NLS-1$
			}
			if (isReqExpLogging) {
				isLogOutput.add(isLog);
			}
		}
		StringBuffer sb = new StringBuffer();
		m.appendTail(sb);
		contentFragments.add(sb.toString());
		if (isTraceLogging) {
			log.finest("Adding to contentFragments - " + sb.toString()); //$NON-NLS-1$
		}
		if (isDebugLogging) {
			log.exiting(JavaScriptBuildRenderer.class.getName(), methodName);
		}
	}


	public String renderBuild(HttpServletRequest request, Set<String> dependentFeatures) {
		final String methodName = "renderBuild"; //$NON-NLS-1$
		final boolean isDebugLogging = log.isLoggable(Level.FINER);
		final boolean isTraceLogging = log.isLoggable(Level.FINEST);
		if (isDebugLogging) {
			log.entering(JavaScriptBuildRenderer.class.getName(), methodName, new Object[]{request, dependentFeatures});
		}
		if (isDebugLogging) {
			log.finer("Rendering expanded dependencies for " + mid); //$NON-NLS-1$
		}
		String result = null;
		ModuleDeps enclosingDeps = (ModuleDeps)request.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		if (contentFragments.size() < 2) {
			result = contentFragments.get(0);
		} else {
			if (contentFragments.size() != expandedDeps.size() + 1) {
				throw new IllegalStateException(mid + "(" + contentFragments.size() + "," + expandedDeps.size() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			StringBuffer sb = new StringBuffer();
			int i;
			for (i = 0; i < expandedDeps.size(); i++) {
				ModuleDeps expanded = new ModuleDeps(expandedDeps.get(i));
				if (enclosingDeps != null) {
					expanded.subtractAll(enclosingDeps);
				}
				Set<String> moduleIds = expanded.getModuleIds();
				if (isTraceLogging) {
					log.finest("contentFragment = " + contentFragments.get(i)); //$NON-NLS-1$
					log.finest("moduleIds = " + moduleIds); //$NON-NLS-1$
				}
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
			if (isTraceLogging) {
				log.finest("contentFragment = " + contentFragments.get(i)); //$NON-NLS-1$
			}
			result = sb.toString();
		}
		if (isDebugLogging) {
			log.exiting(JavaScriptBuildRenderer.class.getName(), methodName, result);
		}
		return result;
	}
}
