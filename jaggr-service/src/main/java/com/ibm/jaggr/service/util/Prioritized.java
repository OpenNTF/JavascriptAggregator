package com.ibm.jaggr.service.util;

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
		return value.toString() + ";" + Double.toString(priority);
	}
}