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

package com.ibm.jaggr.core.deps;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.jaggr.core.impl.deps.BooleanFormula;
import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.BooleanVar;


/**
 * Encapsulates dependency meta-data for an AMD module. This includes
 * has! plugin conditionals used in has! plugin branching during require list
 * expansion and optional trace comments.
 */
public class ModuleDepInfo implements Serializable {
	
	private static final long serialVersionUID = 8798463630504113388L;

	/**
	 * Boolean formula representing the has! plugin expressions used to refer to
	 * the module associated with this object.  If null, then the module is 
	 * referenced un-conditionally.
	 */
	private BooleanFormula formula;
	
	/**
	 * Map of boolean terms to comments depicting require expansion trace data.
	 * {@link BooleanTerm#emptyTerm} is used as the map key when {@link #formula}
	 * is null (TRUE).  
	 */
	private Map<BooleanTerm, String> comments = null;
	
	private String pluginName = null;
	
	/**
	 * If true, the plugin name is declared in a has! plugin expression
	 * referencing this module. Otherwise, the plugin name is derived though has
	 * plugin branching. Derived names are replaced with declared names when
	 * combining instances of this class using {@link #add(ModuleDepInfo)}
	 */
	private boolean isPluginNameDeclared = false;
	
	public ModuleDepInfo() {
		this(null, null, null);
	}
	
	/**
	 * @param pluginName
	 *            The plugin name. May be null if term is null.
	 * @param term
	 *            The has! plugin expression used to refer to the module
	 *            associated with this object, or null if the module is referred
	 *            to directly.
	 * @param comment
	 *            Optional comment string that can be retrieved using {
	 *            {@link #getComment()}
	 */
	public ModuleDepInfo(String pluginName, BooleanTerm term, String comment) {
		this(pluginName, term, comment, false);
	}
	
	/**
	 * @param pluginName
	 *            The plugin name. May be null if term is null.
	 * @param term
	 *            The has! plugin expression used to refer to the module
	 *            associated with this object, or null if the module is referred
	 *            to directly.
	 * @param comment
	 *            Optional comment string that can be retrieved using {
	 *            {@link #getComment()}
	 * @param isPluginNameDeclared
	 *            True if the plugin name is declared in a has! plugin
	 *            expression referencing this module. Otherwise, the plugin name
	 *            is derived though has plugin branching. Derived names are
	 *            replaced with declared names when combining instances of this
	 *            class using {@link #add(ModuleDepInfo)}
	 */
	public ModuleDepInfo(String pluginName, BooleanTerm term, String comment, boolean isPluginNameDeclared) {
		if (term != null) {
			formula = new BooleanFormula();
			if (term.size() > 0) {
				formula.add(term);
			}
			if (pluginName == null) {
				throw new NullPointerException();
			}
			this.pluginName = pluginName;
			this.isPluginNameDeclared = isPluginNameDeclared;
		} else {
			formula = new BooleanFormula(true);
		}
		if (comment != null) {
			comments = new LinkedHashMap<BooleanTerm, String>();
			comments.put(term == null ? BooleanTerm.emptyTerm : term, comment);
		}		
	}
	
	/**
	 * A return value of null means that the associated module should be
	 * included in the expanded dependency list unconditionally. A return value
	 * consisting of an empty list means that it should not be included at all.
	 * <p>
	 * If the list is not empty, then the list elements are the has! plugin
	 * prefixes that should be used with this module. One module id per list
	 * entry specifying the same module name should be used.
	 * <p>
	 * This method has the side effect of simplifying the boolean formula.
	 * 
	 * @return The list of has! plugin prefixes for this module.
	 */
	public Collection<String> getHasPluginPrefixes() {
		if (formula.isTrue()) {
			return null;
		}
		if (formula.isFalse()) {
			return Collections.emptySet();
		}
		Set<String> result = new TreeSet<String>();
		for (BooleanTerm term : formula) {
			StringBuffer sb = new StringBuffer(pluginName).append("!"); //$NON-NLS-1$
			for (BooleanVar featureVar : term) {
				sb.append(featureVar.name).append("?"); //$NON-NLS-1$
				if (!featureVar.state) {
					sb.append(":"); //$NON-NLS-1$
				}
			}
			result.add(sb.toString());
		}
		return result;
	}
	
