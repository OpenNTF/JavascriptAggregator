/*
 * (C) Copyright IBM Corp. 2012, 2016
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

package com.ibm.jaggr.core.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Object that represents a boolean expression in <a
 * href="http://en.wikipedia.org/wiki/Disjunctive_normal_form">Disjunctive
 * Normal Form (DNF)</a><br>
 * e.g. (A*B*C)|(!A*B)|(B*!C)
 * <p>
 * The variable names are instances of {@link BooleanVar}.
 * <p>
 * The group of variables that are and'ed together are terms (a.k.a. clauses),
 * instances of {@link BooleanTerm}.
 * <p>
 * The group of terms that are or'ed together are the formula encapsulated by
 * this object.
 */
public class BooleanFormula implements Set<BooleanTerm>, Serializable {

	private static final long serialVersionUID = 1202056345279875004L;

	/**
	 * A Set of {@link BooleanTerm} objects. A BooleanTerm is a set of variable
	 * state objects that are logically and'ed together, while instances of this
	 * object are comprised of the set of BooleanTerms that are logically or-ed
	 * together.
	 * <p>
	 * An empty set means that the expression evaluates to false. <br>
	 * A null set means that the expression evaluates to true;
	 */
	private Set<BooleanTerm> booleanTerms;

	/**
	 * True if the formula has been simplified.
	 */
	private boolean isSimplified = false;

	/**
	 * Default constructor.  Constructs a formula that evaluates to false.
	 */
	public BooleanFormula() {
		this(false);
	}

	/**
	 * Constructs a formula that evaluates to the specified boolean state.
	 *
	 * @param state
	 *            The initial state of the formula
	 */
	public BooleanFormula(boolean state) {
		booleanTerms = state ? null : new HashSet<BooleanTerm>();
		isSimplified = true;
	}

	/**
	 * Copy constructor. Constructs a formula which has the same state as the
	 * specified formula.
	 *
	 * @param other
	 *            The formula who's state this formula should copy.
	 */
	public BooleanFormula(BooleanFormula other) {
		this.booleanTerms = other.booleanTerms != null ? new HashSet<BooleanTerm>(other.booleanTerms) : null;
		this.isSimplified = other.isSimplified;
	}

	/**
	 * Constructs a formula who's state is given by the specified string.
	 * The string takes the form of (A*B)|(A*!C)
	 *
	 * Used mostly by unit test cases.
	 *
	 * @param str The string representation of the formula
	 */
	public BooleanFormula(String str) {
		booleanTerms = new HashSet<BooleanTerm>();
		String[] strTerms = str.split("\\+"); //$NON-NLS-1$
		for (String strTerm : strTerms) {
			booleanTerms.add(new BooleanTerm(strTerm));
		}
		if (booleanTerms.size() == 0) {
			throw new IllegalArgumentException(str);
		}
	}

	/**
	 * Provided for backwards compatibility
	 *
	 * @return The simplified representation of this formula, or this object, if already simplified.
	 */
	public BooleanFormula simplify() {
		return simplify(null);
	}

