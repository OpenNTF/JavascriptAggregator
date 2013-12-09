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

package com.ibm.jaggr.core.impl.executors;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.options.IOptions;

public class ExecutorsImpl implements IExecutors {

    private static final Logger log = Logger.getLogger(ICacheManager.class.getName());

    /* Thread group name constants */
    private static final String SCHEDULED_EXECUTOR_THREADNAME = "Aggregator Scheduled Executor"; //$NON-NLS-1$
    private static final String CACHE_FILE_CREATOR_THREADNAME = "Aggregator Cache File Creator"; //$NON-NLS-1$
    private static final String CACHE_FILE_DELETOR_THREADNAME = "Aggregator Cache File Deletor"; //$NON-NLS-1$
    private static final String MODULE_BUILDER_THREADNAME = "{0} Thread - {1}"; //$NON-NLS-1$
    
    /** {@link ExecutorService} thread pool used to compile javascript modules */
    private static final String MODULE_BUILDER_TGNAME = "AMD Module Builder"; //$NON-NLS-1$
    
    private final ThreadGroup buildTG = new ThreadGroup(MODULE_BUILDER_TGNAME);

    /** Single thread {@link ScheduledExcetutorService} to periodically serialize the cache metadata */
    private ScheduledExecutorService scheduledExecutor;
	
    /** Single thread {@link ExecutorService} used to asynchrnously create cache files */ 
    private ExecutorService createExecutor; 
    
    /** Single thread {@link ScheduledExecutorService} used to asynchronously delete cache files */
    private ScheduledThreadPoolExecutor deleteExecutor; 

    private ExecutorService buildExecutor; 
    
    private transient boolean opened = false;

    public ExecutorsImpl(IOptions options) {
    	this(options, null, null, null, null);
    }
    
    public ExecutorsImpl(
    	IOptions options,
		ExecutorService createExecutor,
		ExecutorService buildExecutor,
		ScheduledThreadPoolExecutor deleteExecutor,
		ScheduledExecutorService scheduledExecutor) {
			
    	this.createExecutor = createExecutor;
    	this.buildExecutor = buildExecutor;
    	this.deleteExecutor = deleteExecutor;
    	this.scheduledExecutor = scheduledExecutor;
    	
	}
    private void open() {
    	
    	if (opened) return;
    	
        if (scheduledExecutor == null) {
        	scheduledExecutor = 
        	Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        		public Thread newThread(Runnable r) {
        			return new Thread(r, SCHEDULED_EXECUTOR_THREADNAME);
        		}
        	});
        }
        if (createExecutor == null) {
        	createExecutor = 
        	Executors.newSingleThreadExecutor(new ThreadFactory() {
        		public Thread newThread(Runnable r) {
        			return new Thread(r, CACHE_FILE_CREATOR_THREADNAME);
        		}
        	});
        }
        if (buildExecutor == null) {
        	buildExecutor =  
        	Executors.newFixedThreadPool(10, new ThreadFactory() {
        		public Thread newThread(Runnable r) {
        			return new Thread(buildTG, r,
        					MessageFormat.format(MODULE_BUILDER_THREADNAME,  
        							new Object[]{
        								buildTG.getName(),
        								buildTG.activeCount()
        							}
        					)
        			);
        		}
        	});
        }
        /** Single thread {@link ScheduledExecutorService} used to asynchronously delete cache files */
        if (deleteExecutor == null) { 
        	deleteExecutor = 
        	new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        		public Thread newThread(Runnable r) {
        			Thread t = new Thread(r, CACHE_FILE_DELETOR_THREADNAME);
        			t.setPriority(Thread.MIN_PRIORITY);
        			return t;
        		}
        	});
        }
        opened = true;
    }
     
    public synchronized void shutdown() {
    	
    	if (!opened) return;
    	
		// Shutdown the asynchronous executor services
		Collection<ExecutorService> executors = new ArrayList<ExecutorService>();
		executors.add(scheduledExecutor);
		executors.add(deleteExecutor);
		executors.add(createExecutor);
		executors.add(buildExecutor);
		
		for(ExecutorService executor : executors) {
			executor.shutdown();
		}
		
		// finish up any waiting deletions
		BlockingQueue<Runnable> queue = deleteExecutor.getQueue();
		Runnable task;
		while ((task = queue.poll()) != null) {
			task.run();
		}
		
		// Wait for executors to finish shutting down.
		for(ExecutorService executor : executors) {
			int retryCount = 5;
			while (!executor.isShutdown() && !executor.isTerminated() && retryCount-- > 0) {
				try {
					executor.awaitTermination(10, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					log.log(Level.WARNING, e.getMessage(), e);
					break;
				}
				if (!executor.isShutdown() && !executor.isTerminated()) {
					if (log.isLoggable(Level.WARNING)) {
						log.warning(Messages.ExecutorsImpl_0);
					}
				}
			}
		}
		
    }
    
    @Override
	public ExecutorService getBuildExecutor() {
    	if (!opened) open();
    	return buildExecutor;
    }

	@Override
	public ScheduledExecutorService getScheduledExecutor() {
    	if (!opened) open();
		return scheduledExecutor;
	}

	@Override
	public ScheduledExecutorService getFileDeleteExecutor() {
    	if (!opened) open();
		return deleteExecutor;
	}

	@Override
	public ExecutorService getFileCreateExecutor() {
    	if (!opened) open();
		return createExecutor;
	}

}
