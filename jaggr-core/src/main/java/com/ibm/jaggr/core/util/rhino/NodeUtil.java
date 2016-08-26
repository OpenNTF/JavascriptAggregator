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
package com.ibm.jaggr.core.util.rhino;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

/**
 * Utility methods for traversing Rhino nodes.
 */
public class NodeUtil {

	/**
	 * If the specified node represents a has() function call, then return the
	 * formal parameter of the function call if it is a string literal, and the
	 * result of the function call is used as a boolean value.
	 *
	 * @param cursor
	 *            the node specifying a has() function call
	 * @return the feature name (function argument), else null.
	 */
	public static String conditionFromHasNode(Node cursor) {
		if (cursor.getType() == Token.CALL) {
			// The node is a function or method call
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
					return null;
				}

				return arg.getString();
			}
		}
		return null;
	}

	/**
	 * If the specified node represents a define() function call, then return
	 * the node for the dependency list, else return null.
	 *
	 * @param cursor
	 *            the node for a define() function call
	 * @return the dependency list node for the define() function call, else null
	 */
	public static Node moduleDepsFromDefine(Node cursor) {
		if (cursor.getType() == Token.CALL) {
			// The node is a function or method call
			Node name;
			if ((name = cursor.getFirstChild()) != null && name.getType() == Token.NAME && // named function call
					name.getString().equals("define")) { // name is "define //$NON-NLS-1$
				/*
				 * This is a define() function call.  There are multiple variants and
				 * the dependency array can be the first or second parameter.
				 */
				Node param = name;
				for (int i = 0; i < 3 && param != null; i++) {
					param = param.getNext();
					if (param != null && param.getType() == Token.ARRAYLIT) {
						return param;
					}
				}
			}
		}
		return null;
	}

	/**
	 * If the specified node represents a require() function call, then return
	 * the node for the dependency list, else return null.
	 *
	 * @param cursor
	 *            the node for a require() function call
	 * @return the dependency list node for the require() function call, else null
	 */
	public static Node moduleDepsFromRequire(Node cursor) {
		if (cursor.getType() == Token.CALL) {
			// The node is a function or method call
			Node name = cursor.getFirstChild();
			if (name != null && name.getType() == Token.NAME && // named function call
					name.getString().equals("require")) { // name is "require" //$NON-NLS-1$
				Node param = name.getNext();
				if (param.getType() == Token.ARRAYLIT) {
					return param;
				}
			}
		}
		return null;
	}

	/**
	 * If the specified node is for a property named 'deps' and the property is
	 * a member of the object identified by <code>configVarName</code>, and the
	 * 'deps' property is being assigned an array literal, then return the node
	 * for the array literal, else return null.
	 * <p>
	 * For example, if <code>configVarName</code> is <code>require</code> and
	 * the specified node is for the 'deps' property in
	 * <code>require.deps = ["foo", "bar"];</code>, then this method will return
	 * the node for the array. Various flavors of the assignment are supported.
	 *
	 * @param cursor
	 *            the node for the 'deps' property.
	 * @param configVarName
	 *            The name of the object containing the 'deps' property.
	 * @return the node for the array being assigned to the 'deps' property, or
	 *         null.
	 */
	public static Node moduleDepsFromConfigDeps(Node cursor, String configVarName) {
		if (cursor.getType() == Token.STRING && cursor.getString().equals("deps")) { //$NON-NLS-1$
			// handle require.deps assignment of array literal
			Node parent = cursor.getParent(),
					previousSibling = parent.getChildBefore(cursor);
			if (previousSibling != null &&
					parent.getType() == Token.GETPROP &&
					parent.getParent().getType() == Token.ASSIGN &&
					(previousSibling.getType() == Token.NAME &&
					previousSibling.getString().equals(configVarName) ||
					previousSibling.getType() == Token.GETPROP &&
					previousSibling.getFirstChild().getNext().getString().equals(configVarName)) &&
					parent.getNext() != null &&
					parent.getNext().getType() == Token.ARRAYLIT) {
				// require.deps = [...];
				return parent.getNext();
			} else if (parent.getType() == Token.OBJECTLIT &&
					parent.getParent().getType() == Token.ASSIGN &&
					(parent.getParent().getFirstChild().getType() == Token.NAME &&
					parent.getParent().getFirstChild().getString().equals(configVarName) ||
					parent.getParent().getFirstChild().getType() == Token.GETPROP &&
					parent.getParent().getFirstChild().getFirstChild().getNext().getString().equals(configVarName)) &&
					cursor.getFirstChild() != null &&
					cursor.getFirstChild().getType() == Token.ARRAYLIT) {
				// require = { deps: [...] }
				return cursor.getFirstChild();
			} else if (parent.getType() == Token.OBJECTLIT &&
					parent.getParent().getType() == Token.NAME &&
					parent.getParent().getString().equals(configVarName) &&
					parent.getParent().getParent().getType() == Token.VAR &&
					cursor.getFirstChild() != null &&
					cursor.getFirstChild().getType() == Token.ARRAYLIT) {
				// var require = { deps: [...] }
				return cursor.getFirstChild();
			} else if (parent.getType() == Token.OBJECTLIT &&
					parent.getParent().getType() == Token.STRING &&
					parent.getParent().getString().equals(configVarName) &&
					parent.getParent().getParent().getType() == Token.OBJECTLIT &&
					cursor.getFirstChild() != null &&
					cursor.getFirstChild().getType() == Token.ARRAYLIT) {
				// require: { deps: [...] }
				return cursor.getFirstChild();
			}
		}
		return null;
	}
}