	/**
	 * Returns an instance of this class which is the simplified representation of the formula
	 * represented by this object. May return this object if the formula is already simplified.
	 * <p>
	 * This method uses the Quine-McCluskey algorithm and code described at
	 * http://en.literateprograms.org/Quine-McCluskey_algorithm_%28Java%29
	 *
	 * @param fCache
	 *          the formula cache
	 *
	 * @return The simplified representation of this formula, or this object, if already simplified.
	 */
	public BooleanFormula simplify(Map<?, ?> fCache) {

		if (isSimplified) {
			return this;
		}
		if (booleanTerms == null || booleanTerms.size() < 2) {
			isSimplified = true;
			return this;
		}

		// Remove true and false terms.  False terms are discarded.  True terms
		// make the entire formula evaluate to true.
		Set<BooleanTerm> trimmed = new HashSet<BooleanTerm>();
		for (BooleanTerm term : booleanTerms) {
			if (term.isTrue()) {
				booleanTerms = null;
				isSimplified = true;
				return this;
			} else if (term.isFalse()) {
				continue;			// omitting a false term
			} else {
				trimmed.add(term);
			}
		}
		// if a formula cache was provided.  See if we already evaluated this formula
		@SuppressWarnings("unchecked")
		Map<BooleanFormula, BooleanFormula> formulaCache = (Map<BooleanFormula, BooleanFormula>)fCache;
		if (formulaCache != null) {
			BooleanFormula f = (BooleanFormula)formulaCache.get(this);
			if (f != null) {
				return f;
			}
		}


		if (trimmed.size() != booleanTerms.size()) {
			booleanTerms = trimmed;
		}

		// Determine number of unique variable names in the formula
		Map<String, Integer> names = new TreeMap<String, Integer>();
		for (Set<BooleanVar> term : booleanTerms) {
			for (BooleanVar state : term) {
				names.put(state.name, -1);
			}
		}
		int i = 0;
		for (Map.Entry<String, Integer> entry : names.entrySet()) {
			entry.setValue(i++);
		}
		int count = names.size();
		if (count >= Integer.SIZE) {
			throw new IllegalArgumentException(Integer.toString(count));
		}
		List<Term> terms = new ArrayList<Term>(booleanTerms.size());
		for (Set<BooleanVar> term : booleanTerms) {
			byte[] bytes = new byte[count];
			for (i = 0; i < count; i++) bytes[i] = Term.DontCare;
			boolean ignoreTerm = false;
			for (BooleanVar state : term) {
				int index = names.get(state.name);
				byte val = state.state ? (byte)1 : (byte)0;
				if (bytes[index] == Term.DontCare) bytes[index] = val;
				else if (bytes[index] != val) {
					// Mutually exclusive states.  Don't consider this term
					ignoreTerm = true;
					break;
				}
			}
			if (!ignoreTerm) {
				terms.add(new Term(bytes));
			}
		}
		Set<BooleanTerm> result = new HashSet<BooleanTerm>();
		if (terms.size() == 0) {
			BooleanFormula f = new BooleanFormula(false);
			// Add the result to the cache if one was provided
			if (formulaCache != null) {
				formulaCache.put(this, f);
			}
			return f;
		}

		terms = expandDontCares(count, terms);

		Formula formula = new Formula(terms);
		formula.reduceToPrimeImplicants();
		formula.reducePrimeImplicantsToSubset();

		// now convert back to featureExpression form
		Term[] termList = formula.getTermList();
		if (termList.length == 0) {
			BooleanFormula f = new BooleanFormula(false);
			if (formulaCache != null) {
				formulaCache.put(this, f);
			}
			return f;
		}
		for (Term term : termList) {
			Set<BooleanVar> states = new HashSet<BooleanVar>();
			for (Map.Entry<String, Integer> entry : names.entrySet()) {
				if (term.get(entry.getValue()) == (byte)1) {
					states.add(new BooleanVar(entry.getKey(), true));
				} else if (term.get(entry.getValue()) == (byte)0) {
					states.add(new BooleanVar(entry.getKey(), false));
				}
			}
			if (states.size() > 0) {
				result.add(new BooleanTerm(states));
			}
		}
		BooleanFormula newFormula;
		if (result.size() == 0) {
			newFormula = new BooleanFormula(true);
		} else {
			newFormula = new BooleanFormula();
			newFormula.booleanTerms = result;
			newFormula.isSimplified = true;
		}
		if (formulaCache != null) {
			formulaCache.put(this, newFormula);
		}
		return newFormula;
	}

	/**
	 * Resolves the formula using the boolean values specified in
	 * <code>features</code>. If there are no more un-resolved variables in the
	 * forumula, the resulting formula will be either TRUE or FALSE.
	 *
	 * @param features
	 *            the values to assign to variables in the formula
	 * @return this object
	 */
	public BooleanFormula resolveWith(Features features) {
		return resolveWith(features, false);
	}

