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

package com.ibm.jaggr.service.impl.cache;

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
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IShutdownListener;
import com.ibm.jaggr.service.cache.ICache;
import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.config.IConfigListener;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.deps.IDependenciesListener;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.options.IOptionsListener;
import com.ibm.jaggr.service.util.ConsoleService;
import com.ibm.jaggr.service.util.CopyUtil;

public class CacheManagerImpl implements ICacheManager, IShutdownListener, IConfigListener, IDependenciesListener, IOptionsListener {
    
    private static final Logger log = Logger.getLogger(ICacheManager.class.getName());
    
    private static final String CACHEDIR_NAME = "cache"; //$NON-NLS-1$
    private static final int CACHE_INITIAL_SIZE = 50;
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
    
    /** The initial cache size for the hash maps */
    private final int _initialSize;
    
    private Map<String, String> _optionsMap;
    
    private IAggregator _aggregator;
    
    private String _rawConfig;
    
    private long _depsLastMod;
    
    private ServiceRegistration _shutdownListener = null;
    
    private ServiceRegistration _configUpdateListener = null;
    
    private ServiceRegistration _depsUpdateListener = null;
    
    private ServiceRegistration _optionsUpdateListener = null;
    
    private long updateSequenceNumber = 0;
    
    private Object cacheSerializerSyncObj = new Object();
    
    /**
     * Starts up the cache.  Attempts to de-serialize a previously serialized cache from
     * disk and starts the periodic serializer task.
     * 
     * @param directory The location of the cache directory on the file system
     * @param initialSize The initial cache size
     */
    public CacheManagerImpl(IAggregator aggregator) throws IOException {
    	
        _directory = new File(aggregator.getWorkingDirectory(), CACHEDIR_NAME);
        _initialSize = CACHE_INITIAL_SIZE;
        _aggregator = aggregator;
        IOptions options = aggregator.getOptions();
        if (options != null) {
        	_optionsMap = options.getOptionsMap();
        }
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
    		cache = (CacheImpl)is.readObject();
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
            _cache.set(cache);
    	} else if (_optionsMap != null){
    		clearCache();
    	}
    	
    	_rawConfig = cache != null ? cache.getRawConfig() : null; 
    	_depsLastMod = cache != null ? cache.getDepsLastModified() : -1; 
    	
        // Start up the periodic serializer task.  Serializes the cache every 10 minutes.
        // This is done so that we can recover from an unexpected shutdown
        aggregator.getExecutors().getCacheSerializeExecutor().scheduleAtFixedRate(new Runnable() {
        	public void run() {
        		try {
        			File file = new File(_directory, CACHE_META_FILENAME);
        			ICache clone = (ICache)_cache.get().clone();
        			// Synchronize on the cache object to keep the scheduled cache sync thread and
        			// the thread processing servlet destroy from colliding.
        			synchronized(cacheSerializerSyncObj) {
        				ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
        				os.writeObject(clone);
        				os.close();
        			}
        		} catch(Exception e) {
        			if (log.isLoggable(Level.SEVERE))
        				log.log(Level.SEVERE, e.getMessage(), e);
        		}
        	}
        }, 10, 10, TimeUnit.MINUTES);
        
