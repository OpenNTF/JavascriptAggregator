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

package com.ibm.jaggr.core.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A collection of BooleanVar objects which are logically anded together.
 * Implements an unmodifiable set of BooleanVar objects. 
 */
public class BooleanTerm extends TreeSet<BooleanVar> {
	
	private static final long serialVersionUID = -2969569532410527153L;

	public static BooleanTerm emptyTerm = new BooleanTerm(Collections.<BooleanVar> emptySet());

	private boolean initialized = false;	// true except when the object is being constructed
	
	public BooleanTerm() { super(); }
	
	public BooleanTerm(BooleanVar var) {
		super();
		add(var);
		initialized = true;
	}

	public BooleanTerm(Set<BooleanVar> term) { 
		super(term);
		initialized = true;
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

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		if (size() > 1) sb.append("("); //$NON-NLS-1$
		int i = 0;
		for (BooleanVar var : this) {
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
}