	/**
	 * Resolves the formula using the boolean values specified in
	 * <code>features</code>. If there are no more un-resolved variables in the
	 * forumula, the resulting formula will be either TRUE or FALSE.
	 *
	 * @param features
	 *            the values to assign to variables in the formula
	 * @param coerceUndefinedToFalse
	 *            if true, then undefined features will be treated as false
	 * @return this object
	 */
	public BooleanFormula resolveWith(Features features, boolean coerceUndefinedToFalse) {
		if (isTrue() || isFalse()) {
			return this;
		}
		BooleanFormula formula = new BooleanFormula();
		for (BooleanTerm term : this) {
			BooleanTerm evaluated = term.resolveWith(features, coerceUndefinedToFalse);
			if (evaluated.isFalse()) {
				// term is false.  Don't add to result
			} else if (evaluated.isTrue()) {
				// term is true so formula is true
				booleanTerms = null;
				isSimplified = true;
				return this;
			} else {
				formula.add(evaluated);
			}
		}
		booleanTerms = formula.booleanTerms;
		isSimplified = formula.isSimplified;
		return this;
	}

	/**
	 * Adds the terms from the specified formula to this formula
	 *
	 * @param other
	 *            The formula who's terms are to be added
	 * @return True if the formula was modified.
	 */
	public boolean addAll(BooleanFormula other) {
		return addAll(other.booleanTerms);
	}

	/**
	 * Logically ands the provided terms with the terms in the formula,
	 * replacing this formula with the result
	 *
	 * @param other
	 *            the terms to and with this formula
	 * @return this object.
	 */
	public BooleanFormula andWith(BooleanFormula other) {
		if (other.isTrue()) {
			return this;
		}
		if (other.isFalse() || isFalse()) {
			if (booleanTerms == null) {
				booleanTerms = new HashSet<BooleanTerm>();
			} else {
				booleanTerms.clear();
			}
			isSimplified = true;
			return this;
		}
		if (isTrue()) {
			booleanTerms = new HashSet<BooleanTerm>(other.booleanTerms);
			return this;
		}
		BooleanFormula newTerms = new BooleanFormula();
		for (BooleanTerm otherTerm : other) {
			for (BooleanTerm term : booleanTerms) {
				BooleanTerm newTerm = term.andWith(otherTerm);
				if (newTerm != null) {
					newTerms.add(newTerm);
				}
			}
		}
		booleanTerms = newTerms.booleanTerms;
		isSimplified = newTerms.isSimplified;
		return this;
	}

	/**
	 * Removes the specified terms from the formula
	 *
	 * @param toRemove The set of terms to remove
	 * @return True if the formula was modified
	 */
	public boolean removeTerms(Set<BooleanTerm> toRemove) {
		boolean modified = false;
		if (toRemove == null || toRemove.iterator() == null) {
			modified = booleanTerms == null || size() != 0;
			booleanTerms = new HashSet<BooleanTerm>();
		} else if (booleanTerms != null) {
			modified = removeAll(toRemove);
		}
		return modified;
	}

	/**
	 * Returns true if the formula is known to evaluate to true. Note that the
	 * formula may contain terms that logically evaluate to true but will not be
	 * recognized by this method. To determine if the formula logically
	 * evaluates to true, call this method on the value returned by
	 * {@link #simplify()}.
	 *
	 * @return True if the formula is known to evaluate to true
	 */
	public boolean isTrue() {
		return booleanTerms == null;
	}

	/**
	 * Returns true if the formula is known to evaluate to false. Note that the
	 * formula may contain terms that logically evaluate to false but will not be
	 * recognized by this method. To determine if the formula logically
	 * evaluates to false, call this method on the value returned by
	 * {@link #simplify()}.
	 *
	 * @return True if the formula is known to evaluate to false
	 */
	public boolean isFalse() {
		return booleanTerms != null && booleanTerms.size() == 0;
	}

