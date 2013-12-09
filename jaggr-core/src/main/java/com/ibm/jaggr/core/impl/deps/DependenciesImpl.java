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

package com.ibm.jaggr.core.impl.deps;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigListener;
import com.ibm.jaggr.core.config.IConfig.Location;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.IDependenciesListener;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.PlatformAggregatorFactory;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.SequenceNumberProvider;

public class DependenciesImpl implements IDependencies, IConfigListener, IOptionsListener, IShutdownListener {

	protected static final Logger log = Logger.getLogger(DependenciesImpl.class.getName());
    
	protected String servletName;
	protected long depsLastModified = -1;
	protected long initStamp;
	private String rawConfig = null;
    protected DepTreeRoot depTree = null;
    protected CountDownLatch initialized;
	protected boolean processingDeps = false;
	private boolean validate = false;
	private String cacheBust = null;
	protected boolean initFailed = false;
	
	public IAggregator aggregator = null;
	
	private Object configUpdateListener;
    private Object optionsUpdateListener;
	private Object shutdownListener;
	
	
	protected ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
	
	public DependenciesImpl(IAggregator aggregator, long stamp) {
		Hashtable<String, String> dict = new Hashtable<String, String>();
		dict.put("name", aggregator.getName());
		this.aggregator = aggregator;
		this.initStamp = stamp;
		servletName = aggregator.getName();
		initialized = new CountDownLatch(1);
		
		shutdownListener = PlatformAggregatorFactory.getPlatformAggregator().registerService(IShutdownListener.class.getName(), this, dict);		
		configUpdateListener= PlatformAggregatorFactory.getPlatformAggregator().registerService(IConfigListener.class.getName(), this, dict);
		optionsUpdateListener= PlatformAggregatorFactory.getPlatformAggregator().registerService(IOptionsListener.class.getName(), this, dict);

			if (aggregator.getConfig() != null) {
				configLoaded(aggregator.getConfig(), 1);
			}
			
			if (aggregator.getOptions() != null) {
				optionsUpdated(aggregator.getOptions(), 1);
			}
		}
	@Override
	public void shutdown(IAggregator aggregator) {
		this.aggregator = null;
		PlatformAggregatorFactory.getPlatformAggregator().unRegisterService(configUpdateListener);
		PlatformAggregatorFactory.getPlatformAggregator().unRegisterService(optionsUpdateListener);
		PlatformAggregatorFactory.getPlatformAggregator().unRegisterService(shutdownListener);
		
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
		if (sequence > 1 && cacheBust != null && !cacheBust.equals(previousCacheBust) && rawConfig != null) {
			// Cache bust property has been updated subsequent to server startup
			processDeps(false, false, sequence);
		}
	}

	
	@Override
	public long getLastModified() {
		return depsLastModified;
	}
	
	public synchronized void processDeps(final boolean validate, final boolean clean, final long sequence) {
		if (aggregator.getConfig() == null || processingDeps) {
			return;
		}
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		processingDeps = true;
		final AtomicBoolean processDepsThreadStarted = new AtomicBoolean(false);
		//final ConsoleService cs = new ConsoleService();
		try {
			executor.execute(new Runnable() {
				public void run() {
					rwl.writeLock().lock();
					processDepsThreadStarted.set(true);
					// initialize the console service for the worker thread.
					//ConsoleService workerCs = new ConsoleService(cs);
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
						
						boolean cleanCache = clean;
						while (true) {
							DepTree deps = null;
							try {
								deps = new DepTree(
										paths,
										getAggregator(),
										initStamp,
										cleanCache, 
										validate); 
						
								DepTreeRoot depTree = new DepTreeRoot(config);
						deps.mapDependencies(depTree, null, baseURIs, config);
						deps.mapDependencies(depTree, null, packageURIs, config);
						deps.mapDependencies(depTree, null, packageOverrideURIs, config);
						deps.mapDependencies(depTree, null, pathURIs, config);
						deps.mapDependencies(depTree, null, pathOverrideURIs, config);
								/*
								 * For each module name in the dependency lists, try to resolve the name
								 * to a reference to another node in the tree
								 */
								depTree.resolveDependencyRefs();
								depsLastModified = depTree.lastModifiedDepTree();
								DependenciesImpl.this.depTree = depTree;
							} catch (Exception e) {
								if (!cleanCache && (deps == null || deps.isFromCache())) {
									if (log.isLoggable(Level.WARNING)) {
										log.log(Level.WARNING, e.getMessage(), e);
										log.warning(Messages.DepTree_10);
									}
									cleanCache = true;
									continue;
								}
								throw e;	// rethrow the exception
							}
							break;
						}						
						// Notify listeners that dependencies have been updated
						Object[] refs = null;
						
						refs = PlatformAggregatorFactory.getPlatformAggregator().getServiceReferences(IDependenciesListener.class.getName(),"(name="+servletName+")");
						
						if (refs != null) {
							for (Object ref : refs) {								
								IDependenciesListener listener = (IDependenciesListener)(PlatformAggregatorFactory.getPlatformAggregator().getService(ref));
								if (listener != null) {
									try {
										listener.dependenciesLoaded(DependenciesImpl.this, sequence);
									} catch (Exception e) {
										if (log.isLoggable(Level.SEVERE)) {
											log.log(Level.SEVERE, e.getMessage(), e);
										}
									} finally {
										PlatformAggregatorFactory.getPlatformAggregator().ungetService(ref);
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
						//workerCs.close();
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
