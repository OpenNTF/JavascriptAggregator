/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.deps;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;
import com.ibm.jaggr.service.resource.IResourceVisitor;

/**
 * This class implements the {@link Callable} interface to parse an AMD module
 * and extract the dependency information from the AMD module's define()
 * function. The dependency information thus obtained is used to update the
 * dependency array in the provided {@link DepTreeNode} and the node's
 * last-modified times are updated as well.
 * 
 * @author chuckd@us.ibm.com
 */
final class DepParser implements Callable<URI> {
	static {
		Compiler.setLoggingLevel(Level.WARNING);
	}
	
	private final DepTreeNode treeNode;
	private final IResourceVisitor.Resource resource;

	/**
	 * Object constructor
	 * 
	 * @param treeNode
	 *            The node to be populated with the dependency list obtained
	 *            from jsFile
	 * @param resource
	 *            The resource to be parsed for dependencies
	 */
	DepParser(DepTreeNode treeNode, IResourceVisitor.Resource resource) {
		this.treeNode = treeNode;
		this.resource = resource;
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	public URI call() throws Exception {
		// Save original time stamp for dependency list
		long lastModifiedDep = treeNode.lastModifiedDep();
		long lastModified = resource.lastModified();
		// Parse the javascript code
		Compiler compiler = new Compiler();
		InputStream in = resource.getInputStream();
		Node node = compiler.parse(JSSourceFile.fromInputStream(resource.getURI().toString(), in));
		in.close();
		// walk the AST for the node looking for define calls
		// and pull out the required dependency list.
		ArrayList<String> deps = DepUtils.parseDependencies(node);
		String[] depArray = (deps == null) ? 
				new String[0] : deps.toArray(new String[deps.size()]);
		/*
		 * Determine if the dependency list has changed.  We keep track of 
		 * dependency list changes separate from code changes in general
		 * because a dependency list change necessitates invalidating all
		 * cached responses for the configs that reference this file, and
		 * we want to do this only when necessary.
		 */
		if (lastModifiedDep ==  -1 || !Arrays.equals(depArray, treeNode.getDepArray())) {
			lastModifiedDep = lastModified;
		}
		// update the dependency info in the node
		treeNode.setDependencies(depArray, 
				lastModified, lastModifiedDep);
		return resource.getURI();
	}
}