	/**
	 * Adds the term to the formula.  Note that if the formula has
	 * previously been simplified and determined to evaluate to
	 * true, then adding any term will have no effect.
	 * <p>
	 * A null term represents an expression of true and sets
	 * the formula to true (null).
	 *
	 * @return true if the formula was modified
	 */
	@Override
	public boolean add(BooleanTerm booleanTerm) {
		boolean modified = false;
		if (booleanTerm.isTrue()) {
			modified = booleanTerms != null;
			booleanTerms = null;
		} else if (booleanTerms != null && !booleanTerm.isFalse()) {
			for (BooleanTerm term : booleanTerms) {
				if (booleanTerm.containsAll(term)) {
					// formula already includes the term
					return false;
				}
			}
			Set<BooleanTerm> newTerms = new HashSet<BooleanTerm>();
			newTerms.add(booleanTerm);
			for (BooleanTerm term : booleanTerms) {
				if (!term.containsAll(booleanTerm)) {
					newTerms.add(term);
				}
			}
			booleanTerms = newTerms;
			isSimplified = false;
			modified = true;
		}
		return modified;
	}

	/**
	 * Adds the terms to the formula.  Note that if the formula has
	 * previously been simplified and determined to evaluate to
	 * true, then adding any terms will have no effect.
	 */
	@Override
	public boolean addAll(Collection<? extends BooleanTerm> terms) {
		boolean modified = false;
		// a null booleanTerms means that the expression is
		// already true, so no need to modify it
		if (terms == null) {
			modified = booleanTerms != null;
			booleanTerms = null;
		} else if (booleanTerms != null) {
			for (BooleanTerm term : terms) {
				modified |= add(term);
			}
		}
		return modified;
	}

	/* (non-Javadoc)
	 * @see java.util.Set#clear()
	 */
	@Override
	public void clear() {
		// This makes the formula false
		if (booleanTerms != null) {
			booleanTerms.clear();
		} else {
			booleanTerms = new HashSet<BooleanTerm>();
		}
		isSimplified = true;
	}

	/* (non-Javadoc)
	 * @see java.util.Set#contains(java.lang.Object)
	 */
	@Override
	public boolean contains(Object object) {
		if (booleanTerms == null) {
			return false;
		}
		return booleanTerms.contains(object);
	}

	/* (non-Javadoc)
	 * @see java.util.Set#containsAll(java.util.Collection)
	 */
	@Override
	public boolean containsAll(Collection<?> collection) {
		if (booleanTerms == null) {
			return false;
		}
		return booleanTerms.containsAll(collection);
	}

	/* (non-Javadoc)
	 * @see java.util.Set#isEmpty()
	 */
	@Override
	public boolean isEmpty() {
		return booleanTerms == null ? true : booleanTerms.isEmpty();
	}

	/* (non-Javadoc)
	 * @see java.util.Set#iterator()
	 */
	@Override
	public Iterator<BooleanTerm> iterator() {
		return booleanTerms == null ? null : booleanTerms.iterator();
	}

	/* (non-Javadoc)
	 * @see java.util.Set#remove(java.lang.Object)
	 */
	@Override
	public boolean remove(Object object) {
		if (booleanTerms == null) {
			return false;
		}
		return booleanTerms.remove(object);
	}

	/* (non-Javadoc)
	 * @see java.util.Set#removeAll(java.util.Collection)
	 */
	@Override
	public boolean removeAll(Collection<?> collection) {
		if (booleanTerms == null) {
			return false;
		}
		return booleanTerms.removeAll(collection);
	}

	/* (non-Javadoc)
	 * @see java.util.Set#retainAll(java.util.Collection)
	 */
	@Override
	public boolean retainAll(Collection<?> collection) {
		if (booleanTerms == null) {
			return false;
		}
		return booleanTerms.retainAll(collection);
	}

	/* (non-Javadoc)
	 * @see java.util.Set#size()
	 */
	@Override
	public int size() {
		return booleanTerms == null ? 0 : booleanTerms.size();
	}

	/* (non-Javadoc)
	 * @see java.util.Set#toArray()
	 */
	@Override
	public Object[] toArray() {
		return booleanTerms == null ? new Object[0] : booleanTerms.toArray();
	}

