/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.layer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.NotFoundException;
import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.cachekeygenerator.AbstractCacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.impl.module.NotFoundModule;
import com.ibm.jaggr.service.layer.ILayer;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.module.ModuleIdentifier;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.readers.BuildListReader;
import com.ibm.jaggr.service.readers.ModuleBuildReader;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.transport.IHttpTransport.LayerContributionType;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.DependencyList;
import com.ibm.jaggr.service.util.Features;
import com.ibm.jaggr.service.util.TypeUtil;

/**
 * A LayerImpl is a collection of LayerBuild objects that are composed using the same
 * list of modules, but vary according to build options, compilation level, has filtering,
 * etc. 
 */
public class LayerImpl implements ILayer {
	private static final long serialVersionUID = 4304278339204787218L;
	private static final Logger log = Logger.getLogger(LayerImpl.class.getName());
    public static final String PREAMBLEFMT = "\n/*-------- %s --------*/\n"; //$NON-NLS-1$
    public static final String LAST_MODIFIED_PROPNAME = LayerImpl.class.getName() + ".LAST_MODIFIED_FILES"; //$NON-NLS-1$
    public static final String MODULE_FILES_PROPNAME = LayerImpl.class.getName() + ".MODULE_FILES"; //$NON-NLS-1$
    public static final String LAYERCACHEINFO_PROPNAME = LayerImpl.class.getName() + ".LAYER_CACHEIFNO"; //$NON-NLS-1$
    public static final String MODULECACHEIFNO_PROPNAME = LayerImpl.class.getName() + ".MODULE_CACHEINFO"; //$NON-NLS-1$
    
