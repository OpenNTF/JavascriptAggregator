/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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

import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.util.PathUtil;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * This class is the node object for a module dependency. Nodes are organized in
 * a tree structure according to the module naming hierarchy. For example, the
 * node for the module foo/bar has the name bar and is a child of the node for
 * the module with name foo. A node can be a directory node with children, or a
 * file node with a dependency list, or both. The reason that a node can have
 * both children and a dependency list is that the names of javascript modules
 * omit the .js extension, so a directory can have both a child directory named
 * bar and a javascript module named bar (named bar.js on the file system).
 */
public class DepTreeNode implements Cloneable, Serializable {
	private static final long serialVersionUID = -7506166918682469345L;

	static final Logger log = Logger.getLogger(DepTreeNode.class.getName());

	/** regular expression for detecting if a plugin name is the has! plugin */
	static final Pattern hasPattern = Pattern.compile("(^|\\/)has$"); //$NON-NLS-1$

	/** Map of name/child-node pairs for this node */
	private Map<String, DepTreeNode> children;
	/** The name of this node */
	private String name;
	/** The list of define() module name dependencies for this node if it is a module */
	private String[] defineDependencies;
	/** The list of require() module name dependencies for this node if it is a module */
	private String[] requireDependencies;
	/** The list of dependent features in the module */
	private String[] dependentFeatures;
	/** The file last modified date if this node is for a module */
	private long lastModified = -1;

	private URI uri;
	/**
	 * The last modified date of the dependency list if this node is for a
	 * module. Used to keep track of when caches need to be invalidated as a
	 * buildReader of dependency chain changes.
	 */
	private long lastModifiedDep = -1;

	// The following don't get serialized.

	/**
	 * A reference to the parent node. We use {@link WeakReference} to avoid
	 * circular references that could prevent the tree from being garbage
	 * collected when there are no more references to the root node
	 */
	private transient WeakReference<DepTreeNode> parent;

	class DependencyInfo {
		private List<String> declaredDependencies;
		private List<String> requireDependencies;
		private List<String> dependentFeatures;
		private final URI uri;
		private DependencyInfo(String[] declaredDependencies, String[] requireDependencies, String[] dependentFeatures, URI uri) {
			this.declaredDependencies = declaredDependencies != null ?
					Collections.unmodifiableList(Arrays.asList(declaredDependencies)) :
						Collections.<String>emptyList();
			this.requireDependencies = requireDependencies != null ?
					Collections.unmodifiableList(Arrays.asList(requireDependencies)) :
						Collections.<String>emptyList();
			this.dependentFeatures = dependentFeatures != null ?
					Collections.unmodifiableList(Arrays.asList(dependentFeatures)) :
						Collections.<String>emptyList();
			this.uri = uri;
		}
		public List<String> getDeclaredDependencies() { return declaredDependencies; }
		public List<String> getRequireDependencies() { return requireDependencies; }
		public List<String> getDepenedentFeatures() { return dependentFeatures; }
		public URI getURI() { return uri; }
	}

	/**
	 * Object constructor. Creates a node with the given name.
	 *
	 * @param name
	 *            The module name. Must not contain '/' characters.
	 * @param uri
	 *            The source URI
	 */
	public DepTreeNode(String name, URI uri) {
		if (name.contains("/")) { //$NON-NLS-1$
			throw new IllegalArgumentException(name);
		}
		children = null;
		defineDependencies = null;
		requireDependencies = null;
		parent = new WeakReference<DepTreeNode>(null);
		this.name = name;
		this.uri = uri;
	}

	/**
	 * @return The name for the module without path information
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return The last modified date of the of the corresponding file on the
	 *         file system if this node is for a module (as opposed to a
	 *         directory). -1 otherwise.
	 */
	public long lastModified() {
		return lastModified;
	}

	/**
	 * Returns the last modified date of the dependencies for this node. The
	 * dependency last modified date is updated only when the javascript file
	 * this node references is initially parsed, and when the dependency list
	 * specified in the module's define() function is determined to have changed
	 * from the last time the file was parsed. So this date is updated less
	 * frequently than the file last modified date.
	 * <p>
	 * Dependency last modified dates are used by the cache manager to determine
	 * when the entire cache must be invalidated do to changes in dependency
	 * lists which can have ripple effects across many cached responses.
	 *
	 * @return The last modified date of this node's dependency list
	 */
	public long lastModifiedDep() {
		return lastModifiedDep;
	}

