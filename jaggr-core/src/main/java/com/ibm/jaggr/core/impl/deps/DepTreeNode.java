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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.BooleanVar;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.HasNode;
import com.ibm.jaggr.core.util.PathUtil;

/**
 * This class is the node object for a module dependency. Nodes are organized in
 * a tree structure according to the module naming hierarchy. For example, the
 * node for the module foo/bar has the name bar and is a child of the node for
 * the module with name foo. A node can be a directory node with children, or a
 * file node with a dependency list, or both. The reason that a node can have
 * both children and a dependency list is that the names of javascript modules
 * omit the .js extension, so a directory can have both a child directory named
 * bar and a javascript module named bar (named bar.js on the file system).
 * <p>
 * When a node tree is resolved by calling {@link #resolveDependencyRefs()} on
 * the root node of the tree, then each node with a dependency list will also
 * contains an array of {@link DepRef} objects where each member of the array
 * corresponds to the name in the dependency list at the same index and
 * represents a reference to the {@code DepTreeNode} in the same tree for the
 * named dependency.
 * 
 */
public class DepTreeNode implements Cloneable, Serializable {
	private static final long serialVersionUID = 5598497628602046531L;

	static final Logger log = Logger.getLogger(DepTreeNode.class.getName());

	/** regular expression for detecting if a plugin name is the has! plugin */
	static final Pattern hasPattern = Pattern.compile("(^|\\/)has$"); //$NON-NLS-1$

	/** Map of name/child-node pairs for this node */
	private Map<String, DepTreeNode> children;
	/** The name of this node */
	private String name;
	/** The list of module name dependencies for this node if it is a module */
	private String[] dependencies;
	/** The file last modified date if this node is for a module */
	private long lastModified = -1;
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

	/**
	 * List of {@link DepRef} objects, each of which encapsulates a
	 * {@code DepTreeNode} reference to a node in the same tree for the module
	 * named in {@link #dependencies} at the same index location. Initialized by
	 * {@link #resolveDependencyRefs()}. List members in a resolved tree will
	 * never by null, although the reference returned by the {@link DepRef} may
	 * be null if the named dependency could not be located in the tree.
	 */
	private transient List<WeakReference<DepTreeNode>> depRefs;

