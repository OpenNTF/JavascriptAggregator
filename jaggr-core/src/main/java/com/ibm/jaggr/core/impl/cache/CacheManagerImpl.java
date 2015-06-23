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

package com.ibm.jaggr.core.impl.cache;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.PlatformServicesException;
import com.ibm.jaggr.core.cache.CacheControl;
import com.ibm.jaggr.core.cache.ICache;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.cache.ICacheManagerListener;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigListener;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.IDependenciesListener;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.util.ConsoleService;
import com.ibm.jaggr.core.util.CopyUtil;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class CacheManagerImpl implements ICacheManager, IShutdownListener, IConfigListener, IDependenciesListener, IOptionsListener {

	private static final Logger log = Logger.getLogger(CacheManagerImpl.class.getName());

	private static final String CACHEDIR_NAME = "cache"; //$NON-NLS-1$
	/**
	 * Reference the cache with an atomic reference so that we don't need to synchronize
	 * access to it.  The atomic reference is needed for when we swap the cache out with
	 * a new empty one when processing the clearcache command.
	 */
	private final AtomicReference<CacheImpl>  _cache = new AtomicReference<CacheImpl>();

	/** The filename for the serialized cache object */
	private static final String CACHE_META_FILENAME = "metadata.cache"; //$NON-NLS-1$

	/** The cache directory */
	private final File _directory;

	private CacheControl _control;

	private IAggregator _aggregator;

	private List<IServiceRegistration> _serviceRegistrations = new ArrayList<IServiceRegistration>();

	private long updateSequenceNumber = 0;

	private Object cacheSerializerSyncObj = new Object();

	/**
	 * Starts up the cache. Attempts to de-serialize a previously serialized
	 * cache from disk and starts the periodic serializer task.
	 *
	 * @param aggregator
	 *            the aggregator instance this cache manager belongs to
	 * @param stamp
	 *            a time stamp used to determine if the cache should be cleared.
	 *            The cache should be cleared if the time stamp is later than
	 *            the one associated with the cached resources.
	 * @throws IOException
	 */
	public CacheManagerImpl(IAggregator aggregator, long stamp) throws IOException {

		_directory = new File(aggregator.getWorkingDirectory(), CACHEDIR_NAME);
		_aggregator = aggregator;
		// Make sure the cache directory exists
		if (!_directory.exists()) {
			if (!_directory.mkdirs()) {
				throw new IOException(MessageFormat.format(
						Messages.CacheManagerImpl_0,
						new Object[]{_directory.getAbsoluteFile()}
						));
			}
		}
		// Attempt to de-serialize the cache from disk
		CacheImpl cache = null;
		try {
			File file = new File(_directory, CACHE_META_FILENAME);
			ObjectInputStream is = new ObjectInputStream(new FileInputStream(file));
			try {
				cache = (CacheImpl)is.readObject();
			} finally {
				try { is.close(); } catch (Exception ignore) {}
			}
		} catch (FileNotFoundException e) {
			if (log.isLoggable(Level.INFO))
				log.log(Level.INFO, Messages.CacheManagerImpl_1);
		} catch (InvalidClassException e) {
			if (log.isLoggable(Level.INFO))
				log.log(Level.INFO, Messages.CacheManagerImpl_2);
			// one or more of the serializable classes has changed.  Delete the stale
			// cache files
		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE))
				log.log(Level.SEVERE, e.getMessage(), e);
		}
		if (cache != null) {
			_control = (CacheControl)cache.getControlObj();
		}
		if (_control != null) {
			// stamp == 0 means no overrides.  Need to check for this explicitly
			// in case the overrides directory has been removed.
			if (stamp == 0 && _control.getInitStamp() == 0 ||
					stamp != 0 && stamp <= _control.getInitStamp()) {
				// Use AggregatorProxy so that getCacheManager will return non-null
				// if called from within setAggregator.  Need to do this because
				// IAggregator.getCacheManager() is unable to return this object
				// since it is still being constructed.
				cache.setAggregator(AggregatorProxy.newInstance(_aggregator, this));
				_cache.set(cache);
			}
		} else {
			_control = new CacheControl();
			_control.setInitStamp(stamp);
		}

		// Start up the periodic serializer task.  Serializes the cache every 10 minutes.
		// This is done so that we can recover from an unexpected shutdown
		aggregator.getExecutors().getScheduledExecutor().scheduleAtFixedRate(new Runnable() {
			public void run() {
				try {
					File file = new File(_directory, CACHE_META_FILENAME);
					// Synchronize on the cache object to keep the scheduled cache sync thread and
					// the thread processing servlet destroy from colliding.
					synchronized(cacheSerializerSyncObj) {
						ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
						try {
							os.writeObject(_cache.get());
						} finally {
							try { os.close(); } catch (Exception ignore) {}
						}
					}
				} catch(Exception e) {
					if (log.isLoggable(Level.SEVERE))
						log.log(Level.SEVERE, e.getMessage(), e);
				}
			}
		}, 10, 10, TimeUnit.MINUTES);

		Dictionary<String,String> dict;


		if(_aggregator.getPlatformServices() != null){
			dict = new Hashtable<String, String>();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			_serviceRegistrations.add(_aggregator.getPlatformServices().registerService(IShutdownListener.class.getName(), this, dict));

			dict = new Hashtable<String, String>();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			_serviceRegistrations.add(_aggregator.getPlatformServices().registerService(IConfigListener.class.getName(), this, dict));

			dict = new Hashtable<String, String>();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			_serviceRegistrations.add(_aggregator.getPlatformServices().registerService(IDependenciesListener.class.getName(), this, dict));

			dict = new Hashtable<String, String>();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			_serviceRegistrations.add(_aggregator.getPlatformServices().registerService(IOptionsListener.class.getName(), this, dict));
		}

		// Now invoke the listeners for objects that have already been initialized
		IOptions options = _aggregator.getOptions();
		if (options != null) {
			optionsUpdated(options, 1);
		}
		IConfig config = _aggregator.getConfig();
		if (config != null) {
			configLoaded(config, 1);
		}

		IDependencies deps = _aggregator.getDependencies();
		if (deps != null) {
			dependenciesLoaded(deps, 1);
		}

		// notify listeners that we're initialized
		notifyInit();
	}

	public synchronized void clearCache() {
		CacheImpl newCache = new CacheImpl(_aggregator, _control, _cache.get());
		// Use AggregatorProxy so that getCacheManager will return non-null
		// if called from within setAggregator.  Need to do this because
		// IAggregator.getCacheManager() may be unable to return this object
		// if it is still being constructed.
		newCache.setAggregator(AggregatorProxy.newInstance(_aggregator, this));
		clean(_directory);
		CacheImpl oldCache = _cache.getAndSet(newCache);
		if (oldCache != null) {
			oldCache.clear();
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICacheManager#dumpCache(java.io.Writer, java.util.regex.Pattern)
	 */
	@Override
	public void dumpCache(Writer writer, Pattern filter) throws IOException {
		_cache.get().dump(writer, filter);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICacheManager#shutdown()
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		for(IServiceRegistration reg : _serviceRegistrations) {
			reg.unregister();
		}
		_serviceRegistrations.clear();

		// Serialize the cache metadata one last time
		serializeCache();

		// avoid memory leaks caused by circular references
		_aggregator = null;
		_cache.set(null);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICacheManager#getCacheDir()
	 */
	@Override
	public File getCacheDir() {
		return _directory;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICacheManager#getCache()
	 */
	@Override
	public ICache getCache() {
		return _cache.get();
	}

	/**
	 * Serializes the specified cache object to the sepecified directory.  Note that we
	 * actually serialize a clone of the specified cache because some of the objects
	 * that are serialized require synchronization and we don't want to cause service
	 * threads to block while we are doing file I/O.
	 */
	@Override
	public void serializeCache() {

		// Queue up the serialization behind any pending cache file creations.
		Future<Void> future = _aggregator.getExecutors().getFileCreateExecutor().submit(new Callable<Void>() {
			public Void call() {
				// Synchronize on the cache object to keep the scheduled cache sync thread and
				// the thread processing servlet destroy from colliding.
				synchronized(cacheSerializerSyncObj) {
					File cacheFile = new File(_directory, CACHE_META_FILENAME);
					File controlFile = new File(new File(_directory, ".."), CacheControl.CONTROL_SERIALIZATION_FILENAME); //$NON-NLS-1$
					try {
						// Serialize the cache
						ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(cacheFile));
						os.writeObject(_cache.get());
						os.close();
						// Serialize the control object to the parent directory so the
						// aggregator can manage the cache primer
						os = new ObjectOutputStream(new FileOutputStream(controlFile));
						os.writeObject(_cache.get().getControlObj());
						os.close();
					} catch(Exception e) {
						throw new RuntimeException(e);
					}
				}
				return null;
			}
		});

		// Wait for the serialization to complete before returning.
		try {
			future.get(5, TimeUnit.MINUTES);	// time-out after 5 minutes
		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE))
				log.log(Level.SEVERE, e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}

	private void clean(File directory) {
		final File[] oldCacheFiles = directory.listFiles();
		if (oldCacheFiles != null) {
			_aggregator.getExecutors().getScheduledExecutor().submit(new Runnable() {
				public void run() {
					for (File file : oldCacheFiles) {
						try {
							if (!file.delete()) {
								if (log.isLoggable(Level.WARNING)) {
									log.warning(MessageFormat.format(
											Messages.CacheManagerImpl_8,
											new Object[]{file.getAbsolutePath()}
											));
								}
							}
						} catch (Exception e) {
							if (log.isLoggable(Level.WARNING)) {
								log.log(Level.WARNING, e.getMessage(), e);
							}
						}
					}
				}
			});
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICacheManager#createCacheFileAsync(java.lang.String, java.io.Reader, com.ibm.jaggr.service.cache.ICacheManager.CreateCompletionCallback)
	 */
	@Override
	public void createCacheFileAsync(final String fileNamePrefix, final Reader reader,
			final CreateCompletionCallback callback) {
		createCacheFileAsync(fileNamePrefix, new ReaderInputStream(reader, "UTF-8"), callback); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICacheManager#createCacheFileAsync(java.lang.String, java.io.InputStream, com.ibm.jaggr.service.cache.ICacheManager.CreateCompletionCallback)
	 */
	@Override
	public void createCacheFileAsync(final String fileNamePrefix, final InputStream is,
			final CreateCompletionCallback callback) {

		_aggregator.getExecutors().getFileCreateExecutor().submit(new Runnable() {
			public void run() {
				File file = null;
				try {
					file = File.createTempFile(fileNamePrefix, ".cache", _directory); //$NON-NLS-1$
					OutputStream os = new FileOutputStream(file);
					CopyUtil.copy(is, os);
					if (callback != null) {
						callback.completed(file.getName(), null);
					}
				} catch (IOException e) {
					if (log.isLoggable(Level.WARNING))
						log.log(Level.WARNING, MessageFormat.format(
								Messages.CacheManagerImpl_4, new Object[]{file.getPath()}), e);
					if (callback != null) {
						callback.completed(file.getName(), e);
					}
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICacheManager#externalizeObjectAsync(java.lang.String, java.lang.Object, com.ibm.jaggr.service.cache.ICacheManager.CreateCompletionCallback)
	 */
	@Override
	public void externalizeCacheObjectAsync(final String fileNamePrefix, final Object object,
			final CreateCompletionCallback callback) {
		_aggregator.getExecutors().getFileCreateExecutor().submit(new Runnable() {
			File file = null;
			public void run() {
				try {
					file = File.createTempFile(fileNamePrefix, ".cache", _directory); //$NON-NLS-1$
					ObjectOutputStream os;
					os = new ObjectOutputStream(new FileOutputStream(file));
					try {
						os.writeObject(object);
					} finally {
						try { os.close(); } catch (Exception ignore) {}
					}
					if (callback != null) {
						callback.completed(file.getName(), null);
					}
				} catch (IOException e) {
					if (log.isLoggable(Level.WARNING))
						log.log(Level.WARNING, MessageFormat.format(
								Messages.CacheManagerImpl_4, new Object[]{file.getPath()}), e);
					if (callback != null) {
						callback.completed(file != null ? file.getName() : null, e);
					}
				}
			}
		});
	}

	@Override
	public void createNamedCacheFileAsync(final String filename, final InputStream is,
			final CreateCompletionCallback callback) {
		_aggregator.getExecutors().getFileCreateExecutor().submit(new Runnable() {
			File file = null;
			public void run() {
				try {
					file = new File(_directory, filename);
					OutputStream os = new FileOutputStream(file.isAbsolute() ? file : new File(_directory, file.getPath()));
					CopyUtil.copy(is, os);
					if (callback != null) {
						callback.completed(file.getName(), null);
					}
				} catch (IOException e) {
					if (log.isLoggable(Level.WARNING))
						log.log(Level.WARNING, MessageFormat.format(
								Messages.CacheManagerImpl_4, new Object[]{file.getPath()}), e);
					if (callback != null) {
						callback.completed(file != null ? file.getName() : null, e);
					}
				}
			}
		});
	}

	@Override
	public void createNamedCacheFileAsync(String fileNamePrefix, Reader reader,
			CreateCompletionCallback callback) {
		createNamedCacheFileAsync(fileNamePrefix, new ReaderInputStream(reader, "UTF-8"), callback); //$NON-NLS-1$
	}
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cache.ICacheManager#deleteFileDelayed(java.lang.String)
	 */
	public void deleteFileDelayed(final String fname) {
		_aggregator.getExecutors().getFileDeleteExecutor().schedule(new Runnable() {
			public void run() {
				File file = new File(_directory, fname);
				try {
					if (!file.delete()) {
						if (log.isLoggable(Level.WARNING)) {
							log.warning(MessageFormat.format(
									Messages.CacheManagerImpl_8,
									new Object[]{file.getAbsolutePath()}
									));
						}
					}
				} catch (Exception e) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, e.getMessage(), e);
					}
				}
			}
		}, _aggregator.getOptions().getDeleteDelay(), TimeUnit.SECONDS);

	}

	@Override
	public synchronized void optionsUpdated(IOptions options, long sequence) {
		final String sourceMethod = "optionsUpdated"; //$NON-NLS-1$
		if (options == null) {
			return;
		}
		if (_cache.get() == null || !options.getOptionsMap().equals(_control.getOptionsMap())) {
			if (log.isLoggable(Level.FINER)) {
				String msg = "No local cache"; //$NON-NLS-1$
				if (_cache.get() != null) {
					msg = "options = " + options.getOptionsMap().toString() + " -- options from cache = " + //$NON-NLS-1$ //$NON-NLS-2$
						_control.getOptionsMap();
				}
				log.logp(Level.FINER, CacheManagerImpl.class.getName(), sourceMethod, msg);
			}
			Map<String, String> previousOptions = _control.getOptionsMap();
			_control.setOptionsMap(options.getOptionsMap());
			if (_cache.get() == null || previousOptions != null) {
				if (sequence > updateSequenceNumber) {
					if (_cache.get() != null) {
						updateSequenceNumber = sequence;
						String msg = MessageFormat.format(
								Messages.CacheManagerImpl_5,
								new Object[]{_aggregator.getName()}
								);
						new ConsoleService().println(msg);
						if (log.isLoggable(Level.INFO)) {
							log.info(msg);
						}
					}
					clearCache();
				}
			}
		}
	}

	@Override
	public synchronized void dependenciesLoaded(IDependencies deps, long sequence) {
		final String sourceMethod = "dependenciesLoaded"; //$NON-NLS-1$
		if (deps == null) {
			return;
		}
		long lastMod = deps.getLastModified();
		if (_cache.get() == null || lastMod > _control.getDepsLastMod()) {
			if (log.isLoggable(Level.FINER)) {
				String msg = "No local cache"; //$NON-NLS-1$
				if (_cache.get() != null) {
					msg = "Dependencies last-modified = " + lastMod + " -- Cached dependencies last-modified = " + //$NON-NLS-1$ //$NON-NLS-2$
						_control.getDepsLastMod();
				}
				log.logp(Level.FINER, CacheManagerImpl.class.getName(), sourceMethod, msg);
			}
			long previousLastMod = _control.getDepsLastMod();
			_control.setDepsLastMod(lastMod);
			if (previousLastMod != -1 || _cache.get() == null) {
				if (sequence > updateSequenceNumber) {
					if (_cache.get() != null) {
						updateSequenceNumber = sequence;
						String msg = MessageFormat.format(
								Messages.CacheManagerImpl_6,
								new Object[]{ _aggregator.getName()}
								);
						new ConsoleService().println(msg);
						if (log.isLoggable(Level.INFO)) {
							log.info(msg);
						}
					}
					clearCache();
				}
			}
		}
	}

	@Override
	public synchronized void configLoaded(IConfig config, long sequence) {
		final String sourceMethod = "configLoaded"; //$NON-NLS-1$
		if (config == null) {
			return;
		}
		String rawConfig = config.toString();
		if (_cache.get() == null || !StringUtils.equals(rawConfig, _control.getRawConfig())) {
			if (log.isLoggable(Level.FINER)) {
				String msg = "No local cache"; //$NON-NLS-1$
				if (_cache.get() != null) {
					msg = "config = " + rawConfig + " -- config from cache = " + //$NON-NLS-1$ //$NON-NLS-2$
						_control.getRawConfig();
				}
				log.logp(Level.FINER, CacheManagerImpl.class.getName(), sourceMethod, msg);
			}
			Object previousConfig = _control.getRawConfig();
			_control.setRawConfig(rawConfig);
			if (_cache.get() == null || previousConfig != null) {
				if (sequence > updateSequenceNumber) {
					if (_cache.get() != null) {
						updateSequenceNumber = sequence;
						String msg = MessageFormat.format(
								Messages.CacheManagerImpl_7,
								new Object[]{ _aggregator.getName()}
								);
						new ConsoleService().println(msg);
						if (log.isLoggable(Level.INFO)) {
							log.info(msg);
						}
					}
					clearCache();
				}
			}
		}
	}

	/**
	 * Notify listeners that the cache manager is initialized.
	 */
	protected void notifyInit () {
		final String sourceMethod = "notifyInit"; //$NON-NLS-1$
		IServiceReference[] refs = null;
		try {
			if(_aggregator != null && _aggregator.getPlatformServices() != null){
				refs = _aggregator.getPlatformServices().getServiceReferences(ICacheManagerListener.class.getName(),"(name=" + _aggregator.getName() + ")");	//$NON-NLS-1$ //$NON-NLS-2$
				if (refs != null) {
					for (IServiceReference ref : refs) {
						ICacheManagerListener listener = (ICacheManagerListener)_aggregator.getPlatformServices().getService(ref);
						if (listener != null) {
							try {
								listener.initialized(this);
							} catch (Throwable t) {
								if (log.isLoggable(Level.WARNING)) {
									log.logp(Level.WARNING, CacheManagerImpl.class.getName(), sourceMethod, t.getMessage(), t);
								}
							} finally {
								_aggregator.getPlatformServices().ungetService(ref);
							}
						}
					}
				}
			}
		} catch (PlatformServicesException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}

}