		Properties dict;
		BundleContext bundleContext = aggregator.getBundleContext();
		if (bundleContext != null) {
	        // Register listeners
			dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			_shutdownListener = bundleContext.registerService(
					IShutdownListener.class.getName(), 
					this, 
					dict
			);
	
			dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			_configUpdateListener = bundleContext.registerService(
					IConfigListener.class.getName(), 
					this, 
					dict
			);
			
			dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			_depsUpdateListener = bundleContext.registerService(
					IDependenciesListener.class.getName(), 
					this, 
					dict
			);
			
			dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			_optionsUpdateListener = bundleContext.registerService(
					IOptionsListener.class.getName(), 
					this, 
					dict
			);
			optionsUpdated(aggregator.getOptions(), 1);
	        configLoaded(aggregator.getConfig(), 1);
	        dependenciesLoaded(aggregator.getDependencies(), 1);
		}        
    }
    
    public synchronized void clearCache() {
    	CacheImpl newCache = new CacheImpl(_initialSize, _rawConfig, _depsLastMod, _optionsMap);
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
    	if (_shutdownListener != null) {
    		_shutdownListener.unregister();
    	}
		if (_configUpdateListener != null) {
			_configUpdateListener.unregister();
		}
		if (_depsUpdateListener != null) {
			_depsUpdateListener.unregister();
		}
		if (_optionsUpdateListener != null) {
			_optionsUpdateListener.unregister();
		}
    	
		// Serialize the cache metadata one last time
		serializeCache();
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
     * 
     * @param cache The object to serialize
     * @param directory The target directory
     */
    private void serializeCache() {
		try {
			File file = new File(_directory, CACHE_META_FILENAME);
			ICache clone = (ICache)_cache.get().clone();
			// Synchronize on the cache object to keep the scheduled cache sync thread and
			// the thread processing servlet destroy from colliding.
			synchronized(cacheSerializerSyncObj) {
				ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
				os.writeObject(clone);
				os.close();
			}
		} catch(Exception e) {
			if (log.isLoggable(Level.SEVERE))
				log.log(Level.SEVERE, e.getMessage(), e);
		}
    }
    
    private void clean(File directory) {
    	final File[] oldCacheFiles = directory.listFiles();
    	_aggregator.getExecutors().getCacheSerializeExecutor().submit(new Runnable() {
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
		if (options == null) {
			return;
		}
		if (_cache.get() == null || !options.getOptionsMap().equals(_cache.get().getOptionsMap())) {
			Map<String, String> previousOptions = _optionsMap;
			_optionsMap = options.getOptionsMap();
			if (_cache.get() == null || previousOptions != null) {
				if (sequence > updateSequenceNumber || _cache.get() == null) {
					updateSequenceNumber = sequence;
					String msg = MessageFormat.format(
							Messages.CacheManagerImpl_5,
							new Object[]{_aggregator.getName()}
					);
					new ConsoleService().println(msg);
					if (log.isLoggable(Level.INFO)) {
						log.info(msg);
					}
					clearCache();
				}
			} else {
				_cache.get().setOptionsMap(_optionsMap);
			}
		}
	}

	@Override
	public synchronized void dependenciesLoaded(IDependencies deps, long sequence) {
		if (deps == null) {
			return;
		}
		if (_cache.get() == null || deps.getLastModified() > _cache.get().getDepsLastModified()) {
			long previousLastMod = _depsLastMod;
			_depsLastMod = deps.getLastModified();
			if (previousLastMod != -1 || _cache.get() == null) {
				if (sequence > updateSequenceNumber) {
					updateSequenceNumber = sequence;
					String msg = MessageFormat.format(
							Messages.CacheManagerImpl_6,
							new Object[]{ _aggregator.getName()}
					);
					new ConsoleService().println(msg);
					if (log.isLoggable(Level.INFO)) {
						log.info(msg);
					}
					clearCache();
				}
			} else {
				// if the previous last modified was -1, then we are being initialized so no need
				// to clear the cache.
				_cache.get().setDepsLastModified(_depsLastMod);
			}
		}
	}

	@Override
	public synchronized void configLoaded(IConfig config, long sequence) {
		if (config == null) {
			return;
		}
		String rawConfig = null;
		rawConfig = config.getRawConfig().toString();
		if (_cache.get() == null || !StringUtils.equals(rawConfig, _cache.get().getRawConfig())) {
			Object previousConfig = _rawConfig;
			_rawConfig = rawConfig;
			if (previousConfig != null || _cache.get() == null) {
				if (sequence > updateSequenceNumber) {
					updateSequenceNumber = sequence;
					String msg = MessageFormat.format(
							Messages.CacheManagerImpl_7,
							new Object[]{ _aggregator.getName()}
					);
					new ConsoleService().println(msg);
					if (log.isLoggable(Level.INFO)) {
						log.info(msg);
					}
					clearCache();
				}
			} else {
				_cache.get().setRawConfig(_rawConfig);
			}
		}
	}
}