	/**
	 * Returns the most recent last modified date of all the dependencies that are
	 * descendants of this node, including this node.
	 *
	 * @return The most recent last modified date
	 */
	public long lastModifiedDepTree() {
		return lastModifiedDepTree(-1);
	}

	/**
	 * @return A reference to the parent node, or null if this node is the root
	 *         node or has not been inserted into a tree.
	 */
	public DepTreeNode getParent() {
		return parent.get();
	}

	/**
	 * @return A reference to the root node for the tree that this node is
	 *         contained in, or null if this node is the root node or is not a
	 *         member of a tree.
	 */
	public DepTreeNode getRoot() {
		DepTreeNode parentRef = parent.get();
		return parentRef != null ? parentRef.getRoot() : this;
	}

	/**
	 * @return The define() dependency array
	 */
	public String[] getDefineDepArray() {
		return defineDependencies;
	}

	/**
	 * @return The require() dependency array
	 */
	public String[] getRequireDepArray() {
		return requireDependencies;
	}



	/**
	 * @return The depenedent features array
	 */
	public String[] getDependentFeatures() {
		return dependentFeatures;
	}

	/**
	 * @return The full path name from root of this node's parent node, or an
	 *         empty string if this node is the root node or this node is not a
	 *         member of a tree.
	 */
	public String getParentPath() {
		StringBuffer sb = new StringBuffer();
		DepTreeNode parentRef = parent.get();
		if (parentRef != null) {
			String path = parentRef.getParentPath();
			if (path != null) {
				sb.append(path);
			}
			sb.append(sb.length() == 0 ? "" : "/").append(parentRef.getName()); //$NON-NLS-1$ //$NON-NLS-2$

		} else {
			return null;
		}
		return sb.toString();
	}

