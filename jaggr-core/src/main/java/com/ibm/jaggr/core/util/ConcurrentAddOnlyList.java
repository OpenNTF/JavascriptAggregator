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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An add-only, List-like class that supports concurrency for insertion without the concurrency
 * bottlenecks of a synchronized List. Does <strong>not</strong> support the {@link java.util.List}
 * interface.
 * <p>
 * Note that this list is thread-safe for adding elements only. It is not thread-safe for
 * concurrent add and retrieval, meaning that <code>lst.get(lst.size()-1)</code> is not guaranteed
 * to return the value that was last added to the list because time-slicing may occur between
 * incrementing of the size property and assignment of the value in the list.
 * <p>
 * This class is best used when adding of elements to the list does not need to overlap reading
 * the list.  Also, to avoid thread contention, the initial size of the list should be specified so
 * as to avoid the need to resize the internal array when its size is exceeded.
 *
 * @param <T>
 *            the type of the list element
 */
public class ConcurrentAddOnlyList<T> implements Iterable<T> {
	final private AtomicInteger index = new AtomicInteger(0);
	final private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	private T[] items;

	public ConcurrentAddOnlyList() {
		this(32);
	}

	@SuppressWarnings("unchecked")
	public ConcurrentAddOnlyList(int initialSize) {
		items = (T[])new Object[initialSize];
	}

	@SuppressWarnings("unchecked")
	public ConcurrentAddOnlyList(ConcurrentAddOnlyList<T> other) {
		items = (T[])new Object[other.size()];
		for (T item : other) {
			add(item);
		}
	}

	@SuppressWarnings("unchecked")
	public ConcurrentAddOnlyList(Collection<T> other) {
		items = (T[])new Object[other.size()];
		for (T elem : other) {
			add(elem);
		}
	}

	/**
	 * Like {@link java.util.List#add(Object)} except returns the index in the list to which the new
	 * element was added.
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
		if (item == null) {
			throw new NullPointerException();
		}
		Lock lock = null;
		int idx = index.getAndIncrement();
		try {
			lock = (idx >= items.length) ? rwl.writeLock() : rwl.readLock();
			lock.lock();
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
	 * Like {@link java.util.List#size()}.
	 * <p>
	 * Note that the actual insertion of elements into the list lags behind the updating of the size
	 * value, so in a threading environment, the value returned by this method may be larger than
	 * the actual number of element values currently in the list. If accurate values of the list
	 * size are needed concurrent with list updates, then external synchronization would need to be
	 * provided.
	 *
	 * @return the list size
	 */
	public int size() {
		return index.get();
	}

	/**
	 * Like {@link java.util.List#get(int)}
	 *
	 * @param index
	 *            the index of the item to retrieve
	 * @return the item at the specified index or null.
	 */
	public T get(int index) {
		return index >= items.length ? null : items[index];
	}

	/**
	 * Returns a copy of this list as a {@link List}. This method may block if the list is being
	 * grown. Note that this method is susceptible to the same concurrency issues noted with
	 * {@link #size()}, so the returned list may have some uninitialized values if this method is
	 * called in a threading environment without external synchronization.
	 *
	 * @return a copy of this list as a {@link List}.
	 */
	public List<T> toList() {
		rwl.readLock().lock();
		List<T> result = null;
		try {
			result = Arrays.asList(Arrays.copyOf(items, index.get()));
		} finally {
			rwl.readLock().unlock();
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<T> iterator() {
		return new _Iterator(this);
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("["); //$NON-NLS-1$
		int i = 0;
		for (T elem : this) {
			sb.append(i++ == 0 ? "" : ", ").append(elem.toString()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		sb.append("]"); //$NON-NLS-1$
		return sb.toString();
	}

	@Override
	public int hashCode() {
        int result = 1;
        for (T elem : this) {
            result = (31 * result) + (elem == null ? 0 : elem.hashCode());
        }
        return result;
	}

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof ConcurrentAddOnlyList) {
            ConcurrentAddOnlyList<?> list = (ConcurrentAddOnlyList<?>) other;
            if (list.size() != size()) {
                return false;
            }
            for (int i = 0; i < size(); i++) {
                if (!(get(i) == null ? list.get(i) == null : get(i).equals(list.get(i)))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

	private class _Iterator implements Iterator<T> {

		private final ConcurrentAddOnlyList<T> list;
		private int cursor = 0;

		private _Iterator(ConcurrentAddOnlyList<T> list) {
			this.list = list;
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		@Override
		public boolean hasNext() {
			return cursor < list.size();
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		@Override
		public T next() {
			return list.get(cursor++);
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
}