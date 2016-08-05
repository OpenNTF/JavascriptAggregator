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

package com.ibm.jaggr.core.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A list builder that supports concurrency for insertion without the concurrency bottlenecks of a
 * synchronized List.
 * <p>
 * Note that this class is thread-safe for adding elements only. It is not thread-safe for adding
 * elements concurrent with calling {@link #toList()}, with the possible result being that some
 * elements in the returned list may have un-assigned values.
 * <p>
 * To avoid thread contention, the initial size of the list should be specified so as to avoid the
 * need to resize the internal array when its size is exceeded.
 *
 * @param <T>
 *            the type of the list element
 */
public class ConcurrentListBuilder<T> {
	private static final String sourceClass = ConcurrentListBuilder.class.getName();

	final private AtomicInteger index = new AtomicInteger(0);
	final private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	private T[] items;

	public ConcurrentListBuilder() {
		this(32);
	}

	@SuppressWarnings("unchecked")
	public ConcurrentListBuilder(int initialSize) {
		items = (T[])new Object[initialSize];
	}

	@SuppressWarnings("unchecked")
	public ConcurrentListBuilder(Collection<T> other) {
		items = (T[])new Object[other.size()];
		for (T elem : other) {
			add(elem);
		}
	}

	/**
	 * Adds the specified element to the builder.
	 * <p>
	 * Blocking on add operations only occurs when the list array needs to be grown.
	 *
	 * @param item
	 *            The item to add
	 * @return the index in the list to which the item was added
	 * @throws NullPointerException
	 *             on attempt to add null to the list
	 */
	public int add(T item) {
		final String sourceMethod = "add"; //$NON-NLS-1$
		if (item == null) {
			throw new NullPointerException();
		}
		Lock lock = null;
		int idx = index.getAndIncrement();
		lock = (idx >= items.length) ? rwl.writeLock() : rwl.readLock();
		SignalUtil.lock(lock, sourceClass, sourceMethod);
		try {
			if (idx >= items.length) {
				int newLength = items.length;
				while (idx >= newLength) { newLength*=2; }
				items = Arrays.copyOf(items, newLength);
			}
			items[idx] = item;
		} finally {
			lock.unlock();
		}
		return idx;
	}

	/**
	 * Returns the number of elements that have been added.
	 *
	 * @return the list size
	 */
	public int size() {
		return index.get();
	}

	/**
	 * Returns a list object for this builder.
	 * <p>
	 * If this method is called concurrently with adding elements, then there is the potential that
	 * one or more of the list elements may have unassigned values.
	 *
	 * @return a list object for this builder.
	 */
	public List<T> toList() {
		final String sourceMethod = "toList"; //$NON-NLS-1$
		SignalUtil.lock(rwl.readLock(), sourceClass, sourceMethod);
		List<T> result = null;
		try {
			result = Arrays.asList(Arrays.copyOf(items, index.get()));
		} finally {
			rwl.readLock().unlock();
		}
		return result;
	}
}