    protected static final ICacheKeyGenerator[] layerCacheKeyGenerators  = new ICacheKeyGenerator[]{
    	new AbstractCacheKeyGenerator() {
			private static final long serialVersionUID = 2013098945317787755L;
			@Override
			public String generateKey(HttpServletRequest request) {
				boolean showFilenames =  TypeUtil.asBoolean(request.getAttribute(IHttpTransport.SHOWFILENAMES_REQATTRNAME));
				return "sn:" + (showFilenames ? "1" : "0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			@Override
			public String toString() {
				return "sn"; //$NON-NLS-1$
			}
    	}
    };
    	
    
    /**
     * Map of cache dependency objects for module classes included in this layer.
     */
	private volatile Map<String, ICacheKeyGenerator> _cacheKeyGenerators = null;
	
	/**
	 * The last modified date of the layer.  This is the latest of the last modified dates
	 * of all the modules in the module list
	 */
	private volatile long _lastModified = -1;
	
	/**
	 * Flag to indicate whether or not last modified time needs to be validated.  Transient
	 * because we always want last-modified validated on startup.
	 */
	private transient AtomicBoolean _validateLastModified = new AtomicBoolean(true);
	
	/** The map of key/builds for this layer. */
	private ConcurrentMap<String, CacheEntry> _layerBuilds = null;
	
	/** 
	 * Flag to indicate that addition information used unit tests should be added to the
	 * request object
	 */
	private transient boolean _isReportCacheInfo = false;
	
	/**
	 * @param moduleList The folded module list as specified in the request
	 */
	public LayerImpl() {
	}
	
	/**
	 * @param isReportCacheInfo
	 */
	public void setReportCacheInfo(boolean isReportCacheInfo) {
		this._isReportCacheInfo = isReportCacheInfo;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayer#getInputStream(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	public InputStream getInputStream(HttpServletRequest request, 
			HttpServletResponse response) throws IOException {
		
		final boolean isLogLevelFiner = log.isLoggable(Level.FINER);
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		ICacheManager mgr = aggr.getCacheManager();
		boolean ignoreCached = options.isDevelopmentMode() &&
				TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOCACHE_REQATTRNAME));
        Map<String, ICacheKeyGenerator> cacheKeyGenerators;
        ConcurrentMap<String, CacheEntry> layerBuilds, oldLayerBuilds = null;
        InputStream result;
        long lastModified = getLastModified(request);

        if (ignoreCached) {
        	request.setAttribute(NOCACHE_RESPONSE_REQATTRNAME, Boolean.TRUE);
        }
        synchronized(this) {
        	// See if we need to discard previously built LayerBuilds
        	if (options.isDevelopmentMode() && lastModified > _lastModified) {
        		if (isLogLevelFiner){
        			log.finer("Resetting cached layer builds for layer " +  //$NON-NLS-1$
        					request.getAttribute(IHttpTransport.REQUESTEDMODULES_REQATTRNAME).toString() + 
        					"\nOld last modified=" + _lastModified + ", new last modified=" + lastModified); //$NON-NLS-1$ //$NON-NLS-2$
        		}
        		if (lastModified != Long.MAX_VALUE) {
        			// max value means missing requested source
        			_lastModified = lastModified;
        		}
        		oldLayerBuilds = _layerBuilds;
        		_cacheKeyGenerators = null;
        		_layerBuilds = null;
        	}
        	// Copy volatile instance variables to final local variables so we can 
        	// continue to reference them after releasing the lock without worrying
        	// that they might be modified by another thread.
        	cacheKeyGenerators = _cacheKeyGenerators;
        	layerBuilds = _layerBuilds = (_layerBuilds != null) ? 
        			_layerBuilds : new ConcurrentHashMap<String, CacheEntry>();
        }

        // If we have stale cache files, queue them up for deletion
        if (oldLayerBuilds != null) {
        	for (Map.Entry<String, CacheEntry> entry : oldLayerBuilds.entrySet()) {
        		entry.getValue().delete(mgr);
        	}
        }
        
        // Creata a cache key.
        String key = generateCacheKey(request);

        // Try retrieving the cached layer build first using get() since it doesn't block.  If that fails,
        // then try again using the locking putIfAbsent()
        CacheEntry existingEntry = (key != null) ? layerBuilds.get(key) : null;
        if (!ignoreCached  && existingEntry != null) {
        	try {
	        	result = existingEntry.tryGetInputStream(request, response);
	        	if (result != null) {
		        	if (isLogLevelFiner) {
		        		log.finer("returning cached layer build with cache key: " + key); //$NON-NLS-1$
		        	}
		        	if (_isReportCacheInfo)
		        		request.setAttribute(LAYERCACHEINFO_PROPNAME, "hit_1"); //$NON-NLS-1$
					return result;
	        	} 
        	} catch (IOException e) {
        		// Something happened to the cached result (deleted???)  
	        	if (log.isLoggable(Level.WARNING)) {
	        		log.log(Level.WARNING, e.getMessage(), e);
	        	}
        		// remove the cache entry
        		layerBuilds.remove(key, existingEntry);
        		existingEntry = null;
	        }
        }
        
		CacheEntry newEntry = new CacheEntry();
		// Try to retrieve an existing layer build using the blocking putIfAbsent.  If the return 
		// value is null, then the newEntry was successfully added to the map, otherwise the 
		// existing entry is returned in the buildReader and newEntry was not added.
		if (!ignoreCached && key != null) {
			existingEntry = layerBuilds.putIfAbsent(key, newEntry);
		}
		if (!ignoreCached && existingEntry != null 
			&& (result = existingEntry.tryGetInputStream(request, response)) != null) {
        	if (isLogLevelFiner) {
        		log.finer("returning cached layer build with cache key: " + key); //$NON-NLS-1$
        	}
        	if (_isReportCacheInfo) {
        		request.setAttribute(LAYERCACHEINFO_PROPNAME, "hit_2"); //$NON-NLS-1$
        	}
			return result;
		}
    	if (_isReportCacheInfo)
    		request.setAttribute(LAYERCACHEINFO_PROPNAME, "add"); //$NON-NLS-1$

		// putIfAbsent() succeeded and the new entry was added to the cache
		CacheEntry entry = (existingEntry != null) ? existingEntry : newEntry;
		
		BuildListReader in;
		
        // List of Future<IModule.ModuleReader> objects that will be used to read the module
        // data from
        List<Future<ModuleBuildReader>> futures;

        // Synchronize on the LayerBuild object for the build.  This will prevent multiple
		// threads from building the same output.  If more than one thread requests the same
		// output (same cache key), then the first one to grab the sync object will win and
		// the rest will wait for the first thread to finish building and then just return
		// the output from the first thread when they wake.
        synchronized(entry) {

        	// Check to see if data is available one more time in case a different thread finished
			// building the output while we were blocked on the sync object.
        	if (!ignoreCached && key != null && (result = entry.tryGetInputStream(request, response)) != null) {
            	if (isLogLevelFiner) {
            		log.finer("returning built layer with cache key: " + key); //$NON-NLS-1$
            	}
        		return result;
        	}

        	if (isLogLevelFiner) {
        		log.finer("Building layer with cache key: " + key); //$NON-NLS-1$
        	}
        	
			futures = collectFutures(request, ignoreCached);

	        // Create a BuildListReader from the list of Futures.  This reader will obtain a 
	        // ModuleReader from each of the Futures in the list and read data from each one in
	        // succession until all the data has been read, blocking on each Future until the 
	        // reader becomes available.
			in = new BuildListReader(futures);
			
			// Create the compression stream for the output
	        ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        VariableGZIPOutputStream compress = new VariableGZIPOutputStream(bos, 10240);  // is 10k too big?
	        compress.setLevel(Deflater.BEST_COMPRESSION);
	        Writer writer = new OutputStreamWriter(compress, "UTF-8"); //$NON-NLS-1$

	        // Copy the data from the input stream to the output, compressing as we go.
	        CopyUtil.copy(in, writer);
            
            // Set the buildReader to the LayerBuild and release the lock by exiting the sync block
            entry.setBytes(bos.toByteArray());
        }
        
    	// if any of the readers included an error response, then don't cache the layer.
        if (in.hasErrors()) {
        	request.setAttribute(NOCACHE_RESPONSE_REQATTRNAME, Boolean.TRUE);
        	if (key != null) {
        		layerBuilds.remove(key, entry);
        	}
        } else {
        	if (!ignoreCached) {	
		        // If we don't yet have a cache key for this layer, then get one 
				// from the cache key generators, and then update the cache key for this 
	        	// cache entry.
		        if (key == null) {
		        	if (_cacheKeyGenerators == null) {   // opportunistic check to possibly avoid sync block
		        		cacheKeyGenerators = new HashMap<String, ICacheKeyGenerator>();
		        		addCacheKeyGenerators(cacheKeyGenerators, layerCacheKeyGenerators);
		        		for (Future<ModuleBuildReader> future : futures) {
		        			ICacheKeyGenerator[] gen = null;
							try {
								gen = future.get().getCacheKeyGenerators();
							} catch (InterruptedException e) {
								throw new IOException(e);
							} catch (ExecutionException e) {
								throw new IOException(e);
							}
							addCacheKeyGenerators(cacheKeyGenerators, gen);
		        		}
		        		addCacheKeyGenerators(cacheKeyGenerators, getModules(request).getCacheKeyGenerators());
		        		addCacheKeyGenerators(cacheKeyGenerators, aggr.getTransport().getCacheKeyGenerators());
		        		
		            	synchronized(this) {
		            		if (_cacheKeyGenerators == null && _layerBuilds == layerBuilds) {
		            			_cacheKeyGenerators = cacheKeyGenerators;
		            		}
		            	}
		        	}
		            key = generateCacheKey(request);
		        }
	            // dd the LayerBuild to the
	            // cache using the new key and remove the one under the old key
            	if (log.isLoggable(Level.FINE)) {
            		log.fine("Adding layer to cache with key: " + key); //$NON-NLS-1$
            	}
        		CacheEntry oldEntry = layerBuilds.putIfAbsent(key, entry);
	            // Write the file to disk only if the LayerBuild was successfully added to the cache,
        		// or if the current cache entry is the cache entry we're working onf
	        	if (oldEntry == null || oldEntry == entry) {
	            	entry.persist(mgr);
	        	}
        	}
        }
        // return the input stream to the LayerBuild
		result = entry.getInputStream(request, response);
        return result;
	}

