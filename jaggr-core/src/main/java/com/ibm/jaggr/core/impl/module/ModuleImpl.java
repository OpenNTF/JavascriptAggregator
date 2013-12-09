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

package com.ibm.jaggr.core.impl.module;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.DependencyVerificationException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.cachekeygenerator.AbstractCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.impl.layer.CompletedFuture;
import com.ibm.jaggr.core.impl.layer.ModuleBuildFuture;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.module.ModuleIdentifier;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.modulebuilder.IModuleBuildRenderer;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ErrorModuleReader;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.StringUtil;
import com.ibm.jaggr.core.util.TypeUtil;

public class ModuleImpl extends ModuleIdentifier implements IModule, Serializable {

	private static final long serialVersionUID = -970059455315515031L;

	private static final Logger log = Logger.getLogger(ModuleImpl.class
			.getName());
	

	@SuppressWarnings("serial")
	private static final List<ICacheKeyGenerator> defaultCacheKeyGenerators = Collections.unmodifiableList(
		Arrays.asList(new ICacheKeyGenerator[] {new AbstractCacheKeyGenerator() {
			// This is a singleton, so default equals() will suffice
			private static final String eyecatcher = "nokey"; //$NON-NLS-1$
			@Override
			public String generateKey(HttpServletRequest request) {
				return eyecatcher;
			}
			@Override
			public String toString() {
				return eyecatcher;
			}
		}
	}));
	
	private final URI uri;
	
	private transient IResource resource = null;
	
	private volatile List<ICacheKeyGenerator> _cacheKeyGenerators;

	/** Last modified time of source file that the cached entries are based on. */
	private long _lastModified = 0;

	/**
	 * A mapping of keyname to cache object pairs where the keyname is a string
	 * that is computed by the cache key generator
	 */
	private volatile ConcurrentMap<String, CacheEntry> _moduleBuilds = null; 

	/**
	 * Constructor
	 * 
	 * @param source
	 *            The module source
	 */
	public ModuleImpl(String mid, URI uri) {
		super(mid);
		this.uri = uri;
	}
	
	/**
	 * Copy constructor.  Needed by subclasses that override writeReplace
	 * 
	 * @param module
	 */
	protected ModuleImpl(ModuleImpl module) {
		super(module.getModuleId());
		uri = module.uri;
		resource = module.resource;
		synchronized(module) {
			_cacheKeyGenerators = module._cacheKeyGenerators;
			_lastModified = module._lastModified;
			_moduleBuilds = module._moduleBuilds;
		}
	}
	
	/**
	 * @return The uri for this module
	 */
	@Override
	public URI getURI() {
		return uri;
	}

	@Override
	public IResource getResource(IAggregator aggregator) {
		if (resource == null) {
			resource = aggregator.newResource(uri);
		}
		return resource;
	}

	/**
	 * Returns the compiled (minified, has-filtered) output for this JavaScript
	 * module. AggregatorOptions such as compilation level and has-conditions
	 * are specified as attributes in the request object using the
	 * {@link #COMPILATION_LEVEL}, {@link #HASMAP_REQATTRNAME} and
	 * {@link #COERCE_UNDEFINED_TO_FALSE} request attribute name constants.
	 * <p>
	 * This function returns a {@link Future}{@code <}{@link ModuleReader}
	 * {@code >} which can be used to obtain a reference to the
	 * {@link ModuleReader} when it is available. If the minified version of
	 * this module with the requested compilation level and has-filtering
	 * conditions already exists in the cache, then a completed {@link Future}
	 * will be returned. If the module needs to be compiled, then the
	 * compilation will be performed by an asynchronous thread and a
	 * {@link Future} will be returned which can be used to access the
	 * {@link ModuleReader} when it is available.
	 * <p>
	 * The caller of this function is responsible for closing the reader
	 * associated with the build object.
	 * 
	 * @param request
	 *            The http servlet request object
	 * 
	 * @return A {@link Future}{@code <}{@link ModuleReader}{@code >} that can
	 *         be used to obtain a reader to the minified output.
	 * @throws IOException
	 */
	public Future<ModuleBuildReader> getBuild(final HttpServletRequest request)
			throws IOException {
		return getBuild(request, false);
	}

