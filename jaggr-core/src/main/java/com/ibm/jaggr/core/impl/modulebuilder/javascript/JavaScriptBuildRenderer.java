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
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.ConcurrentListBuilder;
import com.ibm.jaggr.core.util.Features;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

public class JavaScriptBuildRenderer implements Serializable, IModuleBuildRenderer {

	private static final Logger log = Logger.getLogger(JavaScriptBuildRenderer.class.getName());

	/**
	 * Format string for the place holder variable used to stand in for the expanded require list
	 * array element reference. Note that the first parameter (%1$s) is use for a variable length
	 * string of underscore characters (0-2) in order to ensure that the length of the place holder
	 * string will be constant regardless of the number of digits used to represent the second
	 * parameter (%2$d), the array index. This is needed because the place holder is inserted before
	 * optimizations are performed and the closure compiler would remove any leading zeros that we
	 * tried to use for padding in the index. The underscore pad characters are removed and replaced
	 * with leading zeros for the array index and the index value is updated for to reference the
	 * expanded module list in the layer's dependency array during the build renderer phase.
	 * <p>
	 * For example, the expression {@code _$$JAGGR_DEPS$$___[0]} in the compiled module would be
	 * replaced with {@code _$$JAGGR_DEPS$$_[012]} in the rendered build, assuming that the first
	 * dependency array element from in the module mapped to the twelfth dependency array element in
	 * the rendered layer.
	 * <p>
	 * We need to ensure that the number of characters used to represent the expanded deps variable
	 * remains constant before vs. after compilation in order to avoid throwing off source maps.
	 */
	static final String REQUIRE_EXPANSION_PLACEHOLDER_FMT =
			JavaScriptModuleBuilder.EXPDEPS_VARNAME + "%1$s[0][%2$d]"; //$NON-NLS-1$

	/**
	 * Format string for the replacement string that is used to replace the place holder reference
	 * (see {@link #REQUIRE_EXPANSION_LOG_PLACEHOLDER_FMT}) at build render time. Uses a fixed
	 * length array index with leading zeros to ensure that the length of the expression is
	 * unaffected by the array index value.
	 */
	static final String REQUIRE_EXPANSION_REPLACE_FMT =
			JavaScriptModuleBuilder.EXPDEPS_VARNAME + "[0][%1$03d]"; //$NON-NLS-1$

	/**
	 * Format string for the replacement string that is used to append the expanded
	 * module list to the require dependency logging.  Note that we don't worry about
	 * the length of the expression like we do for the require list expansion because
	 * logging code is appended to the end of the module and therefore doesn't affect
	 * the module's source map.
	 */
	static final String REQUIRE_EXPANSION_LOG_PLACEHOLDER_FMT =
			JavaScriptModuleBuilder.EXPDEPS_VARNAME + "LOG[%1$d]"; //$NON-NLS-1$

	/**
	 * Regular expression pattern for locating the place holder module name used
	 * to stand in for the expanded require list.
	 */
	static Pattern REQUIRE_EXPANSION_PLACEHOLDER_PAT =
			Pattern.compile(
					"(" + JavaScriptModuleBuilder.EXPDEPS_VARNAME.replace("$", "\\$") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					"_{0,2}\\[0\\]\\[([0-9]+)\\])|(" + //$NON-NLS-1$
					JavaScriptModuleBuilder.EXPDEPS_VARNAME.replace("$", "\\$") + //$NON-NLS-1$ //$NON-NLS-2$
					"LOG\\[([0-9]+)\\])", Pattern.MULTILINE); //$NON-NLS-1$

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
	private Map<Integer, ModuleDeps> expandedDeps = new HashMap<Integer, ModuleDeps>();

	/**
	 * Maps the index of the dependency variable within the content to the array index
	 * specified in the dependency variable.
	 */
	private List<Integer> expDepIndicies = new ArrayList<Integer>();

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
			expandedDeps.put(depIdx, expandedDep);
			expDepIndicies.add(depIdx);

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
		Features features = (Features)request.getAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME);
		ModuleDeps enclosingDeps = (ModuleDeps)request.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		if (enclosingDeps != null && features != null) {
			enclosingDeps.resolveWith(features);
		}
		if (contentFragments.size() < 2) {
			result = contentFragments.get(0);
		} else {
			if (contentFragments.size() != expDepIndicies.size() + 1) {
				throw new IllegalStateException(mid + "(" + contentFragments.size() + "," + expandedDeps.size() + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}

			@SuppressWarnings("unchecked")
			ConcurrentListBuilder<String[]> expDeps = (ConcurrentListBuilder<String[]>)request.getAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS);
			Map<?,?> formulaCache = (Map<?,?>)request.getAttribute(JavaScriptModuleBuilder.FORMULA_CACHE_REQATTR);
			Map<Integer, Integer> indexMap = new HashMap<Integer, Integer>();
			Map<Integer, Set<String>> midMap = new HashMap<Integer, Set<String>>();
			StringBuffer sb = new StringBuffer();
			int i;
			for (Map.Entry<Integer, ModuleDeps> entry : expandedDeps.entrySet()) {
				ModuleDeps expanded = new ModuleDeps(entry.getValue());
				if (features != null) {
					expanded.resolveWith(features);
				}
				if (enclosingDeps != null) {
					expanded.subtractAll(enclosingDeps);
				}
				Set<String> moduleIds = expanded.getModuleIds(formulaCache);
				indexMap.put(entry.getKey(), expDeps.add(moduleIds.toArray(new String[moduleIds.size()])));
				midMap.put(entry.getKey(), moduleIds);
			}
			for (i = 0; i < contentFragments.size()-1; i++) {
				if (isTraceLogging) {
					log.finest("contentFragment = " + contentFragments.get(i)); //$NON-NLS-1$
					log.finest("moduleIds = " + midMap.get(i)); //$NON-NLS-1$
				}
				sb.append(contentFragments.get(i));
				boolean isLog = isLogOutput != null && isLogOutput.get(i);
				if (!isLog) {
					// use concat to append list of modules specified elsewhere to dependency list
					sb.append(String.format(REQUIRE_EXPANSION_REPLACE_FMT, indexMap.get(expDepIndicies.get(i))));
				} else {
					// For log output, expand the dependencies inline instead of referencing an
					// entry in the dependency list array because the logging executes before
					// the dependency list array has been initialized.  We can do this because
					// the log output is appended to the end of the module and therefore doesn't
					// affect the integrity of the source maps.
					int j = 0;
					for (String s : midMap.get(expDepIndicies.get(i))) {
						sb.append(j++ > 0 ? ", " : "").append(s); //$NON-NLS-1$ //$NON-NLS-2$
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
