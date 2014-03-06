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
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.ServiceRegistration;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfigModifier;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.IDependenciesListener;
import com.ibm.jaggr.core.impl.layer.LayerImpl;
import com.ibm.jaggr.core.impl.resource.ExceptionResource;
import com.ibm.jaggr.core.readers.AggregationReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;
import com.ibm.jaggr.core.resource.StringResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.RequestedModuleNames;
import com.ibm.jaggr.core.util.TypeUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

/**
 * Implements common functionality useful for all Http Transport implementation
 * and defines abstract methods that subclasses need to implement
 */
public abstract class AbstractHttpTransport implements IHttpTransport, IConfigModifier, IShutdownListener, IDependenciesListener {
	private static final Logger log = Logger.getLogger(AbstractDojoHttpTransport.class.getName());

	public static final String PATH_ATTRNAME = "path"; //$NON-NLS-1$
	public static final String PATHS_PROPNAME = "paths"; //$NON-NLS-1$

	public static final String REQUESTEDMODULES_REQPARAM = "modules"; //$NON-NLS-1$
	public static final String REQUESTEDMODULESCOUNT_REQPARAM = "count"; //$NON-NLS-1$

	public static final String CONSOLE_WARNING_MSG_FMT = "window.console && console.warn(\"{0}\");"; //$NON-NLS-1$

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

