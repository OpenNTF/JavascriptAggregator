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

package com.ibm.jaggr.core.impl.transport;

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfigModifier;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.IDependenciesListener;
import com.ibm.jaggr.core.impl.resource.ExceptionResource;
import com.ibm.jaggr.core.readers.AggregationReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;
import com.ibm.jaggr.core.resource.StringResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransportExtensionPoint;
import com.ibm.jaggr.core.util.AggregatorUtil;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.TypeUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

/**
 * Implements common functionality useful for all Http Transport implementation
 * and defines abstract methods that subclasses need to implement
 */
public abstract class AbstractHttpTransport implements IHttpTransport, IConfigModifier, IShutdownListener, IDependenciesListener {
	private static final Logger log = Logger.getLogger(AbstractHttpTransport.class.getName());

	public static final String PATHS_PROPNAME = "paths"; //$NON-NLS-1$

	public static final String REQUESTEDMODULES_REQPARAM = "modules"; //$NON-NLS-1$
	public static final String REQUESTEDMODULEIDS_REQPARAM = "moduleIds"; //$NON-NLS-1$
	public static final String REQUESTEDMODULESCOUNT_REQPARAM = "count"; //$NON-NLS-1$

	public static final String CONSOLE_WARNING_MSG_FMT = "window.console && console.warn(\"{0}\");"; //$NON-NLS-1$
	static final Pattern hasPluginPattern = Pattern.compile("(^|\\/)has!(.*)$"); //$NON-NLS-1$


	/**
	 * Request param specifying the non-AMD script files to include in an application boot layer.
	 * This typically includes the applications AMD loader config file, the aggregator loader
	 * extension config and the AMD loader itself, as well as any other non-AMD script files
	 * that the application wishes to include in the boot layer.
	 */
	public static final String SCRIPTS_REQPARAM = "scripts"; //$NON-NLS-1$

	/**
	 * Request param specifying the AMD modules to include in the boot layer.  These modules will
	 * be 'required' by the AMD loader (i.e. a synthetic require call will be executed specifying
	 * these modules after the loader has been initialized).
	 */
	public static final String DEPS_REQPARAM = "deps"; //$NON-NLS-1$

	/**
	 * @deprecated
	 * Same as {@link #DEPS_REQPARAM}
	 */
	public static final String REQUIRED_REQPARAM = "required"; //$NON-NLS-1$

	/**
	 * Request param specifying the AMD modules to preload in the boot layer.  These modules
	 * will be included in the boot layer, but will not be initialized (i.e. their define
	 * function callbacks will not be invoked) unless they are direct or nested dependencies
	 * of modules specified by the 'required' request param.  Instead, they will reside in the
	 * loader's module cache, inactive, until needed by the loader to resolve a module
	 * dependency.
	 */
	public static final String PRELOADS_REQPARAM = "preloads"; //$NON-NLS-1$

	public static final String FEATUREMAP_REQPARAM = "has"; //$NON-NLS-1$
	public static final String FEATUREMAPHASH_REQPARAM = "hashash"; //$NON-NLS-1$

	public static final String[] OPTIMIZATIONLEVEL_REQPARAMS = {"optimize", "opt"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] EXPANDREQUIRELISTS_REQPARAMS = {"expandRequire", "re" //$NON-NLS-1$ //$NON-NLS-2$
		,"reqexp"	// backwards compatibility, to be removed. //$NON-NLS-1$
	};

