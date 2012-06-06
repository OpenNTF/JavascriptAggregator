/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.executors;

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

import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.executors.IExecutors;
import com.ibm.jaggr.service.options.IOptions;

public class ExecutorsImpl implements IExecutors {

    private static final Logger log = Logger.getLogger(ICacheManager.class.getName());

    /* Thread group name constants */
    private static final String CACHE_SERIALIZER_THREADNAME = "Aggregator Scheduled Cache Serializer"; //$NON-NLS-1$
    private static final String CACHE_FILE_CREATOR_THREADNAME = "Aggregator Cache File Creator"; //$NON-NLS-1$
    private static final String CACHE_FILE_DELETOR_THREADNAME = "Aggregator Cache File Deletor"; //$NON-NLS-1$
    private static final String MODULE_BUILDER_THREADNAME = "{0} Thread - {1}"; //$NON-NLS-1$
    
    /** {@link ExecutorService} thread pool used to compile javascript modules */
    private static final String MODULE_BUILDER_TGNAME = "AMD Module Builder"; //$NON-NLS-1$
    
    private final ThreadGroup buildTG = new ThreadGroup(MODULE_BUILDER_TGNAME);

    /** Single thread {@link ScheduledExcetutorService} to periodically serialize the cache metadata */
    private ScheduledExecutorService cacheSerializeExecutor;
	
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
		ScheduledExecutorService cacheSerializeExecutor) {
			
    	this.createExecutor = createExecutor;
    	this.buildExecutor = buildExecutor;
    	this.deleteExecutor = deleteExecutor;
    	this.cacheSerializeExecutor = cacheSerializeExecutor;
    	
	}
    private void open() {
    	
    	if (opened) return;
    	
        if (cacheSerializeExecutor == null) {
        	cacheSerializeExecutor = 
        	Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        		public Thread newThread(Runnable r) {
        			return new Thread(r, CACHE_SERIALIZER_THREADNAME);
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
		executors.add(cacheSerializeExecutor);
		executors.add(deleteExecutor);
		executors.add(createExecutor);
		executors.add(buildExecutor);
		
		for(ExecutorService executor : executors) {
			executor.shutdown();
		}
		
		// finish up any waiting deletions
		BlockingQueue<Runnable> queue = deleteExecutor.getQueue();
		Runnable task;
		while ((task = queue.peek()) != null) {
			deleteExecutor.remove(task);
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
	public ScheduledExecutorService getCacheSerializeExecutor() {
    	if (!opened) open();
		return cacheSerializeExecutor;
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
