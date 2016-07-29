/*
 * (C) Copyright IBM Corp. 2012, 2016
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

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
	public Future<?> submit(Runnable task) {
		if (task == null) throw new NullPointerException();
		RunnableFuture<Object> ftask = newTaskFor(task, null);
		execute(ftask);
		return ftask;
	}

	public <T> Future<T> submit(Runnable task, T result) {
		if (task == null) throw new NullPointerException();
		RunnableFuture<T> ftask = newTaskFor(task, result);
		execute(ftask);
		return ftask;
	}

	public <T> Future<T> submit(Callable<T> task) {
		if (task == null) throw new NullPointerException();
		RunnableFuture<T> ftask = newTaskFor(task);
		execute(ftask);
		return ftask;
	}

	public ScheduledFuture<?> schedule(Runnable command,
			long delay,
			TimeUnit unit) {
		if (command == null || unit == null)
			throw new NullPointerException();
		if (delay <= 0) {
			RunnableScheduledFuture<?> t = decorateTask(command,
					new ScheduledFutureTask<Boolean>(command, null));
			t.run();
			return t;
		} else {
			return super.schedule(command, delay, unit);
		}
	}

	public <V> ScheduledFuture<V> schedule(Callable<V> callable,
			long delay,
			TimeUnit unit) {
		if (callable == null || unit == null)
			throw new NullPointerException();
		if (delay <= 0) {
			RunnableScheduledFuture<V> t = decorateTask(callable,
					new ScheduledFutureTask<V>(callable));
			t.run();
			return t;
		} else {
			return super.schedule(callable, delay, unit);
		}
	}

	@Override
	public void execute(Runnable command) {
		command.run();
	}

	static private class ScheduledFutureTask<V> extends FutureTask<V> implements RunnableScheduledFuture<V> {

		public ScheduledFutureTask(Callable<V> callable) {
			super(callable);
		}

		public ScheduledFutureTask(Runnable runnable, V result) {
			super(runnable, result);
		}

		@Override
		public long getDelay(TimeUnit unit) {
			return 0;
		}

		@Override
		public int compareTo(Delayed other) {
			long d = (getDelay(TimeUnit.NANOSECONDS) -
					other.getDelay(TimeUnit.NANOSECONDS));
			return (d == 0)? 0 : ((d < 0)? -1 : 1);
		}

		@Override
		public boolean isPeriodic() {
			return false;
		}

	}


}