	public static final String[] SHOWFILENAMES_REQPARAMS = {"showFilenames", "fn"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] NOCACHE_REQPARAMS = {"noCache", "nc"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] REQUESTEDLOCALES_REQPARAMS = {"locales", "locs"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] HASPLUGINBRANCHING_REQPARAMS = {"hasBranching", "hb"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String CONFIGVARNAME_REQPARAM = "configVarName"; //$NON-NLS-1$

	public static final String LAYERCONTRIBUTIONSTATE_REQATTRNAME = AbstractHttpTransport.class.getName() + ".LayerContributionState"; //$NON-NLS-1$
	public static final String ENCODED_FEATURE_MAP_REQPARAM = "hasEnc"; //$NON-NLS-1$

	static final String WARN_DEPRECATED_USE_OF_MODULES_QUERYARG = AbstractHttpTransport.class.getName() + ".DEPRECATED_USE_OF_MODULES"; //$NON-NLS-1$
	static final String WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG = AbstractHttpTransport.class.getName() + ".DEPRECATED_USE_OF_REQUIRED"; //$NON-NLS-1$

	static final int REQUESTED_MODULES_MAX_COUNT = 10000;

	protected static String FEATUREMAP_JS_NAME = "featureList.js"; //$NON-NLS-1$
	public static final String FEATURE_LIST_PRELUDE = "define([], "; //$NON-NLS-1$
	public static final String FEATURE_LIST_PROLOGUE = ");"; //$NON-NLS-1$

	private List<IServiceRegistration> serviceRegistrations = new ArrayList<IServiceRegistration>();
	private IAggregator aggregator = null;
	private List<String> extensionContributions = new LinkedList<String>();

	private List<String> dependentFeatures = null;
	private IResource depFeatureListResource = null;

	private CountDownLatch depsInitialized = null;

	private Map<String, Integer> moduleIdMap = null;
	private List<String> moduleIdList = null;
	private byte[] moduleIdListHash = null;

	private String resourcePathId = null;
	private String transportId = null;
	private URI comboUri = null;


	/** default constructor */
	public AbstractHttpTransport() {}

	/** For unit tests
	 * @param depsInitialized
	 * @param dependentFeatures
	 * @param depsLastMod */
	AbstractHttpTransport(CountDownLatch depsInitialized, List<String> dependentFeatures, long depsLastMod) {
		this.depsInitialized = depsInitialized;
		this.dependentFeatures = dependentFeatures;
		this.depFeatureListResource = createFeatureListResource(dependentFeatures, getFeatureListResourceUri(), depsLastMod);
	}

	/**
	 * Returns the URI to the folder containing the javascript resources
	 * for this transport.
	 *
	 * @return the combo resource URI
	 */
	protected URI getComboUri() {
		return comboUri;
	}

	/**
	 * Returns the name of the aggregator text plugin module name (e.g. combo/text)
	 *
	 * @return the name of the plugin
	 */
	protected abstract String getAggregatorTextPluginName();

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.IHttpTransport#decorateRequest(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.service.IAggregator)
	 */
	@Override
	public void decorateRequest(HttpServletRequest request) throws IOException {

		// Get module lists from request
		setRequestedModuleNames(request);

		// Get the feature list, if any
		request.setAttribute(FEATUREMAP_REQATTRNAME, getFeaturesFromRequest(request));

		request.setAttribute(OPTIMIZATIONLEVEL_REQATTRNAME, getOptimizationLevelFromRequest(request));

		String value = getParameter(request, EXPANDREQUIRELISTS_REQPARAMS);
		if ("log".equals(value)) { //$NON-NLS-1$
			request.setAttribute(EXPANDREQLOGGING_REQATTRNAME, Boolean.TRUE);
			request.setAttribute(EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		} else {
			request.setAttribute(EXPANDREQUIRELISTS_REQATTRNAME, TypeUtil.asBoolean(value));
		}
		request.setAttribute(EXPORTMODULENAMES_REQATTRNAME, true);

		request.setAttribute(SHOWFILENAMES_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, SHOWFILENAMES_REQPARAMS)));

		request.setAttribute(NOCACHE_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, NOCACHE_REQPARAMS)));

		request.setAttribute(HASPLUGINBRANCHING_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, HASPLUGINBRANCHING_REQPARAMS), true));

		request.setAttribute(REQUESTEDLOCALES_REQATTRNAME, getRequestedLocales(request));

		if (request.getParameter(CONFIGVARNAME_REQPARAM) != null) {
			request.setAttribute(CONFIGVARNAME_REQATTRNAME, request.getParameter(CONFIGVARNAME_REQPARAM));
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IHttpTransport#getModuleIdMap()
	 */
	@Override
	public Map<String, Integer> getModuleIdMap() {
		return moduleIdMap;
	}

	/**
	 * Returns the module id list used to map module name ids to module names.  The
	 * id corresponds to the postion of the name in the list.  The list element at
	 * offset 0 is unused.
	 *
	 * @return the module id list
	 */
	protected List<String> getModuleIdList() {
		return moduleIdList;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IHttpTransport#getModuleIdRegFunctionName()
	 */
	@Override
	public String getModuleIdRegFunctionName() {
		// Loader specific subclass provides implementation.  If not overridden, then
		// module name id encoding is effectively disabled.
		return null;
	}

	protected void setRequestedModuleNames(HttpServletRequest request) throws IOException {
		final String sourceMethod = "setRequestedModuleNames"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{request.getQueryString()});
		}

		RequestedModuleNames requestedModuleNames = null;
		if (moduleIdList == null){
			requestedModuleNames = new RequestedModuleNames(request, null, null);
		}else{
			requestedModuleNames = new RequestedModuleNames(request, moduleIdList, Arrays.copyOf(moduleIdListHash, moduleIdListHash.length));
		}
		request.setAttribute(REQUESTEDMODULENAMES_REQATTRNAME, requestedModuleNames);
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod);
		}
	}

	/**
	 * Returns the requested locales as a collection of locale strings
	 *
	 * @param request the request object
	 * @return the locale strings
	 */
	protected Collection<String> getRequestedLocales(HttpServletRequest request) {
		final String sourceMethod = "getRequestedLocales"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{request.getQueryString()});
		}
		String[] locales;
		String sLocales = getParameter(request, REQUESTEDLOCALES_REQPARAMS);
		if (sLocales != null) {
			locales = sLocales.split(","); //$NON-NLS-1$
		} else {
			locales = new String[0];
		}
		Collection<String> result = Collections.unmodifiableCollection(Arrays.asList(locales));
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod, result);
		}
		return result;
	}



	/**
	 * Returns a map containing the has-condition/value pairs specified in the request
	 *
	 * @param request
	 *            The http request object
	 * @return The map containing the has-condition/value pairs.
	 * @throws IOException
	 */
	protected Features getFeaturesFromRequest(HttpServletRequest request) throws IOException {
		final String sourceMethod = "getFeaturesFromRequest"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{request});
		}
		Features features = getFeaturesFromRequestEncoded(request);
		if (features == null) {
			features = new Features();
			String has  = getHasConditionsFromRequest(request);
			if (has != null) {
				for (String s : has.split("[;*]")) { //$NON-NLS-1$
					boolean value = true;
					if (s.startsWith("!")) { //$NON-NLS-1$
						s = s.substring(1);
						value = false;
					}
					features.put(s, value);
				}
			}
		}
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod, features);
		}
		return features.unmodifiableFeatures();
	}

	/**
	 * This method checks the request for the has conditions which may either be contained in URL
	 * query arguments or in a cookie sent from the client.
	 *
	 * @param request
	 *            the request object
	 * @return The has conditions from the request.
	 * @throws IOException
	 * @throws UnsupportedEncodingException
	 */
	protected String getHasConditionsFromRequest(HttpServletRequest request) throws IOException {

		final String sourceMethod = "getHasConditionsFromRequest"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{request});
		}
		String ret = null;
		if (request.getParameter(FEATUREMAPHASH_REQPARAM) != null) {
			// The cookie called 'has' contains the has conditions
			if (isTraceLogging) {
				log.finer("has hash = " + request.getParameter(FEATUREMAPHASH_REQPARAM)); //$NON-NLS-1$
			}
			Cookie[] cookies = request.getCookies();
			if (cookies != null) {
				for (int i = 0; ret == null && i < cookies.length; i++) {
					Cookie cookie = cookies[i];
					if (cookie.getName().equals(FEATUREMAP_REQPARAM) && cookie.getValue() != null) {
						if (isTraceLogging) {
							log.finer("has cookie = " + cookie.getValue()); //$NON-NLS-1$
						}
						ret = URLDecoder.decode(cookie.getValue(), "US-ASCII"); //$NON-NLS-1$
						break;
					}
				}
			}
			if (ret == null) {
				if (log.isLoggable(Level.WARNING)) {
					StringBuffer url = request.getRequestURL();
					if (url != null) {	// might be null if using mock request for unit testing
						url.append("?").append(request.getQueryString()).toString(); //$NON-NLS-1$
						log.warning(MessageFormat.format(
								Messages.AbstractHttpTransport_0,
								new Object[]{url, request.getHeader("User-Agent")})); //$NON-NLS-1$
					}
				}
			}
		}
		else {
			ret = request.getParameter(FEATUREMAP_REQPARAM);
			if (isTraceLogging) {
				log.finer("reading features from has query arg"); //$NON-NLS-1$
			}
		}
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod, ret);
		}
		return ret;
	}

	/**
	 * Returns the value of the requested parameter from the request, or null
	 *
	 * @param request
	 *            the request object
	 * @param aliases
	 *            array of query arg names by which the request may be specified
	 * @return the value of the param, or null if it is not specified under the
	 *         specified names
	 */
	protected static String getParameter(HttpServletRequest request, String[] aliases) {
		final String sourceMethod = "getParameter"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{request.getQueryString(), Arrays.asList(aliases)});
		}
		Map<String, String[]> params = request.getParameterMap();
		String result = null;
		for (Map.Entry<String, String[]> entry : params.entrySet()) {
			String name = entry.getKey();
			for (String alias : aliases) {
				if (alias.equalsIgnoreCase(name)) {
					String[] values = entry.getValue();
					result = values[values.length-1];	// return last value in array
				}
			}
		}
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod, result);
		}
		return result;
	}

	/**
	 * Returns the requested optimization level from the request.
	 *
	 * @param request the request object
	 * @return the optimization level specified in the request
	 */
	protected OptimizationLevel getOptimizationLevelFromRequest(HttpServletRequest request) {
		final String sourceMethod = "getOptimizationLevelFromRequest"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{request.getQueryString()});
		}
		// Get the optimization level specified in the request and set the ComilationLevel
		String optimize = getParameter(request, OPTIMIZATIONLEVEL_REQPARAMS);
		OptimizationLevel level = OptimizationLevel.SIMPLE;
		if (optimize != null && !optimize.equals("")) { //$NON-NLS-1$
			if (optimize.equalsIgnoreCase("whitespace")) //$NON-NLS-1$
				level = OptimizationLevel.WHITESPACE;
			else if (optimize.equalsIgnoreCase("advanced")) //$NON-NLS-1$
				level = OptimizationLevel.ADVANCED;
			else if (optimize.equalsIgnoreCase("none")) //$NON-NLS-1$
				level = OptimizationLevel.NONE;
		}
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod, level);
		}
		return level;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfigModifier#modifyConfig(com.ibm.jaggr.service.IAggregator, java.util.Map)
	 */
	@Override
	public void modifyConfig(IAggregator aggregator, Object configObj) {
		final String sourceMethod = "modifyConfig"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		// The server-side AMD config has been updated.  Add an entry to the
		// {@code paths} property to map the resource (combo) path to the
		// location of the combo resources on the server.
		Context context = Context.enter();
		Scriptable config = (Scriptable)configObj;
		try {
			if (isTraceLogging) {
				log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{aggregator, Context.toString(config)});
			}
			Object pathsObj = config.get(PATHS_PROPNAME, config);
			if (pathsObj == Scriptable.NOT_FOUND) {
				// If not present, add it.
				config.put("paths", config, context.newObject(config)); //$NON-NLS-1$
				pathsObj = (Scriptable)config.get(PATHS_PROPNAME, config);
			}
			((Scriptable)pathsObj).put(getResourcePathId(), (Scriptable)pathsObj, getComboUri().toString());
			if (isTraceLogging) {
				log.finer("modified config = " + Context.toString(config)); //$NON-NLS-1$
				log.exiting(AbstractHttpTransport.class.getName(), sourceMethod);
			}
		} finally {
			Context.exit();
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IExtensionInitializer#initialize(com.ibm.jaggr.service.IAggregator, com.ibm.jaggr.service.IAggregatorExtension, com.ibm.jaggr.service.IExtensionInitializer.IExtensionRegistrar)
	 */
	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension extension, IExtensionRegistrar reg) {
		this.aggregator = aggregator;

		resourcePathId = extension.getAttribute(IHttpTransportExtensionPoint.PATH_ATTRIBUTE);
		if (resourcePathId == null) {
			throw new IllegalArgumentException(
				IHttpTransportExtensionPoint.PATH_ATTRIBUTE  +
				" attribute not specified for extension " + //$NON-NLS-1$
				extension.getUniqueId()
			);
		}

		String comboUriStr = extension.getAttribute(IHttpTransportExtensionPoint.RESOURCESURI_ATTRIBUTE);
		if (comboUriStr == null) {
			throw new IllegalArgumentException(
				IHttpTransportExtensionPoint.RESOURCESURI_ATTRIBUTE  +
				" attribute not specified for extension " + //$NON-NLS-1$
				extension.getUniqueId()
			);
		} else {
			comboUri = URI.create(comboUriStr);
		}

		transportId = extension.getUniqueId();

		URI featureListResourceUri = getFeatureListResourceUri();
		// register a config listener so that we get notified of changes to
		// the server-side AMD config file.
		String name = aggregator.getName();
		Dictionary<String, String> dict = new Hashtable<String, String>();
		dict.put("name", name); //$NON-NLS-1$
		serviceRegistrations.add(aggregator.getPlatformServices().registerService(
				IConfigModifier.class.getName(), this, dict
				));
		dict = new Hashtable<String, String>();
		dict.put("name", name); //$NON-NLS-1$
		serviceRegistrations.add(aggregator.getPlatformServices().registerService(
				IShutdownListener.class.getName(), this, dict
				));
		dict = new Hashtable<String, String>();
		dict.put("name", aggregator.getName()); //$NON-NLS-1$
		serviceRegistrations.add(aggregator.getPlatformServices().registerService(
				IDependenciesListener.class.getName(), this, dict));

		if (featureListResourceUri != null) {
			depsInitialized = new CountDownLatch(1);

			// Get first resource factory extension so we can add to beginning of list
			Iterable<IAggregatorExtension> resourceFactoryExtensions = aggregator.getExtensions(IResourceFactoryExtensionPoint.ID);
			IAggregatorExtension first = resourceFactoryExtensions.iterator().next();

			// Register the featureMap resource factory
			Properties props = new Properties();
			props.put("scheme", getComboUri().getScheme()); //$NON-NLS-1$
			reg.registerExtension(
					newFeatureListResourceFactory(featureListResourceUri),
					props,
					new InitParams(Collections.<InitParam>emptyList()),
					IResourceFactoryExtensionPoint.ID,
					getTransportId(),
					first);

		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IShutdownListener#shutdown(com.ibm.jaggr.service.IAggregator)
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		// unregister the service registrations
		for (IServiceRegistration reg : serviceRegistrations) {
			reg.unregister();
		}
		serviceRegistrations.clear();
	}

	/**
	 * Returns the unique id for the transport.  Must
	 * be implemented by subclasses.
	 *
	 * @return the plugin unique id.
	 */
	protected String getTransportId() {
		return transportId;
	}

	/**
	 * Default implementation that returns null URI. Subclasses should
	 * implement this method to return the loader specific URI to the resource
	 * that implements the featureMap JavaScript code.
	 *
	 * @return null
	 */
	protected URI getFeatureListResourceUri() {
		return getComboUri().resolve(FEATUREMAP_JS_NAME);
	}

	/**
	 * Returns the aggregator instance that created this transport
	 *
	 * @return the aggregator
	 */
	protected IAggregator getAggregator() {
		return aggregator;
	}

	/**
	 * Returns the resource (combo) path id specified by the transport's
	 * plugin extension {@code path} attribute
	 *
	 * @return the resource path id
	 */
	protected String getResourcePathId() {
		return resourcePathId;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.IHttpTransport#contributeLoaderExtensionJavaScript(java.lang.String)
	 */
	@Override
	public void contributeLoaderExtensionJavaScript(String contribution) {
		extensionContributions.add(contribution);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.IHttpTransport#getLayerContribution(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.service.transport.IHttpTransport.LayerContributionType, java.lang.String)
	 */
	@Override
	public String getLayerContribution(HttpServletRequest request,
			LayerContributionType type, Object arg) {
		final String sourceMethod = "getLayerContribution"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{request, type, arg});
		}
		String result = ""; //$NON-NLS-1$
		if (type == LayerContributionType.END_RESPONSE) {
			if (TypeUtil.asBoolean(request.getAttribute(WARN_DEPRECATED_USE_OF_MODULES_QUERYARG))) {
				result += MessageFormat.format(CONSOLE_WARNING_MSG_FMT,
						new Object[]{
							Messages.AbstractHttpTransport_2
						}
				);
			}
			if (TypeUtil.asBoolean(request.getAttribute(WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG))) {
				result += MessageFormat.format(CONSOLE_WARNING_MSG_FMT,
						new Object[]{
							Messages.AbstractHttpTransport_3
						}
				);
			}

		}
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod, result);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.IHttpTransport#isServerExpandable(javax.servlet.http.HttpServletRequest, java.lang.String)
	 */
	public abstract boolean isServerExpandable(HttpServletRequest request, String mid);

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.IHttpTransport#getCacheKeyGenerators()
	 */
	@Override
	public abstract List<ICacheKeyGenerator> getCacheKeyGenerators();

	/**
	 * Returns the extension contributions that have been registered with this
	 * transport
	 *
	 * @return the extension contributions
	 */
	protected List<String> getExtensionContributions() {
		return extensionContributions;
	}

	/**
	 * Returns the dynamic portion of the loader extension javascript for this
	 * transport.  This includes all registered extension contributions.
	 *
	 * @return the dynamic portion of the loader extension javascript
	 */
	protected String getDynamicLoaderExtensionJavaScript() {
		final String sourceMethod = "getDynamicLoaderExtensionJavaScript"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod);
		}
		StringBuffer sb = new StringBuffer();
		for (String contribution : getExtensionContributions()) {
			sb.append(contribution).append("\r\n"); //$NON-NLS-1$
		}
		String cacheBust = AggregatorUtil.getCacheBust(aggregator);
		if (cacheBust != null && cacheBust.length() > 0) {
			sb.append("if (!require.combo.cacheBust){combo.cacheBust = '") //$NON-NLS-1$
			.append(cacheBust).append("';}\r\n"); //$NON-NLS-1$
		}
		if (moduleIdListHash != null) {
			sb.append("require.combo.reg(null, ["); //$NON-NLS-1$
			for (int i = 0; i < moduleIdListHash.length; i++) {
				sb.append(i == 0 ? "" : ", ").append(((int)moduleIdListHash[i])&0xFF); //$NON-NLS-1$ //$NON-NLS-2$
			}
			sb.append("]);\r\n"); //$NON-NLS-1$
		}
		sb.append(clientRegisterSyntheticModules());
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod, sb.toString());
		}
		return sb.toString();
	}

	/**
	 * Validate the {@link com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType} and argument type specified
	 * in a call to
	 * {@link #getLayerContribution(HttpServletRequest, com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType, Object)}
	 *
	 * @param request
	 *            The http request object
	 * @param type
	 *            The layer contribution (see
	 *            {@link com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType})
	 * @param arg
	 *            The argument value
	 */
	protected void validateLayerContributionState(HttpServletRequest request,
			LayerContributionType type, Object arg) {

		final String sourceMethod = "validateLayerContributionState"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), sourceMethod, new Object[]{request, type, arg});
		}
		LayerContributionType previousType = (LayerContributionType)request.getAttribute(LAYERCONTRIBUTIONSTATE_REQATTRNAME);
		if (isTraceLogging) {
			log.finer("previousType = " + previousType); //$NON-NLS-1$
		}
		switch (type) {
		case BEGIN_RESPONSE:
			if (previousType != null) {
				throw new IllegalStateException();
			}
			break;
		case BEGIN_MODULES:
			if (previousType != LayerContributionType.BEGIN_RESPONSE) {
				throw new IllegalStateException();
			}
			break;
		case BEFORE_FIRST_MODULE:
			if (previousType != LayerContributionType.BEGIN_MODULES ||
			!(arg instanceof String)) {
				throw new IllegalStateException();
			}
			break;
		case BEFORE_SUBSEQUENT_MODULE:
			if (previousType != LayerContributionType.AFTER_MODULE ||
			!(arg instanceof String)) {
				throw new IllegalStateException();
			}
			break;
		case AFTER_MODULE:
			if (previousType != LayerContributionType.BEFORE_FIRST_MODULE &&
			previousType != LayerContributionType.BEFORE_SUBSEQUENT_MODULE ||
			!(arg instanceof String)) {
				throw new IllegalStateException();
			}
			break;
		case END_MODULES:
			if (previousType != LayerContributionType.AFTER_MODULE) {
				throw new IllegalStateException();
			}
			break;
		case BEGIN_LAYER_MODULES:
			if (previousType != LayerContributionType.BEGIN_RESPONSE &&
			previousType != LayerContributionType.END_MODULES ||
			!(arg instanceof Set)) {
				throw new IllegalStateException();
			}
			break;
		case BEFORE_FIRST_LAYER_MODULE:
			if (previousType != LayerContributionType.BEGIN_LAYER_MODULES ||
			!(arg instanceof String)) {
				throw new IllegalStateException();
			}
			break;
		case BEFORE_SUBSEQUENT_LAYER_MODULE:
			if (previousType != LayerContributionType.AFTER_LAYER_MODULE ||
			!(arg instanceof String)) {
				throw new IllegalStateException();
			}
			break;
		case AFTER_LAYER_MODULE:
			if (previousType != LayerContributionType.BEFORE_FIRST_LAYER_MODULE &&
			previousType != LayerContributionType.BEFORE_SUBSEQUENT_LAYER_MODULE ||
			!(arg instanceof String)) {
				throw new IllegalStateException();
			}
			break;
		case END_LAYER_MODULES:
			if (previousType != LayerContributionType.AFTER_LAYER_MODULE ||
			!(arg instanceof Set)) {
				throw new IllegalStateException();
			}
			break;
		case END_RESPONSE:
			if (previousType != LayerContributionType.END_MODULES &&
			previousType != LayerContributionType.END_LAYER_MODULES) {
				throw new IllegalStateException();
			}
			break;
		}
		request.setAttribute(LAYERCONTRIBUTIONSTATE_REQATTRNAME, type);
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), sourceMethod);
		}
	}

	/**
	 * Returns the has conditions specified in the request as a base64 encoded
	 * trit map of feature values where each trit (three state value - 0, 1 and
	 * don't care) represents the state of a feature in the list of
	 * deendentFeatures that was sent to the client in the featureMap JavaScript
	 * resource served by
	 * {@link AbstractHttpTransport.FeatureListResourceFactory}.
	 * <p>
	 * Each byte from the base64 decoded byte array encodes 5 trits (3**5 = 243
	 * states out of the 256 possible states).
	 *
	 * @param request
	 *            The http request object
	 * @return the decoded feature list as a string, or null
	 * @throws IOException
	 */
	protected Features getFeaturesFromRequestEncoded(HttpServletRequest request) throws IOException {
		final String methodName = "getFeaturesFromRequestEncoded"; //$NON-NLS-1$
		boolean traceLogging = log.isLoggable(Level.FINER);
		if (traceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), methodName, new Object[]{request});
		}
		if (depsInitialized == null) {
			if (traceLogging) {
				log.finer("No initialization semphore"); //$NON-NLS-1$
				log.exiting(AbstractHttpTransport.class.getName(), methodName);
			}
			return null;
		}
		String encoded = request.getParameter(ENCODED_FEATURE_MAP_REQPARAM);
		if (encoded == null) {
			if (traceLogging) {
				log.finer(ENCODED_FEATURE_MAP_REQPARAM + " param not specified in request"); //$NON-NLS-1$
				log.exiting(AbstractHttpTransport.class.getName(), methodName);
			}
			return null;
		}
		if (traceLogging) {
			log.finer(ENCODED_FEATURE_MAP_REQPARAM + " param = " + encoded); //$NON-NLS-1$
		}
		try {
			depsInitialized.await();
		} catch (InterruptedException e) {
			throw new IOException(e);
		}

		byte[] decoded = Base64.decodeBase64(encoded);
		int len = dependentFeatures.size();
		ByteArrayOutputStream bos = new ByteArrayOutputStream(len);

		// Validate the input - first two bytes specify length of feature list on the client
		if (len != (decoded[0]&0xFF)+((decoded[1]&0xFF)<< 8) || decoded.length != len/5 + (len%5==0?0:1) + 2) {
			if (log.isLoggable(Level.FINER)) {
				log.finer("Invalid encoded feature list.  Expected feature list length = " + len); //$NON-NLS-1$
			}
			throw new BadRequestException("Invalid encoded feature list"); //$NON-NLS-1$
		}
		// Now decode the trit map
		for (int i = 2; i < decoded.length; i++) {
			int q = decoded[i] & 0xFF;
			for (int j = 0; j < 5 && (i-2)*5+j < len; j++) {
				bos.write(q % 3);
				q = q / 3;
			}
		}
		Features result = new Features();
		int i = 0;
		for (byte b : bos.toByteArray()) {
			if (b < 2) {
				result.put(dependentFeatures.get(i), b == 1);
			}
			i++;
		}
		if (traceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), methodName, result);
		}
		return result;
	}

	/**
	 * Returns an instance of a FeatureMapJSResourceFactory.
	 *
	 * @param resourceUri
	 *            the resource {@link URI}
	 * @return a new factory object
	 */
	protected FeatureListResourceFactory newFeatureListResourceFactory(URI resourceUri) {
		final String methodName = "newFeatureMapJSResourceFactory"; //$NON-NLS-1$
		boolean traceLogging = log.isLoggable(Level.FINER);
		if (traceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), methodName, new Object[]{resourceUri});
		}
		FeatureListResourceFactory factory = new FeatureListResourceFactory(resourceUri);
		if (traceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), methodName);
		}
		return factory;
	}

	/**
	 * Resource factory for serving the featureMap JavaScript resource containing the
	 * dynamically generated list of dependent features.  This list is used by the
	 * client to encode the states of the features as a trit map of values over
	 * the list, where each trit in the encoded data is the value of a feature
	 * in the list.
	 */
	protected class FeatureListResourceFactory implements IResourceFactory {

		private final URI resourceUri;

		public FeatureListResourceFactory(URI resourceUri) {
			this.resourceUri = resourceUri;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceFactory#handles(java.net.URI)
		 */
		@Override
		public boolean handles(URI uri) {
			final String methodName = "handles"; //$NON-NLS-1$
			boolean traceLogging = log.isLoggable(Level.FINER);
			if (traceLogging) {
				log.entering(AbstractHttpTransport.FeatureListResourceFactory.class.getName(), methodName, new Object[]{uri});
			}
			boolean result = false;
			if (StringUtils.equals(uri.getPath(), resourceUri.getPath()) &&
					StringUtils.equals(uri.getScheme(), resourceUri.getScheme()) &&
					StringUtils.equals(uri.getHost(),  resourceUri.getHost())) {
				result = true;
			}
			if (traceLogging) {
				log.exiting(AbstractHttpTransport.FeatureListResourceFactory.class.getName(), methodName, result);
			}
			return result;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceFactory#newResource(java.net.URI)
		 */
		@Override
		public IResource newResource(URI uri) {
			final String methodName = "newResource"; //$NON-NLS-1$
			boolean traceLogging = log.isLoggable(Level.FINER);
			if (traceLogging) {
				log.entering(AbstractHttpTransport.FeatureListResourceFactory.class.getName(), methodName, new Object[]{uri});
			}
			// validate the URI
			if (!StringUtils.equals(uri.getPath(), resourceUri.getPath()) ||
					!StringUtils.equals(uri.getScheme(), resourceUri.getScheme()) ||
					!StringUtils.equals(uri.getHost(),  resourceUri.getHost())) {
				return new ExceptionResource(uri, 0, new IOException(new UnsupportedOperationException()));
			}
			// wait for dependencies to be initialized
			try {
				depsInitialized.await();
			} catch (InterruptedException e) {
				return new ExceptionResource(uri, 0L, new IOException(e));
			}
			IResource result = depFeatureListResource;
			if (traceLogging) {
				log.exiting(AbstractHttpTransport.FeatureListResourceFactory.class.getName(), methodName, depFeatureListResource);
			}
			return result;
		}
	}

	/**
	 * Creates an {@link IResource} object for the dependent feature list AMD
	 * module
	 *
	 * @param list
	 *            the dependent features list
	 * @param uri
	 *            the resource URI
	 * @param lastmod
	 *            the last modified time of the resource
	 * @return the {@link IResource} object for the module
	 */
	protected IResource createFeatureListResource(List<String> list, URI uri, long lastmod) {
		JSONArray json;
		try {
			json = new JSONArray(new ArrayList<String>(dependentFeatures));
		} catch (JSONException ex) {
			return new ExceptionResource(uri, lastmod, new IOException(ex));
		}
		StringBuffer sb = new StringBuffer();
		sb.append(FEATURE_LIST_PRELUDE).append(json).append(FEATURE_LIST_PROLOGUE);
		IResource result = new StringResource(sb.toString(), uri, lastmod);
		return result;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.deps.IDependenciesListener#dependenciesLoaded(com.ibm.jaggr.service.deps.IDependencies, long)
	 */
	@Override
	public void dependenciesLoaded(IDependencies deps, long sequence) {
		/*
		 * Aggregate the dependent features from all the modules into a single,
		 * alphabetically sorted, list of features.
		 */
		Set<String> features = new TreeSet<String>();
		try {
			for (String mid : deps.getDependencyNames()) {
				features.addAll(deps.getDependentFeatures(mid));
			}
			dependentFeatures = Collections.unmodifiableList(Arrays.asList(features.toArray(new String[features.size()])));
			depFeatureListResource = createFeatureListResource(dependentFeatures, getFeatureListResourceUri(), deps.getLastModified());
			depsInitialized.countDown();
			generateModuleIdMap(deps);
		} catch (ProcessingDependenciesException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
			// Don't clear the latch as another call to this method can be expected soon
		} catch (Throwable t) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, t.getMessage(), t);
			}
			// Clear the latch to allow the waiting thread to wake up.  If
			// dependencies have never been initialized, then NullPointerExceptions
			// will be thrown for any threads trying to access the uninitialized
			// dependencies.
			depsInitialized.countDown();

		}
	}

	/**
	 * Returns a collection of the names of synthetic modules that may be used by the transport.
	 * Module names provided here will be assigned module name unique ids and can be requested by
	 * the client using module id encoding.
	 * <p>
	 * Synthetic modules are those which don't have physical source files that will be discovered
	 * when building the dependency map.
	 *
	 * @return the collection of synthetic names used by the transport
	 */
	protected Collection<String> getSyntheticModuleNames() {
		final String methodName = "getSyntheticModuleNames"; //$NON-NLS-1$
		boolean traceLogging = log.isLoggable(Level.FINER);
		if (traceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), methodName);
		}
		Collection<String> result = new HashSet<String>();
		result.add(getAggregatorTextPluginName());
		if (traceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), methodName, result);
		}
		return result;
	}

	/**
	 * Returns the JavaScript code for calling the client-side module name id registration function
	 * to register name ids for the transport synthetic modules.
	 *
	 * @return the registration JavaScript or an empty string if no synthetic modules.
	 */
	protected String clientRegisterSyntheticModules() {
		final String methodName = "clientRegisterSyntheticModules"; //$NON-NLS-1$
		boolean traceLogging = log.isLoggable(Level.FINER);
		if (traceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), methodName);
		}
		StringBuffer sb = new StringBuffer();
		Map<String, Integer> map = getModuleIdMap();
		if (map != null && getModuleIdRegFunctionName() != null) {
			Collection<String> names = getSyntheticModuleNames();
			if (names != null && names.size() > 0) {
				// register the text plugin name (combo/text) and name id with the client
				sb.append(getModuleIdRegFunctionName()).append("([[["); //$NON-NLS-1$
				int i = 0;
				for (String name : names) {
					if (map.get(name) != null) {
						sb.append(i++==0 ?"":",").append("\"").append(name).append("\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					}
				}
				sb.append("]],[["); //$NON-NLS-1$
				i = 0;
				for (String name : names) {
					Integer id = map.get(name);
					if (id != null) {
						sb.append(i++==0?"":",").append(id.intValue()); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
				sb.append("]]]);"); //$NON-NLS-1$
			}
		}
		if (traceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), methodName, sb.toString());
		}
		return sb.toString();
	}

	/**
	 * Generates the module id map used by the transport to encode/decode module names
	 * using assigned module name ids.
	 *
	 * @param deps
	 *            The dependencies object
	 *
	 * @throws IOException
	 */
	protected void generateModuleIdMap(IDependencies deps) throws IOException {
		final String methodName = "generateModuleIdMap"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AbstractHttpTransport.class.getName(), methodName);
		}
		if (getModuleIdRegFunctionName() == null) {
			if (isTraceLogging) {
				log.finer("No module id list registration function - returning"); //$NON-NLS-1$
				log.exiting(AbstractHttpTransport.class.getName(), methodName);
			}
			return;
		}
		Map<String, String> names = new TreeMap<String, String>(); // Use TreeMap to get consistent ordering

		for (String name : deps.getDependencyNames()) {
			names.put(name, isTraceLogging ? deps.getURI(name).toString() : null);
		}
		for (String name : getSyntheticModuleNames()) {
			names.put(name, isTraceLogging ? "transport added" : null); //$NON-NLS-1$
		}
		if (isTraceLogging) {
			// Log the module name id list.  This information is useful when trying to determine
			// why different servers in the same cluster might be generating different list hashes.
			StringBuffer sb = new StringBuffer("Module ID list:\r\n"); //$NON-NLS-1$
			int i = 1;
			for (Map.Entry<String, String> entry : names.entrySet()) {
				sb.append(i++).append(": ").append(entry.getKey()).append(" - ").append(entry.getValue()).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			log.finer(sb.toString());
		}

		Map<String, Integer> idMap = new HashMap<String, Integer>(names.size());
		List<String> idList = new ArrayList<String>(names.size()+1);
		idList.add("");	// slot 0 is unused //$NON-NLS-1$
		idList.addAll(names.keySet());
		for (int i = 1; i < idList.size(); i++) {
			idMap.put(idList.get(i), i);
		}

		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
			throw new IOException(e);
		}
		moduleIdListHash = md.digest(idList.toString().getBytes("UTF-8")); //$NON-NLS-1$
		moduleIdMap = Collections.unmodifiableMap(idMap);
		moduleIdList = idList;

		if (log.isLoggable(Level.INFO)) {
			log.info("Module ID List hash = " + TypeUtil.byteArray2String(moduleIdListHash)); //$NON-NLS-1$
		}
		if (isTraceLogging) {
			log.exiting(AbstractHttpTransport.class.getName(), methodName);
		}
	}

	/**
	 * Implementation of an {@link IResource} that aggregates the various
	 * sources (dynamic and static content) of the loader extension
	 * javascript for this transport
	 */
	protected class LoaderExtensionResource implements IResource, IResourceVisitor.Resource {
		IResource res;

		public LoaderExtensionResource(IResource res) {
			this.res = res;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#getURI()
		 */
		@Override
		public URI getURI() {
			return res.getURI();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#getPath()
		 */
		@Override
		public String getPath() {
			return getURI().getPath();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#exists()
		 */
		@Override
		public boolean exists() {
			return res.exists();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#lastModified()
		 */
		@Override
		public long lastModified() {
			return res.lastModified();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return new ReaderInputStream(getReader(), "UTF-8"); //$NON-NLS-1$
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#getReader()
		 */
		@Override
		public Reader getReader() throws IOException {

			// Return an aggregation reader for the loader extension javascript
			return new AggregationReader(
					"(function(){", //$NON-NLS-1$
					res.getReader(),
					getDynamicLoaderExtensionJavaScript(),
					"})();"); //$NON-NLS-1$
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#walkTree(com.ibm.jaggr.service.resource.IResourceVisitor)
		 */
		@Override
		public void walkTree(IResourceVisitor visitor) throws IOException {
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#asVisitorResource()
		 */
		@Override
		public Resource asVisitorResource() throws IOException {
			return this;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceVisitor.Resource#isFolder()
		 */
		@Override
		public boolean isFolder() {
			return false;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResource#resolve(java.lang.String)
		 */
		@Override
		public IResource resolve(String relative) {
			throw new UnsupportedOperationException();
		}
	}
}
