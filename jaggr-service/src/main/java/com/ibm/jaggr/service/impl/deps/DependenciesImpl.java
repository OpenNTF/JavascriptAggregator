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

package com.ibm.jaggr.service.impl.deps;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IShutdownListener;
import com.ibm.jaggr.service.ProcessingDependenciesException;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.config.IConfig.Location;
import com.ibm.jaggr.service.config.IConfigListener;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.deps.IDependenciesListener;
import com.ibm.jaggr.service.deps.ModuleDeps;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.options.IOptionsListener;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.IResourceVisitor;
import com.ibm.jaggr.service.util.ConsoleService;
import com.ibm.jaggr.service.util.Features;
import com.ibm.jaggr.service.util.SequenceNumberProvider;

public class DependenciesImpl implements IDependencies, IConfigListener, IOptionsListener, IShutdownListener {

	private static final Logger log = Logger.getLogger(DependenciesImpl.class.getName());
    
    private ServiceRegistration<?> configUpdateListener;
    private ServiceRegistration<?> optionsUpdateListener;
	private ServiceRegistration<?> shutdownListener;
	private BundleContext bundleContext;
	private String servletName;
	private long depsLastModified = -1;
	private long initStamp;
	private String rawConfig = null;
    private DepTreeRoot depTree = null;
	private CountDownLatch initialized;
	private boolean processingDeps = false;
	private boolean validate = false;
	private String cacheBust = null;
	private boolean initFailed = false;
	
	private IAggregator aggregator = null;
	private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	
	public DependenciesImpl(IAggregator aggregator, long stamp) {
		this.aggregator = aggregator;
		this.initStamp = stamp;
		Dictionary<String, String> dict;
		bundleContext = aggregator.getBundleContext();
		servletName = aggregator.getName();
		initialized = new CountDownLatch(1);

		if (bundleContext != null) {
			// register shutdown listener
			dict = new Hashtable<String, String>();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			shutdownListener = bundleContext.registerService(
					IShutdownListener.class.getName(), 
					this, 
					dict
			);
	
			// register the config change listener service.
			dict = new Hashtable<String, String>();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			configUpdateListener = bundleContext.registerService(
					IConfigListener.class.getName(), 
					this, 
					dict
			);
			
			// register the config change listener service.
			dict = new Hashtable<String, String>();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			optionsUpdateListener = bundleContext.registerService(
					IOptionsListener.class.getName(), 
					this, 
					dict
			);

			if (aggregator.getConfig() != null) {
				configLoaded(aggregator.getConfig(), 1);
			}
			
			if (aggregator.getOptions() != null) {
				optionsUpdated(aggregator.getOptions(), 1);
			}
		}
	}

	protected IAggregator getAggregator() {
		return aggregator;
	}
	
