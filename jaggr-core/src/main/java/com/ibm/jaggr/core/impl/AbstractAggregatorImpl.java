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

package com.ibm.jaggr.core.impl;

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.DependencyVerificationException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.IExtensionInitializer.IExtensionRegistrar;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IRequestListener;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.PlatformServicesException;
import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.ServiceRegistration;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigListener;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.impl.cache.CacheManagerImpl;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.impl.deps.DependenciesImpl;
import com.ibm.jaggr.core.impl.layer.LayerImpl;
import com.ibm.jaggr.core.impl.module.ModuleImpl;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.layer.ILayerCache;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.SequenceNumberProvider;
import com.ibm.jaggr.core.util.StringUtil;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Dictionary;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Implementation for IAggregator and HttpServlet interfaces.
 *
 * Note that despite the fact that HttpServlet (which this class extends)
 * implements Serializable, attempts to serialize instances of this class will
 * fail due to the fact that not all instance data is serializable. The
 * assumption is that because instances of this class are created by the OSGi
 * Framework, and the framework itself does not support serialization, then no
 * attempts will be made to serialize instances of this class.
 */
@SuppressWarnings({ "serial", "deprecation" })
public abstract class AbstractAggregatorImpl extends HttpServlet implements IOptionsListener, IAggregator {
	/**
	 * Default value for resourcefactories init-param
	 */
	protected static final String DEFAULT_RESOURCEFACTORIES =
			"com.ibm.jaggr.service.default.resourcefactories"; //$NON-NLS-1$

	/**
	 * Default value for modulebuilders init-param
	 */
	protected static final String DEFAULT_MODULEBUILDERS =
			"com.ibm.jaggr.service.default.modulebuilders"; //$NON-NLS-1$

	/**
	 * Default value for httptransport init-param
	 */
	protected static final String DEFAULT_HTTPTRANSPORT =
			"com.ibm.jaggr.service.dojo.httptransport"; //$NON-NLS-1$

	private static final Logger log = Logger.getLogger(AbstractAggregatorImpl.class.getName());

	protected ICacheManager cacheMgr = null;
	protected IConfig config = null;

	//protected Bundle bundle = null;
	protected String name = null;
	protected IDependencies deps = null;
	protected List<ServiceRegistration> registrations = new LinkedList<ServiceRegistration>();
	protected List<Object> serviceReferences = Collections.synchronizedList(new LinkedList<Object>());
	protected InitParams initParams = null;
	protected IOptions localOptions = null;

	private LinkedList<IAggregatorExtension> resourceFactoryExtensions = new LinkedList<IAggregatorExtension>();
	private LinkedList<IAggregatorExtension> moduleBuilderExtensions = new LinkedList<IAggregatorExtension>();
	private IAggregatorExtension httpTransportExtension = null;
	protected IPlatformServices platformServices;

	enum RequestNotifierAction {
		start,
		end
	};

	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
	 */
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		super.init(servletConfig);

		final ServletContext context = servletConfig.getServletContext();

