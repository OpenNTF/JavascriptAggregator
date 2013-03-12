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

package com.ibm.jaggr.service.impl.layer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

import com.ibm.jaggr.service.layer.ILayer;

/**
 * Wrapper class use to restrict write access to the layer build queue that is
 * exposed via the {@link ILayer#BUILDFUTURESQUEUE_REQATTRNAME} request
 * attribute. Write access to the queue is limited to those methods that add
 * elements to the end of the queue. All other methods which would modify the
 * queue throw a {@link UnsupportedOperationException}
 */
public class LayerBuildQueueWrapper implements Queue<ModuleBuildFuture> {

	private Queue<ModuleBuildFuture> queue;
	
	public LayerBuildQueueWrapper(Queue<ModuleBuildFuture> queue) {
		this.queue = queue;
	}
	
	@Override
	public boolean add(ModuleBuildFuture object) {
		return queue.add(object);
	}

	@Override
	public boolean addAll(Collection<? extends ModuleBuildFuture> collection) {
		return queue.addAll(collection);
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean contains(Object object) {
		return queue.contains(object);
	}

	@Override
	public boolean containsAll(Collection<?> collection) {
		return queue.containsAll(collection);
	}

	@Override
	public boolean isEmpty() {
		return queue.isEmpty();
	}

	@Override
	public Iterator<ModuleBuildFuture> iterator() {
		return queue.iterator();
	}

	@Override
	public boolean remove(Object object) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> collection) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean retainAll(Collection<?> collection) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return queue.size();
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T[] toArray(T[] array) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean offer(ModuleBuildFuture o) {
		return queue.offer(o);
	}

	@Override
	public ModuleBuildFuture poll() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ModuleBuildFuture remove() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ModuleBuildFuture peek() {
		return queue.peek();
	}

	@Override
	public ModuleBuildFuture element() {
		return queue.element();
	}

}