	@Override
	public ModuleDeps getExpandedDependencies(String modulePath,
			Features features, Set<String> dependentFeatures,
			boolean includeDetails, boolean performHasBranching) throws IOException {

		ModuleDeps result = new ModuleDeps();
		try {
			DepTreeNode node;
			getReadLock();
			try {
				modulePath = aggregator.getConfig().resolve(modulePath, features, dependentFeatures, null, true);
				node = depTree.getDescendent(modulePath);
				if (node != null) {
					result = node.getExpandedDependencies(features, dependentFeatures, includeDetails, performHasBranching);
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
		if (aggregator.getConfig() == null) {
			validate = true;
			return;
		}
		processDeps(true, clean, SequenceNumberProvider.incrementAndGetSequenceNumber());
	}

	@Override
	public synchronized void configLoaded(IConfig config, long sequence) {
		String previousRawConfig = rawConfig;
		rawConfig = config.toString();
		
		if (previousRawConfig == null || !previousRawConfig.equals(rawConfig)) {
			processDeps(validate, false, sequence);
			validate = false;
		}
	}

	@Override
	public synchronized void optionsUpdated(IOptions options, long sequence) {
		String previousCacheBust = cacheBust;
		cacheBust = options.getCacheBust();
		if (sequence > 1 && !cacheBust.equals(previousCacheBust) && rawConfig != null) {
			// Cache bust property has been updated subsequent to server startup
			processDeps(false, false, sequence);
		}
	}

	@Override
	public void shutdown(IAggregator aggregator) {
		this.aggregator = null;
		shutdownListener.unregister();
		configUpdateListener.unregister();
		optionsUpdateListener.unregister();
	}
	
	@Override
	public long getLastModified() {
		return depsLastModified;
	}
	
	private synchronized void processDeps(final boolean validate, final boolean clean, final long sequence) {
		if (aggregator.getConfig() == null || processingDeps) {
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
						final Map<String, URI> packageURIs = new LinkedHashMap<String, URI>(); 
						final Map<String, URI> pathURIs = new LinkedHashMap<String, URI>();
						final Map<String, URI> packageOverrideURIs = new LinkedHashMap<String, URI>();
						final Map<String, URI> pathOverrideURIs = new LinkedHashMap<String, URI>();
						
						// Add top level files and folders in the location specified by baseUrl
						// unless disabled by servlet init-param
						IConfig config = getAggregator().getConfig();
						if (config.isDepsIncludeBaseUrl()) {
							Location base = config.getBase();
							if (base != null) {
								List<IResource> list = new ArrayList<IResource>(2);
								list.add(getAggregator().newResource(base.getPrimary()));
								if (base.getOverride() != null) {
									list.add(getAggregator().newResource(base.getOverride()));
								}
								for (IResource baseres : list) {
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
						}
						for (Map.Entry<String, Location> entry  : config.getPackageLocations().entrySet()) {
							packageURIs.put(entry.getKey(), entry.getValue().getPrimary());
							if (entry.getValue().getOverride() != null) {
								packageOverrideURIs.put(entry.getKey(), entry.getValue().getOverride());
							}
						}
						for (Map.Entry<String, Location> entry  : config.getPaths().entrySet()) {
							pathURIs.put(entry.getKey(), entry.getValue().getPrimary());
							if (entry.getValue().getOverride() != null) {
								pathOverrideURIs.put(entry.getKey(), entry.getValue().getOverride());
							}
						}
						
						Collection<URI> paths = new LinkedList<URI>();
						paths.addAll(baseURIs.values());
						paths.addAll(packageURIs.values());
						paths.addAll(pathURIs.values());
						paths.addAll(packageOverrideURIs.values());
						paths.addAll(pathOverrideURIs.values());
						
						DepTree deps = new DepTree(
								paths,
								getAggregator(),
								initStamp,
								clean, 
								validate); 
				
						DepTreeRoot depTree = new DepTreeRoot(config);
						deps.mapDependencies(depTree, bundleContext, baseURIs, config);
						deps.mapDependencies(depTree, bundleContext, packageURIs, config);
						deps.mapDependencies(depTree, bundleContext, packageOverrideURIs, config);
						deps.mapDependencies(depTree, bundleContext, pathURIs, config);
						deps.mapDependencies(depTree, bundleContext, pathOverrideURIs, config);
						/*
						 * For each module name in the dependency lists, try to resolve the name
						 * to a reference to another node in the tree
						 */
						depTree.resolveDependencyRefs();
						depsLastModified = depTree.lastModifiedDepTree();
						DependenciesImpl.this.depTree = depTree;
						
						// Notify listeners that dependencies have been updated
						ServiceReference<?>[] refs = null;
						refs = bundleContext
								.getServiceReferences(IDependenciesListener.class.getName(),
								              "(name="+servletName+")" //$NON-NLS-1$ //$NON-NLS-2$
						);
						if (refs != null) {
							for (ServiceReference<?> ref : refs) {
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
					} catch (Throwable t) {
						initFailed = true;
						if (log.isLoggable(Level.SEVERE)) {
							log.log(Level.SEVERE, t.getMessage(), t);
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
			if (initFailed) {
				throw new IllegalStateException("Init failed"); //$NON-NLS-1$
			}
			if (!rwl.readLock().tryLock(1, TimeUnit.SECONDS)) {
				throw new ProcessingDependenciesException();
			}
		} else {
			initialized.await();
			if (initFailed) {
				throw new IllegalStateException("Init failed"); //$NON-NLS-1$
			}
			rwl.readLock().lock();
		}
	}
	
	private void releaseReadLock() {
		rwl.readLock().unlock();
	}

}