		// Set servlet context attributes for access though the request
		context.setAttribute(IAggregator.AGGREGATOR_REQATTRNAME, this);
	}

	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#destroy()
	 */
	@Override
	public void destroy() {
		shutdown();
		// Clear references to objects that can potentially reference this object
		// so as to avoid memory leaks due to circular references.
		resourceFactoryExtensions.clear();
		moduleBuilderExtensions.clear();
		httpTransportExtension = null;
		cacheMgr = null;
		config = null;
		deps = null;
		super.destroy();
	}


	/**
	 * Called when the aggregator is shutting down.  Note that there is inconsistency
	 * among servlet bridge implementations over when {@link HttpServlet#destroy()}
	 * is called relative to when (or even if) the bundle is stopped.  So this method
	 * may be called from the destroy method or the bundle listener or both.
	 */
	synchronized protected void shutdown() {
		if(getPlatformServices().isShuttingdown()){
			Object[] refs = null;
			try {
				refs = getPlatformServices().getServiceReferences(IShutdownListener.class.getName(), "(name=" + getName() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (PlatformServicesException e) {
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
			}
			if (refs != null) {
				for (Object ref : refs) {
					IShutdownListener listener = (IShutdownListener)getPlatformServices().getService(ref);
					if (listener != null) {
						try {
							listener.shutdown(this);
						} catch (Exception e) {
							if (log.isLoggable(Level.SEVERE)) {
								log.log(Level.SEVERE, e.getMessage(), e);
							}
						} finally {
							getPlatformServices().ungetService(ref);
						}
					}
				}
			}
			for (ServiceRegistration registration : registrations) {
				registration.unregister();
			}
			for (Object ref : serviceReferences) {
				getPlatformServices().ungetService(ref);
			}
		}
	}

	/* (non-Javadoc)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
		if (log.isLoggable(Level.FINEST))
			log.finest("doGet: URL=" + req.getRequestURI()); //$NON-NLS-1$

		req.setAttribute(AGGREGATOR_REQATTRNAME, this);
		ConcurrentMap<String, Object> concurrentMap = new ConcurrentHashMap<String, Object>();
		req.setAttribute(CONCURRENTMAP_REQATTRNAME, concurrentMap);

		try {
			// Validate config last-modified if development mode is enabled
			if (getOptions().isDevelopmentMode()) {
				long lastModified = -1;
				URI configUri = getConfig().getConfigUri();
				if (configUri != null) {
					try {
						// try to get platform URI from IResource in case uri specifies
						// aggregator specific scheme like namedbundleresource
						configUri = newResource(configUri).getURI();
					} catch (UnsupportedOperationException e) {
						// Not fatal.  Just use uri as specified.
					}
					lastModified = configUri.toURL().openConnection().getLastModified();
				}
				if (lastModified > getConfig().lastModified()) {
					if (reloadConfig()) {
						// If the config has been modified, then dependencies will be revalidated
						// asynchronously.  Rather than forcing the current request to wait, return
						// a response that will display an alert informing the user of what is
						// happening and asking them to reload the page.
						String content = "alert('" +  //$NON-NLS-1$
								StringUtil.escapeForJavaScript(Messages.ConfigModified) +
								"');"; //$NON-NLS-1$
						resp.addHeader("Cache-control", "no-store"); //$NON-NLS-1$ //$NON-NLS-2$
						CopyUtil.copy(new StringReader(content), resp.getOutputStream());
						return;
					}
				}
			}

			getTransport().decorateRequest(req);
			notifyRequestListeners(RequestNotifierAction.start, req, resp);

			ILayer layer = getLayer(req);
			long modifiedSince = req.getDateHeader("If-Modified-Since"); //$NON-NLS-1$
			long lastModified = (Math.max(getCacheManager().getCache().getCreated(), layer.getLastModified(req)) / 1000) * 1000;
			if (modifiedSince >= lastModified) {
				if (log.isLoggable(Level.FINER)) {
					log.finer("Returning Not Modified response for layer in servlet" +  //$NON-NLS-1$
							getName() + ":" + req.getAttribute(IHttpTransport.REQUESTEDMODULES_REQATTRNAME).toString()); //$NON-NLS-1$
				}
				resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			}
			else {
				// Get the InputStream for the response.  This call sets the Content-Type,
				// Content-Length and Content-Encoding headers in the response.
				InputStream in = layer.getInputStream(req, resp);
				// if any of the readers included an error response, then don't cache the layer.
				if (req.getAttribute(ILayer.NOCACHE_RESPONSE_REQATTRNAME) != null) {
					resp.addHeader("Cache-Control", "no-store"); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					resp.setDateHeader("Last-Modified", lastModified); //$NON-NLS-1$
					int expires = getConfig().getExpires();
					if (expires > 0) {
						resp.addHeader("Cache-Control", "public, max-age=" + expires); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
				CopyUtil.copy(in, resp.getOutputStream());
			}
			notifyRequestListeners(RequestNotifierAction.end, req, resp);
		} catch (DependencyVerificationException e) {
			// clear the cache now even though it will be cleared when validateDeps has
			// finished (asynchronously) so that any new requests will be forced to wait
			// until dependencies have been validated.
			getCacheManager().clearCache();
			getDependencies().validateDeps(false);

			resp.addHeader("Cache-control", "no-store"); //$NON-NLS-1$ //$NON-NLS-2$
			if (getOptions().isDevelopmentMode()) {
				String msg = StringUtil.escapeForJavaScript(
						MessageFormat.format(
								Messages.DepVerificationFailed,
								new Object[]{
										e.getMessage(),
										"aggregator " + //$NON-NLS-1$
												"validatedeps " + //$NON-NLS-1$
												getName() +
												" clean",  //$NON-NLS-1$
												getWorkingDirectory().toString().replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
								}
								)
						);
				String content = "alert('" + msg + "');"; //$NON-NLS-1$ //$NON-NLS-2$
				try {
					CopyUtil.copy(new StringReader(content), resp.getOutputStream());
				} catch (IOException e1) {
					if (log.isLoggable(Level.SEVERE)) {
						log.log(Level.SEVERE, e1.getMessage(), e1);
					}
					resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				}
			} else {
				resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			}
		} catch (ProcessingDependenciesException e) {
			resp.addHeader("Cache-control", "no-store"); //$NON-NLS-1$ //$NON-NLS-2$
			if (getOptions().isDevelopmentMode()) {
				String content = "alert('" + StringUtil.escapeForJavaScript(Messages.Busy) + "');"; //$NON-NLS-1$ //$NON-NLS-2$
				try {
					CopyUtil.copy(new StringReader(content), resp.getOutputStream());
				} catch (IOException e1) {
					if (log.isLoggable(Level.SEVERE)) {
						log.log(Level.SEVERE, e1.getMessage(), e1);
					}
					resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				}
			} else {
				resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			}
		} catch (BadRequestException e) {
			exceptionResponse(req, resp, e, HttpServletResponse.SC_BAD_REQUEST);
		} catch (NotFoundException e) {
			exceptionResponse(req, resp, e, HttpServletResponse.SC_NOT_FOUND);
		} catch (Exception e) {
			exceptionResponse(req, resp, e, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		} finally {
			concurrentMap.clear();
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#reloadConfig()
	 */
	@Override
	public boolean reloadConfig() throws IOException {
		return loadConfig(SequenceNumberProvider.incrementAndGetSequenceNumber());
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getDependencies()
	 */
	@Override
	public IDependencies getDependencies() {
		return deps;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getName()
	 */
	@Override
	public String getName() {
		return name;
	}



	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getConfig()
	 */
	@Override
	public IConfig getConfig() {
		return config;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getCacheManager()
	 */
	@Override
	public ICacheManager getCacheManager() {
		return cacheMgr;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IAggregator#getInitParams()
	 */
	@Override
	public InitParams getInitParams() {
		return initParams;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#asServlet()
	 */
	@Override
	public HttpServlet asServlet() {
		return this;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getTransport()
	 */
	@Override
	public IHttpTransport getTransport() {
		return (IHttpTransport)getHttpTransportExtension().getInstance();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.resource.IResourceProvider#getResource(java.net.URI)
	 */
	@Override
	public IResource newResource(URI uri) {
		IResourceFactory factory = null;
		String scheme = uri.getScheme();

		for (IAggregatorExtension extension : getResourceFactoryExtensions()) {
			if (scheme.equals(extension.getAttribute(IResourceFactoryExtensionPoint.SCHEME_ATTRIBUTE))) {
				IResourceFactory test = (IResourceFactory)extension.getInstance();
				if (test.handles(uri)) {
					factory = test;
					break;
				}
			}
		}
		if (factory == null) {
			throw new UnsupportedOperationException(
					"No resource factory for " + uri.toString() //$NON-NLS-1$
					);
		}

		return factory.newResource(uri);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.IModuleBuilderProvider#getModuleBuilder(java.lang.String, com.ibm.jaggr.service.resource.IResource)
	 */
	@Override
	public IModuleBuilder getModuleBuilder(String mid, IResource res) {
		IModuleBuilder builder = null;

		String path = res.getURI().getPath();
		int idx = path.lastIndexOf("."); //$NON-NLS-1$
		String ext = (idx == -1) ? "" : path.substring(idx+1); //$NON-NLS-1$
		if (ext.contains("/")) { //$NON-NLS-1$
			ext = ""; //$NON-NLS-1$
		}

		for (IAggregatorExtension extension : getModuleBuilderExtensions()) {
			String extAttrib = extension.getAttribute(IModuleBuilderExtensionPoint.EXTENSION_ATTRIBUTE);
			if (ext.equals(extAttrib) || "*".equals(extAttrib)) { //$NON-NLS-1$
				IModuleBuilder test = (IModuleBuilder)extension.getInstance();
				if (test.handles(mid, res)) {
					builder = test;
					break;
				}
			}
		}
		if (builder == null) {
			throw new UnsupportedOperationException(
					"No module builder for " + mid //$NON-NLS-1$
					);
		}
		return builder;

	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getResourceFactoryExtensions()
	 */
	@Override
	public Iterable<IAggregatorExtension> getResourceFactoryExtensions() {
		return Collections.unmodifiableList(resourceFactoryExtensions);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getModuleBuilderExtensions()
	 */
	@Override
	public Iterable<IAggregatorExtension> getModuleBuilderExtensions() {
		return Collections.unmodifiableList(moduleBuilderExtensions);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getHttpTransportExtension()
	 */
	@Override
	public IAggregatorExtension getHttpTransportExtension() {
		return httpTransportExtension;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.IModuleFactory#newModule(java.lang.String, java.net.URI)
	 */
	@Override
	public IModule newModule(String mid, URI uri) {
		return new ModuleImpl(mid, uri);
	}



	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#newLayerCache()
	 */
	@Override
	public ILayerCache newLayerCache() {
		return LayerImpl.newLayerCache(this);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#newModuleCache()
	 */
	@Override
	public IModuleCache newModuleCache() {
		return ModuleImpl.newModuleCache(this);
	}

	/**
	 * Options update listener forwarder.  This listener is registered under the option's
	 * name (the name of the servlet's bundle), and forwards listener events to listeners
	 * that are registered under the aggregator name.
	 *
	 * @param options The options object
	 * @param sequence The event sequence number
	 */
	@Override
	public void optionsUpdated(IOptions options, long sequence) {
		// Options have been updated.  Notify any listeners that registered using this
		// aggregator instance's name.
		Object[] refs = null;
		try {
			refs = getPlatformServices().getServiceReferences(IOptionsListener.class.getName(), "(name=" + getName() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (PlatformServicesException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}

		if (refs != null) {
			for (Object ref : refs) {
				IOptionsListener listener = (IOptionsListener)getPlatformServices().getService(ref);
				if (listener != null) {
					try {
						listener.optionsUpdated(options, sequence);
					} catch (Throwable ignore) {
					} finally {
						getPlatformServices().ungetService(ref);
					}
				}
			}
		}
	}




	/**
	 * Sets response status and headers for an error response based on the
	 * information in the specified exception.  If development mode is
	 * enabled, then returns a 200 status with a console.error() message
	 * specifying the exception message
	 *
	 * @param resp The response object
	 * @param t The exception object
	 * @param status The response status
	 */
	protected void exceptionResponse(HttpServletRequest req, HttpServletResponse resp, Throwable t, int status) {
		resp.addHeader("Cache-control", "no-store"); //$NON-NLS-1$ //$NON-NLS-2$
		Level logLevel = (t instanceof BadRequestException || t instanceof NotFoundException)
				? Level.WARNING : Level.SEVERE;
		if (log.isLoggable(logLevel)) {
			String queryArgs = req.getQueryString();
			StringBuffer url = req.getRequestURL();
			if (queryArgs != null) {
				url.append("?").append(queryArgs); //$NON-NLS-1$
			}
			log.log(logLevel, url.toString(), t);
		}
		if (getOptions().isDevelopmentMode() || getOptions().isDebugMode()) {
			// In development mode, display server exceptions on the browser console
			String msg = StringUtil.escapeForJavaScript(
					MessageFormat.format(
							Messages.ExceptionResponse,
							new Object[]{
									t.getClass().getName(),
									t.getMessage() != null ? StringUtil.escapeForJavaScript(t.getMessage()) : "" //$NON-NLS-1$
							}
							)
					);
			String content = "console.error('" + msg + "');";  //$NON-NLS-1$ //$NON-NLS-2$
			try {
				CopyUtil.copy(new StringReader(content), resp.getOutputStream());
			} catch (IOException e1) {
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e1.getMessage(), e1);
				}
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		} else {
			resp.setStatus(status);
		}
	}

	/**
	 * Returns the {@code Layer} object for the specified request.
	 *
	 * @param request The request object
	 * @return The layer for the request
	 * @throws Exception
	 */
	protected ILayer getLayer(HttpServletRequest request) throws Exception {

		// Try non-blocking get() request first
		return getCacheManager().getCache().getLayers().getLayer(request);
	}

	/* (non-Javadoc)
	 * @see com.ibm.servlets.amd.aggregator.IAggregator#substituteProps(java.lang.String)
	 */
	@Override
	public String substituteProps(String str) {
		return substituteProps(str, null);
	}

	protected final Pattern pattern = Pattern.compile("\\$\\{([^}]*)\\}"); //$NON-NLS-1$

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IAggregator#substituteProps(java.lang.String, com.ibm.jaggr.core.IAggregator.SubstitutionTransformer)
	 */
	@Override
	public String substituteProps(String str, SubstitutionTransformer transformer) {
		if (str == null) {
			return null;
		}
		StringBuffer buf = new StringBuffer();
		Matcher matcher = pattern.matcher(str);
		while ( matcher.find() ) {
			String propName = matcher.group(1);
			String propValue = getPropValue(propName);
			if (propValue != null) {
				if (transformer != null) {
					propValue = transformer.transform(propName, propValue);
				}
				matcher.appendReplacement(
						buf,
						propValue
						.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
						.replace("$", "\\$")  //$NON-NLS-1$ //$NON-NLS-2$
						);
			} else {
				matcher.appendReplacement(buf, "\\${"+propName+"}"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		matcher.appendTail(buf);
		return buf.toString();
	}

	/**
	 * Returns the value for the property name used by the aggregator. By default,
	 * it returns the system property indicated by the specified key. This method may be overriden by the platform
	 * dependent implementation of the aggregator.
	 * @param propName
	 * @return Value of the property
	 */
	public String getPropValue (String propName){
		return System.getProperty(propName);
	}


	/**
	 * Loads the {@code IConfig} for this aggregator
	 *
	 * @param seq The config change sequence number
	 * @return True if the new config is changed from the old config
	 * @throws IOException
	 */
	protected boolean loadConfig(long seq) throws IOException {
		boolean modified = false;
		try {
			Object previousConfig = config != null ? config.toString() : null;
			config = newConfig();
			modified = previousConfig != null && !previousConfig.equals(config.toString());
		} catch (IOException e) {
			throw new IOException(e);
		}
		notifyConfigListeners(seq);
		return modified;
	}

	/**
	 * Calls the registered request notifier listeners.
	 *
	 * @param action The request action (start or end)
	 * @param req The request object.
	 * @param resp The response object.
	 * @throws IOException
	 */
	protected void notifyRequestListeners(RequestNotifierAction action, HttpServletRequest req, HttpServletResponse resp) throws IOException {
		// notify any listeners that the config has been updated
		Object[] refs = null;
		try {
			refs = getPlatformServices().getServiceReferences(IRequestListener.class.getName(),  "(name="+getName()+")"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (PlatformServicesException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		if (refs != null) {
			for (Object ref : refs) {
				IRequestListener listener = (IRequestListener)getPlatformServices().getService(ref);
				try {
					if (action == RequestNotifierAction.start) {
						listener.startRequest(req, resp);
					} else {
						listener.endRequest(req, resp);
					}
				} finally {
					getPlatformServices().ungetService(ref);
				}
			}
		}
	}

	/**
	 * Call the registered config change listeners
	 *
	 * @param seq The change listener sequence number
	 * @throws IOException
	 */
	protected void notifyConfigListeners(long seq) throws IOException {
		Object[] refs = null;
		try {
			refs = getPlatformServices().getServiceReferences(IConfigListener.class.getName(),  "(name="+getName()+")"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (PlatformServicesException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}

		if (refs != null) {
			for (Object ref : refs) {
				IConfigListener listener =
						(IConfigListener)getPlatformServices().getService(ref);
				if (listener != null) {
					try {
						listener.configLoaded(config, seq);
					} catch (Throwable t) {
						if (log.isLoggable(Level.SEVERE)) {
							log.log(Level.SEVERE, t.getMessage(), t);
						}
						throw new IOException(t);
					} finally {
						getPlatformServices().ungetService(ref);
					}
				}
			}
		}
	}

	/**
	 * Adds the specified resource factory extension to the list of registered
	 * resource factory extensions.
	 *
	 * @param loadedExt
	 *            The extension to add
	 * @param before
	 *            Reference to an existing resource factory extension that the
	 *            new extension should be placed before in the list. If null,
	 *            then the new extension is added to the end of the list
	 */
	protected void registerResourceFactory(IAggregatorExtension loadedExt, IAggregatorExtension before) {
		if (!(loadedExt.getInstance() instanceof IResourceFactory)) {
			throw new IllegalArgumentException(loadedExt.getInstance().getClass().getName());
		}
		if (before == null) {
			resourceFactoryExtensions.add(loadedExt);
		} else {
			// find the extension to insert the item in front of
			boolean inserted = false;
			for (int i = 0; i < resourceFactoryExtensions.size(); i++) {
				if (resourceFactoryExtensions.get(i) == before) {
					resourceFactoryExtensions.add(i, loadedExt);
					inserted = true;
					break;
				}
			}
			if (!inserted) {
				throw new IllegalArgumentException();
			}
		}
	}

	/**
	 * Adds the specified module builder extension to the list of registered
	 * module builder extensions.
	 *
	 * @param loadedExt
	 *            The extension to add
	 * @param before
	 *            Reference to an existing module builder extension that the
	 *            new extension should be placed before in the list. If null,
	 *            then the new extension is added to the end of the list
	 */
	protected void registerModuleBuilder(IAggregatorExtension loadedExt, IAggregatorExtension before) {
		if (!(loadedExt.getInstance() instanceof IModuleBuilder)) {
			throw new IllegalArgumentException(loadedExt.getInstance().getClass().getName());
		}
		if (before == null) {
			moduleBuilderExtensions.add(loadedExt);
		} else {
			// find the extension to insert the item in front of
			boolean inserted = false;
			for (int i = 0; i < moduleBuilderExtensions.size(); i++) {
				if (moduleBuilderExtensions.get(i) == before) {
					moduleBuilderExtensions.add(i, loadedExt);
					inserted = true;
					break;
				}
			}
			if (!inserted) {
				throw new IllegalArgumentException();
			}
		}
	}

	/**
	 * Registers the specified http transport extension as the
	 * http transport for this aggregator.
	 *
	 * @param loadedExt The extension to register
	 */
	protected void registerHttpTransport(IAggregatorExtension loadedExt) {
		if (!(loadedExt.getInstance() instanceof IHttpTransport)) {
			throw new IllegalArgumentException(loadedExt.getInstance().getClass().getName());
		}
		httpTransportExtension = loadedExt;
	}


	/**
	 * For each extension specified, call the extension's
	 * {@link IExtensionInitializer#initialize} method.  Note that this
	 * can cause additional extensions to be registered though the
	 * {@link ExtensionRegistrar}.
	 *
	 * @param extensions The list of extensions to initialize
	 * @param reg The extension registrar.
	 */
	protected void callExtensionInitializers(Iterable<IAggregatorExtension> extensions, ExtensionRegistrar reg) {
		for (IAggregatorExtension extension : extensions) {
			Object instance = extension.getInstance();
			if (instance instanceof IExtensionInitializer) {
				((IExtensionInitializer)instance).initialize(this, extension, reg);
			}
		}
	}

	// Instances of this class are NOT serializable
	private void writeObject(ObjectOutputStream out) throws IOException {
		throw new NotSerializableException();
	}

	// Instances of this class are NOT serializable
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		throw new NotSerializableException();
	}

	/**
	 * Returns the working directory for this aggregator.
	 * <p>
	 * This method is called during aggregator intialization.  Subclasses may
	 * override this method to initialize the aggregator using a different
	 * working directory.  Use the public {@link #getWorkingDirectory()} method
	 * to get the working directory from an initialized aggregator.
	 *
	 * @param configElem
	 *            The configuration element. Not used by this class but provided
	 *            for use by subclasses.
	 * @return The {@code File} object for the working directory
	 * @throws FileNotFoundException
	 */
	protected File initWorkingDirectory(File defaultLocation, Map<String, String> configMap, String versionString) throws FileNotFoundException {
		String dirName = getOptions().getCacheDirectory();
		File dirFile = null;
		if (dirName == null) {
			dirFile = defaultLocation;
		} else {
			// Make sure the path exists
			dirFile = new File(dirName);
			dirFile.mkdirs();
		}
		if (!dirFile.exists()) {
			throw new FileNotFoundException(dirFile.toString());
		}
		// Create a directory using the alias name within the contributing bundle's working
		// directory
		File workDir = new File(dirFile, getName());
		// Create a bundle-version specific subdirectory.  If the directory doesn't exist, assume
		// the bundle has been updated and clean out the workDir to remove all stale cache files.
		File servletDir = versionString != null ? new File(workDir, versionString) : workDir;

		if (!servletDir.exists()) {
			FileUtils.deleteQuietly(workDir);
		}
		servletDir.mkdirs();
		if (!servletDir.exists()) {
			throw new FileNotFoundException(servletDir.getAbsolutePath());
		}
		return servletDir;
	}

	/**
	 * Returns the name for this aggregator
	 * <p>
	 * This method is called during aggregator intialization.  Subclasses may
	 * override this method to initialize the aggregator using a different
	 * name.  Use the public {@link AggregatorImpl#getName()} method
	 * to get the name of an initialized aggregator.
	 *
	 * @param configMap
	 *            A Map having key-value pairs denoting configuration settings for the aggregator servlet
	 * @return The aggregator name
	 */
	protected String getAggregatorName(Map<String, String> configMap) {
		// trim leading and trailing '/'
		String alias = (String)configMap.get("alias"); //$NON-NLS-1$
		while (alias.charAt(0) == '/')
			alias = alias.substring(1);
		while (alias.charAt(alias.length()-1) == '/')
			alias = alias.substring(0, alias.length()-1);
		return alias;
	}

	/**
	 * Returns the init params for this aggregator
	 * <p>
	 * This method is called during aggregator intialization.  Subclasses may
	 * override this method to initialize the aggregator using different
	 * init params.  Use the public {@link AggregatorImpl#getInitParams()} method
	 * to get the init params for an initialized aggregator.
	 *
	 * @param configMap
	 *            A Map having key-value pairs denoting configuration init-params for the aggregator servlet
	 * @return The init params
	 */
	protected InitParams getInitParams(Map<String, String> configMap) {
		List<InitParam> initParams = new LinkedList<InitParam>();
		for (Entry<String, String> child : configMap.entrySet()) {
			String name = (String)child.getKey(); //$NON-NLS-1$
			String value = (String)child.getValue(); //$NON-NLS-1$
			initParams.add(new InitParam(name, value));
		}
		return new InitParams(initParams);
	}

	/**
	 * Instantiates a new dependencies object
	 *
	 * @return The new dependencies
	 */
	protected IDependencies newDependencies(long stamp) {
		return new DependenciesImpl(this, stamp);
	}

	/**
	 * Instantiates a new config object
	 *
	 * @return The new config
	 * @throws IOException
	 */
	protected IConfig newConfig() throws IOException {
		return new ConfigImpl(this);
	}

	/**
	 * Instantiates a new cache manager
	 *
	 * @return The new cache manager
	 */
	protected ICacheManager newCacheManager(long stamp) throws IOException {
		return new CacheManagerImpl(this, stamp);
	}

	@Override
	public abstract IOptions getOptions();

	@Override
	public abstract IExecutors getExecutors();


	@Override
	public IPlatformServices getPlatformServices() {
		return platformServices;
	}

	/**
	 * This method does some initialization for the aggregator servlet. This method is called from platform
	 * dependent Aggregator implementation during its initialization.
	 *
	 *
	 * @param configMap
	 *            A Map having key-value pairs denoting configuration settings for the aggregator servlet
	 * @param configInitParams
	 *            A Map having key-value pairs denoting servlet init parameters for the aggregator servlet
	 */

	public void initialize(Map<String, String> configMap, Map<String, String> configInitParams)
			throws Exception {

		// create the config. Keep it local so it won't be seen by deps and cacheMgr
		// until after we check for customization last-mods. Then we'll set the config
		// in the instance data and call the config listeners.
		IConfig config = newConfig();

		// Check last-modified times of resources in the overrides folders. These resources
		// are considered to be dynamic in a production environment and we want to
		// detect new/changed resources in these folders on startup so that we can clear
		// caches, etc.
		OverrideFoldersTreeWalker walker = new OverrideFoldersTreeWalker(this, config);
		walker.walkTree();
		deps = newDependencies(walker.getLastModifiedJS());
		cacheMgr = newCacheManager(walker.getLastModified());
		this.config = config;

	}

	/**
	 * Implements the {@link IExtensionRegistrar} interface
	 */
	public class ExtensionRegistrar implements IExtensionRegistrar {
		 boolean open = true;

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.IExtensionInitializer.IExtensionRegistrar#registerExtension(java.lang.Object, java.util.Properties, java.lang.String, java.lang.String)
		 */
		@Override
		public void registerExtension(
				Object impl,
				Properties attributes,
				String extensionPointId,
				String uniqueId,
				IAggregatorExtension before) {
			if (!open) {
				throw new IllegalStateException("ExtensionRegistrar is closed"); //$NON-NLS-1$
			}
			IAggregatorExtension extension;
			if (impl instanceof IResourceFactory) {
				extension = new AggregatorExtension(
						impl,
						new Properties(attributes),
						extensionPointId,
						uniqueId
						);
				registerResourceFactory(
						extension,
						before
						);
			} else if (impl instanceof IModuleBuilder) {
				extension = new AggregatorExtension(
						impl,
						attributes,
						extensionPointId,
						uniqueId
						);
				registerModuleBuilder(
						extension,
						before
						);
			} else {
				throw new UnsupportedOperationException(impl.getClass().getName());
			}
			if (impl instanceof IExtensionInitializer) {
				((IExtensionInitializer)impl).initialize(AbstractAggregatorImpl.this, extension, this);
			}
		}

		public void closeRegistration() {
			open = false;
		}
	}

	/**
	 * Registers the layer listener
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void registerLayerListener() {
		Dictionary dict = new Properties();
		dict.put("name", getName()); //$NON-NLS-1$
		registrations.add(getPlatformServices().registerService(
				ILayerListener.class.getName(), new AggregatorLayerListener(this), dict));
	}
}