	/**
	 * Adds <code>fromCacheOnly</code> param that is used by unit test cases. Do
	 * not call this method directly from production code. Use
	 * {@link #getBuild(HttpServletRequest)} instead.
	 * 
	 * @param request
	 *            The http servlet request object
	 * @param fromCacheOnly
	 *            If true, an exception is thrown if the the requested module
	 *            cannot be returned from cache.  Used by unit tests.
	 * @return
	 * @throws IOException
	 *             if source resource OR cached output file is not found
	 */
	protected Future<ModuleBuildReader> getBuild(final HttpServletRequest request,
			boolean fromCacheOnly) throws IOException {

		final boolean isLogLevelFiner = log.isLoggable(Level.FINER);
		final IAggregator aggr = (IAggregator) request
				.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		final IOptions options = aggr.getOptions();
		final ICacheManager mgr = aggr.getCacheManager();

		final boolean ignoreCached = options.isDevelopmentMode() &&
				TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOCACHE_REQATTRNAME));

		if (_moduleBuilds == null) {
			synchronized(this) {
				if (_moduleBuilds == null) {
					_moduleBuilds = new ConcurrentHashMap<String, CacheEntry>();
				}
			}
			
		}
		// If the source doesn't exist, throw an exception.
		final IResource resource = getResource(aggr);
		if (!resource.exists()) {
			if (log.isLoggable(Level.WARNING))
				log.warning(
						MessageFormat.format(
								Messages.ModuleImpl_1,
								new Object[]{resource.getURI().toString()}
						)
				);
			throw new NotFoundException(resource.getURI()
					.toString());
		}

		// Get the last modified date of the source file.
		long modified = resource.lastModified();

		// make local copies of instance variables so we can access them in a
		// thread-safe way
		// without holding onto the synchronization lock for too long
		final List<ICacheKeyGenerator> cacheKeyGenerators;
		final ConcurrentMap<String, CacheEntry> moduleBuilds;
		ConcurrentMap<String, CacheEntry> oldModuleBuilds = null;
		synchronized (this) {
			if (modified != _lastModified) {
				if (isLogLevelFiner) {
					log.finer("Resetting cached modules builds for module " //$NON-NLS-1$
							+ getModuleName() + "\nOld last modified=" //$NON-NLS-1$
							+ _lastModified + ", new last modified=" + modified); //$NON-NLS-1$
				}
				oldModuleBuilds = _moduleBuilds;
				_moduleBuilds = null;
				_lastModified = modified;
				IModuleBuilder builder = aggr.getModuleBuilder(getModuleId(), resource);
				_cacheKeyGenerators = Collections.unmodifiableList(builder.getCacheKeyGenerators(aggr));
				if (_cacheKeyGenerators == null) {
					_cacheKeyGenerators = defaultCacheKeyGenerators;
				}
			}
			cacheKeyGenerators = _cacheKeyGenerators;
			moduleBuilds = _moduleBuilds = (_moduleBuilds != null) ? _moduleBuilds
					: new ConcurrentHashMap<String, CacheEntry>();
		}

		// If we have stale cache files, queue them up for deletion
		if (oldModuleBuilds != null) {
			for (Map.Entry<String, CacheEntry> entry : oldModuleBuilds
					.entrySet()) {
				entry.getValue().delete(mgr); // asynchronous
			}
			oldModuleBuilds.clear(); // help out the GC
		}
		// Generate a cache key and see if we have a cached buildReader already
		final String key = KeyGenUtil.generateKey(request, cacheKeyGenerators);

		Reader reader = null;

		// Try retrieving the cache entry using get first since it doesn't lock.
		// If that fails,
		// then use the locking putIfAbsent
		CacheEntry existingEntry = null;
		if (!ignoreCached) {
			existingEntry = moduleBuilds.get(key);
			if (existingEntry != null
				&& (reader = existingEntry.tryGetReader(mgr.getCacheDir(), request)) != null) {
				if (isLogLevelFiner) {
					log.finer("returning cached module build with cache key: " //$NON-NLS-1$
							+ key);
				}
				ModuleBuildReader mbr = new ModuleBuildReader(reader,
						cacheKeyGenerators, false);
				processExtraModules(mbr, request, existingEntry);
				return new CompletedFuture<ModuleBuildReader>(mbr);
			}
		}

		CacheEntry newEntry = new CacheEntry();
		// Try to retrieve an existing cache entry using the blocking
		// putIfAbsent. If the return
		// value is null, then the newEntry was successfully added to the map,
		// otherwise the
		// existing entry is returned in the buildReader and newEntry was not
		// added.
		if (!ignoreCached) {
			existingEntry = moduleBuilds.putIfAbsent(key, newEntry);
			if (existingEntry != null
				&& (reader = existingEntry.tryGetReader(mgr.getCacheDir(), request)) != null) {
				if (isLogLevelFiner) {
					log.finer("returning cached module build with cache key: " //$NON-NLS-1$
							+ key);
				}
				ModuleBuildReader mbr = new ModuleBuildReader(reader,
						cacheKeyGenerators, false);
				processExtraModules(mbr, request, existingEntry);
				return new CompletedFuture<ModuleBuildReader>(mbr);
			}
		}

		if (fromCacheOnly) {
			// For unit testing purposes
			throw new NotFoundException(getModuleName());
		}
		// store the ModuleBuild we're working with in a final local so it can
		// be accessed from the
		// annonymous inner class of the Callable
		final CacheEntry cacheEntry = (existingEntry != null) ? existingEntry
				: newEntry;

		// Submit the task to the request executor and return a
		// Future<ModuleReader> to the caller
		return aggr.getExecutors().getBuildExecutor().submit(new Callable<ModuleBuildReader>() {
			public ModuleBuildReader call() throws Exception {
				List<ICacheKeyGenerator> newCacheKeyGenerators = 
						KeyGenUtil.isProvisional(cacheKeyGenerators) ? null : cacheKeyGenerators;
				ModuleBuild build;
				// Synchronize on the ModuleBuild object for the compile.
				// This will prevent multiple
				// threads from compiling the same output. If more than one
				// thread requests the same
				// output (same cache key), then the first one to grab the
				// sync object will win and
				// the rest will wait for the first thread to finish
				// compiling and then just return
				// the output from the first thread when they wake.
				synchronized (cacheEntry) {
					Reader reader = null;
					try {
						// Check to see if data is available in case a different
						// thread finished
						// compiling the output while we were blocked on the
						// sync object.
						if (!ignoreCached
								&& (reader = cacheEntry.tryGetReader(mgr
										.getCacheDir(), request)) != null) {
							if (isLogLevelFiner) {
								log.finer("returning built module with cache key: " //$NON-NLS-1$
										+ key);
							}
							ModuleBuildReader mbr = new ModuleBuildReader(reader,
									_cacheKeyGenerators, false); 
							processExtraModules(mbr, request, cacheEntry);
							return mbr;
						}
						// Build the output
						IModuleBuilder builder = aggr.getModuleBuilder(getModuleId(), resource);
						build = builder.build(
								getModuleId(), 
								resource, 
								request, 
								newCacheKeyGenerators);
						if (build.isError()) {
							// Don't cache error results
							return new ModuleBuildReader(new StringReader(build
									.getBuildOutput().toString()), null, true);
						}
						cacheEntry.setData(build.getBuildOutput(), build.getBefore(), build.getAfter());
						
						// If the cache key generator has changed, then update the 
						if (newCacheKeyGenerators == null || !newCacheKeyGenerators.equals(build.getCacheKeyGenerators())) {
							synchronized (this) {
								if (_moduleBuilds == moduleBuilds) {
									_cacheKeyGenerators = newCacheKeyGenerators = Collections.unmodifiableList(
											newCacheKeyGenerators == null ?
													build.getCacheKeyGenerators() :
													KeyGenUtil.combine(newCacheKeyGenerators, build.getCacheKeyGenerators())
									);
								}
							}
							if (!ignoreCached) {
								CacheEntry oldEntry = null;
								String newkey = KeyGenUtil.generateKey(request, newCacheKeyGenerators);
								// If the key changed, then add the ModuleBuild to the
								// cache using the new key and remove the one under the
								// old key
								if (!newkey.equals(key)) {
									if (isLogLevelFiner) {
										log.finer("Updating cache key for module build.  Old key = " //$NON-NLS-1$
												+ key + ", new key = " + newkey); //$NON-NLS-1$
									}
									oldEntry = moduleBuilds.putIfAbsent(newkey,
											cacheEntry);
									moduleBuilds.remove(key, cacheEntry);
								}
								// Only write out the cache file if the put was
								// successful
								if (oldEntry == null || oldEntry == cacheEntry) {
									cacheEntry.persist(mgr, ModuleImpl.this); // asynchronous
								}
							}
						} else if (!ignoreCached) {
							// Write the cache file to disk
							cacheEntry.persist(mgr, ModuleImpl.this); // asynchronous
						}
					} catch (Exception ex) {
						// don't cache error responses
						moduleBuilds.remove(key, cacheEntry);
						if (options.isDevelopmentMode() || options.isDebugMode()) {
							if (options.isDevelopmentMode()) {
								Throwable t = ex;
					        	while (t != null) {
						        	if (t instanceof DependencyVerificationException) {
						        		// This exception will be handled by the layer builder
						        		throw (DependencyVerificationException)t;
						        	} else if (t instanceof ProcessingDependenciesException) {
						        		throw (ProcessingDependenciesException)t;
						        	}
					        		t = t.getCause();
					        	}
							}
							// Log error
							if (log.isLoggable(Level.SEVERE)) {
								log.log(Level.SEVERE, ex.getMessage(), ex);
							}
							// In debug/development mode, don't throw an exception.
							// Instead, log the
							// error on the client.
							return new ModuleBuildReader(
								new ErrorModuleReader(
									StringUtil.escapeForJavaScript(	
										ex.getClass().getName() + 
										": " + 	ex.getMessage() + //$NON-NLS-1$
										"\r\n" + //$NON-NLS-1$
										Messages.ModuleImpl_2
									),
									getModuleName(),
									request
								), null, true
							);
		
						} else {
							throw ex;
						}
					}
				}
				ModuleBuildReader mbr = new ModuleBuildReader(
						cacheEntry.getReader(mgr.getCacheDir(), request),
						newCacheKeyGenerators, false); 
				processExtraModules(mbr, request, cacheEntry);
				// return a build reader object
				return mbr;
			}
		});
	}

	/**
	 * For any extra modules specified by {@code cacheEntry}, obtain a build
	 * future from the module cache manager and add it to the build futures
	 * queue specified by {@link ILayer#BUILDFUTURESQUEUE_REQATTRNAME}.
	 * 
	 * @param request
	 *            The http request
	 * @param cacheEntry
	 *            The cache entry object for the current module
	 * @throws IOException
	 */
	public void processExtraModules(ModuleBuildReader reader, HttpServletRequest request, CacheEntry cacheEntry) 
			throws IOException {
		
		List<IModule> before = cacheEntry.getBefore(), after = cacheEntry.getAfter();
		if (!before.isEmpty() || !after.isEmpty()) {
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			for (IModule module : cacheEntry.getBefore()) {
				Future<ModuleBuildReader> future = aggr.getCacheManager().getCache().getModules().getBuild(request, module);
				ModuleBuildFuture mbf = new ModuleBuildFuture(
						module, 
						future,
						ModuleSpecifier.BUILD_ADDED);
				reader.addBefore(mbf);
			}
			for (IModule module : cacheEntry.getAfter()) {
				Future<ModuleBuildReader> future = aggr.getCacheManager().getCache().getModules().getBuild(request, module);
				ModuleBuildFuture mbf = new ModuleBuildFuture(
						module, 
						future,
						ModuleSpecifier.BUILD_ADDED);
				reader.addAfter(mbf);
			}
		}
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		String linesep = System.getProperty("line.separator"); //$NON-NLS-1$
		StringBuffer sb = new StringBuffer();
		sb.append("IModule: ").append(getModuleName()) //$NON-NLS-1$
				.append("\nSource: ") //$NON-NLS-1$
				.append(uri).append(linesep);

		long lastModified = _lastModified;
		if (lastModified != 0) {
			sb.append("Modified: ").append(new Date(lastModified).toString()) //$NON-NLS-1$
					.append(linesep);
		}
		if (_cacheKeyGenerators != null) {
			sb.append("KeyGen: ") //$NON-NLS-1$
				.append(KeyGenUtil.toString(_cacheKeyGenerators))
				.append(linesep);
		}
		if (_moduleBuilds != null) {
			for (Map.Entry<String, CacheEntry> entry : _moduleBuilds.entrySet()) {
				sb.append("\t").append(entry.getKey()).append(" : ") //$NON-NLS-1$ //$NON-NLS-2$
						.append(entry.getValue().fileName()).append(linesep);
			}
		}
		sb.append(linesep);
		return sb.toString();
	}

	/*
	 * This accessor is provided for unit testing only
	 */
	protected String getCachedFileName(String key) throws InterruptedException {
		String result = null;
		if (_moduleBuilds != null) {
			CacheEntry bld = _moduleBuilds.get(key);
			for (int i = 0; i < 5; i++) {
				if (bld.filename == null) {
					Thread.sleep(500L);
				}
			}
			result = _moduleBuilds.get(key).fileName();
		}
		return result;
	}

	/*
	 * Returns the set of keys for cached entries. Used for unit testing
	 * 
	 * @return the cache keys
	 */
	protected Collection<String> getKeys() {
		Collection<String> result = Collections.emptySet();
		if (_moduleBuilds != null) {
			result = Collections.unmodifiableCollection(_moduleBuilds.keySet());
		}
		return result;
	}

	/*
	 * Returns the cache key generators for this module. Used for unit testing
	 * 
	 * @return the cache keys
	 */
	protected List<ICacheKeyGenerator> getCacheKeyGenerators() {
		return _cacheKeyGenerators; 
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.ibm.domino.servlets.aggrsvc.modules.Module#deleteCached(com.ibm.domino
	 * .servlets.aggrsvc.modules.CacheManager, int)
	 */
	/**
	 * Asynchronously delete the set of cached files for this module.
	 */
	@Override
	public void clearCached(ICacheManager mgr) {
		Map<String, CacheEntry> moduleBuilds;
		synchronized (this) {
			moduleBuilds = _moduleBuilds;
			_moduleBuilds = null;
		}
		if (moduleBuilds != null) {
			for (Map.Entry<String, CacheEntry> entry : moduleBuilds.entrySet()) {
				entry.getValue().delete(mgr);
			}
			moduleBuilds.clear();
		}
	}

	/**
	 * Static factory method for a new module cache object
	 * 
	 * @param aggregator the aggregator that this module cache belongs to
	 * @return the new module cache object
	 */
	public static IModuleCache newModuleCache(IAggregator aggregator) {
		return new ModuleCacheImpl();
	}

	/* ---------------- Serialization Support -------------- */
	/*
	 * Use a Serialization proxy so that we can copy the proxy object 
	 * in a thread safe manner, thereby avoiding the need to use 
	 * synchronization while doing disk i/o. 
	 */
	protected Object writeReplace() throws ObjectStreamException {
		return new SerializationProxy(this);
	}

	private void readObject(ObjectInputStream stream) throws InvalidObjectException {
	    throw new InvalidObjectException("Proxy required"); //$NON-NLS-1$
	}

	protected static class SerializationProxy extends ModuleIdentifier implements Serializable {
		private static final long serialVersionUID = 5050612601255358014L;

		private final URI uri;
		private final List<ICacheKeyGenerator> _cacheKeyGenerators;
		private final long _lastModified;
		private final ConcurrentHashMap<String, CacheEntry> _moduleBuilds; 

		protected SerializationProxy(ModuleImpl module) {
			super(module.getModuleId());
			synchronized (module) {
				uri = module.uri;
				_cacheKeyGenerators = module._cacheKeyGenerators;
				_lastModified = module._lastModified;
				_moduleBuilds = (module._moduleBuilds != null) ? 
						new ConcurrentHashMap<String, CacheEntry>(module._moduleBuilds) : null;
			}
	    }

	    protected Object readResolve() {
	    	ModuleImpl module = new ModuleImpl(super.getModuleId(), uri);
	    	module._cacheKeyGenerators = _cacheKeyGenerators;
	    	module._lastModified = _lastModified;
	    	module._moduleBuilds = _moduleBuilds;
	    	return module;
	    }
	}

	/**
	 * Class to encapsulate operations on module builds. Uses
	 * {@link ExecutorService} objects to asynchronously create and delete cache
	 * files. ICache files are deleted using a {@link ScheduledExecutorService}
	 * with a 10 minute delay to allow sufficient time for any threads that may
	 * still be using the file to be done with them.
	 * 
	 * This class avoids synchronization by declaring the instance variables
	 * volatile and being careful about the order in which variables are
	 * assigned and read. See comments in the various methods for details.
	 */
	/*
	 * (non-Javadoc) Resist the temptation to change this from a static inner
	 * class to a non-static inner class. Although doing so may seem convenient
	 * by providing access to the parent class instance fields, it would cause
	 * problems with cloning and serialization of the cloned objects because the
	 * cloned instances of this class would continue to reference the original
	 * instances of the containing class and these original instances would end
	 * up getting serialized along with the cloned instances of the containing
	 * class. Serialization is done using cloned cache objects to avoid
	 * contention on synchronized locks that would need to be held during file
	 * I/O if the live cache objects were serialized.
	 */
	static final private class CacheEntry implements Cloneable, Serializable {
		private static final long serialVersionUID = -8079746606394403358L;

		private volatile transient Object content = null;
		private volatile String filename = null;
		private volatile boolean isString = false;
		private volatile List<IModule> beforeModules = Collections.emptyList();
		private volatile List<IModule> afterModules = Collections.emptyList();

		/**
		 * @return The filename of the cached module build
		 */
		public String fileName() {
			return filename;
		}

		/**
		 * @return A ModuleReader for the build output
		 * @throws FileNotFoundException
		 */
		public Reader getReader(File cacheDir, HttpServletRequest request) throws IOException {
			Reader reader = null;
			// Make local copies of volatile instance variables so that we can
			// check
			// and then use the values without locking. Note that it's important
			// to get
			// the value of this.content before this.filepath because
			// this.filepath is
			// set before this.content is cleared in persist().
			Object content = this.content;
			String filename = this.filename;
			if (isString) {
				if (content == null) {
					if (filename == null) {
						throw new IllegalStateException();
					}
					// Read the file and return a StringReader instead of just
					// returning a reader to the file so that we can take advantage of
					// parallel processing to read the files on the module builder threads.  
					Reader fileReader = new FileReader(new File(cacheDir, filename));
					StringWriter writer = new StringWriter();
					CopyUtil.copy(fileReader, writer);
					content = writer.toString();
				}
				return new StringReader((String)content);
			} else {
				if (content == null) {
					if (filename == null) {
						throw new IllegalStateException();
					}
					ObjectInputStream is = new ObjectInputStream(
							new FileInputStream(new File(cacheDir, filename)));
					try {
						content = is.readObject();
					} catch (ClassNotFoundException e) {
						throw new IOException(e.getMessage(), e);
					} finally {
						try { is.close(); } catch (Exception ignore) {}
					}
				}
				reader = new StringReader(
						(content instanceof IModuleBuildRenderer) ? 
								((IModuleBuildRenderer)content).renderBuild(request) :
								content.toString());
			}
			return reader;
		}

		/**
		 * @param content
		 *            The built output
		 * @param before
		 *            The list of before modules for this module build
		 * @param after
		 *            The list of after modules for this module build
		 */
		public void setData(Object content, List<IModule> before, List<IModule> after) {
			this.beforeModules = before;
			this.afterModules = after;
			this.content = content;
			this.isString = content instanceof String;
		}

		/**
		 * Returns the list of module IDs to include in a layer build before
		 * this module
		 * 
		 * @return The list of before modules
		 */
		public List<IModule> getBefore()  {
			return beforeModules == null ? Collections.<IModule>emptyList() : Collections.unmodifiableList(beforeModules);
		}
		
		/**
		 * Returns the list of module IDs to include in a layer build after
		 * this module
		 * 
		 * @return The list of after modules
		 */
		public List<IModule> getAfter()  {
			return afterModules == null ? Collections.<IModule>emptyList() : Collections.unmodifiableList(afterModules);
		}
		
		/**
		 * A version of getReader that can fail, but won't throw
		 * 
		 * @return A ModuleReader for the output, or null if no output is
		 *         available
		 */
		Reader tryGetReader(File cacheDir, HttpServletRequest request) {
			Reader reader = null;
			if (content != null || filename != null) {
				try {
					reader = getReader(cacheDir, request);
				} catch (IOException e) {
					// If we get a FileNotFoundException, continue on and
					// replace the cached entry
					if (log.isLoggable(Level.INFO))
						log.info(
							MessageFormat.format(
								Messages.ModuleImpl_3,
								new Object[]{filename}
							)
						);

					// Clear filename so that we won't throw an exception again
					filename = null;
				} catch (Exception e) {
					// just return null
				}
			}
			return reader;
		}

		/**
		 * Asynchronously saves the build output to a cache file on disk and
		 * updates the {@code filename} field with the cache file name.
		 * 
		 * @param mgr
		 *            The {@Link ICacheManager} from which to obtain the
		 *            {@Link ExecutorService}
		 * @param module
		 *            A reference to the {@Link IModule} object that this
		 *            build is attached to
		 */
		public void persist(final ICacheManager mgr, final ModuleImpl module) {
			String mid = new ModuleIdentifier(module.getModuleId()).getModuleName();
			int idx = mid.lastIndexOf("/"); //$NON-NLS-1$
			String name = "_" + ((idx != -1) ? mid.substring(idx + 1) : mid) + "."; //$NON-NLS-1$ //$NON-NLS-2$
			if (isString) {
				mgr.createCacheFileAsync(name, 
						new StringReader(content.toString()),
						new ICacheManager.CreateCompletionCallback() {
							@Override
							public void completed(String fname, Exception e) {
								if (e == null) {
									// Must set filename before clearing content
									// since we don't synchronize.
									filename = fname;
									// Free up the memory for the content now that
									// we've written out to disk
									// TODO: Determine a size threshold where we may
									// want to keep the contents
									// of small files in memory to reduce disk i/o.
									content = null;
								}
							}
						});
			} else {
				mgr.externalizeCacheObjectAsync(name, 
						content,
						new ICacheManager.CreateCompletionCallback() {
							@Override
							public void completed(String fname, Exception e) {
								if (e == null) {
									// Must set filename before clearing content
									// since we don't synchronize.
									filename = fname;
									// Free up the memory for the content now that
									// we've written out to disk
									// TODO: Determine a size threshold where we may
									// want to keep the contents
									// of small files in memory to reduce disk i/o.
									content = null;
								}
							}
						});
			}
		}

		/**
		 * @param mgr
		 *            The {@link ICacheManager} object from which to get the
		 *            {@link ScheduledExecutorService} to submit the delete task
		 * @param delay
		 *            The number of minutes to wait before performing the delete
		 */
		public void delete(ICacheManager mgr) {
			if (filename != null) {
				mgr.deleteFileDelayed(filename);
			}
		}
	}
}
