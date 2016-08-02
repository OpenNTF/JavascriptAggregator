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

package com.ibm.jaggr.core.test;

import com.ibm.jaggr.core.impl.layer.CompletedFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SynchronousExecutor implements ExecutorService {

	private boolean isShutdown = false;

	public SynchronousExecutor() {
	}

	@Override
	public void execute(Runnable command) {
		command.run();
	}

	@Override
	public void shutdown() {
		isShutdown = true;
	}

	@Override
	public List<Runnable> shutdownNow() {
		isShutdown = true;
		return Collections.emptyList();
	}

	@Override
	public boolean isShutdown() {
		return isShutdown;
	}

	@Override
	public boolean isTerminated() {
		return isShutdown;
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return true;
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		try {
			T result = task.call();
			return new CompletedFuture<T>(result);
		} catch (Throwable t) {
			return new CompletedFuture<T>(new ExecutionException(t));
		}
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		try {
			task.run();
			return new CompletedFuture<T>(result);
		} catch (Throwable t) {
			return new CompletedFuture<T>(new ExecutionException(t));
		}
	}

	@Override
	public Future<?> submit(Runnable task) {
		try {
			task.run();
			return new CompletedFuture<Object>(null);
		} catch (Throwable t) {
			return new CompletedFuture<Object>(new ExecutionException(t));
		}
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
			throws InterruptedException {
		List<Future<T>> results = new ArrayList<Future<T>>();
		for (Callable<T> task : tasks) {
			results.add(submit(task));
		}
		return results;
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
			TimeUnit unit) throws InterruptedException {
		return invokeAll(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
			throws InterruptedException, ExecutionException {
		throw new UnsupportedOperationException("Not supported");
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		throw new UnsupportedOperationException("Not supported");
	}

}
