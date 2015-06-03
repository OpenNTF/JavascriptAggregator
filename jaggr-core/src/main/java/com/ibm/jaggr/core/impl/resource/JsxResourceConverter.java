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
package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.cache.ICache;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.cache.ICacheManagerListener;
import com.ibm.jaggr.core.impl.cache.ResourceConverterCacheImpl;
import com.ibm.jaggr.core.impl.cache.ResourceConverterCacheImpl.IConverter;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceConverter;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Reader;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Converter for jsx files.  If the requested JavaScript file doesn't exist, then
 * check to see if there's a .jsx file in the same directory with the same base name
 * and if there is, then return the compiled output as the converted resource.
 */
public class JsxResourceConverter implements IResourceConverter, IExtensionInitializer, ICacheManagerListener {
	private static final String sourceClass = JsxResourceConverter.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);

	/**
	 * Thread local variable used to allow us to invoke {@link IAggregator#newResource(URI)} without
	 * running the convert method recursively
	 */
	private static final ThreadLocal<Boolean> isSkipResourceConversion = new ThreadLocal<Boolean>() {
        @Override protected Boolean initialValue() {
            return false;
        }
	};

	private static final String JSX_CACHE_NAME = "jsxCache"; //$NON-NLS-1$
	private static final String JSXTRANSFORMER_DIRNAME = "JSXTransformer.js"; //$NON-NLS-1$
	private static final String JSXTRANSFORMER_NAME = "JSXTransformer"; //$NON-NLS-1$
	private IAggregator aggregator;
	private IServiceRegistration cacheMgrListenerReg;

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IExtensionInitializer#initialize(com.ibm.jaggr.core.IAggregator, com.ibm.jaggr.core.IAggregatorExtension, com.ibm.jaggr.core.IExtensionInitializer.IExtensionRegistrar)
	 */
	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension extension,
			IExtensionRegistrar registrar) {
		final String sourceMethod = "initialize"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceMethod, sourceMethod, new Object[]{aggregator, extension});
		}
		this.aggregator = aggregator;
		isSkipResourceConversion.set(false);
		// Register a cache manager listener so that we can add our named cache after
		// the cache manager has been initialized.
		Dictionary<String,String> dict;
		dict = new Hashtable<String, String>();
		dict.put("name", aggregator.getName()); //$NON-NLS-1$
		cacheMgrListenerReg = aggregator.getPlatformServices().registerService(ICacheManagerListener.class.getName(), this, dict);

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cache.ICacheManager.ICacheManagerListener#initialized(com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void initialized(ICacheManager cacheManager) {
		final String sourceMethod = "initialized"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceMethod, sourceMethod);
		}
		// Cache manager is initialized.  De-register the listener and add our named cache
		cacheMgrListenerReg.unregister();
		ResourceConverterCacheImpl cache = new ResourceConverterCacheImpl(new JsxConverter(), "jsx.", ""); //$NON-NLS-1$ //$NON-NLS-2$
		cache.setAggregator(aggregator);
		ResourceConverterCacheImpl oldCache = (ResourceConverterCacheImpl)cacheManager.getCache().putIfAbsent(JSX_CACHE_NAME, cache);
		cache = oldCache != null ? oldCache : cache;

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.resource.IResourceConverter#convert(java.net.URI, com.ibm.jaggr.core.resource.IResource)
	 */
	@Override
	public IResource convert(final IResource resource) {
		final String sourceMethod = "convert"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceMethod, sourceMethod, new Object[]{resource});
		}
		IResource result = resource;

		if (!isSkipResourceConversion.get()) {
			if (!resource.isFolder()) {
				// is the request for a JavaScript file
				if (resource.getPath().endsWith(".js") && resource.getPath().length() > 3) { //$NON-NLS-1$
					if (!resource.exists()){
						// The requested javascript file doesn't exist.  See if we can satisfy the request
						// from our cache.
						ICache cache = aggregator.getCacheManager().getCache();
						try {
							ResourceConverterCacheImpl jsxCache = (ResourceConverterCacheImpl)cache.getCache(JSX_CACHE_NAME);
							if (jsxCache == null) {
								throw new NotFoundException(JSX_CACHE_NAME);
							}
							// Use the resource's reference uri as the cache key
							String cacheKey = resource.getReferenceURI().toString();
							// Look for the file in the cache.
							IResource tryResult = jsxCache.convert(cacheKey, null);
							if (tryResult != null && !aggregator.getOptions().isDevelopmentMode()) {
								// Converted resource found in cache.  Return it.
								result = tryResult;
							} else {
								// Resource not in cache, or we're in development mode and we need to
								// check the last-modified time of the cache entry against the source.
								// Construct a resource object for the .jsx file with the same base name
								// and in the same directory as the requested javascript file.
								String path = resource.getReferenceURI().getPath();
								int idx = path.lastIndexOf("/"); //$NON-NLS-1$
								String name = path.substring(idx == -1 ? 0 : idx+1, path.length()-3) + ".jsx"; //$NON-NLS-1$
								IResource jsxRes = aggregator.newResource(resource.resolve(name));
								// see if the jsx resource exists.
								if (jsxRes.exists()) {
									// get the converted resource from the cache, this time
									// providing the .jsx source so that it can be converted if
									// the cache entry is missing or is stale.
									jsxRes.setReferenceURI(resource.getReferenceURI());
									result = jsxCache.convert(cacheKey, jsxRes);
								}
							}
						} catch (IOException ex) {
							log.logp(Level.WARNING, sourceClass, sourceMethod, ex.getMessage(), ex);
							result = new ExceptionResource(resource.getReferenceURI(), resource.lastModified(), ex);
						}
					}
				}
			} else {
				// It's a folder resource.  Wrap the folder resource so that we can override the
				// tree walker so as to make .jsx files in folder listings appear as .js files.
				return new FolderResourceWrapper(resource);
			}
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, result);
		}
		return result;
	}

	/**
	 * Wrapper class for folder resources used to modify folder contents reported by
	 * the tree walker for the purpose of making .jsx files appear as .js files.
	 */
	private class FolderResourceWrapper implements IResource {

		private final IResource wrapped;  // the wrapped forlder resource

		private FolderResourceWrapper(IResource wrapped) {
			this.wrapped = wrapped;
		}
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getURI()
		 */
		@Override
		public URI getURI() {
			return wrapped.getURI();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getPath()
		 */
		@Override
		public String getPath() {
			return wrapped.getPath();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getReferenceURI()
		 */
		@Override
		public URI getReferenceURI() {
			return wrapped.getReferenceURI();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#setReferenceURI(java.net.URI)
		 */
		@Override
		public void setReferenceURI(URI uri) {
			wrapped.setReferenceURI(uri);
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#exists()
		 */
		@Override
		public boolean exists() {
			return wrapped.exists();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#isFolder()
		 */
		@Override
		public boolean isFolder() {
			return wrapped.isFolder();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getSize()
		 */
		@Override
		public long getSize() throws IOException{
			return wrapped.getSize();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#lastModified()
		 */
		@Override
		public long lastModified() {
			return wrapped.lastModified();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#resolve(java.lang.String)
		 */
		@Override
		public URI resolve(String relative) {
			return wrapped.resolve(relative);
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getReader()
		 */
		@Override
		public Reader getReader() throws IOException {
			return wrapped.getReader();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return wrapped.getInputStream();
		}

		/**
		 * Our implementation of the tree walker overrides the wrapped implementation to
		 * provide our own implementation of the resource visitor.  Our resource visitor
		 * converts .jsx entries to .js entries before calling the visitor belonging to
		 * the wrapped object.  Entries with .jsx extensions are mapped to .js files only
		 * if the folder does not already contain a .js file with the same name.  This way
		 * we avoid the possibility of calling the visitor twice for the same name.
		 */
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#walkTree(com.ibm.jaggr.core.resource.IResourceVisitor)
		 */
		@Override
		public void walkTree(final IResourceVisitor visitor) throws IOException {
			wrapped.walkTree(new IResourceVisitor() {
				@Override
				public boolean visitResource(Resource resource, String pathName) throws IOException {
					if (!resource.isFolder() && pathName.endsWith(".jsx") && pathName.length() > 4) { //$NON-NLS-1$
						// Convert jsx to js
						String jsPathName = pathName.substring(0, pathName.length()-3) + "js"; //$NON-NLS-1$
						URI jsUri = wrapped.resolve(jsPathName);
						IResource jsRes;
						// Create a new resource object for the javascript resource so we can
						// check for existence, setting the thread local flag so that we ignore
						// this resource when the aggregator calls our convert method.
						isSkipResourceConversion.set(true);
						try {
							jsRes = aggregator.newResource(jsUri);
						} finally {
							isSkipResourceConversion.set(false);
						}
						if (!jsRes.exists()) {
							// Synthesize a visitor resource entry for the .js file and
							// call the resource visitor
							visitor.visitResource(
									new JsxVisitorResource(jsUri, resource.lastModified(), jsPathName),
									jsPathName);
						}
					}
					return visitor.visitResource(resource, pathName);
				}
			});
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#asVisitorResource()
		 */
		@Override
		public Resource asVisitorResource() {
			return wrapped.asVisitorResource();
		}

	}

	/**
	 * Resource visitor for synthesized .js entries that returns a {@link NotFoundResource}
	 * from the {@link #newResource()} method.  When this {@link NotFoundResource} is provided
	 * to the {@link JsxResourceConverter#convert(IResource)} method, then we will convert
	 * it to a resource with content from the transpiled .jsx file.
	 */
	static class JsxVisitorResource implements Resource {
		final URI uri;
		final long lastModified;
		final String path;

		private JsxVisitorResource(URI uri, long lastModified, String path) {
			final String sourceMethod = "<ctor>"; //$NON-NLS-1$
			final boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(JsxVisitorResource.class.getName(), sourceMethod, new Object[]{uri, lastModified, path});
			}
			this.uri = uri;
			this.lastModified = lastModified;
			this.path = path;
			if (isTraceLogging) {
				log.exiting(JsxVisitorResource.class.getName(), sourceMethod);
			}
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#getURI()
		 */
		@Override
		public URI getURI() {
			return uri;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#isFolder()
		 */
		@Override
		public boolean isFolder() {
			return false;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#lastModified()
		 */
		@Override
		public long lastModified() {
			return lastModified;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#newResource()
		 */
		@Override
		public IResource newResource() {
			if (path == null) {
				throw new UnsupportedOperationException();
			}
			// return a new NotFoundResource, which JsxResourceConverter#convert() will
			// convert into a resource for the js file when it is called
			// to process the NotFoundResource.
			return new NotFoundResource(uri);
		}

	}
	/**
	 * The converter class provided to the {@link ResourceConverterCacheImpl} constructor.
	 * Writes the transpiled content of the source .jsx file to the specified cache file.
	 * Needs to be serializable because it is referenced by ResourceConverterCacheImpl which
	 * is a serializable cache implementation.
	 */
	static class JsxConverter implements IConverter, Serializable {
		private static final long serialVersionUID = -590148122175231052L;

		private transient Function transformFunction;
		private transient Scriptable scope;
		private transient Scriptable jsxTransformScript;

		protected JsxConverter() {
			initTransients();	// initialize the transient properties
		}

		/**
		 * The rhino properties are not serializable and so must be initialized every time
		 * this class is instantiated or de-serialized.
		 */
		protected void initTransients() {
			// Initialize the rhino properties used by the converter
			ArrayList<URI> modulePaths = new ArrayList<URI>(1);
			try {
				modulePaths.add(JsxResourceConverter.class.getClassLoader().getResource(JSXTRANSFORMER_DIRNAME).toURI());
			} catch (URISyntaxException e) {
				// rethrow as unchecked exception
				throw new RuntimeException(e);
			}
			Context ctx = Context.enter();
			try {
				// make a require builder and initialize scope
				RequireBuilder builder = new RequireBuilder();
				builder.setModuleScriptProvider(new SoftCachingModuleScriptProvider(
					new UrlModuleSourceProvider(modulePaths, null)
				));
				scope = ctx.initStandardObjects();
				Require require = builder.createRequire(ctx, scope);

				// require in the transformer and extract the transform function
				jsxTransformScript = require.requireMain(ctx, JSXTRANSFORMER_NAME);
				transformFunction = (Function) jsxTransformScript.get("transform", scope); //$NON-NLS-1$
			} finally {
				Context.exit();
			}
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.impl.cache.ResourceConverterCacheImpl.IConverter#generateCacheContent(com.ibm.jaggr.core.resource.IResource, java.io.File)
		 */
		@Override
		public void generateCacheContent(IResource source, File cacheFile) throws IOException {
			final String sourceMethod = "generateCacheContent"; //$NON-NLS-1$
			final boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(JsxConverter.class.getName(), sourceMethod, new Object[]{source, cacheFile});
			}
			Context ctx = Context.enter();
			try {
				// read the contents of the jsx file and convert it
				String jsx = IOUtils.toString(source.getInputStream());
				NativeObject convertedJSX;
				String jsstring;
				synchronized (this) {
					convertedJSX = (NativeObject) transformFunction.call(ctx, scope, jsxTransformScript, new String[]{jsx});
					// write the contents of the transformed javascript to the target file
					jsstring = convertedJSX.get("code").toString();  //$NON-NLS-1$
				}
				FileUtils.writeStringToFile(cacheFile, jsstring);
			} finally {
				Context.exit();
			}
			if (isTraceLogging) {
				log.exiting(JsxConverter.class.getName(), sourceMethod);
			}
		}

		/**
		 * Called when this object is de-serialized.
		 *
		 * @param stream the serialization input stream
		 * @throws InvalidObjectException
		 */
		private void readObject(ObjectInputStream stream) throws InvalidObjectException {
			/*
			 * We have no serializable properties, but we need to initialize the transient
			 * properties each time this object is de-serialized.
			 */
			initTransients();
		}
	}

}
