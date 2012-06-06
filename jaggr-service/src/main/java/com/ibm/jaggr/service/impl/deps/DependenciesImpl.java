/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.deps;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IShutdownListener;
import com.ibm.jaggr.service.ProcessingDependenciesException;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.config.IConfigListener;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.deps.IDependenciesListener;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.IResourceVisitor;
import com.ibm.jaggr.service.util.ConsoleService;
import com.ibm.jaggr.service.util.Features;
import com.ibm.jaggr.service.util.SequenceNumberProvider;

public class DependenciesImpl implements IDependencies, IConfigListener, IShutdownListener {

	private static final Logger log = Logger.getLogger(DependenciesImpl.class.getName());
    
    private ServiceRegistration configUpdateListener;
	private ServiceRegistration shutdownListener;
	private BundleContext bundleContext;
	private String servletName;
	private long depsLastModified = -1;
	private IConfig config = null;
	private String rawConfig = null;
    private DepTreeRoot depTree = null;
	private CountDownLatch initialized;
	private boolean processingDeps = false;
	
	private IAggregator aggregator = null;
	private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	
	public DependenciesImpl(IAggregator aggregator) {
		this.aggregator = aggregator;
		Properties dict;
		bundleContext = aggregator.getBundleContext();
		servletName = aggregator.getName();
		initialized = new CountDownLatch(1);

		if (bundleContext != null) {
			// register shutdown listener
			dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			shutdownListener = bundleContext.registerService(
					IShutdownListener.class.getName(), 
					this, 
					dict
			);
	
			// register the config change listener service.
			dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			configUpdateListener = bundleContext.registerService(
					IConfigListener.class.getName(), 
					this, 
					dict
			);
			
			if (aggregator.getConfig() != null) {
				configLoaded(aggregator.getConfig(), 1);
			}
		}
	}

	protected IAggregator getAggregator() {
		return aggregator;
	}
	
