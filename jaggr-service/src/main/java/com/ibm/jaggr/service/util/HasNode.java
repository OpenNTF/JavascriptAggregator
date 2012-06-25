/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recursive has condition checking. Performs full ternary expression parsing
 * for the has plugin.
 */
public class HasNode {
	private static Pattern p = Pattern.compile("([^:?]*)([:?]?)"); //$NON-NLS-1$
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
