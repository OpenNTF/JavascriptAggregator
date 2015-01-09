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

package com.ibm.jaggr.core.impl.deps;

import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.HasNode;
import com.ibm.jaggr.core.util.NodeUtil;
import com.ibm.jaggr.core.util.PathUtil;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Collection of utility classes used for processing Dependency trees
 */
public class DepUtils {

	static final Pattern hasPattern = Pattern.compile("(^|\\/)has!"); //$NON-NLS-1$

	static public class ParseResult {
		private Collection<String> defineDeps;
		private Collection<String> requireDeps;

		public Collection<String> getDefineDependencies() {
			return defineDeps == null ? null : Collections.unmodifiableCollection(defineDeps);
		}

		public Collection<String> getRequireDependencies() {
			return requireDeps == null ? null : Collections.unmodifiableCollection(requireDeps);
		}

		private void addAll(Collection<String> defineDeps, Collection<String> requireDeps) {
			if (this.defineDeps == null) {
				this.defineDeps = defineDeps;
			} else if (defineDeps != null) {
				this.defineDeps.addAll(defineDeps);
			}
			if (this.requireDeps == null) {
				this.requireDeps = requireDeps;
			} else if (requireDeps != null) {
				this.requireDeps.addAll(requireDeps);
			}
		}

		private void addAll(ParseResult other) {
			addAll(other.defineDeps, other.requireDeps);
		}
	}

	/**
	 * Removes URIs containing duplicate and non-orthogonal paths so that the
	 * collection contains only unique and non-overlapping paths.
	 *
	 * @param uris collection of URIs
	 *
	 * @return a new collection with redundant paths removed
	 */
	static public Collection<URI> removeRedundantPaths(Collection<URI> uris) {
		List<URI> result = new ArrayList<URI>();
		for (URI uri : uris) {
			String path = uri.getPath();
			if (!path.endsWith("/")) {  //$NON-NLS-1$
				path += "/"; //$NON-NLS-1$
			}
			boolean addIt = true;
			for (int i = 0; i < result.size(); i++) {
				URI testUri = result.get(i);
				if (!StringUtils.equals(testUri.getScheme(), uri.getScheme()) ||
						!StringUtils.equals(testUri.getHost(), uri.getHost()) ||
						testUri.getPort() != uri.getPort()) {
					continue;
				}
				String test = testUri.getPath();
				if (!test.endsWith("/")) { //$NON-NLS-1$
					test += "/"; //$NON-NLS-1$
				}
				if (path.equals(test) || path.startsWith(test)) {
					addIt = false;
					break;
				} else if (test.startsWith(path)) {
					result.remove(i);
				}
			}
			if (addIt)
				result.add(uri);
		}
		// Now copy the trimmed list back to the original
		return result;
	}


	/**
	 * Maps a resource URI to a {@link DepTreeNode}. This routine finds the key
	 * in <code>dependencies</code> that is an ancestor of
	 * <code>requiestURI</code> and then looks for the {@link DepTreeNode} who's
	 * name corresponds to descendant part of the URI path.
	 *
	 * @param requestUri
	 *            The URI for the resource location being sought
	 * @param dependencies
	 *            Map of file path names to root {@link DepTreeNode}s
	 * @return The node corresponding to <code>requestURI</code>
	 */
	static public DepTreeNode getNodeForResource(URI requestUri,
			Map<URI, DepTreeNode> dependencies) {
		DepTreeNode result = null;
		/*
		 * Iterate through the map entries and find the entry who's key is the same,
		 * or an ancestor of, the specified path
		 */
		for (Entry<URI, DepTreeNode> dependency : dependencies.entrySet()) {
			URI uri = dependency.getKey();
			if (requestUri.getScheme() == null && uri.getScheme() != null ||
					requestUri.getScheme() != null && !requestUri.getScheme().equals(uri.getScheme()))
				continue;

			if (requestUri.getHost() == null && uri.getHost() != null ||
					requestUri.getHost() != null && !requestUri.getHost().equals(uri.getHost()))
				continue;

			if (requestUri.getPath().equals(uri.getPath())
					|| requestUri.getPath().startsWith(uri.getPath())
					&& (uri.getPath().endsWith("/") || requestUri.getPath().charAt(uri.getPath().length()) == '/'))  //$NON-NLS-1$
			{
				/*
				 * Found the entry.  Now find the node corresponding to the
				 * remainder of the path.
				 */
				if (requestUri.getPath().equals(uri.getPath())) {
					return dependency.getValue();
				} else {
					String modulePath = requestUri.getPath().substring(uri.getPath().length());
					if (modulePath.startsWith("/")) { //$NON-NLS-1$
						modulePath = modulePath.substring(1);
					}
					result = dependency.getValue().getDescendent(modulePath);
				}
				break;
			}
		}
		return result;
	}

	/**
	 * Walks the parsed AST {@link Node} looking for the AMD define() function
	 * call and extracts the require list of dependencies to a string array and
	 * returns the buildReader.
	 * <p>
	 * Any require list entries that are not a string literal (e.g. an object
	 * ref) are omitted from the returned array.
	 *
	 * @param node
	 *            A parsed AST {@link Node} for a javascript file
	 * @param dependentFeatures
	 *            Output - any features specified using the has! loader plugin
	 *            will be added to this set.
	 * @return The String array of module dependencies.
	 */
	static public ParseResult parseDependencies(Node node, Set<String> dependentFeatures) {
		ParseResult result = new ParseResult();
		for (Node cursor = node.getFirstChild(); cursor != null; cursor = cursor
				.getNext()) {
			Node defineDeps = null, requireDeps = null, dependencies;
			String condition;
			if ((condition = NodeUtil.conditionFromHasNode(cursor)) != null) {
				dependentFeatures.add(condition);
			} else {
				@SuppressWarnings("unchecked")
				LinkedHashSet<String>[] resultArray = new LinkedHashSet[]{null, null};
				defineDeps = NodeUtil.moduleDepsFromDefine(cursor);
				requireDeps = NodeUtil.moduleDepsFromRequire(cursor);
				// Found the array.  Now copy the string values to the buildReader.
				int i;
				for (i = 0, dependencies = defineDeps; i < 2; i++, dependencies = requireDeps) {
					if (dependencies == null) {
						continue;
					}
					resultArray[i] = new LinkedHashSet<String>();
					Node strNode = dependencies.getFirstChild();
					while (strNode != null) {
						if (strNode.getType() == Token.STRING) {
							String mid = strNode.getString();
							URI uri = URI.create(mid);
							// Don't add module ids with invalid characters or that specify an absolute or server relative resource
							if (!PathUtil.invalidChars.matcher(mid).find() && !uri.isAbsolute() && !uri.getPath().startsWith("/")) { //$NON-NLS-1$
								resultArray[i].add(mid);
								// if the id specifies a has loader plugin, then add the
								// has dependencies to the dependencies list
								if (hasPattern.matcher(mid).find()) {
									int idx = mid.indexOf("!"); //$NON-NLS-1$
									HasNode hasNode = new HasNode(mid.substring(idx+1));
									hasNode.evaluateAll(
											mid.substring(0, idx),
											Features.emptyFeatures,
											dependentFeatures,
											BooleanTerm.TRUE, null);
								}
							}
						}
						strNode = strNode.getNext();
					}
				}
				result.addAll(resultArray[0], resultArray[1]);
			}
			// Recursively call this method to process the child nodes
			if (cursor.hasChildren()) {
				result.addAll(parseDependencies(cursor, dependentFeatures));
			}
		}
		return result;
	}
}
