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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfig.Location;
import com.ibm.jaggr.core.config.IConfigListener;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.IDependenciesListener;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.util.ConsoleService;
import com.ibm.jaggr.core.util.SequenceNumberProvider;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DependenciesImpl implements IDependencies, IConfigListener, IOptionsListener, IShutdownListener {

	private static final Logger log = Logger.getLogger(DependenciesImpl.class.getName());

	private List<IServiceRegistration> serviceRegistrations = new ArrayList<IServiceRegistration>();
	private String servletName;
	private long depsLastModified = -1;
	private long initStamp;
	private String rawConfig = null;
	private CountDownLatch initialized;
	private boolean processingDeps = false;
	private boolean validate = false;
	private String cacheBust = null;
	private boolean initFailed = false;
	private Map<String, DepTreeNode.DependencyInfo> depMap;

	private IAggregator aggregator = null;
	private ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

	public DependenciesImpl(IAggregator aggregator, long stamp) {
		Hashtable<String, String> dict;
		this.aggregator = aggregator;
		this.initStamp = stamp;
		servletName = aggregator.getName();
		initialized = new CountDownLatch(1);

		dict = new Hashtable<String, String>();
		dict.put("name", aggregator.getName()); //$NON-NLS-1$
		serviceRegistrations.add(aggregator.getPlatformServices().registerService(IShutdownListener.class.getName(), this, dict));

		dict = new Hashtable<String, String>();
		dict.put("name", aggregator.getName()); //$NON-NLS-1$
		serviceRegistrations.add(aggregator.getPlatformServices().registerService(IConfigListener.class.getName(), this, dict));

		dict = new Hashtable<String, String>();
		dict.put("name", aggregator.getName()); //$NON-NLS-1$
		serviceRegistrations.add(aggregator.getPlatformServices().registerService(IOptionsListener.class.getName(), this, dict));

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
		for (IServiceRegistration reg : serviceRegistrations) {
			reg.unregister();
		}
		serviceRegistrations.clear();
	}

	protected IAggregator getAggregator() {
		return aggregator;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.deps.IDependencies#validateDeps(boolean)
	 */
	@Override
	public void validateDeps(boolean clean) {
		if (aggregator.getConfig() == null) {
			validate = true;
			return;
		}
		processDeps(true, clean, SequenceNumberProvider.incrementAndGetSequenceNumber());
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfigListener#configLoaded(com.ibm.jaggr.service.config.IConfig, long)
	 */
	@Override
	public synchronized void configLoaded(IConfig config, long sequence) {
		String previousRawConfig = rawConfig;
		rawConfig = config.toString();

		if (previousRawConfig == null || !previousRawConfig.equals(rawConfig)) {
			processDeps(validate, false, sequence);
			validate = false;
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.options.IOptionsListener#optionsUpdated(com.ibm.jaggr.service.options.IOptions, long)
	 */
	@Override
	public synchronized void optionsUpdated(IOptions options, long sequence) {
		String previousCacheBust = cacheBust;
		cacheBust = options.getCacheBust();
		if (sequence > 1 && cacheBust != null && !cacheBust.equals(previousCacheBust) && rawConfig != null) {
			// Cache bust property has been updated subsequent to server startup
			processDeps(false, false, sequence);
		}
	}


	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.deps.IDependencies#getLastModified()
	 */
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
						final Map<String, URI> baseOverrideURIs = new LinkedHashMap<String, URI>();
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
								baseURIs.put("",  base.getPrimary()); //$NON-NLS-1$
								if (base.getOverride() != null) {
									baseOverrideURIs.put("", base.getOverride()); //$NON-NLS-1$
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
						paths.addAll(baseOverrideURIs.values());
						paths.addAll(packageURIs.values());
						paths.addAll(packageOverrideURIs.values());
						paths.addAll(pathURIs.values());
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
								deps.mapDependencies(depTree, baseURIs, true);
								deps.mapDependencies(depTree, baseOverrideURIs, false);
								deps.mapDependencies(depTree, packageURIs, true);
								deps.mapDependencies(depTree, packageOverrideURIs, false);
								deps.mapDependencies(depTree, pathURIs, true);
								deps.mapDependencies(depTree, pathOverrideURIs, false);
								depTree.normalizeDependencies();
								DependenciesImpl.this.depMap = new HashMap<String, DepTreeNode.DependencyInfo>();
								depTree.populateDepMap(depMap);
								depsLastModified = depTree.lastModifiedDepTree();
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
						IServiceReference[] refs = null;

						refs = aggregator.getPlatformServices().getServiceReferences(IDependenciesListener.class.getName(),"(name="+servletName+")"); //$NON-NLS-1$ //$NON-NLS-2$

						if (refs != null) {
							for (IServiceReference ref : refs) {
								IDependenciesListener listener = (IDependenciesListener)(aggregator.getPlatformServices().getService(ref));
								if (listener != null) {
									try {
										listener.dependenciesLoaded(DependenciesImpl.this, sequence);
									} catch (Exception e) {
										if (log.isLoggable(Level.SEVERE)) {
											log.log(Level.SEVERE, e.getMessage(), e);
										}
									} finally {
										aggregator.getPlatformServices().ungetService(ref);
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

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.deps.IDependencies#getDelcaredDependencies(java.lang.String)
	 */
	@Override
	public List<String> getDelcaredDependencies(String mid) throws ProcessingDependenciesException {
		List<String> result = null;
		try {
			getReadLock();
			try {
				DepTreeNode.DependencyInfo depInfo = depMap.get(mid);
				if (depInfo != null) {
					result = depInfo.getDeclaredDependencies();
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
	public URI getURI(String mid) throws ProcessingDependenciesException {
		URI result = null;
		try {
			getReadLock();
			try {
				DepTreeNode.DependencyInfo depInfo = depMap.get(mid);
				if (depInfo != null) {
					result = depInfo.getURI();
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

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.deps.IDependencies#getDependentFeatures()
	 */
	@Override
	public List<String> getDependentFeatures(String mid) throws ProcessingDependenciesException {
		List<String> result = null;
		try {
			getReadLock();
			try {
				DepTreeNode.DependencyInfo depInfo = depMap.get(mid);
				if (depInfo != null) {
					result = depInfo.getDepenedentFeatures();
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

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.deps.IDependencies#getDependencyNames()
	 */
	@Override
	public Iterable<String> getDependencyNames() throws ProcessingDependenciesException {
		Iterable<String> result = null;
		try {
			getReadLock();
			try {
				result = depMap.keySet();
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
