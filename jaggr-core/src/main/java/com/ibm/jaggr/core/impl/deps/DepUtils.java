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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.ibm.jaggr.core.util.PathUtil;

/**
 * Collection of utility classes used for processing Dependency trees
 */
public class DepUtils {

	/**
	 * Removes URIs containing duplicate and non-orthogonal paths so that the
	 * collection contains only unique and non-overlapping paths.
	 * 
	 * @param paths
	 *            List of URIs to be culled
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
	 * @return The String array of module dependencies.
	 */
	static public ArrayList<String> parseDependencies(Node node) {
		ArrayList<String> result = null;
		for (Node cursor = node.getFirstChild(); cursor != null; cursor = cursor
				.getNext()) {
			if (cursor.getType() == Token.CALL) {
				// The node is a function or method call
				Node name = cursor.getFirstChild();
				if (name != null && name.getType() == Token.NAME && // named function call
					name.getString().equals("define")) { // name is "define //$NON-NLS-1$
					/*
					 * This is a define() function call.  There are multiple variants and
					 * the dependency array can be the first or second parameter.
					 */
					result = new ArrayList<String>();
					Node param = name;
					for (int i = 0; i < 3 && param != null; i++) {
						param = param.getNext();
						if (param != null && param.getType() == Token.ARRAYLIT) {
							// Found the array.  Now copy the string values to the buildReader.
							Node strNode = param.getFirstChild();
							while (strNode != null) {
								if (strNode.getType() == Token.STRING) {
									String mid = strNode.getString();
									// Don't add module ids with invalid characters
									if (!PathUtil.invalidChars.matcher(mid).find()) {
										result.add(mid);
									}
								}
								strNode = strNode.getNext();
							}
						}
					}
					return result;
				}
			}
			// Recursively call this method to process the child nodes
			if (cursor.hasChildren()) {
				result = parseDependencies(cursor);
				if (result != null)
					return result;
			}
		}
		return null;
	}
}
