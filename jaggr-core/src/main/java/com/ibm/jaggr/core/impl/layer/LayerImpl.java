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

package com.ibm.jaggr.core.impl.layer;

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.cachekeygenerator.AbstractCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.FeatureSetCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.layer.ILayerCache;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.ModuleIdentifier;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.modulebuilder.ModuleBuildFuture;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IRequestedModuleNames;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.RequestUtil;
import com.ibm.jaggr.core.util.TypeUtil;

import org.apache.commons.lang3.mutable.MutableObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A LayerImpl is a collection of LayerBuild objects that are composed using the same
 * list of modules, but vary according to build options, compilation level, has filtering,
 * etc.
 */
public class LayerImpl implements ILayer {
	private static final long serialVersionUID = 2491460740123061848L;
	static final String sourceClass = LayerImpl.class.getName();
	static final Logger log = Logger.getLogger(sourceClass);

	static final String LAST_MODIFIED_PROPNAME = LayerImpl.class.getName() + ".LAST_MODIFIED_FILES"; //$NON-NLS-1$
	static final String MODULE_FILES_PROPNAME = LayerImpl.class.getName() + ".MODULE_FILES"; //$NON-NLS-1$

	// The following request attributes are used by unit tests
	static final String LAYERCACHEINFO_PROPNAME = LayerImpl.class.getName() + ".LAYER_CACHEIFNO"; //$NON-NLS-1$
	static final String LAYERBUILDCACHEKEY_PROPNAME = LayerImpl.class.getName() + ".LAYERBUILD_CACHEKEY"; //$NON-NLS-1$
	static final String BOOTLAYERDEPS_PROPNAME = LayerImpl.class.getName() + ".BOOT_LAYER_DEPS"; //$NON-NLS-1$

	static final String DEPSOURCE_REQDEPS = " URL - deps"; //$NON-NLS-1$
	static final String DEPSOURCE_REQPRELOADS = "URL - preloads"; //$NON-NLS-1$
	static final String DEPSOURCE_EXCLUDES = "URL - excludes";  //$NON-NLS-1$

	static final Pattern nlsPat = Pattern.compile("^.*(^|\\/)nls(\\/|$)"); //$NON-NLS-1$

	protected static final List<ICacheKeyGenerator> s_layerCacheKeyGenerators  = Collections.unmodifiableList(Arrays.asList(new ICacheKeyGenerator[]{
			new AbstractCacheKeyGenerator() {
				// This is a singleton, so default equals() will do
				private static final long serialVersionUID = 2013098945317787755L;
				private static final String eyeCatcher = "lyr"; //$NON-NLS-1$
				@Override
				public String generateKey(HttpServletRequest request) {
					boolean showFilenames =  TypeUtil.asBoolean(request.getAttribute(IHttpTransport.SHOWFILENAMES_REQATTRNAME));
					return new StringBuffer(eyeCatcher).append(":") //$NON-NLS-1$
							.append(RequestUtil.isGzipEncoding(request) ? "1" : "0").append(":") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.append(showFilenames ? "1" : "0").append(":") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.append(RequestUtil.isIncludeRequireDeps(request) ? "1" : "0").append(":") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.append(RequestUtil.isIncludeUndefinedFeatureDeps(request) ? "1" : "0").toString(); //$NON-NLS-1$ //$NON-NLS-2$

				}
				@Override
				public String toString() {
					return eyeCatcher;
				}
			}
	}));

	public static final Pattern GZIPFLAG_KEY_PATTERN  = Pattern.compile(s_layerCacheKeyGenerators.get(0).toString() + ":([01]):"); //$NON-NLS-1$

	static int LAYERBUILD_REMOVE_DELAY_SECONDS = 10;

	/**
	 * Map of cache dependency objects for module classes included in this layer.
	 * Cloned by reference since cache key generators are immutable.
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
	private transient LayerBuildsAccessor _layerBuilds;

	/**
	 * Flag to indicate that addition information used unit tests should be added to the
	 * request object
	 */
	private transient boolean _isReportCacheInfo = false;

