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

import com.ibm.jaggr.core.util.BooleanFormula;
import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.BooleanVar;
import com.ibm.jaggr.core.util.Features;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;


/**
 * Encapsulates dependency meta-data for an AMD module. This includes
 * has! plugin conditionals used in has! plugin branching during require list
 * expansion and optional trace comments.
 */
public class ModuleDepInfo implements Serializable {

	private static final long serialVersionUID = 3224914379637472034L;

	/**
	 * Boolean formula representing the has! plugin expressions used to refer to
	 * the module associated with this object.
	 */
	private BooleanFormula formula;

	/**
	 * Map of boolean terms to comments depicting require expansion trace data.
	 * {@link BooleanTerm#emptyTerm} is used as the map key when {@link #formula}
	 * is null (TRUE).
	 */
	private String comment = null;

	int commentTermSize = 0;

	private String pluginName = null;

	/**
	 * If true, the plugin name is declared in a has! plugin expression
	 * referencing this module. Otherwise, the plugin name is derived though has
	 * plugin branching. Derived names are replaced with declared names when
	 * combining instances of this class using {@link #add(ModuleDepInfo)}
	 */
	private boolean isPluginNameDeclared = false;

	public ModuleDepInfo() {
		this(null, BooleanTerm.TRUE, null);
	}

	/**
	 * Copy constructor
	 *
	 * @param other
	 */
	public ModuleDepInfo(ModuleDepInfo other) {
		this(other, null);
	}