	/**
	 * Returns true if the specified term is logically included by the formula
	 * for this object.
	 * <p>
	 * A term logically includes another term if it evaluates to the same result
	 * for all vars within the term. For example, the term A contains (A*B), but
	 * not vice-versa.
	 * 
	 * @param term
	 *            The term to test for logical inclusion
	 * @return True if the formula logically includes the specified term
	 */
	public boolean containsTerm(BooleanTerm term) {
		if (formula.isTrue() || term != null && term.equals(BooleanTerm.emptyTerm)) {
			return true;
		}
		if (term != null) {
			for (BooleanTerm termToTest : formula) {
				if (term.containsAll(termToTest)) {
					return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * @return An Iterable for the terms in the formula or zero if formula is
	 *         null
	 */
	public Iterable<BooleanTerm> getTerms() {
		return formula.isTrue() ? null : formula;
	}
	
	/**
	 * @return The number of terms in the formula or zero if formula is null
	 */
	public int size() {
		return formula.size();
	}
	
	/**
	 * Returns the comment string associated with the term that has the 
	 * fewest number of vars.
	 * 
	 * @return The comment string
	 */
	public String getComment() {
		String result = null;
		if (comments != null) {
			if (formula.iterator() == null) {
				result = comments.get(BooleanTerm.emptyTerm);
			} else {
				// return the comment associated with the first lowest 
				//  (least number of vars) term.
				BooleanTerm firstLowest = null;
				for (BooleanTerm term : comments.keySet()) {
					if (firstLowest == null) {
						firstLowest = term;
					} else if (term.size() < firstLowest.size()) {
						firstLowest = term;
					}
				}
				if (firstLowest != null) {
					result = comments.get(firstLowest);
				}
			}
		}
		return result;
	}

	/**
	 * Adds the terms and comments from the specified object to this object.
	 * 
	 * @param other
	 *            The object to add
	 * @return True if this object was modified
	 */
	public boolean add(ModuleDepInfo other) {
		Boolean modified = false;
		if (formula.isTrue()) {
			String comment = other.getComment();
			if (getComment() == null && 
					other.formula.isTrue() && 
					comment != null) {
				modified |= setComment(comment, BooleanTerm.emptyTerm);
			}
			return modified;
		}
		if (other.formula.isTrue()) {
			modified = !formula.isTrue();
			formula = new BooleanFormula(true);
			String comment = other.getComment();
			if (comment != null) {
				modified |= setComment(comment, BooleanTerm.emptyTerm);
			}
			return modified;
		}
		modified = formula.addAll(other.formula);
		if (!isPluginNameDeclared && other.isPluginNameDeclared) {
			pluginName = other.pluginName;
			isPluginNameDeclared = true;
			modified = true;
		}
		if (other.comments != null) {
			for (Map.Entry<BooleanTerm, String> entry : other.comments.entrySet()) {
				modified |= setComment(entry.getValue(), entry.getKey());
			}
		}
		return modified;
	}
	
	/**
	 * Logically subtracts the boolean terms in the specified object from the
	 * boolean formula in this object. This means that if a term from the
	 * specified object logically includes a term from this object, then the
	 * term from this object will be removed.
	 * <p>
	 * A term logically includes another term if it evaluates to the same result
	 * for all vars within the term. For example, the term A contains (A*B), but
	 * not vice-versa.
	 * 
	 * @param toSub
	 *            The object to subtract from this object
	 * @return True if this object was modified
	 */
	public boolean subtract(ModuleDepInfo toSub) {
		boolean modified = false;
		if (toSub.formula.isTrue()) {
			modified = !formula.isFalse();
			formula.clear();
			if (comments != null) {
				comments.clear();
			}
		} else if (!formula.isTrue()) {
			Set<BooleanTerm> termsToRemove = new HashSet<BooleanTerm>();
			for (BooleanTerm t1 : formula) {
				for (BooleanTerm t2 : toSub.formula) {
					if (t1.containsAll(t2)) {
						termsToRemove.add(t1);
					}
				}
			}
			modified |= formula.removeTerms(termsToRemove);
			if (modified && comments != null) {
				for (BooleanTerm termToRemove : termsToRemove) {
					comments.remove(termToRemove);
				}
			}
		}
		return modified;
	}
	
	/**
	 * Simplifies the boolean formula
	 */
	public void simplify() {
		formula = formula.simplify();
	}
	
	/**
	 * Simplifies the formula only if it is invariant.  If not
	 * invariant, the terms in the formula remain unchanged.
	 */
	public void simplifyInvariants() {
		BooleanFormula test = formula.simplify();
		if (test.isTrue() || test.isFalse()) {
			formula = test;
		}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return formula != null ? formula.toString() : "TRUE"; //$NON-NLS-1$
	}

	/**
	 * Associates the specified comment string with the specified term.
	 * @param comment The comment string
	 * @param term The term to associate with the comment string
	 * @return true if this object was modified
	 */
	private boolean setComment(String comment, BooleanTerm term) {
		if (comment == null) {
			return false;
		}
		Boolean modified = false;
		if (comments == null) {
			comments = new LinkedHashMap<BooleanTerm, String>();
			modified = true;
		}
		if (term.equals(BooleanTerm.emptyTerm)) {
			modified |= comments.size() != 0;
			comments.clear();
		}
		if (!comments.containsKey(term)) {
			comments.put(term, comment);
			modified = true;
		}
		return modified;
	}
	
}