	/* (non-Javadoc)
	 * @see java.util.Set#toArray(T[])
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] toArray(T[] array) {
		return (T[])(booleanTerms == null ? new BooleanTerm[0] : booleanTerms.toArray(new BooleanTerm[booleanTerms.size()]));
	}

	/**
	 * Returns true if the set of terms encapsulated by this object is equal
	 * to the set of terms of the other object.  Note that this is not the same
	 * as the formulas being logically the same.  If you want to determine if two instances
	 * of this class represent the same logical expression, then call {@link #simplify()}
	 * on both objects before comparing the returned objects for equality.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other instanceof BooleanFormula) {
			Set<BooleanTerm> otherTerms = ((BooleanFormula)other).booleanTerms;
			return (booleanTerms == null) ? otherTerms == null : booleanTerms.equals(otherTerms);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return (size() == 0) ? Boolean.valueOf(booleanTerms == null).hashCode() : booleanTerms.hashCode();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		if (booleanTerms == null) {
			return "TRUE"; //$NON-NLS-1$
		} else if (booleanTerms.size() == 0) {
			return "FALSE"; //$NON-NLS-1$
		}
		StringBuffer sb = new StringBuffer();
		int i = 0;
		for (BooleanTerm term : booleanTerms) {
			sb.append(i++ > 0 ? "|" : "").append(term); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return sb.toString();
	}

	/**
	 * Expands out an array of terms containing don't cares into a new array
	 * which doesn't contain any don't cares. Don't cares are eliminated by
	 * expanding the array to include all values represented by the don't cares.
	 * For example, the array [1, 1, X], [0, X, 0] with don't cares represented
	 * by X is expanded out to the new array [1, 1, 0], [1, 1, 1], [0, 1 0], [0,
	 * 0, 0]. Redundant terms are eliminated in the expansion
	 *
	 * @param ord
	 *            The term size
	 * @param in
	 *            The input list of terms containing don't cares
	 * @return A new term list with don't cares expanded.
	 */
	private static List<Term> expandDontCares(int ord, List<Term> in) {
		Set<Integer> termInts = new HashSet<Integer>();
		for (Term term : in) {
			addExpandTerm(0, ord, 0, term, termInts);
		}
		// Now convert the termInts to Terms
		List<Term> result = new ArrayList<Term>(termInts.size());
		for (Integer termInt : termInts) {
			byte[] varVals = new byte[ord];
			for (int i = 0; i < ord; i++) {
				varVals[i] = (byte)(((termInt & (1 << i)) == 0) ? 0 : 1);
			}
			result.add(new Term(varVals));
		}
		return result;
	}


	/**
	 * Utility method to expand a term and add it to the list of results when the
	 * expansion is complete.  The result terms are represented using a list of
	 * integer bit-fields, with each integer representing a term in bit-field form.
	 * This usage imposes a size limit of  {@link Integer#SIZE} on the terms that
	 * can be handled by this method.
	 *
	 * @param i Shift value used in recursion.  Specify 0 in initial call.
	 * @param ord Term size
	 * @param byteValue Used in recursion.  Specify 0 in initial call.
	 * @param termValue The term being expanded
	 * @param terms The expanded terms represented as a set of bit-field ints
	 */
	private static void addExpandTerm(int i, int ord, int byteValue, Term termValue, Set<Integer> terms) {
		if (i < ord) {
			int mask = 1 << i;
			byte bitValue = termValue.get(i);
			if (bitValue == 1) {
				byteValue |= (-1 & mask);
				addExpandTerm(i+1, ord, byteValue, termValue, terms);
			} else if (bitValue == 0) {
				addExpandTerm(i+1, ord, byteValue, termValue, terms);
			} else {	// Dont care
				addExpandTerm(i+1, ord, byteValue, termValue, terms);
				byteValue |= (-1 & mask);
				addExpandTerm(i+1, ord, byteValue, termValue, terms);
			}
		} else {
			// add the term to the term list
			terms.add(byteValue);
		}
	}
}
