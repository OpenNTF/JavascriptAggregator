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

package com.ibm.jaggr.service.impl.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IShutdownListener;
import com.ibm.jaggr.service.InitParams;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.config.IConfigModifier;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.options.IOptionsListener;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.Features;
import com.ibm.jaggr.service.util.HasNode;
import com.ibm.jaggr.service.util.PathUtil;
import com.ibm.jaggr.service.util.TypeUtil;

public class ConfigImpl implements IConfig, IShutdownListener, IOptionsListener {
	private static final Logger log = Logger.getLogger(ConfigImpl.class.getName());

	/** regular expression for detecting if a plugin name is the has! plugin */
	static final Pattern HAS_PATTERN = Pattern.compile("(^|\\/)has$"); //$NON-NLS-1$
	
	static final String JSON_PROXY_EYECATCHER = "$$JSONProxy$$"; //$NON-NLS-1$
	static final String JSON_PROXY_REGEXP = "regexp"; //$NON-NLS-1$
	static final String JSON_PROXY_FUNCTION = "function"; //$NON-NLS-1$

	private final IAggregator aggregator;
	private final Map<String, Object> rawConfig;
	private final long lastModified;
	private final URI configUri;

	private URI base;
	private Map<String, IPackage> packages;
	private Map<String, URI> paths;
	private List<IAlias> aliases;
	private List<String> deps;
	private boolean depsIncludeBaseUrl;
	private boolean coerceUndefinedToFalse;
	private int expires;
	private String notice;
	private String cacheBust;
	private Scriptable sharedScope;
	protected List<ServiceRegistration> serviceRegs = new LinkedList<ServiceRegistration>();
	