	@Override
	public Map<String, String> getExpandedDependencies(String modulePath,
			Features features, Set<String> dependentFeatures,
			boolean includeDetails) throws IOException {

		Map<String, String> result = Collections.emptyMap();
		try {
			DepTreeNode node;
			getReadLock();
			try {
				modulePath = aggregator.getConfig().resolve(modulePath, features, dependentFeatures, null);
				node = depTree.getDescendent(modulePath);
				if (node != null) {
					result = node.getExpandedDependencies(features, dependentFeatures, includeDetails);
				}
			} finally {
				releaseReadLock();
			}
		} catch (InterruptedException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		return result;
	}

	@Override
	public void validateDeps(boolean clean) {
		processDeps(true, clean, SequenceNumberProvider.incrementAndGetSequenceNumber());
	}

	@Override
	public synchronized void configLoaded(IConfig config, long sequence) {
		if (config == null || processingDeps) {
			return;
		}
		String rawConfig = null;
		rawConfig = config.getRawConfig().toString();
		if (this.rawConfig == null || !rawConfig.equals(this.rawConfig)) {
			this.rawConfig = rawConfig;
			this.config = config;
			processDeps(false, false, sequence);
		}
	}

	@Override
	public void shutdown(IAggregator aggregator) {
		this.aggregator = null;
		shutdownListener.unregister();
		configUpdateListener.unregister();
	}
	
	@Override
	public long getLastModified() {
		return depsLastModified;
	}
	
	private synchronized void processDeps(final boolean validate, final boolean clean, final long sequence) {
		if (config == null || processingDeps) {
			return;
		}
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		processingDeps = true;
		final AtomicBoolean processDepsThreadStarted = new AtomicBoolean(false);
		final ConsoleService cs = new ConsoleService();
		try {
			executor.execute(new Runnable() {
				public void run() {
					rwl.writeLock().lock();
					processDepsThreadStarted.set(true);
					// initialize the console service for the worker thread.
					ConsoleService workerCs = new ConsoleService(cs);
					try {
						// Map of path names to URIs for locations to be scanned for js files 
						final Map<String, URI> baseURIs = new LinkedHashMap<String, URI>();
						final Map<String, URI> packageURIs = config.getPackageURIs();
						final Map<String, URI> pathURIs = config.getPathURIs();
						final Set<URI> uris = new HashSet<URI>();
						
						// Add top level files and folders in the location specified by baseUrl
						// unless disabled by servlet init-param
						IConfig config = getAggregator().getConfig();
						if (config.isDepsIncludeBaseUrl()) {
							URI base = config.getBase();
							if (base != null) {
								IResource baseres = getAggregator().newResource(base);
								if (baseres.exists()) {
									baseres.walkTree(new IResourceVisitor() {
										public boolean visitResource(IResourceVisitor.Resource resource,
												String name) throws IOException {
											if (name.startsWith(".")) { //$NON-NLS-1$
												return false;
											}
											URI uri = resource.getURI();
											if (resource.isFolder()) {
												baseURIs.put(name, uri);
											} else 	if (name.endsWith(".js")) { //$NON-NLS-1$
												baseURIs.put(name.substring(0, name.length()-3), uri);
											}
											return false;		// don't recurse
										}
									});
								}
							}
						}
						uris.addAll(baseURIs.values());
						uris.addAll(packageURIs.values());
						uris.addAll(pathURIs.values());
						
						DepTree deps = new DepTree(
								uris,
								getAggregator(),
								clean, 
								validate); 
				
						DepTreeRoot depTree = new DepTreeRoot(config);
						deps.mapDependencies(depTree, bundleContext, baseURIs, config);
						deps.mapDependencies(depTree, bundleContext, packageURIs, config);
						deps.mapDependencies(depTree, bundleContext, pathURIs, config);
						/*
						 * For each module name in the dependency lists, try to resolve the name
						 * to a reference to another node in the tree
						 */
						depTree.resolveDependencyRefs();
						depsLastModified = depTree.lastModifiedDepTree();
						DependenciesImpl.this.depTree = depTree;
						
						// Notify listeners that dependencies have been updated
						ServiceReference[] refs = null;
						try {
							refs = bundleContext
							.getServiceReferences(IDependenciesListener.class.getName(),
									              "(name="+servletName+")" //$NON-NLS-1$ //$NON-NLS-2$
							);
						} catch (InvalidSyntaxException e) {
							if (log.isLoggable(Level.SEVERE)) {
								log.log(Level.SEVERE, e.getMessage(), e);
							}
						}
						if (refs != null) {
							for (ServiceReference ref : refs) {
								IDependenciesListener listener = 
									(IDependenciesListener)bundleContext.getService(ref);
								if (listener != null) {
									try {
										listener.dependenciesLoaded(DependenciesImpl.this, sequence);
									} catch (Exception e) {
										if (log.isLoggable(Level.SEVERE)) {
											log.log(Level.SEVERE, e.getMessage(), e);
										}
									} finally {
										bundleContext.ungetService(ref);
									}
								}
							}
						}
					} catch (RuntimeException e) {
						throw e;
					} catch (Exception e) {
						if (log.isLoggable(Level.SEVERE)) {
							log.log(Level.SEVERE, e.getMessage(), e);
						}
					} finally {
						rwl.writeLock().unlock();
						executor.shutdown();
						initialized.countDown();
						processingDeps = false;
						workerCs.close();
					}
				}
			});
			// Wait for the worker thread to obtain the read lock.  We do this so that
			// this thread can safely determine when the worker thread has finished by
			// calling getExpandedDependencies() and waiting for it to return (or 
			// not throw ProcessingDependenciesException when in development mode).
			int maxWait = 50;	// Max wait = 5 seconds
			while (processingDeps && !processDepsThreadStarted.get() && maxWait-- > 0) {
				Thread.sleep(100);
			}
		} catch (InterruptedException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		} finally {
			initialized.countDown();
			processingDeps = false;
		}
	}


	@Override
	public List<String> getDelcaredDependencies(String mid) throws ProcessingDependenciesException {
		List<String> result = null;
		try {
			getReadLock();
			DepTreeNode node;
			try {
				node = depTree.getDescendent(mid);
				if (node != null) {
					String[] deps = node.getDepArray();
					if (deps != null) {
						result = Arrays.asList(node.getDepArray());
					}
				}
			} finally {
				releaseReadLock();
			}
		} catch (InterruptedException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		return result;
	}
	
	private void getReadLock() throws InterruptedException, ProcessingDependenciesException {
		if (getAggregator().getOptions().isDevelopmentMode()) {
			if (!initialized.await(1, TimeUnit.SECONDS)) {
				throw new ProcessingDependenciesException();
			}
			if (!rwl.readLock().tryLock(1, TimeUnit.SECONDS)) {
				throw new ProcessingDependenciesException();
			}
		} else {
			initialized.await();
			rwl.readLock().lock();
		}
	}
	
	private void releaseReadLock() {
		rwl.readLock().unlock();
	}
}
