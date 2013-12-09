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

import java.io.Serializable;

/**
 *	Represents the state of a boolean variable.  Instances of this
 *	object are immutable.
 */
public class BooleanVar implements Comparable<BooleanVar>, Serializable {
	private static final long serialVersionUID = 6578878301251930259L;

	public final String name;
	public final boolean state;
	
	public BooleanVar(String name, boolean state) {
		if (name == null) {
			// null names not allowed
			throw new NullPointerException();
		}
		this.name = name;
		this.state = state;		// false if negated, otherwise true
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (other instanceof BooleanVar) {
			return name.equals(((BooleanVar)other).name) && state == ((BooleanVar)other).state;
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return name.hashCode() + Boolean.valueOf(state).hashCode();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return (state ? "" : "!") + name; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public int compareTo(BooleanVar o) {
		int result = name.compareTo(o.name);
		if (result == 0 && state != o.state) {
			result = (state ? 1 : -1); 
		}
		return result;
	}

}