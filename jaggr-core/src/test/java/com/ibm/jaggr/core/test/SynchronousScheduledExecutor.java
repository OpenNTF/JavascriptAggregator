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
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Subclasses ScheduledThreadPoolExecutor and provides for synchronous execution
 * of tasks submitted using the submit methods and scheduled tasks with a 0 timeout.
 * Used for unit test cases to avoid threading issues with attempting to clean up
 * the cache directory after a test case has completed.
 */
public class SynchronousScheduledExecutor extends ScheduledThreadPoolExecutor {
	public SynchronousScheduledExecutor() {
		super(0);
	}

	@Override
	protected <V> RunnableScheduledFuture<V> decorateTask(Runnable runnable,
			RunnableScheduledFuture<V> task) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> callable,
			RunnableScheduledFuture<V> task) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean value) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean getContinueExistingPeriodicTasksAfterShutdownPolicy() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean value) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean getExecuteExistingDelayedTasksAfterShutdownPolicy() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public BlockingQueue<Runnable> getQueue() {
		return super.getQueue();
	}

	@Override
	public boolean isTerminating() {
		return super.isTerminating();
	}

	@Override
	protected void finalize() {
		super.finalize();
	}

	@Override
	public void setThreadFactory(ThreadFactory threadFactory) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public ThreadFactory getThreadFactory() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public RejectedExecutionHandler getRejectedExecutionHandler() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void setCorePoolSize(int corePoolSize) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int getCorePoolSize() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean prestartCoreThread() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int prestartAllCoreThreads() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean allowsCoreThreadTimeOut() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void allowCoreThreadTimeOut(boolean value) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void setMaximumPoolSize(int maximumPoolSize) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int getMaximumPoolSize() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void setKeepAliveTime(long time, TimeUnit unit) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public long getKeepAliveTime(TimeUnit unit) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public boolean remove(Runnable task) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public void purge() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int getPoolSize() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int getActiveCount() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public int getLargestPoolSize() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public long getTaskCount() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public long getCompletedTaskCount() {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	public String toString() {
		return super.toString();
	}

	@Override
	protected void beforeExecute(Thread t, Runnable r) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected void terminated() {
		super.terminated();
	}

	@Override
	protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
		throw new UnsupportedOperationException("Not implemented");
	}

	@Override
	protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
		throw new UnsupportedOperationException("Not implemented");
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

	@Override
	public void execute(Runnable command) {
		command.run();
	}

	@Override
	public void shutdown() {
		super.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return super.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return super.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return super.isTerminated();
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
	public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
		command.run();
		return null;
	}

	@Override
	public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
		try {
			callable.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return null;
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
			TimeUnit unit) {
		return null;
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
			long delay, TimeUnit unit) {
		return null;
	}



}