	public static final String[] EXPORTMODULENAMES_REQPARAMS = {"exportNames", "en"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] SHOWFILENAMES_REQPARAMS = {"showFilenames", "fn"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] NOCACHE_REQPARAMS = {"noCache", "nc"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] REQUESTEDLOCALES_REQPARAMS = {"locales", "locs"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] HASPLUGINBRANCHING_REQPARAMS = {"hasBranching", "hb"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String CONFIGVARNAME_REQPARAM = "configVarName"; //$NON-NLS-1$

	public static final String LAYERCONTRIBUTIONSTATE_REQATTRNAME = AbstractHttpTransport.class.getName() + ".LayerContributionState"; //$NON-NLS-1$
	public static final String ENCODED_FEATURE_MAP_REQPARAM = "hasEnc"; //$NON-NLS-1$

	static final String WARN_DEPRECATED_USE_OF_MODULES_QUERYARG = LayerImpl.class.getName() + ".DEPRECATED_USE_OF_MODULES"; //$NON-NLS-1$
	static final String WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG = LayerImpl.class.getName() + ".DEPRECATED_USE_OF_REQUIRED"; //$NON-NLS-1$


	static final String FEATUREMAP_JS_PATH = "/WebContent/featureList.js"; //$NON-NLS-1$
	public static final String FEATURE_LIST_PRELUDE = "define([], "; //$NON-NLS-1$
	public static final String FEATURE_LIST_PROLOGUE = ");"; //$NON-NLS-1$

	/** A cache of folded module list strings to expanded file name lists.  Used by LayerImpl cache */
	private Map<String, Collection<String>> _encJsonMap = new ConcurrentHashMap<String, Collection<String>>();

	private static final Pattern DECODE_JSON = Pattern.compile("([!()|*<>])"); //$NON-NLS-1$
	private static final Pattern REQUOTE_JSON = Pattern.compile("([{,:])([^{},:\"]+)([},:])"); //$NON-NLS-1$

	private List<ServiceRegistration> serviceRegistrations = new ArrayList<ServiceRegistration>();
	private IAggregator aggregator = null;
	private List<String> extensionContributions = new LinkedList<String>();

	private List<String> dependentFeatures = null;
	private IResource depFeatureListResource = null;

	private CountDownLatch depsInitialized = null;

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
	protected abstract URI getComboUri();

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
		request.setAttribute(EXPORTMODULENAMES_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, EXPORTMODULENAMES_REQPARAMS), true));

		request.setAttribute(SHOWFILENAMES_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, SHOWFILENAMES_REQPARAMS)));

		request.setAttribute(NOCACHE_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, NOCACHE_REQPARAMS)));

		request.setAttribute(HASPLUGINBRANCHING_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, HASPLUGINBRANCHING_REQPARAMS), true));

		request.setAttribute(REQUESTEDLOCALES_REQATTRNAME, getRequestedLocales(request));

		if (request.getParameter(CONFIGVARNAME_REQPARAM) != null) {
			request.setAttribute(CONFIGVARNAME_REQATTRNAME, request.getParameter(CONFIGVARNAME_REQPARAM));
		}
	}

	protected void setRequestedModuleNames(HttpServletRequest request) throws IOException {
		RequestedModuleNames requestedModuleNames = new RequestedModuleNames();
		String moduleQueryArg = request.getParameter(REQUESTEDMODULES_REQPARAM);

		setModuleListFromRequest(request, requestedModuleNames);

		// Get the deprecated require list
		List<String> required = getNameListFromQueryArg(request, REQUIRED_REQPARAM);
		if (required != null) {
			requestedModuleNames.setDeps(required);
			// Log console warning about deprecated query arg if in debug/dev mode
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			if (aggr.getOptions().isDebugMode() || aggr.getOptions().isDevelopmentMode()) {
				request.setAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG, Boolean.TRUE);
			}
		}

		// Get the scripts list
		List<String> scripts = getNameListFromQueryArg(request, SCRIPTS_REQPARAM);
		if (scripts != null) {
			if (moduleQueryArg != null || required != null) {
				throw new BadRequestException(request.getQueryString());
			}
			requestedModuleNames.setModules(scripts);
		}
		List<String> deps = getNameListFromQueryArg(request, DEPS_REQPARAM);
		if (deps != null) {
			if (moduleQueryArg != null || required != null) {
				throw new BadRequestException(request.getQueryString());
			}
			requestedModuleNames.setDeps(deps);
		}
		List<String> preloads = getNameListFromQueryArg(request, PRELOADS_REQPARAM);
		if (preloads != null) {
			if (moduleQueryArg != null || required != null) {
				throw new BadRequestException(request.getQueryString());
			}
			requestedModuleNames.setPreloads(preloads);
		}
		request.setAttribute(REQUESTEDMODULENAMES_REQATTRNAME, requestedModuleNames);
	}

	/**
	 * Unfolds the folded module list in the request into a {@code Collection<String>}
	 * of module names.
	 *
	 * @param request
	 *            the request object
	 * @param requestedModuleNames
	 *            Output - the processed results
	 * @throws IOException
	 */
	protected void setModuleListFromRequest(HttpServletRequest request, RequestedModuleNames requestedModuleNames) throws IOException {
		List<String> moduleList = new LinkedList<String>();
		String moduleQueryArg = request.getParameter(REQUESTEDMODULES_REQPARAM);
		String countParam = request.getParameter(REQUESTEDMODULESCOUNT_REQPARAM);
		int count = 0;
		try {
			if (moduleQueryArg == null) {
				return;
			}
			if (countParam != null) {
				count = Integer.parseInt(request.getParameter(REQUESTEDMODULESCOUNT_REQPARAM));
				// put a reasonable upper limit on the value of count
				if (count < 1 || count > 1000) {
					throw new BadRequestException("count:" + count); //$NON-NLS-1$
				}
			}
			try {
				moduleQueryArg = URLDecoder.decode(moduleQueryArg, "UTF-8"); //$NON-NLS-1$
			} catch (UnsupportedEncodingException e) {
				throw new BadRequestException(e.getMessage());
			}

			if (count > 0) {
				Collection<String> cached = _encJsonMap.get(moduleQueryArg);
				if (cached != null && cached.size() == count) {
					moduleList.addAll(cached);
				} else {
					moduleList.addAll(Arrays.asList(unfoldModules(decodeModules(moduleQueryArg), count)));

					// Save buildReader so we don't have to do this again.
					_encJsonMap.put(moduleQueryArg, moduleList);
				}
				requestedModuleNames.setString(moduleQueryArg);
			} else {
				// Hand crafted URL; get module names from one or more module query args (deprecated)
				moduleList.addAll(Arrays.asList(moduleQueryArg.split("\\s*,\\s*", 0))); //$NON-NLS-1$
				// Set request attribute to warn about use of deprecated param
				IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
				if (aggr.getOptions().isDebugMode() || aggr.getOptions().isDevelopmentMode()) {
					request.setAttribute(WARN_DEPRECATED_USE_OF_MODULES_QUERYARG, Boolean.TRUE);
				}
			}
		} catch (ArrayIndexOutOfBoundsException ex) {
			_encJsonMap.remove(moduleQueryArg);
			throw new BadRequestException(moduleQueryArg + " - " + count, ex); //$NON-NLS-1$
		} catch (NumberFormatException ex) {
			_encJsonMap.remove(moduleQueryArg);
			throw new BadRequestException(moduleQueryArg + " - " + count, ex); //$NON-NLS-1$
		} catch (JSONException ex) {
			_encJsonMap.remove(moduleQueryArg);
			throw new BadRequestException(moduleQueryArg + " - " + count, ex); //$NON-NLS-1$
		} catch (RuntimeException ex) {
			_encJsonMap.remove(moduleQueryArg);
			throw ex;
		}
		requestedModuleNames.setModules(Collections.unmodifiableList(moduleList));
	}

	protected List<String> getNameListFromQueryArg(HttpServletRequest request, String argName) throws IOException {
		List<String> nameList = null;
		String argValue = request.getParameter(argName);
		if (argValue != null) {
			nameList = new LinkedList<String>();
			nameList.addAll(Arrays.asList(argValue.split("\\s*,\\s*", 0))); //$NON-NLS-1$
		}
		return nameList != null ? Collections.unmodifiableList(nameList) : null;
	}

	/**
	 * Returns the requested locales as a collection of locale strings
	 *
	 * @param request the request object
	 * @return the locale strings
	 */
	protected Collection<String> getRequestedLocales(HttpServletRequest request) {
		String[] locales;
		String sLocales = getParameter(request, REQUESTEDLOCALES_REQPARAMS);
		if (sLocales != null) {
			locales = sLocales.split(","); //$NON-NLS-1$
		} else {
			locales = new String[0];
		}
		return Collections.unmodifiableCollection(Arrays.asList(locales));

	}

	/**
	 *  Decode JSON object encoded for url transport.
	 *  Enforces ordering of object keys and mangles JSON format to prevent encoding of frequently used characters.
	 *  Assumes that keynames and values are valid filenames, and do not contain illegal filename chars.
	 *  See http://www.w3.org/Addressing/rfc1738.txt for small set of safe chars.
	 *
	 * @param encstr
	 *            the encoded module name list
	 * @return the decoded string as a JSON object
	 *
	 * @throws IOException
	 * @throws JSONException
	 */
	protected  JSONObject decodeModules(String encstr) throws IOException, JSONException {
		StringBuffer json = new StringBuffer(encstr.length() * 2);
		Matcher m = DECODE_JSON.matcher(encstr);
		while (m.find()) {
			String match = m.group(1);
			if (match.equals("!")) //$NON-NLS-1$
				m.appendReplacement(json, ":");     //$NON-NLS-1$
			else if (match.equals("("))     //$NON-NLS-1$
				m.appendReplacement(json, "{"); //$NON-NLS-1$
			else if (match.equals(")"))     //$NON-NLS-1$
				m.appendReplacement(json, "}"); //$NON-NLS-1$
			else if (match.equals("|"))     //$NON-NLS-1$
				m.appendReplacement(json, "!"); //$NON-NLS-1$
			else if (match.equals("*"))     //$NON-NLS-1$
				m.appendReplacement(json, ","); //$NON-NLS-1$
			else if (match.equals("<"))     //$NON-NLS-1$
				m.appendReplacement(json, "("); //$NON-NLS-1$
			else if (match.equals(">"))     //$NON-NLS-1$
				m.appendReplacement(json, ")"); //$NON-NLS-1$
		}
		m.appendTail(json);
		JSONObject decoded = null;
		String jsonstr = json.toString();
		jsonstr = REQUOTE_JSON.matcher(jsonstr).replaceAll("$1\"$2\"$3"); // matches all keys //$NON-NLS-1$
		jsonstr = REQUOTE_JSON.matcher(jsonstr).replaceAll("$1\"$2\"$3"); // matches all values //$NON-NLS-1$
		decoded = new JSONObject(jsonstr);
		return decoded;
	}

	/**
	 * Regular expression for a non-path property (i.e. auxiliary information or processing
	 * instruction) of a folded path json object.
	 */
	static public Pattern NON_PATH_PROP_PATTERN = Pattern.compile("^/[^/]+/$"); //$NON-NLS-1$

	/**
	 * Name of folded path json property used to identify the names of loader
	 * plugin prefixes and their ordinals used in the folded path.  This must
	 * match the value of pluginPrefixesPropName in loaderExtCommon.js.  The
	 * slashes (/) ensure that the name won't collide with a real path name.
	 */
	static public String PLUGIN_PREFIXES_PROP_NAME = "/pre/"; //$NON-NLS-1$

	/**
	 * Unfolds a folded module name list into a String array of unfolded names
	 * <p>
	 * The returned list must be sorted the same way it was requested ordering
	 * modules in the same way as in the companion js extension to amd loader
	 * order provided in the folded module leaf
	 *
	 * @param modules
	 *            The folded module name list
	 * @param count
	 *            The count of modules in the list
	 * @return The unfolded module name list
	 * @throws IOException
	 * @throws JSONException
	 */
	protected String[] unfoldModules(JSONObject modules, int count) throws IOException, JSONException {
		String[] ret = new String[count];
		Iterator<?> it = modules.keys();
		String[] prefixes = null;
		if (modules.containsKey(PLUGIN_PREFIXES_PROP_NAME)) {
			@SuppressWarnings("unchecked")
			Map<String, String> oPrefixes = (Map<String, String>) modules.get(PLUGIN_PREFIXES_PROP_NAME);
			prefixes = new String[oPrefixes.size()];
			for (String key : oPrefixes.keySet()) {
				prefixes[Integer.parseInt(oPrefixes.get(key))] = key;
			}
		}
		while (it.hasNext()) {
			String key = (String) it.next();
			if (!NON_PATH_PROP_PATTERN.matcher(key).find()) {
				unfoldModulesHelper(modules.get(key), key, prefixes, ret);
			}
		}
		// validate no empty array slots
		for (String name : ret) {
			if (name == null) throw new BadRequestException();
		}
		return ret;
	}

	/**
	 * Helper routine to unfold folded module names
	 *
	 * @param obj
	 *            The folded path list of names, as a string or JSON object
	 * @param path
	 *            The reference path
	 * @param aPrefixes
	 *            Array of loader plugin prefixes
	 * @param modules
	 *            Output - the list of unfolded modlue names
	 * @throws IOException
	 * @throws JSONException
	 */
	protected void unfoldModulesHelper(Object obj, String path, String[] aPrefixes, String[] modules) throws IOException, JSONException {
		if (obj instanceof JSONObject) {
			JSONObject jsonobj = (JSONObject)obj;
			Iterator<?> it = jsonobj.keySet().iterator();
			while (it.hasNext()) {
				String key = (String)it.next();
				String newpath = path + "/" + key;  //$NON-NLS-1$
				unfoldModulesHelper(jsonobj.get(key), newpath, aPrefixes, modules);
			}
		}
		else if (obj instanceof String){
			String[] values = ((String)obj).split("-"); //$NON-NLS-1$
			int idx = Integer.parseInt(values[0]);
			if (modules[idx] != null) {
				throw new BadRequestException();
			}
			modules[idx] = values.length > 1 ?
					((aPrefixes != null ?
							aPrefixes[Integer.parseInt(values[1])] : values[1])
							+ "!" + path) : //$NON-NLS-1$
								path;
		} else {
			throw new BadRequestException();
		}
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
		Features features = getFeaturesFromRequestEncoded(request);
		if (features != null) {
			return features;
		}
		features = new Features();
		String has  = getHasConditionsFromRequest(request);
		if (has != null) {
			if (log.isLoggable(Level.FINEST))
				log.finest("Adding has parameters from request: " + has); //$NON-NLS-1$

			for (String s : has.split("[;*]")) { //$NON-NLS-1$
				boolean value = true;
				if (s.startsWith("!")) { //$NON-NLS-1$
					s = s.substring(1);
					value = false;
				}
				features.put(s, value);
			}
			if (log.isLoggable(Level.FINEST))
				log.finest("features = " + features.toString()); //$NON-NLS-1$
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

		String ret = null;
		if (request.getParameter(FEATUREMAPHASH_REQPARAM) != null) {
			// The cookie called 'has' contains the has conditions
			Cookie[] cookies = request.getCookies();
			if (cookies != null) {
				for (int i = 0; ret == null && i < cookies.length; i++) {
					Cookie cookie = cookies[i];
					if (cookie.getName().equals(FEATUREMAP_REQPARAM) && cookie.getValue() != null) {
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
		else
			ret = request.getParameter(FEATUREMAP_REQPARAM);

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
		return result;
	}

	/**
	 * Returns the requested optimization level from the request.
	 *
	 * @param request the request object
	 * @return the optimization level specified in the request
	 */
	protected OptimizationLevel getOptimizationLevelFromRequest(HttpServletRequest request) {
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
		return level;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfigModifier#modifyConfig(com.ibm.jaggr.service.IAggregator, java.util.Map)
	 */
	@Override
	public void modifyConfig(IAggregator aggregator, Scriptable config) {
		// The server-side AMD config has been updated.  Add an entry to the
		// {@code paths} property to map the resource (combo) path to the
		// location of the combo resources on the server.
		Context context = Context.enter();
		try {
			Object pathsObj = config.get(PATHS_PROPNAME, config);
			if (pathsObj == Scriptable.NOT_FOUND) {
				// If not present, add it.
				config.put("paths", config, context.newObject(config)); //$NON-NLS-1$
				pathsObj = (Scriptable)config.get(PATHS_PROPNAME, config);
			}
			((Scriptable)pathsObj).put(getResourcePathId(), (Scriptable)pathsObj, getComboUri().toString());
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
			Iterable<IAggregatorExtension> resourceFactoryExtensions = aggregator.getResourceFactoryExtensions();
			IAggregatorExtension first = resourceFactoryExtensions.iterator().next();

			// Register the featureMap resource factory
			Properties props = new Properties();
			props.put("scheme", "namedbundleresource"); //$NON-NLS-1$ //$NON-NLS-2$
			reg.registerExtension(
					newFeatureListResourceFactory(featureListResourceUri),
					props,
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
		for (ServiceRegistration reg : serviceRegistrations) {
			if (reg != null) {
				reg.unregister();
			}
		}
		serviceRegistrations.clear();
	}

	/**
	 * Returns the unique id for the transport.  Must
	 * be implemented by subclasses.
	 *
	 * @return the plugin unique id.
	 */
	abstract protected String getTransportId();

	/**
	 * Default implementation that returns null URI. Subclasses should
	 * implement this method to return the loader specific URI to the resource
	 * that implements the featureMap JavaScript code.
	 *
	 * @return null
	 */
	protected URI getFeatureListResourceUri() {
		return getComboUri().resolve(FEATUREMAP_JS_PATH);
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
	abstract protected String getResourcePathId();

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
		StringBuffer sb = new StringBuffer();
		for (String contribution : getExtensionContributions()) {
			sb.append(contribution).append("\r\n"); //$NON-NLS-1$
		}
		String cacheBust = aggregator.getConfig().getCacheBust();
		String optionsCb = aggregator.getOptions().getCacheBust();
		if (optionsCb != null && optionsCb.length() > 0) {
			cacheBust = (cacheBust != null && cacheBust.length() > 0) ?
					(cacheBust + "-" + optionsCb) : optionsCb; //$NON-NLS-1$
		}
		if (cacheBust != null && cacheBust.length() > 0) {
			sb.append("if (!combo.cacheBust){combo.cacheBust = '") //$NON-NLS-1$
			.append(cacheBust).append("';}\r\n"); //$NON-NLS-1$
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

		LayerContributionType previousType = (LayerContributionType)request.getAttribute(LAYERCONTRIBUTIONSTATE_REQATTRNAME);
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
		final boolean traceLogging = log.isLoggable(Level.FINER);
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
		final boolean traceLogging = log.isLoggable(Level.FINER);
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
			final boolean traceLogging = log.isLoggable(Level.FINER);
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
			final boolean traceLogging = log.isLoggable(Level.FINER);
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