	/**
	 * @return The full path name from root of this node
	 */
	public String getFullPathName() {
		String parentPath = getParentPath();
		if (parentPath == null) {
			parentPath = ""; //$NON-NLS-1$
		}
		return parentPath + (parentPath.length() == 0 ? "" : "/") + getName(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Add the specified node to this node's children.
	 *
	 * @param child
	 *            The node to add to this node's children
	 */
	public void add(DepTreeNode child) {
		if (children == null) {
			children = new HashMap<String, DepTreeNode>();
		}
		children.put(child.getName(), child);
		child.setParent(this);
	}

	/**
	 * Overlay the specified node and its descendants over this node.  The
	 * specified node will be merged with this node, with the dependencies
	 * of any nodes from the specified node replacing the dependencies of
	 * the corresponding node in this node's tree.
	 *
	 * @param node
	 */
	public void overlay(DepTreeNode node) {
		if (node.defineDependencies != null || node.requireDependencies != null) {
			setDependencies(node.defineDependencies, node.requireDependencies, node.dependentFeatures, node.lastModified(), node.lastModifiedDep());
		}
		node.uri = uri;
		if (node.getChildren() == null) {
			return;
		}
		for(Map.Entry<String, DepTreeNode> entry : node.getChildren().entrySet()) {
			DepTreeNode existing = getChild(entry.getKey());
			if (existing == null) {
				add(entry.getValue());
			} else {
				existing.overlay(entry.getValue());
			}
		}
	}

	public void addAll(Collection<DepTreeNode> children) {
		for(DepTreeNode child : children) {
			add(child);
		}
	}

	/**
	 * Returns the node at the specified path location within the tree, or
	 * creates it if it is not already in the tree. Will create any required
	 * parent nodes.
	 *
	 * @param path
	 *            The path name, relative to this node, of the node to return.
	 * @param uri
	 *            the source URI
	 * @return The node at the specified path.
	 */
	public DepTreeNode createOrGet(String path, URI uri) {
		if (path.startsWith("/")) { //$NON-NLS-1$
			throw new IllegalArgumentException(path);
		}
		if (path.length() == 0)
			return this;

		String[] pathComps = path.split("/"); //$NON-NLS-1$
		DepTreeNode node = this;
		int i = 0;
		for (String comp : pathComps) {
			DepTreeNode childNode = node.getChild(comp);
			if (childNode == null) {
				childNode = new DepTreeNode(comp, i == pathComps.length-1 ? uri : null);
				node.add(childNode);
			}
			node = childNode;
			i++;
		}
		return node;
	}

	/**
	 * @param path
	 *            The node with the specified path relative to this node, or
	 *            null
	 * @return the descendant node or null
	 */
	public DepTreeNode getDescendent(String path) {
		if (path.startsWith("/")) { //$NON-NLS-1$
			if (log.isLoggable(Level.WARNING)) {
				log.warning(
						MessageFormat.format(
								Messages.DepTreeNode_3,
								new Object[]{path}
								)
						);
			}
			return null;
		}
		if (path.length() == 0)
			return this;

		String[] pathComps = path.split("/"); //$NON-NLS-1$
		DepTreeNode node = this;
		for (String comp : pathComps) {
			DepTreeNode childNode = node.getChild(comp);
			if (childNode == null) {
				return null;
			}
			node = childNode;
		}
		return node;
	}

	/**
	 * Removes the specified child node
	 *
	 * @param child
	 *            The node to remove
	 */
	public void remove(DepTreeNode child) {
		if (children != null) {
			DepTreeNode node = children.get(child.getName());
			if (node == child) {
				children.remove(child.getName());
			}
		}
		child.setParent(null);
	}

	/**
	 * Removes all of this node's children
	 */
	public void removeAll() {
		children = null;
	}

	/**
	 * @return An immutable map of name/node pairs for this node's child nodes.
	 */
	public Map<String, DepTreeNode> getChildren() {
		Map<String, DepTreeNode> result;
		if (children != null) {
			result = Collections.unmodifiableMap(children);
		} else {
			result = Collections.emptyMap();
		}
		return result;
	}

	/**
	 * @param name The name of the child node
	 * @return The specified child node
	 */
	public DepTreeNode getChild(String name) {
		DepTreeNode result = null;
		if (children != null) {
			result = children.get(name);
		}
		return result;
	}

	/**
	 * Recursively walks the node tree removing folder nodes that have not children.
	 */
	public void prune() {
		if (children != null) {
			List<String> removeList = new ArrayList<String>();
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				DepTreeNode child = entry.getValue();
				child.prune();
				if ((child.defineDependencies == null && child.requireDependencies == null && child.uri == null)
						&& (child.children == null || child.children.isEmpty())) {
					removeList.add(entry.getKey());
				}
			}
			for (String key : removeList) {
				children.remove(key);
			}
		}
	}

	/**
	 * Specifies the dependency list of modules for the module named by this
	 * node, along with the last modified date of the javascript file that the
	 * dependency list was obtained from.
	 *
	 * @param defineDependencies
	 *            The define() dependency list of module names
	 * @param requireDependencies
	 *            The require() dependency list of module names
	 * @param dependentFeatures
	 *            The dependent features for the module
	 * @param lastModifiedFile
	 *            The last modified date of the javascript source file
	 * @param lastModifiedDep
	 *            The last modified date of the dependency list. See
	 *            {@link #lastModifiedDep()}
	 */
	public void setDependencies(String[] defineDependencies, String[] requireDependencies, String[] dependentFeatures, long lastModifiedFile, long lastModifiedDep) {
		this.defineDependencies = defineDependencies;
		this.requireDependencies = requireDependencies;
		this.dependentFeatures = dependentFeatures;
		this.lastModified = lastModifiedFile;
		this.lastModifiedDep = lastModifiedDep;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		if (defineDependencies != null || requireDependencies != null) {
			sb.append(getFullPathName()).append(" = "); //$NON-NLS-1$
			if (defineDependencies != null) {
				sb.append("define:["); //$NON-NLS-1$
				int count = 0;
				for (String str : defineDependencies) {
					sb.append(count++ == 0 ? "" : ", ").append(str); //$NON-NLS-1$ //$NON-NLS-2$
				}
				sb.append("]"); //$NON-NLS-1$
			}
			if (requireDependencies != null) {
				sb.append(requireDependencies != null ? ", " : ""); //$NON-NLS-1$ //$NON-NLS-2$
				sb.append("require:["); //$NON-NLS-1$
				int count = 0;
				for (String str : requireDependencies) {
					sb.append(count++ == 0 ? "" : ", ").append(str); //$NON-NLS-1$ //$NON-NLS-2$
				}
				sb.append("]"); //$NON-NLS-1$
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	/**
	 * @return The string representation of the tree rooted at this node.
	 */
	public String toStringTree() {
		StringBuffer sb = new StringBuffer();
		toStringTree("", sb); //$NON-NLS-1$
		return sb.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public DepTreeNode clone() throws CloneNotSupportedException {

		// Clone this node
		DepTreeNode clone = (DepTreeNode)super.clone();

		// Clone the child nodes.
		if (children != null) {
			clone.children = new HashMap<String, DepTreeNode>();
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				DepTreeNode newNode = entry.getValue().clone();
				clone.add(newNode);
				newNode.setParent(clone);
			}
		}
		// clone the dependency array.
		if (defineDependencies != null) {
			clone.defineDependencies = Arrays.copyOf(defineDependencies, defineDependencies.length);
		}
		if (requireDependencies != null) {
			clone.requireDependencies = Arrays.copyOf(requireDependencies, requireDependencies.length);
		}
		clone.uri = uri;
		return clone;
	}

	public IConfig getConfig() {
		return getRoot().getConfig();
	}

	/**
	 * Sets the parent reference for this node using a {@link WeakReference}
	 * so as to avoid unwanted circular references which could prevent the
	 * GC from doing its job.
	 *
	 * @param parent The reference to the parent node.
	 */
	private void setParent(DepTreeNode parent) {
		if (this.name.length() == 0) {
			// A child node must have a name.
			throw new IllegalStateException();
		}
		this.parent = new WeakReference<DepTreeNode>(parent);
	}

	/**
	 * Returns the most recent last modified date for all the dependencies that
	 * are descendants of this node, including this node.
	 *
	 * @param lm
	 *            The most recent last modified date so far
	 * @return The most recent last modified date including this node and it
	 *         descendants
	 */
	private long lastModifiedDepTree(long lm) {
		long result = (defineDependencies == null && requireDependencies == null) ? lm : Math.max(lm, this.lastModifiedDep);
		if (children != null) {
			for (Entry<String, DepTreeNode> entry : this.children.entrySet()) {
				result = entry.getValue().lastModifiedDepTree(result);
			}
		}
		return result;
	}

	/**
	 * Prints the string representaton of the tree rooted at this node to the
	 * specified {@link StringBuffer}.
	 *
	 * @param indent
	 *            String for indenting nodes
	 * @param sb
	 *            The StringBuffer to output to
	 */
	private void toStringTree(String indent, StringBuffer sb) {
		sb.append(toString());
		if (children != null) {
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				entry.getValue().toStringTree(indent + "\t", sb); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Normalizes any module names in this node's dependency list
	 * (i.e. removes ./ and ../ path components) so that all the dependency
	 * names are based on this node's parent path.  For example, if a child
	 * node has the dependency {@code ../bar} and this node's parent path
	 * is {@code foo}, then the normalized dependency will be
	 * {@code foo/bar}.
	 * <p>
	 * Recursively calls itself for all this node's children so that the
	 * entire tree rooted at this node is normalized.
	 */
	void normalizeDependencies() {
		if (defineDependencies != null) {
			LinkedHashSet<String> temp =  new LinkedHashSet<String>(Arrays.asList(PathUtil.normalizePaths(getParentPath(), defineDependencies)));		// normalize and remove dups
			defineDependencies = temp.toArray(new String[temp.size()]);
		}
		if (requireDependencies != null) {
			LinkedHashSet<String> temp =  new LinkedHashSet<String>(Arrays.asList(PathUtil.normalizePaths(getParentPath(), requireDependencies)));		// normalize and remove dups
			requireDependencies = temp.toArray(new String[temp.size()]);
		}
		if (children != null) {
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				entry.getValue().normalizeDependencies();
			}
		}
	}

	/**
	 * Populates the provided map with the dependencies, keyed by
	 * full path name.  This is done to facilitate more efficient
	 * lookups of the dependencies.
	 *
	 * @param depMap
	 */
	void populateDepMap(Map<String, DependencyInfo> depMap) {
		if (uri != null) {
			depMap.put(getFullPathName(), new DependencyInfo(defineDependencies, requireDependencies, dependentFeatures, uri));
		}
		if (children != null) {
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				entry.getValue().populateDepMap(depMap);
			}
		}
	}

	/**
	 * Method called when this object is de-serialized
	 *
	 * @param in The {@link ObjectInputStream} to read from
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream in) throws IOException,
	ClassNotFoundException {

		// Call the default implementation to de-serialize our object
		in.defaultReadObject();

		parent = new WeakReference<DepTreeNode>(null);

		// restore parent reference on all children
		if (children != null) {
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				entry.getValue().setParent(this);
			}
		}
	}
}
