/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.module;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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

import com.ibm.jaggr.service.DependencyVerificationException;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.NotFoundException;
import com.ibm.jaggr.service.ProcessingDependenciesException;
import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.cachekeygenerator.AbstractCacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.impl.layer.CompletedFuture;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.module.ModuleIdentifier;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.service.modulebuilder.ModuleBuild;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.readers.ErrorModuleReader;
import com.ibm.jaggr.service.readers.ModuleBuildReader;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.StringUtil;
import com.ibm.jaggr.service.util.TypeUtil;

public class ModuleImpl extends ModuleIdentifier implements IModule, Cloneable {

	private static final long serialVersionUID = 8809476135160923678L;

	private static final Logger log = Logger.getLogger(ModuleImpl.class
			.getName());
	

	@SuppressWarnings("serial")
	private static final ICacheKeyGenerator[] defaultCacheKeyGenerators =
		new ICacheKeyGenerator[] {new AbstractCacheKeyGenerator() {
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
	};
	
	private final URI uri;
	
	private transient IResource resource = null;
	
	private volatile ICacheKeyGenerator[] _cacheKeyGenerators;

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
	 *            cannot be returned from cache
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
		final ICacheKeyGenerator[] cacheKeyGenerators;
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
				_cacheKeyGenerators = builder.getCacheKeyGenerators(aggr);
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
		CacheEntry existingEntry = moduleBuilds.get(key);
		if (!ignoreCached
				&& existingEntry != null
				&& (reader = existingEntry.tryGetReader(mgr.getCacheDir())) != null) {
			if (isLogLevelFiner) {
				log.finer("returning cached module build with cache key: " //$NON-NLS-1$
						+ key);
			}
			return new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(reader,
					_cacheKeyGenerators, false));
		}

		CacheEntry newEntry = new CacheEntry();
		// Try to retrieve an existing cache entry using the blocking
		// putIfAbsent. If the return
		// value is null, then the newEntry was successfully added to the map,
		// otherwise the
		// existing entry is returned in the buildReader and newEntry was not
		// added.
		existingEntry = moduleBuilds.putIfAbsent(key, newEntry);
		if (!ignoreCached
				&& existingEntry != null
				&& (reader = existingEntry.tryGetReader(mgr.getCacheDir())) != null) {
			if (isLogLevelFiner) {
				log.finer("returning cached module build with cache key: " //$NON-NLS-1$
						+ key);
			}
			return new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(reader,
					_cacheKeyGenerators, false));
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
				ICacheKeyGenerator[] newCacheKeyGenerators = cacheKeyGenerators;
				try {
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

						// Check to see if data is available in case a different
						// thread finished
						// compiling the output while we were blocked on the
						// sync object.
						if (!ignoreCached
								&& (reader = cacheEntry.tryGetReader(mgr
										.getCacheDir())) != null) {
							if (isLogLevelFiner) {
								log.finer("returning built module with cache key: " //$NON-NLS-1$
										+ key);
							}
							return new ModuleBuildReader(reader,
									_cacheKeyGenerators, false);
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
									.getBuildOutput()), null, true);
						}
						cacheEntry.setData(build.getBuildOutput());
					}
					// If we don't yet have has-conditionals for this module,
					// then set them from the
					// discoveredHasConditionalss we got as a buildReader of the
					// compilation, and then update
					// the cache key for this cache entry if necessary.
					if (KeyGenUtil.isProvisional(newCacheKeyGenerators)) {
						newCacheKeyGenerators = build.getCacheKeyGenerators();
						synchronized (this) {
							if (KeyGenUtil.isProvisional(_cacheKeyGenerators)
									&& _moduleBuilds == moduleBuilds) {
								_cacheKeyGenerators = newCacheKeyGenerators;
							}
						}
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
						if ((oldEntry == null || oldEntry == cacheEntry) && !ignoreCached) {
							cacheEntry.persist(mgr, ModuleImpl.this); // asynchronous
						}
					} else if (!ignoreCached) {
						// Write the cache file to disk
						cacheEntry.persist(mgr, ModuleImpl.this); // asynchronous
					}
				} catch (Exception ex) {
					// don't cache error responses
					moduleBuilds.remove(key, cacheEntry);
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
						// Log error
						if (log.isLoggable(Level.SEVERE)) {
							log.log(Level.SEVERE, ex.getMessage(), ex);
						}
						// In development mode, don't throw an exception.
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
				// return a Build object
				return new ModuleBuildReader(
						cacheEntry.getReader(mgr.getCacheDir()),
						newCacheKeyGenerators, false);
			}
		});
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
				.append(KeyGenUtil.toString(Arrays.asList(_cacheKeyGenerators)))
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

	/**
	 * Clones this object. Note that although cloning is thread safe, it is NOT
	 * atomic. This means that some mutable objects, like {@link #_moduleBuilds}
	 * and it's entries, can be changing while we are in the process of cloning.
	 * Although these changes are done in a thread safe way, the code in this
	 * class must be able to tolerate any potential inconsistencies resulting
	 * from such changes when this object is de-serialized.
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		ModuleImpl clone;
		// _hasConditionals is an immutable set, so a reference clone is ok
		synchronized (this) {
			clone = (ModuleImpl) super.clone();
			clone.resource = null;
		}
		// ConcurrentHashMap doesn't implement Cloneable, so need to use the
		// copy
		// constructor instead. Be sure to use the cloned map as the source for
		// the copy in order to maintain consistency with the rest of the cloned
		// object.
		if (clone._moduleBuilds != null) {
			clone._moduleBuilds = new ConcurrentHashMap<String, CacheEntry>(
					clone._moduleBuilds);

			// Now clone each of the entries in the in the map
			for (Map.Entry<String, CacheEntry> cacheEntry : clone._moduleBuilds
					.entrySet()) {
				cacheEntry
						.setValue((CacheEntry) cacheEntry.getValue().clone());
			}
		}
		return clone;
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

	/**
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
		private static final long serialVersionUID = -8117783999830131470L;

		private volatile transient String content = null;
		private volatile String filename = null;

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
		public Reader getReader(File cacheDir) throws FileNotFoundException {
			Reader reader = null;
			// Make local copies of volatile instance variables so that we can
			// check
			// and then use the values without locking. Note that it's important
			// to get
			// the value of this.content before this.filepath because
			// this.filepath is
			// set before this.content is cleared in persist().
			String content = this.content;
			String filename = this.filename;
			if (content != null) {
				reader = new StringReader(content);
			} else if (filename != null) {
				reader = new FileReader(new File(cacheDir, filename));
			}
			return reader;
		}

		/**
		 * @param content
		 *            The built output
		 */
		public void setData(String content) {
			this.content = content;
		}

		/**
		 * A version of getReader that can fail, but won't throw
		 * 
		 * @return A ModuleReader for the output, or null if no output is
		 *         available
		 */
		Reader tryGetReader(File cacheDir) {
			Reader reader = null;
			if (content != null || filename != null) {
				try {
					reader = getReader(cacheDir);
				} catch (FileNotFoundException e) {
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
			String name = (idx != -1) ? mid.substring(idx + 1) : mid;
			mgr.createCacheFileAsync("_" + name + ".", //$NON-NLS-1$ //$NON-NLS-2$
					new StringReader(content),
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

		/*
		 * (non-Javadoc)
		 * 
		 * @see java.lang.Object#clone()
		 */
		@Override
		public Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}
}
