/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.modulebuilder.javascript;

import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class ExportModuleNameCompilerPass implements CompilerPass {

	/* (non-Javadoc)
	 * @see com.google.javascript.jscomp.CompilerPass#process(com.google.javascript.rhino.Node, com.google.javascript.rhino.Node)
	 */
	@Override
	public void process(Node externs, Node root) {
		processChildren(root);
	}
	
	/**
	 * Recursively called to process AST nodes looking for anonymous define calls. If an
	 * anonymous define call is found, then change it be a named define call, specifying
	 * the module name for the file being processed.
	 * 
	 * @param node
	 *            The node being processed
	 */
	public void processChildren(Node node) {
		for (Node cursor = node.getFirstChild(); cursor != null; cursor = cursor.getNext()) {
		if (cursor.getType() == Token.CALL) {
			// The node is a function or method call
			Node name = cursor.getFirstChild();
			if (name != null && name.getType() == Token.NAME && // named function call
					name.getString().equals("define")) { // name is "define" //$NON-NLS-1$
				Node param = name.getNext();
				if (param != null && param.getType() != Token.STRING) {
					param.getParent().addChildBefore(
							Node.newString(
									name.getProp(Node.SOURCENAME_PROP).toString()), 
									param
							);
				}
			}
		}
		// Recursively call this method to process the child nodes
		if (cursor.hasChildren())
			processChildren(cursor);
		}
	}
}