	protected List<Future<ModuleBuildReader>> collectFutures(
			HttpServletRequest request, boolean ignoreCached)
			throws IOException, NotFoundException {

		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		ModuleList moduleList = getModules(request);
		IHttpTransport transport = aggr.getTransport();
		int count = 0;
		boolean required = false;
		List<Future<ModuleBuildReader>> futures = new LinkedList<Future<ModuleBuildReader>>(); 
		
        ConcurrentMap<String, IModule> moduleCache = aggr.getCacheManager().getCache().getModules();
        Map<String, String> moduleCacheInfo = null;
        if (_isReportCacheInfo) {
        	moduleCacheInfo = new HashMap<String, String>();
        	request.setAttribute(MODULECACHEIFNO_PROPNAME, moduleCacheInfo);
        }

        // Add the application specified notice to the beginning of the response
        String notice = aggr.getConfig().getNotice();
        if (notice != null) {
			futures.add(
				new CompletedFuture<ModuleBuildReader>(
					new ModuleBuildReader(notice + "\r\n") //$NON-NLS-1$ //$NON-NLS-2$
				)
			);
        }
        // If development mode is enabled, say so
		if (options.isDevelopmentMode()) {
			futures.add(
				new CompletedFuture<ModuleBuildReader>(
					new ModuleBuildReader("/* " + Messages.LayerImpl_1 + " */\r\n") //$NON-NLS-1$ //$NON-NLS-2$
				)
			);
		}
		
		addTransportContribution(request, transport, futures, LayerContributionType.BEGIN_RESPONSE, null);
		// For each source file, add a Future<IModule.ModuleReader> to the list 
		for(ModuleList.ModuleListEntry moduleListEntry : moduleList) {
			IModule module = moduleListEntry.getModule();
			// Include the filename preamble if requested.
			if (options.isDevelopmentMode() && TypeUtil.asBoolean(request.getAttribute(IHttpTransport.SHOWFILENAMES_REQATTRNAME))) {
				futures.add(
					new CompletedFuture<ModuleBuildReader>(
						new ModuleBuildReader(String.format(PREAMBLEFMT, module.getURI().toString()))
					)
				);
			}
			String cacheKey = new ModuleIdentifier(module.getModuleId()).getModuleName();
			// Try to get the module from the module cache first
			IModule cachedModule = null;
			if (!ignoreCached) {
				cachedModule = moduleCache.get(cacheKey);
			}
			if (moduleCacheInfo != null) {
				moduleCacheInfo.put(cacheKey, (cachedModule != null) ? "hit" : "miss"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			IResource resource = module.getResource(aggr);
			if (!resource.exists()) {
				// Source file doesn't exist.
				if (!options.isDevelopmentMode()) {
					// Avoid the potential for DoS attack in production mode by throwing
					// an exceptions instead of letting the cache grow unbounded
					throw new NotFoundException(resource.getURI().toString());
				}
				// NotFound modules are not cached.  If the module is in the cache (because a 
				// source file has been deleted), then remove the cached module.
				if (cachedModule != null) {
		    		moduleCache.remove(cacheKey);
		        	if (moduleCacheInfo != null) {
		        		moduleCacheInfo.put(cacheKey, "remove"); //$NON-NLS-1$
		        	}
		    		cachedModule.clearCached(aggr.getCacheManager());
				}
				// create a new NotFoundModule
				module = new NotFoundModule(module.getModuleId(), module.getURI());
		    	request.setAttribute(NOCACHE_RESPONSE_REQATTRNAME, Boolean.TRUE);
			} else {
				if (cachedModule != null) {
					module = cachedModule;
				} else {
					// add it to the module cache
					moduleCache.put(cacheKey, module);
		        	if (moduleCacheInfo != null) {
		        		moduleCacheInfo.put(cacheKey, "add"); //$NON-NLS-1$
		        	}
				}
			}
			ModuleList.ModuleListEntry.Type type = moduleListEntry.getType();
			// Get the layer contribution from the transport
			// Note that we depend on the behavior that all non-required
			// modules in the module list appear before all required 
			// modules in the iteration.
			switch (type) {
			case MODULES:
				if (count == 0) {
					addTransportContribution(request, transport, futures, 
							LayerContributionType.BEGIN_MODULES, null);
					addTransportContribution(request, transport, futures, 
							LayerContributionType.BEFORE_FIRST_MODULE, 
							moduleListEntry.getModule().getModuleId());
				} else {	        			
					addTransportContribution(request, transport, futures, 
							LayerContributionType.BEFORE_SUBSEQUENT_MODULE, 
							moduleListEntry.getModule().getModuleId());
				}
				count++;
				break;
			case REQUIRED:
				if (!required) {
					required = true;
					if (count > 0) {
		    			addTransportContribution(request, transport, futures, 
		    					LayerContributionType.END_MODULES, null);
					}
					count = 0;
				}
				if (count == 0) {
					addTransportContribution(request, transport, futures, 
							LayerContributionType.BEGIN_REQUIRED_MODULES,
							moduleList.getRequiredModuleId());
					addTransportContribution(request, transport, futures, 
							LayerContributionType.BEFORE_FIRST_REQUIRED_MODULE, 
							moduleListEntry.getModule().getModuleId());
				} else {	        			
					addTransportContribution(request, transport, futures, 
							LayerContributionType.BEFORE_SUBSEQUENT_REQUIRED_MODULE, 
							moduleListEntry.getModule().getModuleId());
				}
				count++;
				break;
			}
			// Call module.get and add the returned Future to the list
			futures.add(module.getBuild(request));
			switch (type) {
			case MODULES:
				addTransportContribution(request, transport, futures, 
						LayerContributionType.AFTER_MODULE,
						moduleListEntry.getModule().getModuleId());
				break;
			case REQUIRED:
				addTransportContribution(request, transport, futures, 
						LayerContributionType.AFTER_REQUIRED_MODULE, 
						moduleListEntry.getModule().getModuleId());
				break;
			}
		}
		if (count > 0) {
			if (required) {
				addTransportContribution(request, transport, futures, 
					LayerContributionType.END_REQUIRED_MODULES,
					moduleList.getRequiredModuleId());
			} else {
				addTransportContribution(request, transport, futures, 
						LayerContributionType.END_MODULES, null);
			}
		}
		addTransportContribution(request, transport, futures, 
				LayerContributionType.END_RESPONSE, null);
		
		return futures;
	}

	protected void addCacheKeyGenerators(
			Map<String, ICacheKeyGenerator> cacheKeyGenerators,
			ICacheKeyGenerator[] gens) 
	{
		if (gens != null) {
			for (ICacheKeyGenerator gen : gens) {
				String className = gen.getClass().getName();
				ICacheKeyGenerator current = cacheKeyGenerators.get(className);
				cacheKeyGenerators.put(className, 
						(current == null) ? gen : current.combine(gen));
			}
		}		        			
	}

	protected void addTransportContribution(
			HttpServletRequest request,
			IHttpTransport transport, 
			List<Future<ModuleBuildReader>> futures, 
			LayerContributionType type,
			String mid) {

		String transportContrib = transport.getLayerContribution(
				request, 
				type, 
				mid
		);
		if (transportContrib != null) {
    		futures.add(
    			new CompletedFuture<ModuleBuildReader>(
    				new ModuleBuildReader(transportContrib)
    			)
        	);
		}
	}
	
	/**
	 * Generates a cache key for the layer.
	 * 
	 * @param request
	 *            the request object
	 * @return the cache key
	 * @throws IOException
	 */
	protected String generateCacheKey(HttpServletRequest request) throws IOException {
		String cacheKey = null;
		if (_cacheKeyGenerators != null) {
			// First, decompose any composite cache key generators into their 
			// constituent cache key generators so that we can combine them 
			// more effectively.  Use TreeMap to get predictable ordering of
			// keys.
			Map<String, ICacheKeyGenerator> gens = new TreeMap<String, ICacheKeyGenerator>();
			for (ICacheKeyGenerator gen : _cacheKeyGenerators.values()) {
				ICacheKeyGenerator[] constituentGens = gen.getCacheKeyGenerators(request);
				addCacheKeyGenerators(gens, 
						constituentGens == null ? 
								new ICacheKeyGenerator[]{gen} : 
								constituentGens);
			}
			StringBuffer sb = new StringBuffer(
					KeyGenUtil.generateKey(
							request, 
							gens.values())
			);
			cacheKey = sb.toString();
		}
		return cacheKey;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayer#clearCached(com.ibm.jaggr.service.cache.ICacheManager)
	 */
	@Override
	public void clearCached(ICacheManager mgr) {
		Map<String, CacheEntry> layerBuilds;
		synchronized(this) {
			layerBuilds = _layerBuilds;
			_layerBuilds = null;
		}
		if (layerBuilds != null) {
			for (Map.Entry<String, CacheEntry> entry : layerBuilds.entrySet()) {
				entry.getValue().delete(mgr);
			}
			layerBuilds.clear();
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayer#getLastModified(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public long getLastModified(HttpServletRequest request) throws IOException {
		long lastModified = _lastModified;
		IAggregator aggregator = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggregator.getOptions();
		// Don't check last modified dates of source files on every request in production mode
		// for performance reasons.  _validateLastModified is a transient that gets initialize 
		// to true whenever this object is de-serialized (i.e. on server startup).
		if (lastModified == -1 || _validateLastModified.getAndSet(false) || options.isDevelopmentMode()) {
			// see if we already determined the last modified time for this request
			Object obj = request.getAttribute(LAST_MODIFIED_PROPNAME);
			if (obj == null) {
	        	// Determine latest last-modified time from source files in moduleList
		        ModuleList moduleFiles = getModules(request);
		        lastModified = getLastModified(aggregator, moduleFiles);
		        // Get last-modified date of config file
				URI configUri = aggregator.getConfig().getConfigUri();
				if (configUri != null) {
					lastModified = Math.max(
							lastModified, 
							configUri.toURL().openConnection().getLastModified());
				}
		        request.setAttribute(LAST_MODIFIED_PROPNAME, lastModified);
				if (log.isLoggable(Level.FINER)) {
					log.finer("Returning calculated last modified "  //$NON-NLS-1$
							+ lastModified + " for layer " +  //$NON-NLS-1$
							request.getAttribute(IHttpTransport.REQUESTEDMODULES_REQATTRNAME).toString());
				}
			} else {
				lastModified = (Long)obj;
				if (log.isLoggable(Level.FINER)) {
					log.finer("Returning last modified "  //$NON-NLS-1$
							+ lastModified + " from request for layer " +  //$NON-NLS-1$
							request.getAttribute(IHttpTransport.REQUESTEDMODULES_REQATTRNAME).toString());
				}
			}
		} else {
			if (log.isLoggable(Level.FINER)) {
				log.finer("Returning cached last modified "  //$NON-NLS-1$
						+ lastModified + " for layer " + //$NON-NLS-1$
						request.getAttribute(IHttpTransport.REQUESTEDMODULES_REQATTRNAME).toString());
			}
		}
		return lastModified;
	}

	/**
	 * Clones this object.  Note that while the clone is thread-safe, it is NOT atomic
	 */
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		LayerImpl clone;
		// _hasConditionals is an immutable set, so a reference clone is ok
		synchronized(this) {
			clone = (LayerImpl)super.clone();
		}
		// just need to clone _layerBuilds.  ConcurrentHashMap doesn't implement clone, so
		// use the copy constructor instead.
		if (clone._layerBuilds != null) {
			clone._layerBuilds = new ConcurrentHashMap<String, CacheEntry>(clone._layerBuilds);
			for (Map.Entry<String, CacheEntry> layerBuild : clone._layerBuilds.entrySet()) {
				layerBuild.setValue((CacheEntry)layerBuild.getValue().clone());
			}
		}
		return clone;
	}
	
	/**
	 * De-serialize this object from an ObjectInputStream
	 * @param in The ObjectInputStream
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		// Call the default implementation to de-serialize our object
		in.defaultReadObject();
		// init transients
		_validateLastModified = new AtomicBoolean(true);	
		_isReportCacheInfo = false;	
	}
	
	/**
	 * Calls the filtered version with a null filter
	 */
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		String linesep = System.getProperty("line.separator"); //$NON-NLS-1$
		StringBuffer sb = new StringBuffer();
		sb.append("\nModified: ") //$NON-NLS-1$
		  .append(new Date(_lastModified).toString()).append(linesep)
		  .append("KeyGen: ").append(KeyGenUtil.toString(_cacheKeyGenerators.values())) //$NON-NLS-1$
		  .append(linesep); //$NON-NLS-1$
		Map<String, CacheEntry> layerBuilds = _layerBuilds;
		if (layerBuilds != null) {
			for (Map.Entry<String, CacheEntry> entry : _layerBuilds.entrySet()) {
				sb.append("\t").append(entry.getKey()).append(" : ").append(entry.getValue().filename).append(linesep); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
		}
		sb.append(linesep);
		return sb.toString();
	}
	
    /**
     * Unfolds a folded module list and returns a list of Source objects
     * 
     * @param request The request
     * @param modules The folded module list
     * @return A list of Source objects
     * @throws IOException
     */
	protected ModuleList getModules(HttpServletRequest request) throws IOException {
		ModuleList result = (ModuleList)request.getAttribute(MODULE_FILES_PROPNAME);
    	if (result == null) {
	    	IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			@SuppressWarnings("unchecked")
			Collection<String> moduleNames = (Collection<String>)request.getAttribute(IHttpTransport.REQUESTEDMODULES_REQATTRNAME);
	        result = new ModuleList();
    		Features features = (Features)request.getAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME);
    		Set<String> dependentFeatures = new HashSet<String>();
	        for (String name : moduleNames) {
	        	if (name != null) {
	        		name = aggr.getConfig().resolve(name, features, dependentFeatures, null);
	        		result.add(new ModuleList.ModuleListEntry(newModule(request, name), ModuleList.ModuleListEntry.Type.MODULES));
	        	}
	        }
	        // See if we need to add required modules.
			String required = (String)request.getAttribute(IHttpTransport.REQUIRED_REQATTRNAME);
			if (required != null) {
				// If there's a required module, then add it and its dependencies
				// to the module list.  
	    		IDependencies deps = aggr.getDependencies();
	    		DependencyList depList = new DependencyList(
	    				Arrays.asList(required.split(",")), //$NON-NLS-1$
	    				aggr.getConfig(),
	    				deps,
	    				features,
	    				false);
				result.setRequiredModuleId(required);
	    		result.setDependenentFeatures(dependentFeatures);
	    		Map<String, String> combined = depList.getExpandedDeps();
	    		combined.putAll(depList.getExplicitDeps());
	    		for (String name : combined.keySet()) {
	    			if (!name.contains("!")) { //$NON-NLS-1$
		        		result.add(
		        				new ModuleList.ModuleListEntry(
		        						newModule(request, name), 
	        							ModuleList.ModuleListEntry.Type.REQUIRED 
		        				)
		        		);
	    			}
	    		}
			}
	        request.setAttribute(MODULE_FILES_PROPNAME, result);
    	}
        return result;
    }
	
	protected IModule newModule(HttpServletRequest request, String mid) {
		if (mid.endsWith("/.")) { //$NON-NLS-1$
			mid = mid.substring(0, mid.length() - 2);
		}
    	IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);

   		URI uri = aggr.getConfig().locateModuleResource(new ModuleIdentifier(mid).getModuleName());

		return aggr.newModule(mid, uri);
	}
	
    /**
     * Returns the newest last modified time of the files in the list
     * 
     * @param files The list of ModuleFile objects
     * @return The newest last modified time of all the files in the list
     */
    protected long getLastModified(IAggregator aggregator, ModuleList modules) {
    	long result = 0L;
    	for (ModuleList.ModuleListEntry entry : modules) {
    		IResource resource = entry.getModule().getResource(aggregator); 
    		long lastMod = resource.lastModified();
    		if (lastMod == 0 && !resource.exists()) {
    			lastMod = Long.MAX_VALUE;
    		}
    		result = Math.max(result, lastMod);
    	}
    	return result;
    }
	/**
	 * Class to encapsulate operations on layer builds.  Uses {@link ExecutorService}
	 * objects to asynchronously create and delete cache files.  ICache files are deleted
	 * using a {@link ScheduledExecutorService} with a delay to allow sufficient time
	 * for any threads that may still be using the file to be done with them. 
	 * <p>
	 * This class avoids synchronization by declaring the instance variables volatile and being
	 * careful about the order in which variables are assigned and read.  See comments in the 
	 * various methods for details.
	 */
	/* (non-Javadoc)
	 * Resist the temptation to change this from a static inner class to a non-static inner
	 * class.  Although doing so may seem convenient by providing access to the parent class
	 * instance fields, it would cause problems with cloning and serialization of the cloned
	 * objects because the cloned instances of this class would continue to reference the
	 * original instances of the containing class and these original instances would end up
	 * getting serialized along with the cloned instances of the containing class.  Serialization
	 * is done using cloned cache objects to avoid contention on synchronized locks that would 
	 * need to be held during file I/O if the live cache objects were serialized.
	 */
	static protected class CacheEntry implements Serializable, Cloneable{
		private static final long serialVersionUID = -2129350665073838766L;

		private transient volatile byte[] bytes = null;
    	private volatile String filename = null;
    	
    	/**
    	 * Return an input stream to the layer.  Has side effect of setting the 
    	 * appropriate Content-Type, Content-Length and Content-Encoding headers
    	 * in the response.
    	 * 
    	 * @return The InputStream for the built layer
    	 * @throws IOException
    	 */
    	public InputStream getInputStream(HttpServletRequest request, 
    			HttpServletResponse response) throws IOException {
    		// Check bytes before filename when reading and reverse order when setting
    		byte[] bytes = this.bytes;
    		String filename = this.filename;
    		InputStream in;
    		long size;
    		if (bytes != null) {
    			in = new ByteArrayInputStream(bytes);
    			size = bytes.length;
    		} else {
    			ICacheManager cmgr = ((IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME)).getCacheManager();
    			File file = new File(cmgr.getCacheDir(), filename);
    			in = new FileInputStream(file);
    			size = file.length();
    		}
            response.setContentType("application/x-javascript; charset=utf-8"); //$NON-NLS-1$
        	response.setHeader("Content-Length", Long.toString(size)); //$NON-NLS-1$
            String accept = request.getHeader("Accept-Encoding"); //$NON-NLS-1$
            if (accept != null)
            	accept = accept.toLowerCase();
            if (accept != null && accept.contains("gzip") && !accept.contains("gzip;q=0")) { //$NON-NLS-1$ //$NON-NLS-2$
            	response.setHeader("Content-Encoding", "gzip"); //$NON-NLS-1$ //$NON-NLS-2$
            } else {
            	in = new GZIPInputStream(in);
            }
			return in;
    	}
    	
    	/**
    	 * Can fail by returning null, but won't throw an exception.  Will also return null
    	 * if no data is available yet.
    	 * 
    	 * @return The LayerInputStream, or null if data is not available 
    	 */
    	public InputStream tryGetInputStream(HttpServletRequest request, 
    			HttpServletResponse response) throws IOException {
    		InputStream in = null;
    		// Check bytes before filename when reading and reverse order when setting
    		if (bytes != null || filename != null) {
    			try {
    				in = getInputStream(request, response);
    			} catch (IOException e) {
    				throw e;	// caller will handle
    			} catch (Exception e) {
    				if (log.isLoggable(Level.SEVERE)) {
    					log.log(Level.SEVERE, e.getMessage(), e);
    				}
    				// just return null
    			}
    		}
    		return in;
    	}
    	
    	/**
    	 * @param bytes
    	 */
    	public void setBytes(byte[] bytes) {
    		this.bytes = bytes;
    	}
    	
    	/**
    	 * Delete the cached build after the specified delay in minues
    	 * @param mgr The cache manager
    	 * @param delay The delay in minutes
    	 */
    	public void delete(final ICacheManager mgr) {
    		if (CacheEntry.this.filename != null) {
    			mgr.deleteFileDelayed(filename);
    		}
    	}
    	
    	/**
    	 * Asynchronously write the layer build content to disk and set filename to the 
    	 * name of the cache file when done.
    	 * 
    	 * @param mgr The cache manager
    	 */
    	public void persist(final ICacheManager mgr) {
			mgr.createCacheFileAsync("layer.", new ByteArrayInputStream(bytes),  //$NON-NLS-1$
					new ICacheManager.CreateCompletionCallback() {
				@Override
				public void completed(String fname, Exception e) {
					if (e == null) {
		                // Set filename before clearing bytes 
		                filename = fname;
		                // Free up the memory for the content now that we've written out to disk
		                // TODO:  Determine a size threshold where we may want to keep the contents
		                // of small files in memory to reduce disk i/o.
		                bytes = null;
					}
				}
			});
    	}
    	
		/* (non-Javadoc)
		 * @see java.lang.Object#clone()
		 */
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
    }
}