/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.executors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import com.ibm.jaggr.service.options.IOptions;

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
	public ScheduledExecutorService getCacheSerializeExecutor();

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