	/**
	 * Object constructor. Creates a node with the given name.
	 * 
	 * @param name
	 *            The module name. Must not contain '/' characters.
	 */
	public DepTreeNode(String name) {
		if (name.contains("/")) { //$NON-NLS-1$
			throw new IllegalArgumentException(name);
		}
		children = null;
		dependencies = null;
		parent = new WeakReference<DepTreeNode>(null);
		this.name = name;
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
	 * @return The dependency array
	 */
	public String[] getDepArray() {
		return dependencies;
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
	 * @param child
	 */
	public void overlay(DepTreeNode node) {
		if (node.getDepArray() != null) {
			setDependencies(node.getDepArray(), node.lastModified(), node.lastModifiedDep());
		}
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
	 * @return The node at the specified path.
	 */
	public DepTreeNode createOrGet(String path) {
		if (path.startsWith("/")) { //$NON-NLS-1$
			throw new IllegalArgumentException(path);
		}
		if (path.length() == 0)
			return this;
		
		String[] pathComps = path.split("/"); //$NON-NLS-1$
		DepTreeNode node = this;
		for (String comp : pathComps) {
			DepTreeNode childNode = node.getChild(comp);
			if (childNode == null) {
				childNode = new DepTreeNode(comp);
				node.add(childNode);
			}
			node = childNode;
		}
		return node;
	}
	
	/**
	 * @param path
	 *            The node with the specified path relative to this node, or
	 *            null
	 * @return
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
	 * Recursively walks the node tree removing nodes that have neither
	 * child nodes or specify dependencies.
	 */
	public void prune() {
		if (children != null) {
			List<String> removeList = new ArrayList<String>();
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				DepTreeNode child = entry.getValue();
				child.prune();
				if ((child.dependencies == null)
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
	 * @param dependencies
	 *            The dependency list of module names
	 * @param lastModifiedFile
	 *            The last modified date of the javascript source file
	 * @param lastModifiedDep
	 *            The last modified date of the dependency list. See
	 *            {@link #lastModifiedDep()}
	 */
	public void setDependencies(String[] dependencies, long lastModifiedFile, long lastModifiedDep) {
		this.dependencies = dependencies;
		this.lastModified = lastModifiedFile;
		this.lastModifiedDep = lastModifiedDep;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		if (dependencies != null) {
			sb.append(getFullPathName()).append(" = ").append("["); //$NON-NLS-1$ //$NON-NLS-2$
			int count = 0;
			for (String str : dependencies) {
				sb.append(count++ == 0 ? "" : ", ").append(str); //$NON-NLS-1$ //$NON-NLS-2$
			}
			sb.append("]").append('\n'); //$NON-NLS-1$
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
		if (dependencies != null) {
			clone.dependencies = Arrays.copyOf(dependencies, dependencies.length);
		}
		/* 
		 * Dependency references need to be recalculated on cloned trees after the
		 * entire tree has been cloned.
		 */
		depRefs = null;
		
		return clone;
	}
	
	void resolveDependencyRefs() {
		if (dependencies != null) {
			depRefs = new ArrayList<WeakReference<DepTreeNode>>();
			for (int i = 0; i < dependencies.length; i++) {
				String depname = dependencies[i];
				depRefs.add(new WeakReference<DepTreeNode>(
						depname.contains("!") ? null : getRoot().getDescendent(depname)));  //$NON-NLS-1$
						
			}
		}
		if (children != null) {
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				entry.getValue().resolveDependencyRefs();
			}
		}
	}

	/**
	 * Returns the expanded set of module dependencies for this node. The
	 * expanded dependencies are the modules declared in this node's dependency
	 * list, plus the dependencies declared by each of those modules, and so on.
	 * The returned list of module dependencies are unique (no duplicates).
	 * <p>
	 * The order of the modules declared in the returned map is guaranteed to
	 * be the same as the order that the dependencies are declared in the
	 * define() function in the source file for this node's module, but the
	 * order of the additional expanded dependencies is undefined.
	 * <p>
	 * If a feature set is specified, then any conditional dependencies
	 * specified using the has plugin (e.g.
	 * {@code has!feature-name?modulea:moduleb)} will be expanded according to
	 * the set of features provided.
	 * 
	 * @param features
	 *            The features defined in the request.
	 *            Use for resolving dependencies specified using the has! plugin.
	 * @param dependentFeatures
	 *            Collections of features that this compiled build depends on
	 * @param includeComments
	 *            True if the resulting dependencies should include comment
	 *            strings for debugging.
	 * @return The map of expanded dependencies/debug comment pairs.
	 */
	public ModuleDeps getExpandedDependencies(
			Features features,
			Set<String> dependentFeatures, 
			boolean includeComments,
			boolean performHasBranching) throws IOException {
		
		// Create the Dependencies map.  Use a LinkedHashMap to maintain ordering
		// of dependencies as some types of modules (i.e. css) are sensitive to 
		// the order that modules are required relative to one another.
		ModuleDeps deps = new ModuleDeps();
		if (dependencies != null) {
			expandDependencies(deps, features, dependentFeatures, includeComments, performHasBranching, null, null);
			deps.keySet().removeAll(IDependencies.excludes);
		}
		return deps;
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
		long result = (dependencies == null) ? lm : Math.max(lm, this.lastModifiedDep);
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
	 * Recursively called to follow dependency references, accumulating 
	 * module dependency info representing the requested set of
	 * expanded dependencies.
	 * 
	 * @param result
	 *            {@link ModuleDeps} object containing the mapping of 
	 *            module-name to {@link ModuleDepinfo} pairs. Newly discovered 
	 *            dependencies are added to this map.
	 * @param features
	 *            The features defined in the request.
	 *            Used to resolve dependencies specified using the has! plugin.
	 * @param dependentFeatures
	 *            Collections of features that this compiled build depends on
	 * @param includeComments
	 *            True if debug comments should be included. If false, the map
	 *            valueSet will be a set of empty strings.
	 * @param performHasBranching
	 *            True if has! loader plugin branching should be performed
	 * @param term
	 *            The boolean term representing the accumulated has! plugin 
	 *            branching variables for the current dependency.  May be null
	 *            if not terms have been accumulated or if 
	 *            <code>performHasBranching</code> is false.
	 * @param hasPluginName
	 *            The name of the has! plugin module that initiated 
	 *            <code>term</code>
	 */
	private void expandDependencies(
			ModuleDeps result,
			Features features,
			Set<String> dependentFeatures,
			boolean includeComments,
			boolean performHasBranching,
			BooleanTerm term, String hasPluginName) throws IOException {
		if (dependencies != null) {
			if (depRefs == null) {
				// Module dependencies must be resolved by calling resolveDepenedencyRefs() 
				// before you can call getExpandedDependencies()
				throw new NullPointerException();
			}
			List<ChildNode> childNodes = new ArrayList<ChildNode>(dependencies.length);
			for (int i = 0; i < dependencies.length; i++) {
				// provide a StringBuffer for comments if requested
				StringBuffer comment = (includeComments ? new StringBuffer(MessageFormat.format(
							Messages.DepTreeNode_0,
							new Object[]{getFullPathName()}
				)) : null);
				// Get the reference to the DepTreeNode for this dependency
				// (can be null)
				String depName = dependencies[i];
				DepTreeNode node = depRefs.get(i).get();

				String resolved = getConfig().resolve(depName, features, dependentFeatures, comment, true);
				if (resolved != depName) {
					depName = resolved;
					node = getRoot().getDescendent(depName);
				}
				if (depName.length() > 0) {
					int idx = depName.indexOf("!"); //$NON-NLS-1$
					if (node != null && idx == -1) {
						// We need to check for containment here in order to avoid 
						// infinite recursion resulting from circular dependencies.
						if (!result.containsDep(depName, term)) {
							childNodes.add(new ChildNode(node, term, hasPluginName));
							if (comment != null) {
								comment.append(", ").append(Messages.DepTreeNode_1); //$NON-NLS-1$
							}
							result.add(depName, new ModuleDepInfo(hasPluginName, term, comment != null ? comment.toString() : null));
						}
					} else if (performHasBranching && 
							idx > 0 && 
							hasPattern.matcher(depName.substring(0, idx)).find()) {
						// Handle has plugin
						String pluginName = getConfig().resolve(depName.substring(0, idx), features, dependentFeatures, comment, true);
						HasNode hasNode = new HasNode(depName.substring(idx+1));
						ModuleDeps deps = hasNode.evaluateAll(
								pluginName, 
								features, 
								dependentFeatures, 
								term,
								comment != null ? 
									comment.toString() : 
									null
						);
						for (Map.Entry<String, ModuleDepInfo> entry : deps.entrySet()) {
							DepTreeNode depNode = getRoot().getDescendent(entry.getKey());
							if (depNode != null) {
								for (BooleanTerm depTerm : entry.getValue().getTerms()) {
									if (term != null) {
										Set<BooleanVar> vars = new HashSet<BooleanVar>(depTerm);
										vars.addAll(term);
										depTerm = new BooleanTerm(vars);
									}
									// We need to check for containment here in order to avoid 
									// infinite recursion resulting from circular dependencies.
									if (!result.containsDep(depNode.getFullPathName(), depTerm)) {
										childNodes.add(new ChildNode(depNode, depTerm, pluginName));
									}
								}
							}
						}
						result.addAll(deps);
					} else {
						if (comment != null) {
							comment.append(", ").append(Messages.DepTreeNode_2); //$NON-NLS-1$
						}
						result.add(depName, new ModuleDepInfo(hasPluginName, term, comment != null ? comment.toString() : null));
					}
				}
				// If depName specifies a plugin, then add the plugin module to the expanded
				// dependencies
				int idx = depName.indexOf("!"); //$NON-NLS-1$
				if (idx != -1 && idx > 0 && !hasPattern.matcher(depName.substring(0, idx)).find()) {
					depName = getConfig().resolve(depName.substring(0, idx), features, dependentFeatures, comment, true);
					if (!result.containsDep(depName, term)) {
						// We need to check for containment here in order to avoid 
						// infinite recursion resulting from circular dependencies.
						node = getRoot().getDescendent(depName);
						if (node != null) {
							result.add(depName, new ModuleDepInfo(hasPluginName, term, includeComments ? Messages.DepTreeNode_4 : null));
							childNodes.add(new ChildNode(node, term, hasPluginName));
						}
					}
				}
			}
			for (ChildNode child : childNodes) {
				child.node.expandDependencies(result, features, dependentFeatures, includeComments, performHasBranching, child.term, child.hasPluginName);
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
		if (dependencies != null) {
			dependencies = PathUtil.normalizePaths(getParentPath(), dependencies);
		}
		if (children != null) {
			for (Entry<String, DepTreeNode> entry : children.entrySet()) {
				entry.getValue().normalizeDependencies();
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
	
	private class ChildNode {
		final DepTreeNode node;
		final BooleanTerm term;
		final String hasPluginName;
		
		ChildNode(DepTreeNode node, BooleanTerm term, String hasPluginName) {
			this.node = node;
			this.term = term;
			this.hasPluginName = hasPluginName;
		}
	}
}