	/**
	 * Creates an instance of this class with the same properties as the
	 * specified instance except that the specified comment string is associated
	 * with each of the terms in the new instance
	 *
	 * @param other
	 *            the instance who's properties will be used to initialize this
	 *            object
	 * @param comment
	 *            the comment to associate with each of the terms in the new
	 *            object, or null if the comments of {@code other} should be
	 *            preserved in the new object.
	 */
	public ModuleDepInfo(ModuleDepInfo other, String comment) {
		pluginName = other.pluginName;
		formula = new BooleanFormula(other.formula);
		isPluginNameDeclared = other.isPluginNameDeclared;
		this.comment = (comment != null) ? comment : other.comment;
		commentTermSize = other.commentTermSize;
		simplifyInvariant();
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
		if (term == null || term.isTrue()) {
			formula = new BooleanFormula(true);
		} else if (term.isFalse()) {
			formula = new BooleanFormula(false);
		} else {
			formula = new BooleanFormula();
			if (term.size() > 0) {
				formula.add(term);
			}
			if (pluginName == null) {
				throw new NullPointerException();
			}
			this.pluginName = pluginName;
			this.isPluginNameDeclared = isPluginNameDeclared;
		}
		if (comment != null) {
			this.comment = comment;
			commentTermSize = term != null ? term.size() : 0;
		}
		simplifyInvariant();
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
	 * {@link TreeSet} is used to obtain predictable ordering of terms in
	 * compound has conditionals, mostly for unit tests.
	 *
	 * @return The list of has! plugin prefixes for this module.
	 */
	public Collection<String> getHasPluginPrefixes() {
		formula = formula.simplify();
		if (formula.isTrue()) {
			return null;
		}
		if (formula.isFalse()) {
			return Collections.emptySet();
		}
		Set<String> result = new HashSet<String>();
		for (BooleanTerm term : formula) {
			StringBuffer sb = new StringBuffer(pluginName).append("!"); //$NON-NLS-1$
			for (BooleanVar featureVar : new TreeSet<BooleanVar>(term)) {
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
	 * Put another way, this method returns true if adding the specified term
	 * to the formula for this object does not change the truth table for
	 * the formula.
	 *
	 * @param term
	 *            The term to test for logical inclusion
	 * @return True if the formula logically includes the specified term
	 */
	public boolean containsTerm(BooleanTerm term) {
		simplifyInvariant();
		if (term.isFalse() || formula.isTrue()) {
			// If the term is false, then adding it won't change the formula
			// Similarly if the formula is true.  No additional term added
			// will make it not true.
			return true;
		}
		if (term.isTrue()) {
			// answer has to be false because we are here only if formula.isTrue() is false
			return false;
		}
		for (BooleanTerm termToTest : formula) {
			// check for true terms in case formula hasn't been simplified.
			if (termToTest.isTrue()) {
				// A true term means the formula is true, and a true
				// formula contains all terms.
				return true;
			}
			if (term.containsAll(termToTest)) {
				// If a term in the formula includes all the vars that are
				// in the term we are testing, then the term is included
				// in the formula.
				return true;
			}
		}
		return false;
	}

	/**
	 * logically ands the provided terms with the formula belonging to this
	 * object, updating this object with the result.
	 *
	 * @param other
	 *            the {@link ModuleDepInfo} to and with this object.
	 * @return this object
	 */
	public ModuleDepInfo andWith(ModuleDepInfo other) {
		if (other == null) {
			return this;
		}
		formula.andWith(other.formula);
		if (!isPluginNameDeclared && other.isPluginNameDeclared) {
			pluginName = other.pluginName;
			isPluginNameDeclared = true;
		} else if (pluginName == null) {
			pluginName = other.pluginName;
			isPluginNameDeclared = other.isPluginNameDeclared;
		}
		simplifyInvariant();
		return this;
	}

	/**
	 * Returns the comment string associated with the term that has the
	 * fewest number of vars.
	 *
	 * @return The comment string
	 */
	public String getComment() {
		return comment;
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
			// No terms added to a true formula will change the evaluation.
			return false;
		}
		if (other.formula.isTrue()) {
			// Adding true to this formula makes this formula true.
			modified = !formula.isTrue();
			formula = new BooleanFormula(true);
			if (modified && other.comment != null && other.commentTermSize < commentTermSize) {
				comment = other.comment;
				commentTermSize = other.commentTermSize;
			}
			return modified;
		}
		// Add the terms
		modified = formula.addAll(other.formula);

		// Copy over plugin name if needed
		if (!isPluginNameDeclared && other.isPluginNameDeclared) {
			pluginName = other.pluginName;
			isPluginNameDeclared = true;
			modified = true;
		}
		// And comments...
		if (other.comment != null && other.commentTermSize < commentTermSize) {
			comment = other.comment;
			commentTermSize = other.commentTermSize;
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
			// Subtracting true makes this formula false
			modified = !formula.isFalse();
			formula = new BooleanFormula(false);
		} else if (formula.isTrue()) {
			// Subtracting anything from true (other than true)
			// has no effect
			return false;
		} else {
			// Remove terms from this formula that contain the same vars
			// (or a superset of the same vars) as terms in the formula
			// being subtracted.  For example, if toSub.formula contains
			// the term (A*B), then the terms (A*B) and (A*B*C) would be
			// removed from this formula, but the term (A) would not.
			Set<BooleanTerm> termsToRemove = new HashSet<BooleanTerm>();
			for (BooleanTerm t1 : formula) {
				for (BooleanTerm t2 : toSub.formula) {
					if (t1.containsAll(t2)) {
						termsToRemove.add(t1);
					}
				}
			}
			modified |= formula.removeTerms(termsToRemove);
			if (formula.size() == 0) {
				formula = new BooleanFormula(false);
			}
		}
		if (formula.isFalse()) {
			comment = null;
			commentTermSize = 0;
		}
		simplifyInvariant();
		return modified;
	}

	/**
	 * Resolves the formula using the variable values provided
	 * in <code>features</code>
	 *
	 * @param features the variable values to resolve with
	 */
	public void resolveWith(Features features) {
		formula.resolveWith(features);
		simplifyInvariant();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return formula != null ? formula.toString() : "TRUE"; //$NON-NLS-1$
	}

	/**
	 * Simplify the formula and replace the existing formula with the simplified
	 * equivalent only if it is invariant (evaluates to true of false).
	 * <p>
	 * Note that we don't replace non-invariants with the simplified version
	 * of the expression because keeping the terms in their original form
	 * makes it easier to match terms when subtracting.
	 */
	private void simplifyInvariant() {
		BooleanFormula simplified = formula.simplify();
		if  (simplified.isTrue() || simplified.isFalse() || simplified.equals(formula)) {
			formula = simplified;
		}
		if (formula.isTrue() || formula.isFalse()) {
			pluginName = null;
			isPluginNameDeclared = false;
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	/**
	 * Returns true if this module is equal to the specified module
	 * (ignoring comments)
	 */
	@Override
	public boolean equals(Object otherObj) {
		if (this == otherObj) {
			return true;
		}
		if (otherObj != null && otherObj.getClass().getName().equals(ModuleDepInfo.class.getName())) {
			ModuleDepInfo other = (ModuleDepInfo)otherObj;
			return formula.equals(other.formula) &&
					// If forumla is invariant, then they're equal regardless of the plugin name
					(formula.isFalse() || formula.isTrue() ||
					// otherwise, plugin names need to match
					isPluginNameDeclared == other.isPluginNameDeclared &&
					(pluginName == null && other.pluginName == null ||
					pluginName != null && pluginName.equals(other.pluginName)));

		}
		return false;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	/**
	 * Return a hashCode for this object that ignores comments
	 */
	@Override
	public int hashCode() {
		int result = formula.hashCode();
		result = 31 * result + Boolean.valueOf(isPluginNameDeclared).hashCode();
		result = 31 * result + (pluginName != null ? pluginName.hashCode() : 0);
		return result;
	}
}
