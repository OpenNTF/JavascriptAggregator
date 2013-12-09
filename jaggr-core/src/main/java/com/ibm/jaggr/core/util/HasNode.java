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

package com.ibm.jaggr.core.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;

/**
 * Recursive has condition checking. Performs full ternary expression parsing
 * for the has plugin.
 */
public class HasNode {
	private static final Pattern p = Pattern.compile("([^:?]*)([:?]?)"); //$NON-NLS-1$
	private String feature = null, nodeName;
	private HasNode trueNode, falseNode;

	public HasNode(String str) {
		Matcher m = p.matcher(str);
		if (m.find(0) && (m.group(2).equals("?") || m.group(2).equals(":"))) { //$NON-NLS-1$ //$NON-NLS-2$
			feature = m.group(1);

			StringBuilder buffer = new StringBuilder();
			int qcount = 0, index = m.end(2);
			while (qcount > -1 && m.find(index) && index < str.length()) {
				if (m.group(2).equals("?")) //$NON-NLS-1$
					qcount++;
				else if (m.group(2).equals(":")) //$NON-NLS-1$
					qcount--;
				index = m.end(2);
				buffer.append(m.group(1));
				if (qcount > -1)
					buffer.append(m.group(2));
			}
			trueNode = new HasNode(buffer.toString());
			falseNode = new HasNode((index == str.length()) ? "" : str.substring(index)); //$NON-NLS-1$
		} else {
			nodeName = str;
		}
	}

	/**
	 * Evaluate the has plugin for the given set of features.
	 * 
	 * @param features
	 *            The features passed to the aggregator.
	 * @param discovered
	 *            A map in which all features encountered in the evaluation will
	 *            be placed. This will not necessarily contain all features in
	 *            the dependency expression. Only the ones in the evaluation
	 *            chain will be included.
	 * @param coerceUndefinedToFalse
	 *            If true, then a feature not being defined will be treated the
	 *            same as if the feature were defined with a value of false.
	 * @return The evaluated resource based on provided features. If a lack of
	 *         features prevents us from being able to determine the resource,
	 *         then null is returned.  If the required features are provided
	 *         but the evaluation results in no module name, then the empty 
	 *         string is returned.
	 */
	public String evaluate(Features features, Set<String> discovered, boolean coerceUndefinedToFalse) {
		if (feature != null && discovered != null) {
			discovered.add(feature);
		}
		if (feature == null) {
			return nodeName;
		} else if (!coerceUndefinedToFalse && !features.contains(feature)) {
			return null;
		} else {
			if (features.isFeature(feature)) {
				return trueNode.evaluate(features, discovered, coerceUndefinedToFalse);
			} else {
				return falseNode.evaluate(features, discovered, coerceUndefinedToFalse);
			}
		}
	}
	
	public String evaluate(Features features, Set<String> discovered) {
		return evaluate(features, discovered, false);
	}
	
	public ModuleDeps evaluateAll(String pluginName, Features features, Set<String> discovered, BooleanTerm term, String comment) {
		ModuleDeps results = new ModuleDeps();
		evaluateAll(pluginName, features, discovered, term, results, comment);
		return results;
	}
	private void evaluateAll(String pluginName, Features features, Set<String> discovered, BooleanTerm term, ModuleDeps results, String comment) {
		if (feature != null && discovered != null) {
			discovered.add(feature);
		}
		if (feature == null) {
			if (nodeName != null && nodeName.length() > 0) {
				results.add(nodeName, new ModuleDepInfo(pluginName, term, comment, true));
			}
		} else if (!features.contains(feature)) {
			Set<BooleanVar> vars = (term != null ? new HashSet<BooleanVar>(term) : new HashSet<BooleanVar>());
			vars.add(new BooleanVar(feature, true));
			BooleanTerm newTerm = new BooleanTerm(vars);
			trueNode.evaluateAll(pluginName, features, discovered, newTerm, results, comment);
			vars = (term != null ? new HashSet<BooleanVar>(term) : new HashSet<BooleanVar>());
			vars.add(new BooleanVar(feature, false));
			newTerm = new BooleanTerm(vars);
			falseNode.evaluateAll(pluginName, features, discovered, newTerm, results, comment);
		} else {
			if (features.isFeature(feature)) {
				trueNode.evaluateAll(pluginName, features, discovered, term, results, comment);
			} else {
				falseNode.evaluateAll(pluginName, features, discovered, term, results, comment);
			}
		}
	}
	
	/**
	 * Normalizes the current node relative to the path specified in
	 * <code>ref</code> and recursively normalizes child nodes.
	 * 
	 * @param ref
	 *            The reference path to which relative paths are normalized
	 * @return This object, to allow chaining of toString() as in
	 *         <code>normalized = new HasNode(name).normalize(ref).toString();</code>
	 */
	public HasNode normalize(String ref) {
		if (feature == null) {
			nodeName = PathUtil.normalizePaths(ref, new String[]{nodeName})[0];
		} else {
			trueNode.normalize(ref);
			falseNode.normalize(ref);
		}
		return this;
	}
	
	/**
	 * Returns the string representation of this node. This is the same as the
	 * string used to construct the node, unless the node has been normalized,
	 * in which case the output will be the normalized representation of the
	 * string used to construct this node.
	 *
	 * @return The string representation of this node
	 */
	public String toString() {
		if (feature == null) {
			return nodeName;
		}
		String trueName = trueNode.toString(),
		       falseName = falseNode.toString();
		if (trueName.length() == 0 && falseName.length() == 0) {
			return ""; //$NON-NLS-1$
		}
		StringBuffer sb = new StringBuffer();
		sb.append(feature).append("?").append(trueName); //$NON-NLS-1$
		if (falseName.length() > 0) {
			sb.append(":").append(falseName); //$NON-NLS-1$
		}
		return sb.toString();	
	}
	
	public String getFeature() {
		return feature;
	}
}