	public ConfigImpl(IAggregator aggregator) throws IOException {
		this.aggregator = aggregator;
	
		try { 
			configUri = loadConfigUri();
			lastModified = configUri.toURL().openConnection().getLastModified();

			rawConfig = loadConfigJSON();
			
			// Call config modifiers to allow them to update the config
			// before we parse it.
			callConfigModifiers(rawConfig);
			
			init();
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}

	public ConfigImpl(IAggregator aggregator, URI configUri, String configJson) throws IOException, JSONException {
		lastModified = -1;
		this.configUri = configUri;
		this.aggregator = aggregator;
		this.rawConfig = loadConfigJSON(configJson);
		init();
	}
	
	public ConfigImpl(IAggregator aggregator, URI configUri, Map<String, Object> rawConfig) throws IOException {
		lastModified = -1;
		this.configUri = configUri;
		this.aggregator = aggregator;
		this.rawConfig = rawConfig;
		init();
	}
	
	protected void init() throws IOException {
		try { 
			registerServices();
			base = loadBaseURI(rawConfig);
			packages = Collections.unmodifiableMap(loadPackages(rawConfig));
			paths = Collections.unmodifiableMap(loadPaths(rawConfig));
			aliases = Collections.unmodifiableList(loadAliases(rawConfig));
			deps = Collections.unmodifiableList(loadDeps(rawConfig));
			depsIncludeBaseUrl = loadDepsIncludeBaseUrl(rawConfig);
			coerceUndefinedToFalse = loadCoerceUndefinedToFalse(rawConfig);
			expires = loadExpires(rawConfig);
			notice = loadNotice(rawConfig);
			cacheBust = loadCacheBust(rawConfig);
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#lastModified()
	 */
	@Override
	public long lastModified() {
		return lastModified;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getConfigUri()
	 */
	@Override
	public URI getConfigUri() {
		return configUri;
	}
	
	protected IAggregator getAggregator() {
		return aggregator;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getBase()
	 */
	@Override
	public URI getBase() {
		return base;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getPackages()
	 */
	@Override
	public Map<String, IPackage> getPackages() {
		return Collections.unmodifiableMap(packages);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getPaths()
	 */
	@Override
	public Map<String, URI> getPaths() {
		return Collections.unmodifiableMap(paths);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getAliases()
	 */
	@Override
	public List<IAlias> getAliases() {
		return Collections.unmodifiableList(aliases);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#isDepsIncludeBaseUrl()
	 */
	@Override
	public boolean isDepsIncludeBaseUrl() {
		return depsIncludeBaseUrl;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getExpires()
	 */
	@Override
	public int getExpires() {
		return expires;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getDeps()
	 */
	@Override
	public List<String> getDeps() {
		return deps;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#isCoerceUndefinedToFalse()
	 */
	@Override
	public boolean isCoerceUndefinedToFalse() {
		return coerceUndefinedToFalse;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getNotice()
	 */
	@Override
	public String getNotice() {
		return notice;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getCacheBust()
	 */
	@Override
	public String getCacheBust() {
		return cacheBust;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getRawConfig()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> getRawConfig() {
		// return a copy of the raw config so that it can't be
		// modified by the caller.
		JSONObject result = null;
		try {
			result = new JSONObject(rawConfig.toString());
		} catch (JSONException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
			throw new RuntimeException(e);
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getPathURIs()
	 */
	@Override
	public Map<String, URI> getPathURIs() {
		Map<String, URI> result = new LinkedHashMap<String, URI>();
		try {
			for (Entry<String, URI> entry : getPaths().entrySet()) {
				result.put(entry.getKey(), entry.getValue());
			}
			// Remove .js extension from any paths
			for (Entry<String, URI> entry : result.entrySet()) {
				URI value = entry.getValue();
				if (value.getPath().endsWith(".js")) { //$NON-NLS-1$
					entry.setValue(new URI(value.getPath().substring(0, value.getPath().length()-3)));
				}
				entry.setValue(value);
			}
		} catch (URISyntaxException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#getPackageURIs()
	 */
	@Override
	public Map<String, URI> getPackageURIs() {
		Map<String, URI> result = new LinkedHashMap<String, URI>();
		try {
			for (Entry<String, IPackage>entry : getPackages().entrySet()) {
				IPackage pkg = entry.getValue();
				result.put(pkg.getName(), pkg.getLocation());
				String main = pkg.getMain();
				URI uri = locateModuleResource(main);
				if (uri != null) {
					result.put(main, uri);
				} else {
					if (log.isLoggable(Level.WARNING)) {
						log.warning(MessageFormat.format(
								Messages.ConfigImpl_1, new Object[]{main}
						));
					}
				}
			}
			// Remove .js extension from any paths
			for (Entry<String, URI> entry : result.entrySet()) {
				URI value = entry.getValue();
				if (value.getPath().endsWith(".js")) { //$NON-NLS-1$
					entry.setValue(new URI(value.getPath().substring(0, value.getPath().length()-3)));
				}
				entry.setValue(value);
			}
		} catch (URISyntaxException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
		return result;
	}
	
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#locateModuleResource(java.lang.String)
	 */
	@Override
	public URI locateModuleResource(String mid) { 
		URI location = null;
		String remainder = null;
		
		// If the module is a package name, then resolve to the packag main id
		if (getPackages().containsKey(mid)) {
			mid = getPackages().get(mid).getMain();
		} 
		// Now see if mid matches exactly a path
		if (location == null && getPaths().containsKey(mid)) {
			location = getPaths().get(mid);
		}
		if (location == null) {
			// Still no match.  Iterate through the paths and packages looking
			// for an entry that matches part of the module id
			String prefix = ""; //$NON-NLS-1$
			for (Map.Entry<String, URI> entry : getPaths().entrySet()) {
				if (mid.startsWith(entry.getKey()) && entry.getKey().length() > prefix.length()) {
					if (entry.getKey().endsWith("/") || mid.charAt(entry.getKey().length()) == '/') { //$NON-NLS-1$
						prefix = entry.getKey();
						location = entry.getValue();
					}
				}
			}
			for (Map.Entry<String, IPackage> entry : getPackages().entrySet()) {
				if (mid.startsWith(entry.getKey()) && entry.getKey().length() > prefix.length()) {
					if (entry.getKey().endsWith("/") || mid.charAt(entry.getKey().length()) == '/') { //$NON-NLS-1$
						prefix = entry.getKey();
						location = entry.getValue().getLocation();
					}
				}
			}
			if (prefix.length() > 0) {
				// We found a partial match.  Set the remainder
				remainder = mid.substring(prefix.length());
				if (remainder.startsWith("/")) { //$NON-NLS-1$
					remainder = remainder.substring(1);
				}
			} else {
				// not match.  Return a URI relative to the config base URI
				location = getBase().resolve(mid);
			}
		}
		
		try {
			if (remainder != null) {
				// Resolve the URI location and remainder parts
				if (!location.getPath().endsWith("/")) { //$NON-NLS-1$
					location = new URI(location.getScheme(), location.getHost(), 
							           location.getPath() + "/", location.getFragment()); //$NON-NLS-1$
				}
				location = location.resolve(remainder);
			}
			
			// add .js extension if resource uri has no extension
			String path = location.getPath();
			int idx = path.lastIndexOf("/"); //$NON-NLS-1$
			String fname = (idx != -1) ? path.substring(idx+1) : path;
			if (!fname.contains(".")) { //$NON-NLS-1$
				location = new URI(location.getScheme(), location.getHost(), 
						path + ".js", location.getFragment()); //$NON-NLS-1$
			}
			
		} catch (URISyntaxException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		return location;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfig#resolveAlias(java.lang.String, com.ibm.jaggr.service.util.Features, java.util.Set)
	 */
	@Override
	public String resolve(
			String mid, 
			Features features, 
			Set<String> dependentFeatures,
			StringBuffer sb) {
		
		int idx = mid.indexOf("!"); //$NON-NLS-1$
		if (idx != -1 && HAS_PATTERN.matcher(mid.substring(0, idx)).find()) {
			Set<String> depFeatures = new HashSet<String>();
			HasNode hasNode = new HasNode(mid.substring(idx+1)); 
			String name = hasNode.evaluate(
					features, 
					depFeatures, 
					isCoerceUndefinedToFalse()
			);
			dependentFeatures.addAll(depFeatures);
			if (name != null) {
				if (name.length() > 0) {
					if (sb != null) {
						Map<String,Boolean> featureMap = new HashMap<String,Boolean>();
						for (String featureName : depFeatures) {
							featureMap.put(featureName, features.isFeature(featureName));
						}
						sb.append(", ").append(MessageFormat.format( //$NON-NLS-1$
								Messages.ConfigImpl_2,
								new Object[]{name + featureMap.toString()}
						));
					}
				}
				mid = name;
			} else {
				if (sb != null) {
					// determine the missing feature.  Should be the only one left in depFeatures
					// after removing the request features.
					depFeatures.removeAll(features.featureNames());
					sb.append(", ").append(MessageFormat.format( //$NON-NLS-1$
							Messages.ConfigImpl_4,
							new Object[]{depFeatures.toString()}
					));
				}
			}
		}
		String aliased = null;
		try {
			aliased = resolveAlias(mid, features, dependentFeatures, sb);
		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		if (aliased != null && aliased != mid) {
			if (sb != null) {
				sb.append(", ").append(MessageFormat.format( //$NON-NLS-1$
						Messages.ConfigImpl_6,
						new Object[]{mid}
				));
			}
			mid = aliased;
		}

		// check for package name and replace with the package's main module id
		IPackage pkg = packages.get(mid);
		if (pkg != null) {
			mid = pkg.getMain();
		}
		
		return mid;
	}

	/**
	 * Applies alias mappings to the specified name and returns the result
	 * 
	 * @param name
	 *            The module name to map
	 * @param features
	 *            Features that are defined in the request
	 * @param dependentFeatures
	 *            Output - Set of feature names that the returned value is
	 *            conditioned on. Used for cache management.
	 * @param sb
	 *            If not null, then a reference to a string buffer that can 
	 *            be used by the resolver to indicate debug/diagnostic information 
	 *            about the alias resolution.  For example, the resolver may
	 *            indicate that alias resolution was not performed due to
	 *            a missing required feature.
	 * 
	 * @return The module name with alias mappings applied, or {@code name} if there is
	 *         not mapping for this module name.
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws ClassNotFoundException
	 * 
	 * @see IAliasResolver
	 */
	protected String resolveAlias(
			String name, 
			Features features, 
			Set<String> dependentFeatures,
			StringBuffer sb) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {

		if (name == null || name.length() == 0) {
			return name;
		}
		String result = name;
		if (getAliases() != null) {
			for (IAlias alias : getAliases()) {
				String nameToMatch = (result != null) ? result : name;
				Object pattern = alias.getPattern();
				if (pattern instanceof String) {
					if (alias.getPattern().equals(nameToMatch)) {
						result = (String)alias.getReplacement();
					}
				} else {
					Matcher m = ((Pattern)pattern).matcher(nameToMatch);
					if (m.find()) {
						m.reset();
						Object replacement = alias.getReplacement();
						if (replacement instanceof String) {
							result = m.replaceAll((String)replacement);
						} else if (replacement instanceof Script){
							// replacement is a javascript function.  
							Context cx = Context.enter();
							try {
							    Scriptable threadScope = cx.newObject(sharedScope);
							    threadScope.setPrototype(sharedScope);
							    threadScope.setParentScope(null);
							    HasFunction hasFn = newHasFunction(threadScope, features);
								ScriptableObject.putProperty(threadScope, "has", hasFn); //$NON-NLS-1$
								((Script)replacement).exec(cx, threadScope);
								Function fn = (Function)ScriptableObject.getProperty(threadScope, "fn"); //$NON-NLS-1$
								StringBuffer sbResult = new StringBuffer();
								while (m.find()) {
									ArrayList<Object> groups = new ArrayList<Object>(m.groupCount()+1);
									groups.add(m.group(0));
									for (int i = 0; i < m.groupCount(); i++) {
										groups.add(m.group(i+1));
									}
									String r = (String)fn.call(cx, threadScope, null, groups.toArray()).toString();
									m.appendReplacement(sbResult, r);
									dependentFeatures.addAll(hasFn.getDependentFeatures());
								}
								m.appendTail(sbResult);
								result = sbResult.toString();
							} finally {
								Context.exit();
							}
						}
					}
				}
			}
		}
		return result;
	}
	
	/**
	 * Initializes and returns the URI to the server-side config JSON
	 * 
	 * @return The config URI
	 * @throws URISyntaxException
	 * @throws FileNotFoundException
	 */
	protected URI loadConfigUri() throws URISyntaxException, FileNotFoundException {
		URI configUri = null;
		Collection<String> configNames = getAggregator().getInitParams().getValues(InitParams.CONFIG_INITPARAM);
		if (configNames.size() != 1) {
			throw new IllegalArgumentException(InitParams.CONFIG_INITPARAM);
		}
		String configName = configNames.iterator().next();
		configUri = new URI(configName);
		if (!configUri.isAbsolute()) {
			URL configUrl = getAggregator().getBundleContext().getBundle().getResource(configName);
			if (configUrl == null) {
				throw new FileNotFoundException(configName);
			}
			configUri = PathUtil.url2uri(configUrl);
		}
		return configUri;
	}
	
	/**
	 * Initializes and returns the base URI specified by the "baseUrl" property
	 * in the server-side config JSON
	 * 
	 * @param cfg
	 *            the parsed config JSON
	 * @return the base URI
	 * @throws URISyntaxException
	 */
	protected URI loadBaseURI(Map<String, Object> cfg) throws URISyntaxException  {
		String baseUrlStr = (String)cfg.get(BASEURL_CONFIGPARAM);
		URI base = null;
		if (baseUrlStr == null) {
			base = getConfigUri().resolve("."); //$NON-NLS-1$
		} else {
			if (!baseUrlStr.endsWith("/")) //$NON-NLS-1$
				baseUrlStr += "/"; //$NON-NLS-1$
			baseUrlStr = substituteProps(baseUrlStr);
			base = new URI(baseUrlStr).normalize();
			if (!base.isAbsolute()) {
				base = getConfigUri().resolve(base);
			}
		}
		// Convert to resource based uri
		return getAggregator().newResource(base).getURI();
	}

	protected Object toJSONType(Context cx, Object value) throws JSONException {
		Object result = value;
		if (value instanceof ScriptableObject) {
			ScriptableObject scriptable = (ScriptableObject)value;
			String type = scriptable.getClassName();
			if ("RegExp".equals(type)) {  //$NON-NLS-1$
				JSONObject json = new JSONObject();
				json.put(JSON_PROXY_EYECATCHER, JSON_PROXY_REGEXP);
				json.put(JSON_PROXY_REGEXP, Context.toString(value));
				result = json;
			} else if ("Function".equals(type)) { //$NON-NLS-1$
				JSONObject json = new JSONObject();
				json.put(JSON_PROXY_EYECATCHER, JSON_PROXY_FUNCTION);
				json.put(JSON_PROXY_FUNCTION, Context.toString(value));
				result = json;
			} else if (value instanceof NativeObject) {
				result = new JSONObject();
				addJSONObject(cx, (JSONObject)result, (NativeObject)value);
			} else if (value instanceof NativeArray) {
				result = new JSONArray();
				addJSONArray(cx, (JSONArray)result, (NativeArray)value);
			} else {
				result = Context.toString(value);
			}
		} else if (value instanceof NativeJavaObject) {
			return ((NativeJavaObject)value).unwrap();
			
		}
		return result;
	}
	protected void addJSONObject(Context cx, JSONObject jsonObj, NativeObject map) throws JSONException {
		for (Object key : map.getAllIds()) {
			Object value = map.get((String)key, map);
			jsonObj.put(key, toJSONType(cx, value));
		}
	}
	
	protected void addJSONArray(Context cx, JSONArray jsonAry, NativeArray ary) throws JSONException {
		for (int i = 0; i < ary.getLength(); i++) {
			jsonAry.add(toJSONType(cx, ary.get(i, ary)));
		}
	}
	
	protected Map<String, Object> loadConfigJSON() throws MalformedURLException, IOException {
		Reader reader = new InputStreamReader(getConfigUri().toURL().openStream());
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		return loadConfigJSON(writer.toString());
	}
	/**
	 * Loads the config JSON and returns the parsed config in a Map
	 * 
	 * @return the parsed config in a properties map
	 * @throws IOException
	 * @throws JSONException 
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, Object> loadConfigJSON(String configJson) throws IOException {
		Map<String, Object> result = null;
		Context cx = Context.enter();
		try {
			sharedScope = cx.initStandardObjects();

			// set up options object
			IOptions options = aggregator.getOptions();
			if (options != null) {
				optionsUpdated(options, 1);
			}
			// set up init params object
			InitParams initParams = aggregator.getInitParams();
			if (initParams != null) {
			    Scriptable jsInitParams = cx.newObject(sharedScope);
			    Collection<String> names = initParams.getNames();
			    for (String name : names) {
			    	Collection<String> values = initParams.getValues(name);
			    	Object value = Context.javaToJS(values.toArray(new String[values.size()]), sharedScope);
				    ScriptableObject.putProperty(jsInitParams, name, value);
			    }
				ScriptableObject.putProperty(sharedScope, "initParams", jsInitParams); //$NON-NLS-1$
			}
			// set up console object
			Console console = newConsole();
			Object jsConsole = Context.javaToJS(console, sharedScope);
			ScriptableObject.putProperty(sharedScope, "console", jsConsole); //$NON-NLS-1$
			
			cx.evaluateString(sharedScope, "var config = " + configJson, getConfigUri().toString(), 1, null); //$NON-NLS-1$
			Object config = sharedScope.get("config", sharedScope); //$NON-NLS-1$
			if (config == Scriptable.NOT_FOUND) {
				System.out.println("config is not defined."); //$NON-NLS-1$
			} else {
				result = new JSONObject();
				addJSONObject(cx, (JSONObject)result, (NativeObject)config);
			}
		} catch (JSONException e) {
			throw new IOException(e);
		} finally {
			Context.exit();
		}
		return result;
	}
	
	/**
	 * Initializes and returns the map of packages based on the information in
	 * the server-side config JSON
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return the package map
	 * @throws URISyntaxException
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, IPackage> loadPackages(Map<String, Object> cfg) throws URISyntaxException {
		List<Object> obj = (List<Object>)cfg.get(PACKAGES_CONFIGPARAM);
		Map<String, IPackage> packages = new HashMap<String, IPackage>();
		if (obj != null) {
			for (Object pkgobj : obj) {
				IPackage p = newPackage(pkgobj); 
				if (!packages.containsKey(p.getName())) {
					packages.put(p.getName(), p);
				}
			}
		}
		return packages;
	}
	
	/**
	 * Initializes and returns the map of paths based on the information in the
	 * server-side config JSON
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return the map of path-name/path-URI pairs
	 * @throws URISyntaxException
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, URI> loadPaths(Map<String, Object> cfg) throws URISyntaxException {
		Map<String, Object> pathlocs = (Map<String, Object>)cfg.get(PATHS_CONFIGPARAM);
		Map<String, URI> paths = new HashMap<String, URI>();
		if (pathlocs != null) {
			for (Object key : pathlocs.keySet()) {
				String name = (String)key;
				
				if (!paths.containsKey(name)) {
					String location = substituteProps((String)pathlocs.get(name).toString());
					URI uri = new URI(location);
					if (!uri.isAbsolute()) {
						uri = getBase().resolve(uri);
					} else {
						uri = uri.normalize();
					}
					paths.put(name, uri);
				}
			}
		}
		return paths;
	}
	
	/**
	 * Initializes and returns the list of aliases defined in the server-side
	 * config JSON
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return the list of aliases
	 * @throws IOException 
	 */
	@SuppressWarnings("unchecked")
	protected List<IAlias> loadAliases(Map<String, Object> cfg) throws IOException {
		List<Object> aliasList = (List<Object>)cfg.get(ALIASES_CONFIGPARAM);
		List<IAlias> aliases = new LinkedList<IAlias>();
		if (aliasList != null) {
			for (Object entry : aliasList) {
				if (entry instanceof List<?> && ((List<?>)entry).size() == 2) {
					List<?> vec = (List<?>)entry;
					Object pattern = vec.get(0);
					if (pattern instanceof Map) {
						Map<String, String> patMap = (Map<String, String>)pattern;
						if (JSON_PROXY_REGEXP.equals(patMap.get(JSON_PROXY_EYECATCHER))) {
							String regexlit = patMap.get(JSON_PROXY_REGEXP);
							String regex = regexlit.substring(1, regexlit.lastIndexOf("/")); //$NON-NLS-1$
							String flags = regexlit.substring(regexlit.lastIndexOf("/")+1); //$NON-NLS-1$
							int options = 0;
							if (flags.contains("i")) { //$NON-NLS-1$
								options |= Pattern.CASE_INSENSITIVE;
							}
							pattern = Pattern.compile(regex, options);
						}
					}
					Object replacement = vec.get(1);
					if (replacement instanceof Map) {
						Map<String, String> repMap = (Map<String, String>)replacement;
						if (JSON_PROXY_FUNCTION.equals(repMap.get(JSON_PROXY_EYECATCHER))) {
							Context cx = Context.enter();
							try {
								replacement = cx.compileString("fn=" + repMap.get(JSON_PROXY_FUNCTION), "", 1, null); //$NON-NLS-1$ //$NON-NLS-2$
							} finally {
								Context.exit();
							}
						}
					}
					aliases.add(newAlias(pattern, replacement));
				}
			}
		}
		return aliases;
	}
	
	/**
	 * Initializes and returns the list of module dependencies specified in the
	 * server-side config JSON.
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return the list of module dependencies
	 */
	protected List<String> loadDeps(Map<String, Object> cfg) {
		@SuppressWarnings("unchecked")
		List<Object> depsList = (List<Object>)cfg.get(DEPS_CONFIGPARAM);
		List<String> deps = new LinkedList<String>();
		if (depsList != null) {
			for (Object entry : depsList) {
				if (entry instanceof String) {
					deps.add((String)entry);
				} else {
					throw new IllegalArgumentException(entry.toString());
				}
			}
		}
		return deps;
	}

	/**
	 * Initializes and returns the expires time from the server-side
	 * config JSON
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return the expires time
	 */
	protected int loadExpires(Map<String, Object> cfg) {
		int expires = 0;
		Object oExpires = cfg.get(EXPIRES_CONFIGPARAM);
		if (oExpires != null) {
			if (oExpires instanceof Number) {
				expires = ((Number)oExpires).intValue();
			} else if (oExpires instanceof String) {
				try {
					expires = Integer.parseInt((String)oExpires);
				} catch (NumberFormatException ignore) {
					throw new IllegalArgumentException(EXPIRES_CONFIGPARAM+"="+oExpires); //$NON-NLS-1$
				}
			}
		}
		return expires;
	}

	/**
	 * Initializes and returns the flag indicating if files and directories
	 * located under the base directory should be scanned for javascript files
	 * when building the module dependency mappings.  The default is false.
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return true if files and folder under the base directory should be
	 *         scanned
	 */
	protected boolean loadDepsIncludeBaseUrl(Map<String, Object> cfg) {
		return TypeUtil.asBoolean(cfg.get(
				DEPSINCLUDEBASEURL_CONFIGPARAM), false);
	}

	/**
	 * Initializes and returns the flag indicating if features not specified
	 * in the feature set from the request should be treated as false when 
	 * evaluating has conditionals in javascript code.  The default is false.
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return True if un-specified features should be treated as false.
	 */
	protected boolean loadCoerceUndefinedToFalse(Map<String, Object> cfg) {
		
		return TypeUtil.asBoolean(cfg.get(
			COERCEUNDEFINEDTOFALSE_CONFIGPARAM), false);
	}
	
	/**
	 * Initializes and returns the notice string specified by the {@code notice}
	 * property in the server-side config JSON.
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return the {@code notice} property value
	 * @throws URISyntaxException 
	 */
	protected String loadNotice(Map<String, Object> cfg) throws IOException, URISyntaxException {
		String notice = null;
		String noticeUriStr = (String)cfg.get(NOTICE_CONFIGPARAM);
		if (noticeUriStr != null) {
			noticeUriStr = substituteProps(noticeUriStr);
			URI noticeUri = new URI(noticeUriStr).normalize();
			if (!noticeUri.isAbsolute()) {
				noticeUri = getConfigUri().resolve(noticeUri);
			}
			InputStream in = null;
			try {
				// Try using newResource so we can support namedbundleresource URIs
				in = getAggregator().newResource(noticeUri).getURI().toURL().openStream();
			} catch (UnsupportedOperationException e) {
				// No resource provider.  Try using raw URI
				in = noticeUri.toURL().openStream();
			}
			StringWriter out = new StringWriter();
			CopyUtil.copy(in, out);
			notice = out.toString();
		}
		return notice;
	}
	
	/**
	 * Initializes and returns the string specified by the {@code cacheBust}
	 * property in the server-side config JSON.
	 * 
	 * @param cfg
	 *            The parsed config JSON as a properties map
	 * @return the {@code cacheBust} property value
	 */
	protected String loadCacheBust(Map<String, Object> cfg) {
		return (String)cfg.get(CACHEBUST_CONFIGPARAM);
	}
	
	/**
	 * Calls the registered config modifiers to give them an opportunity to
	 * modify the raw config before config properties are evaluated.
	 * 
	 * @param rawConfig
	 *            A map of the top level properties in the config JSON. Lower
	 *            level javascript arrays are represented as
	 *            {@code List<Object>} and lower level javascript objects
	 *            are represented as {@code Map<String, Object>}.
	 */
	protected void callConfigModifiers(Map<String, Object> rawConfig) {
		ServiceReference[] refs = null;
		BundleContext bundleContext = getAggregator().getBundleContext();
		try {
			refs = bundleContext
					.getServiceReferences(IConfigModifier.class.getName(),
							              "(name="+getAggregator().getName()+")" //$NON-NLS-1$ //$NON-NLS-2$
					);
		} catch (InvalidSyntaxException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		if (refs != null) {
			for (ServiceReference ref : refs) {
				IConfigModifier modifier = 
					(IConfigModifier)bundleContext.getService(ref);
				if (modifier != null) {
					try {
						modifier.modifyConfig(getAggregator(), rawConfig);
					} catch (Exception e) {
						if (log.isLoggable(Level.SEVERE)) {
							log.log(Level.SEVERE, e.getMessage(), e);
						}
					} finally {
						bundleContext.ungetService(ref);
					}
				}
			}
		}
	}

	/**
	 * Substitues patterns of the form ${system.property} with the value of 
	 * the specified system property.  Returns the modified string.
	 * 
	 * @param str The input string
	 * @return The modified string
	 */
	public String substituteProps(String str) {
		StringBuffer buf = new StringBuffer();
		final Pattern pattern = Pattern.compile("\\$\\{([^}]*)\\}"); //$NON-NLS-1$
		Matcher matcher = pattern.matcher(str);
	    while ( matcher.find() ) {
	    	String propName = matcher.group(1);
	    	String propValue = System.getProperty(propName);
	    	if (propValue != null)
	    		matcher.appendReplacement(buf, propValue);
	    	else
	    		matcher.appendReplacement(buf, "\\${"+propName+"}"); //$NON-NLS-1$ //$NON-NLS-2$
	    }
	    matcher.appendTail(buf);
	    return buf.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return rawConfig.toString();
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.options.IOptionsListener#optionsUpdated(com.ibm.jaggr.service.options.IOptions, long)
	 */
	@Override
	public void optionsUpdated(IOptions options, long sequence) {
		Context cx = Context.enter();
		try {
		    Scriptable jsOptions = cx.newObject(sharedScope);
		    Map<String, String> optionsMap = options.getOptionsMap();
		    for (Map.Entry<String, String> entry : optionsMap.entrySet()) {
		    	Object value = Context.javaToJS(entry.getValue(), sharedScope);
			    ScriptableObject.putProperty(jsOptions, entry.getKey(), value);
		    }
			ScriptableObject.putProperty(sharedScope, "options", jsOptions); //$NON-NLS-1$
		} finally {
			Context.exit();
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IShutdownListener#shutdown(com.ibm.jaggr.service.IAggregator)
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		for (ServiceRegistration reg : serviceRegs) {
			reg.unregister();
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void registerServices() {
		BundleContext bundleContext = aggregator.getBundleContext();
		if (bundleContext != null) {
	        // Register listeners
			Dictionary dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			serviceRegs.add(bundleContext.registerService(
					IShutdownListener.class.getName(), 
					this, 
					dict
			));
			dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$
			serviceRegs.add(bundleContext.registerService(
					IOptionsListener.class.getName(), 
					this, 
					dict
			));
		}
	}

	protected HasFunction newHasFunction(Scriptable scriptable, Features features) {
		return new HasFunction(scriptable, features);
	}
	
	protected Console newConsole() {
		return new Console();
	}

	protected IPackage newPackage(Object obj) throws URISyntaxException {
		return new Package(obj);
	}
	
	protected IAlias newAlias(/* String | Pattern */Object pattern, /* String | Scriptable */Object replacement) {
		return new Alias(pattern, replacement);
	}
	
	protected class Package implements IPackage {

		private final String name;
		private final URI location;
		private final String main;

		public Package(Object obj) throws URISyntaxException {
			// Defaults
			String locpath = null;
			String main = null;
			String name = null;
			if (obj instanceof Map<?, ?>) {
				Map<?, ?> data = (Map<?, ?>)obj;
				name = (String)data.get(PKGNAME_CONFIGPARAM);
				locpath = substituteProps((String) data.get(PKGLOCATION_CONFIGPARAM));
				if (data.containsKey(PKGMAIN_CONFIGPARAM))
					main = substituteProps((String)data.get(PKGMAIN_CONFIGPARAM));
			} else if (obj instanceof String) {
				name = locpath = (String)obj;
			} else {
				throw new IllegalArgumentException(obj.toString());
			}
			this.name = name;
			
			if (!locpath.endsWith("/"))  //$NON-NLS-1$
				locpath += "/"; //$NON-NLS-1$
			
			URI location = new URI(locpath);
			if (!location.isAbsolute()) {
				location = getBase().resolve(location);
			} else {
				location = location.normalize();
			}
			this.location = location;
			
			if (main == null) {
				main = PKGMAIN_DEFAULT;
			}
			if (main.endsWith(".js")) { //$NON-NLS-1$
				main = main.substring(0, main.length()-3);
			}
			// make sure that main doesn't specify a URI
			if (new URI(main).isAbsolute()) {
				throw new IllegalArgumentException(main);
			}
			this.main = PathUtil.normalizePaths(name, new String[]{main})[0];
			
		}
		
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.config.IConfig.IPackage#getName()
		 */
		@Override
		public String getName() {
			return name;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.config.IConfig.IPackage#getLocation()
		 */
		@Override
		public URI getLocation() {
			return location;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.config.IConfig.IPackage#getMain()
		 */
		@Override
		public String getMain() {
			return main;
		}
	}
	
	protected static class Alias implements IAlias {

		private final Object pattern;
		private final Object replacement;
		
		public Alias(/* String | Pattern */Object pattern, /* String | Scriptable */Object replacement) {
			// validate arguments
			if (!(pattern instanceof String) && !(pattern instanceof Pattern)) {
				throw new IllegalArgumentException(pattern.toString());
			}
			if (!(replacement instanceof String) && !(replacement instanceof Script)) {
				throw new IllegalArgumentException(replacement.toString());
			}
			// replacement can be a Script only if pattern is a regular expression
			if ((pattern instanceof String) && !(replacement instanceof String)) {
				throw new IllegalArgumentException(replacement.toString());
			}
			this.pattern = pattern;
			this.replacement = replacement;
		}

		
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.config.IConfig.IAlias#getPattern()
		 */
		@Override
		public Object getPattern() {
			return pattern;
		}
		
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.config.IConfig.IAlias#getReplacement()
		 */
		@Override
		public Object getReplacement() {
			return replacement;
		}
		
	}
	
	/**
	 * This class implements the JavaScript has function object used by the Rhino interpreter when
	 * evaluating regular expressions in config aliases.
	 * <p>
	 * In addition to providing the has function, this class also keeps track of which features
	 * has is called for by the JavaScript in {@code dependentFeatures}
	 */
	static private class HasFunction extends FunctionObject {
		private static final long serialVersionUID = -399399681025813075L;
		private final Features features;
		private final Set<String> dependentFeatures;
		
		static Method hasMethod;
		
		static {
			try {
				hasMethod = HasFunction.class.getMethod("has", Context.class, Scriptable.class, Object[].class, Function.class); //$NON-NLS-1$
			} catch (SecurityException e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			} catch (NoSuchMethodException e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		
		HasFunction(Scriptable scope, Features features) {
			super("has", hasMethod, scope); //$NON-NLS-1$
			this.features = features;
			this.dependentFeatures = new HashSet<String>();
		}
		@SuppressWarnings("unused")
		public static Object has(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
			Object result = Context.getUndefinedValue();
			HasFunction javaThis = (HasFunction)funObj;
			String feature = (String)args[0];
			javaThis.dependentFeatures.add(feature);
			if (javaThis.features.contains(feature)) {
				result = Boolean.valueOf(javaThis.features.isFeature(feature));
			}
			return result;
		}
		
		public Set<String> getDependentFeatures() {
			return dependentFeatures;
		}
	}

	/**
	 * Console logger for javascript runtime.
	 */
	static protected class Console {
		private static final Logger console = Logger.getLogger(Console.class.getName());
		public void log(String msg) {console.info(msg);}
		public void info(String msg) {console.info(msg);}
		public void warn(String msg) {console.warning(msg);}
		public void error(String msg) {console.severe(msg);}
	}
}
