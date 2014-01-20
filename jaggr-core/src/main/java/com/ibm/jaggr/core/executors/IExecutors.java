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

package com.ibm.jaggr.core.executors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import com.ibm.jaggr.core.options.IOptions;

/**
 * Interface for thread executor providers
 */
public interface IExecutors {

	/**
	 * Returns a build executor from the pool of build executors. The submitted
	 * task runs immediately.
	 * 
	 * @return An executor service
	 */
	public ExecutorService getBuildExecutor();

	/**
	 * Returns an executor used for asynchronously creating files. The submitted
	 * task runs immediately.
	 * 
	 * @return An executor service
	 */
	public ExecutorService getFileCreateExecutor();

	/**
	 * Returns a scheduled executor service that fires on a periodic interval.
	 * Used by the cache manager to periodically serialize cache metadata.
	 * 
	 * @return A scheduled executor service
	 */
	public ScheduledExecutorService getScheduledExecutor();

	/**
	 * Returns a scheduled executor that will execute submitted tasks after a
	 * delay determined by {@link IOptions#getDeleteDelay()}.
	 * 
	 * @return A scheduled executor service
	 */
	public ScheduledExecutorService getFileDeleteExecutor();

	/**
	 * Shuts down the executor services. Any tasks that have been submitted to
	 * the delete executor that have not yet been dispatched will be completed
	 * synchronously before this method returns.
	 */
	public void shutdown();

}
