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

import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	public ModuleDeps evaluateAll(String pluginName, Features features, Set<String> discovered, ModuleDepInfo terms, String comment) {
		ModuleDeps results = new ModuleDeps();
		evaluateAll(pluginName, features, discovered, BooleanTerm.TRUE, results, comment);
		return results.andWith(terms);
	}

	/**
	 * Recursively resolves the current node with the specified features. Any conditionals for
	 * features contained in <code>features</code> will be replaced with the result of the
	 * evaluation.
	 *
	 * @param features
	 *            The features passed to the aggregator.
	 * @param discovered
	 *            A map in which all features encountered in the evaluation will be placed. This
	 *            will not necessarily contain all features in the dependency expression. Only the
	 *            ones in the evaluation chain will be included.
	 * @param coerceUndefinedToFalse
	 *            If true, then a feature not being defined will be treated the same as if the
	 *            feature were defined with a value of false.
	 * @return this node
	 */
	public HasNode resolve(Features features, Set<String> discovered, boolean coerceUndefinedToFalse) {
		if (feature != null && discovered != null) {
			discovered.add(feature);
		}
		if (feature != null && (features.contains(feature) || coerceUndefinedToFalse)) {
			replaceWith(features.isFeature(feature) ? trueNode : falseNode);
			resolve(features, discovered, coerceUndefinedToFalse);
		}
		if (trueNode != null) {
			trueNode.resolve(features, discovered, coerceUndefinedToFalse);
		}
		if (falseNode != null) {
			falseNode.resolve(features, discovered, coerceUndefinedToFalse);
		}
		return this;
	}

	/**
	 * Adds to <code>result</code> the end point nodes (nodes that specify a module name) for this
	 * node and all of the child nodes.
	 *
	 * @param result
	 *            The collection that the end point nodes will be added to
	 */
	public void gatherEndpoints(Collection<HasNode> result) {
		if (nodeName != null && nodeName.length() > 0) {
			result.add(this);
		}
		if (trueNode != null) {
			trueNode.gatherEndpoints(result);
		}
		if (falseNode != null) {
			falseNode.gatherEndpoints(result);
		}
	}

	/**
	 * Replaces the properties of the current node with the properties of the specified node.
	 *
	 * @param node
	 *            The node to replace this node with
	 */
	public void replaceWith(HasNode node) {
		feature = node.feature;
		nodeName = node.nodeName;
		trueNode = node.trueNode;
		falseNode = node.falseNode;
	}

	/**
	 * Replaces the properties of the current node with the specified node name, making this node
	 * into an end point node.
	 *
	 * @param nodeName
	 *            The name of the end point.
	 */
	public void replaceWith(String nodeName) {
		feature = null;
		this.nodeName = nodeName;
		trueNode = falseNode = null;
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
			BooleanTerm newTerm = term.andWith(new BooleanTerm(new BooleanVar(feature, true)));
			trueNode.evaluateAll(pluginName, features, discovered, newTerm, results, comment);
			newTerm = term.andWith(new BooleanTerm(new BooleanVar(feature, false)));
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

	public String getNodeName() {
		return nodeName;
	}
}