	private final String _cacheKey;

	final int _id;

	/**
	 * @param cacheKey The folded module list as specified in the request
	 * @param id unique identifier
	 */
	public LayerImpl(String cacheKey, int id) {
		_cacheKey = cacheKey;
		_id = id;
	}

	/* (non-Javadoc)
	 * @see com.ibm.servlets.amd.aggregator.layer.ILayer#getKey()
	 */
	@Override
	public String getKey() {
		return _cacheKey;
	}

	int getId() {
		return _id;
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
	@SuppressWarnings("unchecked")
	@Override
	public InputStream getInputStream(HttpServletRequest request,
			HttpServletResponse response) throws IOException {

		CacheEntry entry = null;
		String key = null;
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		List<String> cacheInfoReport = null;
		if (_isReportCacheInfo) {
			cacheInfoReport = (List<String>)request.getAttribute(LAYERCACHEINFO_PROPNAME);
			if (cacheInfoReport != null) {
				cacheInfoReport.clear();
			}
		}
		if (log.isLoggable(Level.FINEST) && cacheInfoReport == null) {
			cacheInfoReport = new LinkedList<String>();
		}
		try {
			IOptions options = aggr.getOptions();
			ICacheManager mgr = aggr.getCacheManager();
			boolean ignoreCached = RequestUtil.isIgnoreCached(request);
			InputStream result;
			long lastModified = getLastModified(request);
			CacheEntry newEntry = new CacheEntry(_id, _cacheKey, lastModified);
			CacheEntry existingEntry = null;

			if (ignoreCached) {
				request.setAttribute(NOCACHE_RESPONSE_REQATTRNAME, Boolean.TRUE);
			}
			if (options.isDevelopmentMode()) {
				synchronized(this) {
					// See if we need to discard previously built LayerBuilds
					if (lastModified > _lastModified) {
						if (cacheInfoReport != null) {
							cacheInfoReport.add("update_lastmod2"); //$NON-NLS-1$
						}
						if (lastModified != Long.MAX_VALUE) {
							// max value means missing requested source
							_lastModified = lastModified;
						}
						_cacheKeyGenerators = null;
					}
				}
			}
			Map<String, ICacheKeyGenerator> cacheKeyGenerators = _cacheKeyGenerators;

			// Creata a cache key.
			key = generateCacheKey(request, cacheKeyGenerators);

			if (!ignoreCached && key != null) {
				int loopGuard = 5;
				do {
					// Try to retrieve an existing layer build using the blocking putIfAbsent.  If the return
					// value is null, then the newEntry was successfully added to the map, otherwise the
					// existing entry is returned in the buildReader and newEntry was not added.
					existingEntry = _layerBuilds.putIfAbsent(key, newEntry, options.isDevelopmentMode());
					if (cacheInfoReport != null) {
						cacheInfoReport.add(existingEntry != null ? "hit_1" : "added"); //$NON-NLS-1$ //$NON-NLS-2$
					}
					if (existingEntry != null) {
						if ((result = existingEntry.tryGetInputStream(request)) != null) {
							setResponseHeaders(request, response, existingEntry.getSize());
							if (log.isLoggable(Level.FINEST)) {
								log.finest(cacheInfoReport.toString() + "\n" +  //$NON-NLS-1$
										"key:" + key +  //$NON-NLS-1$
										"\n" + existingEntry.toString()); //$NON-NLS-1$
							}
							if (_isReportCacheInfo) {
								request.setAttribute(LAYERBUILDCACHEKEY_PROPNAME, key);
							}
							return result;
						} else if (existingEntry.isDeleted()) {
							if (_layerBuilds.replace(key, existingEntry, newEntry)) {
								// entry was replaced, use newEntry
								if (cacheInfoReport != null) {
									cacheInfoReport.add("replace_1"); //$NON-NLS-1$
								}
								existingEntry = null;
							} else {
								// Existing entry was removed from the cache by another thread
								// between the time we retrieved it and the time we tried to
								// replace it.  Try to add the new entry again.
								if (cacheInfoReport != null) {
									cacheInfoReport.add("retry_add"); //$NON-NLS-1$
								}
								if (--loopGuard == 0) {
									// Should never happen, but just in case
									throw new IllegalStateException();
								}
								continue;
							}
						}
					}
					break;
				} while (true);
			}

			// putIfAbsent() succeeded and the new entry was added to the cache
			entry = (existingEntry != null) ? existingEntry : newEntry;

			LayerBuilder layerBuilder = null;

			// List of Future<IModule.ModuleReader> objects that will be used to read the module
			// data from
			List<ICacheKeyGenerator> moduleKeyGens = null;

			// Synchronize on the LayerBuild object for the build.  This will prevent multiple
			// threads from building the same output.  If more than one thread requests the same
			// output (same cache key), then the first one to grab the sync object will win and
			// the rest will wait for the first thread to finish building and then just return
			// the output from the first thread when they wake.
			synchronized(entry) {

				// Check to see if data is available one more time in case a different thread finished
				// building the output while we were blocked on the sync object.
				if (!ignoreCached && key != null && (result = entry.tryGetInputStream(request)) != null) {
					if (cacheInfoReport != null) {
						cacheInfoReport.add("hit_2"); //$NON-NLS-1$
					}
					setResponseHeaders(request, response, entry.getSize());
					if (log.isLoggable(Level.FINEST)) {
						log.finest(cacheInfoReport.toString() + "\n" + //$NON-NLS-1$
								"key:" + key +  //$NON-NLS-1$
								"\n" + entry.toString()); //$NON-NLS-1$
					}
					if (_isReportCacheInfo) {
						request.setAttribute(LAYERBUILDCACHEKEY_PROPNAME, key);
					}
					return result;
				}

				boolean isGzip = RequestUtil.isGzipEncoding(request);
				ByteArrayOutputStream bos = new ByteArrayOutputStream();

				// See if we already have a cached response that uses a different gzip
				// encoding option.  If we do, then just zip (or unzip) the cached
				// response
				CacheEntry otherEntry = null;
				if (key != null) {
					StringBuffer sb = new StringBuffer();
					Matcher m = GZIPFLAG_KEY_PATTERN.matcher(key);
					m.find();
					m.appendReplacement(sb,
							new StringBuffer(s_layerCacheKeyGenerators.get(0).toString())
					.append(":") //$NON-NLS-1$
					.append("1".equals(m.group(1)) ? "0" : "1") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					.append(":").toString() //$NON-NLS-1$
							).appendTail(sb);
					otherEntry = _layerBuilds.get(sb.toString());
				}
				if (otherEntry != null) {
					if (isGzip) {
						if (cacheInfoReport != null) {
							cacheInfoReport.add("zip_unzipped"); //$NON-NLS-1$
						}
						// We need gzipped and the cached entry is unzipped
						// Create the compression stream for the output
						VariableGZIPOutputStream compress = new VariableGZIPOutputStream(bos, 10240);  // is 10k too big?
						compress.setLevel(Deflater.BEST_COMPRESSION);
						Writer writer = new OutputStreamWriter(compress, "UTF-8"); //$NON-NLS-1$

						// Copy the data from the input stream to the output, compressing as we go.
						CopyUtil.copy(otherEntry.getInputStream(request), writer);
					} else {
						if (cacheInfoReport != null) {
							cacheInfoReport.add("unzip_zipped"); //$NON-NLS-1$
						}
						// We need unzipped and the cached entry is zipped.  Just unzip it
						CopyUtil.copy(new GZIPInputStream(otherEntry.getInputStream(request)), bos);
					}
					// Set the buildReader to the LayerBuild and release the lock by exiting the sync block
					entry.setBytes(bos.toByteArray());
					if (!ignoreCached) {
						_layerBuilds.replace(key, entry, entry);	// updates entry weight in map
						if (cacheInfoReport != null) {
							cacheInfoReport.add("update_weights_1"); //$NON-NLS-1$
						}
						entry.persist(mgr);
					}
				} else {
					moduleKeyGens = new LinkedList<ICacheKeyGenerator>();

					ModuleList moduleList = getModules(request);

					// Remove the module list from the request to safe-guard it now that we don't
					// need it there anymore
					request.removeAttribute(MODULE_FILES_PROPNAME);

					// Create a BuildListReader from the list of Futures.  This reader will obtain a
					// ModuleReader from each of the Futures in the list and read data from each one in
					// succession until all the data has been read, blocking on each Future until the
					// reader becomes available.
					layerBuilder = new LayerBuilder(request, moduleKeyGens, moduleList);
					String layer = layerBuilder.build();

					if (isGzip) {
						if (cacheInfoReport != null) {
							cacheInfoReport.add("zip"); //$NON-NLS-1$
						}
						VariableGZIPOutputStream compress = new VariableGZIPOutputStream(bos, 10240);  // is 10k too big?
						compress.setLevel(Deflater.BEST_COMPRESSION);
						Writer writer = new OutputStreamWriter(compress, "UTF-8"); //$NON-NLS-1$

						// Copy the data from the input stream to the output, compressing as we go.
						CopyUtil.copy(new StringReader(layer), writer);
						// Set the buildReader to the LayerBuild and release the lock by exiting the sync block
						entry.setBytes(bos.toByteArray());
					} else {
						entry.setBytes(layer.getBytes());
					}

					// entry will be persisted below after we determine if cache key
					// generator needs to be updated
				}
			}

			// if any of the readers included an error response, then don't cache the layer.
			if (layerBuilder != null && layerBuilder.hasErrors()) {
				request.setAttribute(NOCACHE_RESPONSE_REQATTRNAME, Boolean.TRUE);
				if (cacheInfoReport != null) {
					cacheInfoReport.add(key == null ? "error_noaction" : "error_remove"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				if (key != null) {
					_layerBuilds.remove(key, entry);
				}
			} else if (layerBuilder != null) {
				if (!ignoreCached) {
					// See if we need to create or update the cache key generators
					Map<String, ICacheKeyGenerator> newKeyGens = new HashMap<String, ICacheKeyGenerator>();
					Set<String> requiredModuleListDeps = getModules(request).getDependentFeatures();
					addCacheKeyGenerators(newKeyGens, s_layerCacheKeyGenerators);
					addCacheKeyGenerators(newKeyGens, aggr.getTransport().getCacheKeyGenerators());
					addCacheKeyGenerators(newKeyGens, Arrays.asList(new ICacheKeyGenerator[]{new FeatureSetCacheKeyGenerator(requiredModuleListDeps, false)}));
					addCacheKeyGenerators(newKeyGens, moduleKeyGens);

					boolean cacheKeyGeneratorsUpdated = false;
					if (!newKeyGens.equals(cacheKeyGenerators)) {
						// If we don't yet have a cache key for this layer, then get one
						// from the cache key generators, and then update the cache key for this
						// cache entry.

						synchronized(this) {
							if (_cacheKeyGenerators != null) {
								addCacheKeyGenerators(newKeyGens, _cacheKeyGenerators.values());
							}
							_cacheKeyGenerators = Collections.unmodifiableMap(newKeyGens);
						}
						if (cacheInfoReport != null) {
							cacheInfoReport.add("update_keygen"); //$NON-NLS-1$
						}
						cacheKeyGeneratorsUpdated = true;
					}
					final String originalKey = key;
					if (key == null || cacheKeyGeneratorsUpdated) {
						if (cacheInfoReport != null) {
							cacheInfoReport.add("update_key"); //$NON-NLS-1$
						}
						key = generateCacheKey(request, newKeyGens);
					}
					if (originalKey == null || !originalKey.equals(key)) {
						/*
						 * The cache key has changed from what was originally used to put the
						 * un-built entry into the cache.  Add the LayerBuild to the cache
						 * using the new key.
						 */
						if (log.isLoggable(Level.FINE)) {
							log.fine("Key changed!  Adding layer to cache with key: " + key); //$NON-NLS-1$
						}
						final CacheEntry originalEntry = entry;
						CacheEntry updateEntry = (originalKey == null) ? entry : new CacheEntry(entry);
						CacheEntry previousEntry = _layerBuilds.putIfAbsent(key, updateEntry, options.isDevelopmentMode());
						if (cacheInfoReport != null) {
							cacheInfoReport.add(previousEntry == null ? "update_add" : "update_hit"); //$NON-NLS-1$ //$NON-NLS-2$
						}
						// Write the file to disk only if the LayerBuild was successfully added to the cache
						if (previousEntry == null) {
							// Updated entry was added to the cache.
							entry = updateEntry;
							entry.persist(mgr);
						}
						// If the key changed, then remove the entry under the old key.  Use a
						// delay to give other threads a chance to start using the new cache
						// key generator.  No need to update entry weight in map
						if (originalKey != null) {
							aggr.getExecutors().getScheduledExecutor().schedule(new Runnable() {
								public void run() {
									_layerBuilds.remove(originalKey, originalEntry);
								}
							}, LAYERBUILD_REMOVE_DELAY_SECONDS, TimeUnit.SECONDS);
						}
					} else {
						if (cacheInfoReport != null) {
							cacheInfoReport.add("update_weights_2"); //$NON-NLS-1$
						}
						_layerBuilds.replace(key, entry, entry);	// updates entry weight in map
						entry.persist(mgr);
					}
				}
			}
			result = entry.getInputStream(request);
			setResponseHeaders(request, response, entry.getSize());

			// return the input stream to the LayerBuild
			if (log.isLoggable(Level.FINEST)) {
				log.finest(cacheInfoReport.toString() + "\n" + //$NON-NLS-1$
						"key:" + key +  //$NON-NLS-1$
						"\n" + entry.toString()); //$NON-NLS-1$
			}
			if (_isReportCacheInfo) {
				request.setAttribute(LAYERBUILDCACHEKEY_PROPNAME, key);
			}
			return result;
		} catch (IOException e) {
			_layerBuilds.remove(key, entry);
			throw e;
		} catch (RuntimeException e) {
			_layerBuilds.remove(key, entry);
			throw e;
		} finally {
			if (_layerBuilds.isLayerEvicted()) {
				_layerBuilds.removeLayerFromCache(this);
			}
		}
	}


	/**
	 * Adds the cache key generators specified in {@code gens} to the map of
	 * classname/key-generator pairs, combining key-generators as needed.
	 *
	 * @param cacheKeyGenerators
	 *            Map of classname/key-generator pairs to add to.
	 * @param gens
	 *            the cache key generators to add.
	 */
	protected void addCacheKeyGenerators(
			Map<String, ICacheKeyGenerator> cacheKeyGenerators,
			Iterable<ICacheKeyGenerator> gens)
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

	/**
	 * Generates a cache key for the layer.
	 *
	 * @param request
	 *            the request object
	 * @param cacheKeyGenerators
	 *            map of cache key generator class names to instance objects
	 * @return the cache key
	 * @throws IOException
	 */
	protected String generateCacheKey(HttpServletRequest request, Map<String, ICacheKeyGenerator> cacheKeyGenerators) throws IOException {
		String cacheKey = null;
		if (cacheKeyGenerators != null) {
			// First, decompose any composite cache key generators into their
			// constituent cache key generators so that we can combine them
			// more effectively.  Use TreeMap to get predictable ordering of
			// keys.
			Map<String, ICacheKeyGenerator> gens = new TreeMap<String, ICacheKeyGenerator>();
			for (ICacheKeyGenerator gen : cacheKeyGenerators.values()) {
				List<ICacheKeyGenerator> constituentGens = gen.getCacheKeyGenerators(request);
				addCacheKeyGenerators(gens,
						constituentGens == null ?
								Arrays.asList(new ICacheKeyGenerator[]{gen}) :
									constituentGens);
			}
			cacheKey = KeyGenUtil.generateKey(
					request,
					gens.values());
		}
		return cacheKey;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.layer.ILayer#getLastModified(javax.servlet.http.HttpServletRequest)
	 */
	@SuppressWarnings("unchecked")
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
				lastModified = Math.max(
						lastModified,
						aggregator.getConfig().lastModified());
				List<String> cacheInfoReport = null;
				if (_isReportCacheInfo) {
					cacheInfoReport = (List<String>)request.getAttribute(LAYERCACHEINFO_PROPNAME);
				}
				synchronized(this) {
					if (_lastModified == -1) {
						// Initialize value of instance property
						_lastModified = lastModified;
						if (cacheInfoReport != null) {
							cacheInfoReport.add("update_lastmod1"); //$NON-NLS-1$
						}
					}
				}
				request.setAttribute(LAST_MODIFIED_PROPNAME, lastModified);
				if (log.isLoggable(Level.FINER)) {
					log.finer("Returning calculated last modified "  //$NON-NLS-1$
							+ lastModified + " for layer " +  //$NON-NLS-1$
							request.getAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME).toString());
				}
			} else {
				lastModified = (Long)obj;
				if (log.isLoggable(Level.FINER)) {
					log.finer("Returning last modified "  //$NON-NLS-1$
							+ lastModified + " from request for layer " +  //$NON-NLS-1$
							request.getAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME).toString());
				}
			}
		} else {
			if (log.isLoggable(Level.FINER)) {
				log.finer("Returning cached last modified "  //$NON-NLS-1$
						+ lastModified + " for layer " + //$NON-NLS-1$
						request.getAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME).toString());
			}
		}
		return lastModified;
	}

	LayerImpl cloneForSerialization()  {
		LayerImpl clone;
		// The clone lock is owned (as a write lock) by the caller, so no need to
		// acquire it here.
		// _cacheKeyGenerators is an immutable set, so a reference clone is ok
		// _validateLastModified is transient, so no need to bother cloning
		// Layer cache is responsible for setting new _cloneLock and _layerBuilds
		// objects by calling setters subsequent to this call
		clone = new LayerImpl(_cacheKey, _id);
		clone._cacheKeyGenerators = _cacheKeyGenerators;
		clone._lastModified = _lastModified;
		return clone;
	}

	void setLayerBuildsAccessor(LayerBuildsAccessor layerBuilds) {
		if (_layerBuilds != null) {
			throw new IllegalStateException();
		}
		_layerBuilds = layerBuilds;
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
		.append("KeyGen: ").append( //$NON-NLS-1$
				_cacheKeyGenerators != null ? KeyGenUtil.toString(_cacheKeyGenerators.values()) : "null") //$NON-NLS-1$
				.append(linesep);
		if (_layerBuilds != null) {
			for (Map.Entry<String, CacheEntry> entry : _layerBuilds.entrySet()) {
				sb.append("\t").append(entry.getKey()) //$NON-NLS-1$
				.append(" : ").append(entry.getValue().getFilename()).append(linesep); //$NON-NLS-1$
			}
		}
		sb.append(linesep);
		return sb.toString();
	}

	/**
	 * Unfolds a folded module list and returns a list of Source objects
	 *
	 * @param request The request
	 * @return A list of Source objects
	 * @throws IOException
	 */
	protected ModuleList getModules(HttpServletRequest request) throws IOException {
		final String sourceMethod = "getModules"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceMethod, sourceMethod, new Object[]{request});
		}
		ModuleList result = (ModuleList)request.getAttribute(MODULE_FILES_PROPNAME);
		if (result == null) {
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			IRequestedModuleNames requestedModuleNames = (IRequestedModuleNames)request.getAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);

			result = new ModuleList();
			if (requestedModuleNames != null) {
				Features features = (Features)request.getAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME);
				Set<String> dependentFeatures = new HashSet<String>();
				List<String> names = requestedModuleNames.getModules();
				if (!names.isEmpty()) {
					for (String name : names) {
						if (name != null) {
							name = aggr.getConfig().resolve(name, features, dependentFeatures, null,
									false,	// Don't resolve aliases when locating modules requested by the loader
									//  because the loader should have already done alias resolution and
									//  we can't rename a requested module.
									false	// Loader doesn't request modules with has! plugin
									);
							result.add(new ModuleList.ModuleListEntry(newModule(request, name), ModuleSpecifier.MODULES));
						}
					}
				} else {
					boolean includeRequireDeps = RequestUtil.isIncludeRequireDeps(request);
					names = requestedModuleNames.getScripts();
					for (String name : names) {
						if (name != null) {
							name = aggr.getConfig().resolve(name, features, dependentFeatures, null,
									false,	// Don't resolve aliases when locating modules requested by the loader
									//  because the loader should have already done alias resolution and
									//  we can't rename a requested module.
									true	// Resolve has! loader plugin
									);
							result.add(new ModuleList.ModuleListEntry(newModule(request, name), ModuleSpecifier.SCRIPTS));
						}
					}
					// See if we need to add required modules.
					DependencyList requiredList = null, preloadList = null, excludeList = null;
					ModuleDeps combined = new ModuleDeps(), explicit = new ModuleDeps();
					if (!requestedModuleNames.getDeps().isEmpty()) {

						// If there's a required module, then add it and its dependencies
						// to the module list.
						requiredList = new DependencyList(
								DEPSOURCE_REQDEPS,
								requestedModuleNames.getDeps(),
								aggr,
								features,
								true, 	// resolveAliases
								RequestUtil.isRequireExpLogging(request),	// include details
								includeRequireDeps
								);
						dependentFeatures.addAll(requiredList.getDependentFeatures());

						result.setRequiredModules(requiredList.getExplicitDeps().getModuleIds());

						explicit.addAll(requiredList.getExplicitDeps());
						combined.addAll(requiredList.getExplicitDeps());
						combined.addAll(requiredList.getExpandedDeps());
					}
					if (!requestedModuleNames.getPreloads().isEmpty()) {
						preloadList = new DependencyList(
								DEPSOURCE_REQPRELOADS,
								requestedModuleNames.getPreloads(),
								aggr,
								features,
								true, 	// resolveAliases
								RequestUtil.isRequireExpLogging(request),	// include details
								includeRequireDeps
								);
						dependentFeatures.addAll(preloadList.getDependentFeatures());

						explicit.addAll(preloadList.getExplicitDeps());
						combined.addAll(preloadList.getExplicitDeps());
						combined.addAll(preloadList.getExpandedDeps());
					}
					if (!requestedModuleNames.getExcludes().isEmpty()) {
						excludeList = new DependencyList(
								DEPSOURCE_EXCLUDES,
								requestedModuleNames.getExcludes(),
								aggr,
								features,
								true, 	// resolveAliases
								RequestUtil.isRequireExpLogging(request),	// include details
								false
								);
						dependentFeatures.addAll(excludeList.getDependentFeatures());
						combined.subtractAll(excludeList.getExplicitDeps());
						combined.subtractAll(excludeList.getExpandedDeps());
					}
					boolean isAssertNoNLS = RequestUtil.isAssertNoNLS(request);
					for (Map.Entry<String, ModuleDepInfo> entry : combined.entrySet()) {
						String name = entry.getKey();
						if (isAssertNoNLS && nlsPat.matcher(name).find()) {
							throw new BadRequestException("AssertNoNLS: " + name); //$NON-NLS-1$
						}
						ModuleDepInfo info = entry.getValue();
						if (aggr.getTransport().isServerExpandable(request, name)) {
							int idx = name.indexOf("!");
							if (idx != -1) {
								// convert name to a delegate plugin if necessary
								String plugin = name.substring(0, idx);
								if (aggr.getConfig().getTextPluginDelegators().contains(plugin)) {
									name = aggr.getTransport().getAggregatorTextPluginName() + name.substring(idx);
								} else if (aggr.getConfig().getJsPluginDelegators().contains(plugin)) {
									name = name.substring(idx+1);
								}
							}
							Collection<String> prefixes = info.getHasPluginPrefixes();
							if (prefixes == null ||		// condition is TRUE
									RequestUtil.isIncludeUndefinedFeatureDeps(request) && !prefixes.isEmpty()) {
								IModule module = newModule(request, name);
								if (!explicit.containsKey(name) && aggr.getResourceFactory(new MutableObject<URI>(module.getURI())) == null) {
									// Module is server-expanded and it's not a server resource type that we
									// know how handle, so just ignore it.
									if (isTraceLogging) {
										log.logp(Level.FINER, sourceClass, sourceMethod, "Ignoring module " + name + " due to no resource factory found."); //$NON-NLS-1$ //$NON-NLS-2$
									}
									continue;
								}
								result.add(
										new ModuleList.ModuleListEntry(
												module,
												ModuleSpecifier.LAYER,
												!explicit.containsKey(name)
												)
										);
							}
						}
					}
					if ((requiredList != null || preloadList != null) && RequestUtil.isRequireExpLogging(request)) {
						ModuleDeps expanded = new ModuleDeps();
						if (requiredList != null) {
							expanded.addAll(requiredList.getExpandedDeps());
						}
						if (preloadList != null) {
							expanded.addAll(preloadList.getExpandedDeps());
						}
						if (excludeList != null) {
							explicit.subtractAll(excludeList.getExplicitDeps());
							explicit.subtractAll(excludeList.getExpandedDeps());
							expanded.subtractAll(excludeList.getExplicitDeps());
							expanded.subtractAll(excludeList.getExpandedDeps());
						}
						request.setAttribute(BOOTLAYERDEPS_PROPNAME, new DependencyList(explicit, expanded, dependentFeatures));
					}
					result.setDependenentFeatures(dependentFeatures);
				}
			}
			if (result.isEmpty()) {
				throw new BadRequestException(request.getQueryString());
			}
			request.setAttribute(MODULE_FILES_PROPNAME, result);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, result);
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
	 * @param aggregator
	 * @param modules
	 *            The list of ModuleFile objects
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

	protected List<ICacheKeyGenerator> getCacheKeyGenerators(List<ModuleBuildFuture> futures) throws IOException {
		List<ICacheKeyGenerator> result = new LinkedList<ICacheKeyGenerator>();
		for (Future<ModuleBuildReader> future : futures) {
			ModuleBuildReader reader;
			try {
				reader = future.get();
			} catch (InterruptedException e) {
				throw new IOException(e);
			} catch (ExecutionException e) {
				throw new IOException(e);
			}
			List<ICacheKeyGenerator> keyGens = reader.getCacheKeyGenerators();
			if (keyGens != null) {
				result.addAll(keyGens);
			}
		}
		return result;

	}

	/**
	 * Called by the layer cache manager when a layer build is evicted from the
	 * eviction map do to size limitations.
	 *
	 * @param cacheEntry
	 *            The cache entry that was evicted
	 * @return true if this layer has no more builds and the layer should be
	 *         removed from the layer cache
	 */
	protected boolean cacheEntryEvicted(CacheEntry cacheEntry) {
		return _layerBuilds.cacheEntryEvicted(cacheEntry);
	}

	/**
	 * This method is provided for unit testing
	 *
	 * @return The cacheKeyGenerators for this layer
	 */
	Map<String, ICacheKeyGenerator> getCacheKeyGenerators() {
		return _cacheKeyGenerators;
	}

	/**
	 * Used by unit test cases.
	 *
	 * @return The current layer build map.
	 */
	Map<String, CacheEntry> getLayerBuildMap() {
		return _layerBuilds.getMap();
	}

	/**
	 * Static factory method for layer cache objects
	 *
	 * @param aggregator the aggregator this layer cache belongs to
	 * @return a new layer cache
	 */
	public static ILayerCache newLayerCache(IAggregator aggregator) {
		return new LayerCacheImpl(aggregator);
	}

	protected void setResponseHeaders(HttpServletRequest request, HttpServletResponse response, int size) {
		response.setContentType("application/x-javascript; charset=utf-8"); //$NON-NLS-1$
		response.setContentLength(size);
		if (RequestUtil.isGzipEncoding(request)) {
			response.setHeader("Content-Encoding", "gzip"); //$NON-NLS-1$ //$NON-NLS-2$
		}

	}
}