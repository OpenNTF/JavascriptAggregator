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

import java.util.Comparator;

/**
 * A generic class for prioritized elements. Useful in priority queues, etc.
 * where the value is dis-associated from the priority
 * 
 * @param <T>
 *            The type of the value element
 */
public class Prioritized<T> {
	public static final Comparator<Prioritized<?>> comparator = new Comparator<Prioritized<?>>() {
		@Override
		public int compare(Prioritized<?> object1, Prioritized<?> object2) {
			double comparison = object1.priority - object2.priority;
			return comparison < 0 ? -1 : (comparison > 0 ? 1 : 0);
		}
	};
	
	public final T value;
	public final double priority;
	public Prioritized(T value, double priority) {
		this.value = value;
		this.priority = priority;
	}
	@Override
	public String toString() {
		return value.toString() + ";" + Double.toString(priority); //$NON-NLS-1$
	}
}