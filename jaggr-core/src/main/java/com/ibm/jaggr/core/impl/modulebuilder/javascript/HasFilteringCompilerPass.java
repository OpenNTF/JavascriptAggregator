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

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.ibm.jaggr.core.util.Features;

/**
 * Custom Compiler pass for Google Closure compiler to do has trimming.  Must be called before
 * the optimizaton pass since we rely on the optimizer to remove dead branches resulting from 
 * the replacement of has-conditionals with TRUE or FALSE nodes.
 */
public class HasFilteringCompilerPass implements CompilerPass {
	
	private Features features; 		// list of provided has condition/value pairs
	private Set<String> discoveredHasConditions;
	private boolean coerceUndefinedToFalse;
	
	private static final Logger log = Logger.getLogger(HasFilteringCompilerPass.class.getName());
	
	public HasFilteringCompilerPass(Features features, Set<String> discoveredHasConditions, boolean coerceUndefinedToFalse) {
		this.features = (Features) (features != null ? features : Features.emptyFeatures);
		this.discoveredHasConditions = discoveredHasConditions;
		this.coerceUndefinedToFalse = coerceUndefinedToFalse;
	}
	
	public HasFilteringCompilerPass(Features features, boolean coerceUndefinedToFalse) {
		this(features, null, coerceUndefinedToFalse);
	}
	
	public HasFilteringCompilerPass(Features features, Set<String> discoveredHasConditions) {
		this(features, discoveredHasConditions, false);
	}
	
	public HasFilteringCompilerPass(Features features) {
		this(features, null, false);
	}
	
	public Iterable<String> getDiscoveredHasConditions() {
		return discoveredHasConditions;
	}
	
	@Override
	public void process(Node externs, Node root) {
		if (features != null && !features.featureNames().isEmpty() || discoveredHasConditions != null || coerceUndefinedToFalse) {
			processChildren(root);
		}
	}

	/*
	 * This method walks the AST looking for has calls.  If the condition being tested
	 * by the has call is specified in the features, then replace the node for the call in 
	 * the AST with a new node for a literal true/false.  We rely on the optimizer to 
	 * detect and remove any resulting dead branches.
	 */
	private void processChildren(Node n) {
		for (Node cursor = n.getFirstChild(); cursor != null; cursor = cursor.getNext()) {
			if (cursor.getType() == Token.CALL) {
				Node name, arg;
				if ((name = cursor.getFirstChild()) != null && 
					name.getType() == Token.NAME && 		// named function call
					name.getString().equals("has") && 		// name is "has" //$NON-NLS-1$
					(arg = name.getNext()) != null && 		
					arg.getType() == Token.STRING && 		// first param is a string literal
					arg.getNext() == null) 					// only one param
				{
					
					// Ensure that the result of the has function is treated as a 
					// boolean expression.  This is necessary to avoid problems
					// with code similar to "if (has("ieVersion") < 6)"
					Node parent = cursor.getParent();
					int type = parent.getType();
					switch (type) {
					case Token.IF:
					case Token.HOOK:
					case Token.AND:
					case Token.OR:
					case Token.NOT:
						// these implicitly coerce the result of the function call
						// to boolean so we don't need to do anything else
						break;
						
					default:
						// Replacing the function call with a boolean value might not
						// have the desired effects if the code treats the result of
						// the function as a non-boolean, so don't do anything.
						return;
					}
					
					String hasCondition = arg.getString();
					if (discoveredHasConditions != null) {
						discoveredHasConditions.add(hasCondition);
					}
					Node newNode = null;
					if (features.contains(hasCondition)) {
						// features contains the condition being tested.  Replace the call
						//  with a literal true/false
						newNode = new Node(features.isFeature(hasCondition) ? Token.TRUE : Token.FALSE);
						if (log.isLoggable(Level.FINEST))
							log.finest("Replaced has call for \"" + hasCondition + "\" with " + Boolean.toString(features.isFeature(hasCondition))); //$NON-NLS-1$ //$NON-NLS-2$
					} else if (this.coerceUndefinedToFalse) {
						// Not in features means false. 
						newNode = new Node(Token.FALSE);
						if (log.isLoggable(Level.FINEST))
							log.finest("Replaced has call for undefined \"" + hasCondition + "\" with false"); //$NON-NLS-1$ //$NON-NLS-2$
					}
					if (newNode != null) {
						cursor.getParent().replaceChild(cursor, newNode);
						cursor = newNode;
					}
				}
			}
			if (cursor.hasChildren())
				processChildren(cursor);
		}
	}
}
