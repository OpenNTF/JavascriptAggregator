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

package com.ibm.jaggr.service.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * A collection of BooleanVar objects which are logically anded together.
 * Implements an unmodifiable set of BooleanVar objects. 
 */
public class BooleanTerm extends HashSet<BooleanVar> {
	
	private static final long serialVersionUID = -2969569532410527153L;

	private boolean initialized = false;	// true except when the object is being constructed
	
	public static final BooleanTerm TRUE = new BooleanTerm();
	
	public static final BooleanTerm FALSE = new BooleanTerm();
	
	/*
	 * This is private because only the static singletons TRUE and FALSE can have 
	 * an empty term list. 
	 */
	private BooleanTerm() {
		initialized = true;
	}
	
	public BooleanTerm(BooleanVar var) {
		super();
		if (var == null) {
			throw new NullPointerException();
		}
		add(var);
		initialized = true;
	}

	public BooleanTerm(Set<BooleanVar> term) { 
		super(term);
		if (term.isEmpty()) {
			throw new UnsupportedOperationException();
		}
		initialized = true;
	}

	public boolean isTrue() {
		return this == TRUE;
	}
	
	public boolean isFalse() {
		return this == FALSE;
	}
	
	/**
	 * Constructs an object from a string of the form "A*B*!C" where
	 * A, B and C are variable names and names preceeded by ! are 
	 * negated.
	 * 
	 * @param str the boolean term in string form.
	 */
	public BooleanTerm(String str) {
		if (str.contains("+")) { //$NON-NLS-1$
			throw new IllegalArgumentException(str);
		}
		str = str.replaceAll("\\s", ""); //$NON-NLS-1$ //$NON-NLS-2$
		if (str.charAt(0) == '(' && str.charAt(str.length()-1) == ')') {
			str = str.substring(1, str.length()-1);
		}
		String[] strVars = str.split("\\*"); //$NON-NLS-1$
		for (String strVar : strVars) {
			boolean state = (strVar.charAt(0) != '!');
			String name = state ? strVar : strVar.substring(1);
			if (name.length() == 0) {
				throw new IllegalArgumentException(str);
			}
			add(new BooleanVar(name, state));
		}
		if (size() == 0) {
			throw new IllegalArgumentException(str);
		}
		initialized = true;
	}

	/**
	 * Applies the feature values specified in <code>features</code> to the term
	 * and returns a new term with the resolved result. A result of null
	 * indicates the term is false. An empty term indicates the the term is true
	 * 
	 * @param features
	 *            the varialbe names and values to resolve with
	 * 
	 * @return the result
	 */
	public BooleanTerm resolveWith(Features features) {
		if (isEmpty()) {
			return this;
		}
		Set<BooleanVar> term = new HashSet<BooleanVar>();
		for (BooleanVar var : this) {
			if (features.contains(var.name)) {
				boolean featureState = features.isFeature(var.name);
				if (featureState && var.state || !featureState && !var.state) {
					// The var is true.  Don't add to term
				} else {
					return BooleanTerm.FALSE;
				}
			} else {
				term.add(var);
			}
		}
		return term.isEmpty() ? BooleanTerm.TRUE : new BooleanTerm(term);
	}
	
	/**
	 * Returns the logical AND of this term with the specified 
	 * term.
	 * 
	 * @param other the term to and this term with.
	 * @return
	 */
	public BooleanTerm andWith(BooleanTerm other) {
		if (isFalse() || other.isFalse()) {
			// anding with false, the result is false
			return BooleanTerm.FALSE;
		}
		if (other.isTrue()) {
			// anding with true, the result is unchanged
			return this;
		}
		if (isTrue()) {
			// anding with true, other is unchanged
			return other;
		}
		Set<BooleanVar> result = new HashSet<BooleanVar>(this);
		for (BooleanVar var : other) {
			if (result.contains(var.negate())) {
				// If the same term has the same var with opposite states
				// then the resulting term is false;
				return BooleanTerm.FALSE;
			}
			result.add(var);
		}
		return new BooleanTerm(result);
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		if (isTrue()) {
			return "[TRUE]"; //$NON-NLS-1$
		}
		if (isFalse()) {
			return "[FALSE]"; //$NON-NLS-1$
		}
		StringBuffer sb = new StringBuffer();
		if (size() > 1) sb.append("("); //$NON-NLS-1$
		int i = 0;
		for (BooleanVar var : new TreeSet<BooleanVar>(this)) {
			sb.append(i++ > 0 ? "*" : "").append(var); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (size() > 1) sb.append(")"); //$NON-NLS-1$
		return sb.toString();
	}

	/* (non-Javadoc)
	 * @see java.util.HashSet#add(java.lang.Object)
	 */
	@Override
	public boolean add(BooleanVar object) {
		if (initialized) {
			throw new UnsupportedOperationException();
		}
		return super.add(object);
	}

	/* (non-Javadoc)
	 * @see java.util.HashSet#clear()
	 */
	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see java.util.HashSet#remove(java.lang.Object)
	 */
	@Override
	public boolean remove(Object object) {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see java.util.AbstractSet#removeAll(java.util.Collection)
	 */
	@Override
	public boolean removeAll(Collection<?> collection) {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see java.util.AbstractCollection#addAll(java.util.Collection)
	 */
	@Override
	public boolean addAll(Collection<? extends BooleanVar> collection) {
		if (initialized) {
			throw new UnsupportedOperationException();
		}
		return super.addAll(collection);
	}

	/* (non-Javadoc)
	 * @see java.util.AbstractCollection#retainAll(java.util.Collection)
	 */
	@Override
	public boolean retainAll(Collection<?> collection) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public int hashCode() {
		if (this == FALSE) {
			return Boolean.FALSE.hashCode();
		} else if (this == TRUE) {
			return Boolean.TRUE.hashCode();
		}
		return super.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		if (this == FALSE && other == TRUE ||
			this == TRUE && other == FALSE) {
			return false;
		}
		return super.equals(other);
	}
}