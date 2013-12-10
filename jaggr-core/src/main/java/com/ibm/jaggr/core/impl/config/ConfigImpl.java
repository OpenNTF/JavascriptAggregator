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

package com.ibm.jaggr.core.impl.config;

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
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
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

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigModifier;
import com.ibm.jaggr.core.impl.PlatformAggregatorFactory;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.HasNode;
import com.ibm.jaggr.core.util.PathUtil;
import com.ibm.jaggr.core.util.TypeUtil;

public class ConfigImpl implements IConfig, IShutdownListener, IOptionsListener {
	private static final Logger log = Logger.getLogger(ConfigImpl.class.getName());

	/** regular expression for detecting if a plugin name is the has! plugin */
	static final Pattern HAS_PATTERN = Pattern.compile("(^|\\/)has$"); //$NON-NLS-1$
	
	private final IAggregator aggregator;
	private final Scriptable rawConfig;
	private String strConfig;
	private final long lastModified;
	private final URI configUri;

	private Location base;
	private Map<String, IPackage> packages;
	private Map<String, Location> paths;
	private List<IAlias> aliases;
	private List<String> deps;
	private boolean depsIncludeBaseUrl;
	private boolean coerceUndefinedToFalse;
	private int expires;
	private String notice;
	private String cacheBust;
	private Set<String> textPluginDelegators;
	private Set<String> jsPluginDelegators;
	private Scriptable sharedScope;
	
	protected List<Object> serviceRegs = new LinkedList<Object>();
	
	private static class ConfigContextFactory extends ContextFactory {
		@Override
		protected boolean hasFeature(Context context, int feature) {
			if (feature == Context.FEATURE_DYNAMIC_SCOPE || feature == Context.FEATURE_TO_STRING_AS_SOURCE) {
				return true;
			}
			return super.hasFeature(context, feature);
		}
	}
	
	static {
		ContextFactory.initGlobal(new ConfigContextFactory());
	}
	
	public ConfigImpl(IAggregator aggregator) throws IOException {
		this.aggregator = aggregator;
		Context.enter();
	
		try { 
			configUri = loadConfigUri();
			// Try to convert to an IResource in case the URI specifies
			//  an IResource supported scheme like 'namedbundleresource'.
			URI uri;
			try {
				uri = aggregator.newResource(configUri).getURI();
			} catch (UnsupportedOperationException e) {
				// Not fatal.  Just use the configUri as is
				uri = configUri;
			}
			URLConnection connection = uri.toURL().openConnection();
			lastModified = connection.getLastModified();

			rawConfig = loadConfig(connection.getInputStream());
			
			// Call config modifiers to allow them to update the config
			// before we parse it.
			callConfigModifiers(rawConfig);
			
			// Seal the config object and the shared scope to prevent changes
			((ScriptableObject)rawConfig).sealObject();
			((ScriptableObject)sharedScope).sealObject();
			
			init();
		} catch (URISyntaxException e) {
			throw new IOException(e);
		} finally {
			Context.exit();
		}
	}

	public ConfigImpl(IAggregator aggregator, URI configUri, String configScript) throws IOException {
		Context.enter();
		try {
			lastModified = configUri != null ? configUri.toURL().openConnection().getLastModified() : -1;
			this.configUri = configUri;
			this.aggregator = aggregator;
			this.rawConfig = loadConfig(configScript);
			init();
		} finally {
			Context.exit();
		}
	}
	
	public ConfigImpl(IAggregator aggregator, URI configUri, Scriptable rawConfig) throws IOException {
		Context.enter();
		try {
			lastModified = configUri != null ? configUri.toURL().openConnection().getLastModified() : -1;
			this.configUri = configUri;
			this.aggregator = aggregator;
			this.rawConfig = rawConfig;
			init();
		} finally {
			Context.exit();
		}
	}
	
	protected void init() throws IOException {
		try { 
			registerServices();
			strConfig = Context.toString(rawConfig);
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
			textPluginDelegators = loadTextPluginDelegators(rawConfig);
			jsPluginDelegators = loadJsPluginDelegators(rawConfig);
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#lastModified()
	 */
	@Override
	public long lastModified() {
		return lastModified;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getConfigUri()
	 */
	@Override
	public URI getConfigUri() {
		return configUri;
	}
	
	protected IAggregator getAggregator() {
		return aggregator;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getBase()
	 */
	@Override
	public Location getBase() {
		return base;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getPackages()
	 */
	@Override
	public Map<String, IPackage> getPackages() {
		return Collections.unmodifiableMap(packages);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getPaths()
	 */
	@Override
	public Map<String, Location> getPaths() {
		return Collections.unmodifiableMap(paths);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getAliases()
	 */
	@Override
	public List<IAlias> getAliases() {
		return Collections.unmodifiableList(aliases);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#isDepsIncludeBaseUrl()
	 */
	@Override
	public boolean isDepsIncludeBaseUrl() {
		return depsIncludeBaseUrl;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getExpires()
	 */
	@Override
	public int getExpires() {
		return expires;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getDeps()
	 */
	@Override
	public List<String> getDeps() {
		return deps;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#isCoerceUndefinedToFalse()
	 */
	@Override
	public boolean isCoerceUndefinedToFalse() {
		return coerceUndefinedToFalse;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getNotice()
	 */
	@Override
	public String getNotice() {
		return notice;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getCacheBust()
	 */
	@Override
	public String getCacheBust() {
		return cacheBust;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getTextPluginDelegators()
	 */
	@Override
	public Set<String> getTextPluginDelegators() {
		return textPluginDelegators;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getJsPluginDelegators()
	 */
	@Override
	public Set<String> getJsPluginDelegators() {
		return jsPluginDelegators;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getRawConfig()
	 */
	@Override
	public Scriptable getRawConfig() {
		return rawConfig;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#getPackageURIs()
	 */
	@Override
	public Map<String, Location> getPackageLocations() {
		Map<String, Location> result = new LinkedHashMap<String, Location>();
		for (Entry<String, IPackage>entry : getPackages().entrySet()) {
			IPackage pkg = entry.getValue();
			result.put(pkg.getName(), pkg.getLocation());
		}
		return result;
	}
	
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfig#locateModuleResource(java.lang.String)
	 */
	@Override
	public URI locateModuleResource(String mid) { 
		Location location = null;
		String remainder = null;
		
		// Now see if mid matches exactly a path
		if (getPaths().containsKey(mid)) {
			location = getPaths().get(mid);
		}
		// If the module is a package name, then resolve to the packag main id
		if (location == null && getPackages().containsKey(mid)) {
			mid = getPackages().get(mid).getMain();
		} 
		if (location == null) {
			// Still no match.  Iterate through the paths and packages looking
			// for an entry that matches part of the module id
			String prefix = ""; //$NON-NLS-1$
			for (Map.Entry<String, Location> entry : getPaths().entrySet()) {
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
				// Check for illegal relative path. Throws exception if an
				// attempt is made to use relitive path components to go 
				// outside the path defined by the entry.
				PathUtil.normalizePaths(prefix, new String[]{remainder});
			} else {
				// Check for illegal relative path. Throws exception if an
				// attempt is made to use relitive path components to go 
				// outside the path defined by the base url.
				PathUtil.normalizePaths("", new String[]{mid});  //$NON-NLS-1$
				// not match.  Return a URI relative to the config base URI
				Location base = getBase();
				location = new Location(
					base.getPrimary().resolve(mid),
					base.getOverride() == null ? null : base.getOverride().resolve(mid)
				);
			}
		}
		URI result = null;
		try {
			URI override = toResourceUri(location.getOverride(), remainder);
			result = (override != null && aggregator.newResource(override).exists()) ?
				override : toResourceUri(location.getPrimary(), remainder);
		} catch (URISyntaxException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		return result;
	}
	
	protected URI toResourceUri(URI uri, String remainder) throws URISyntaxException {
		if (uri == null) {
			return null;
		}
		if (remainder != null) {
			// Resolve the URI location and remainder parts
			if (!uri.getPath().endsWith("/")) { //$NON-NLS-1$
				uri = new URI(uri.getScheme(), uri.getAuthority(),
						           uri.getPath() + "/", uri.getQuery(), uri.getFragment()); //$NON-NLS-1$
			}
			uri = uri.resolve(remainder);
		}
		
		// add .js extension if resource uri has no extension
		String path = uri.getPath();
		int idx = path.lastIndexOf("/"); //$NON-NLS-1$
		String fname = (idx != -1) ? path.substring(idx+1) : path;
		if (!fname.contains(".")) { //$NON-NLS-1$
			uri = new URI(uri.getScheme(), uri.getAuthority(), 
					path + ".js", uri.getQuery(), uri.getFragment()); //$NON-NLS-1$
		}
		return uri;
		
	}

	/* (non-Javadoc)
	 * @see com.ibm.servlets.amd.aggregator.config.IConfig#resolve(java.lang.String, com.ibm.servlets.amd.aggregator.util.Features, java.util.Set, java.lang.StringBuffer)
	 */
	@Override
	public String resolve(
			String mid, 
			Features features, 
			Set<String> dependentFeatures,
			StringBuffer sb,
			boolean resolveAliases) {
		
		// Resolve has plugin first
		mid = resolveHasPlugin(mid, features, dependentFeatures, sb);
		
		if (resolveAliases) {
			String aliased = null;
			try {
				aliased = resolveAliases(mid, features, dependentFeatures, sb);
			} catch (Exception e) {
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
			}
			if (!mid.equals(aliased)) {
				if (sb != null) {
					sb.append(", ").append(MessageFormat.format( //$NON-NLS-1$
							Messages.ConfigImpl_6,
							new Object[]{mid}
					));
				}
				// If alias resolution introduced a has plugin, then try to resolve it
				int idx = mid.indexOf("!"); //$NON-NLS-1$
				if (idx == -1 || !HAS_PATTERN.matcher(mid.substring(0, idx)).find()) {
					mid = resolveHasPlugin(aliased, features, dependentFeatures, sb);
				} else {
					mid = aliased;
				}
			}
		}
		// check for package name and replace with the package's main module id
		IPackage pkg = packages.get(mid);
		if (pkg != null) {
			mid = pkg.getMain();
		}
		return mid;
	}

	/**
	 * Resolves has! loader plugin based on the specified feature set
	 * 
	 * @param mid
	 *            The module id to resolve.  May specify plugins
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
	 * @return The module id with has! loader plugin resolved, or {@code mid} if the 
	 *         features specified by the loader plugin are not defined.
	 */
	protected String resolveHasPlugin(
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
		return mid;
	}

	private static final Pattern plugins1 = Pattern.compile("[!?:]"); //$NON-NLS-1$
	private static final Pattern plugins2 = Pattern.compile("[^!?:]*"); //$NON-NLS-1$
	
	/**
	 * Applies alias mappings to the specified name and returns the result
	 * 
	 * @param name
	 *            The module name to map.  May specify plugins
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
	protected String resolveAliases(
			String name, 
			Features features, 
			Set<String> dependentFeatures,
			StringBuffer sb) throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {

		if (name == null || name.length() == 0) {
			return name;
		}
		
		String result = name;
		if (getAliases() != null) {
			if (plugins1.matcher(name).find()) {
				// If the module id specifies a plugin, then process each part individually
				Matcher m = plugins2.matcher(name);
				StringBuffer sbResult = new StringBuffer();
				while (m.find()) {
					String replacement = resolveAliases(m.group(0), features, dependentFeatures, sb);
					m.appendReplacement(sbResult, replacement);
				}
				m.appendTail(sbResult);
				return sbResult.toString();
			}
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
						} else if (replacement instanceof Function){
							// replacement is a javascript function.  
							Context cx = Context.enter();
							try {
							    Scriptable threadScope = cx.newObject(sharedScope);
							    threadScope.setPrototype(sharedScope);
							    threadScope.setParentScope(null);
							    HasFunction hasFn = newHasFunction(threadScope, features);
								ScriptableObject.putProperty(threadScope, "has", hasFn); //$NON-NLS-1$
								StringBuffer sbResult = new StringBuffer();
								while (m.find()) {
									ArrayList<Object> groups = new ArrayList<Object>(m.groupCount()+1);
									groups.add(m.group(0));
									for (int i = 0; i < m.groupCount(); i++) {
										groups.add(m.group(i+1));
									}
									String r = (String)((Function)replacement).call(cx, threadScope, null, groups.toArray()).toString();
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
	 * Initializes and returns the URI to the server-side config JavaScript
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
			configUri = PlatformAggregatorFactory.getPlatformAggregator().getConfigURL(configName);
			if (!getAggregator().newResource(configUri).exists()) {
				throw new FileNotFoundException(configName);
			}
		}
		return configUri;
	}
	
	/**
	 * Initializes and returns the base URI specified by the "baseUrl" property
	 * in the server-side config JavaScript
	 * 
	 * @param cfg
	 *            the parsed config JavaScript
	 * @return the base URI
	 * @throws URISyntaxException
	 */
	protected Location loadBaseURI(Scriptable cfg) throws URISyntaxException  {
		Object baseObj = cfg.get(BASEURL_CONFIGPARAM, cfg);
		Location result;
		if (baseObj == Scriptable.NOT_FOUND) {
			result = new Location(getConfigUri().resolve(".")); //$NON-NLS-1$
		} else {
			Location loc = loadLocation(baseObj, true);
			Location configLoc = new Location(getConfigUri(), loc.getOverride() != null ? getConfigUri() : null);
			result = configLoc.resolve(loc);
		}
		return result;
	}
	
	protected Scriptable loadConfig(InputStream in) throws MalformedURLException, IOException {
		Reader reader = new InputStreamReader(in);
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);		// closes the streams
		return loadConfig(writer.toString());
	}
	/**
	 * Add any processing logic to the config file like replacing any place holders from a 
	 * properties file.
	 * 
	 * @return the processed config script
	 */
	
	protected String processConfig(String configScript){
		return configScript;
	}
	
	/**
	 * Loads the config JavaScript and returns the parsed config in a Map
	 * 
	 * @return the parsed config in a properties map
	 * @throws IOException
	 */
	protected Scriptable loadConfig(String configScript) throws IOException {
		configScript = processConfig(configScript);
		Context cx = Context.enter();
		Scriptable config;
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

			// set up bundle manifest headers property
			if (PlatformAggregatorFactory.getPlatformAggregator() != null) {
				if(PlatformAggregatorFactory.getPlatformAggregator().getHeaders() != null){				
					Dictionary<String, String> headers = (Dictionary<String, String>)(PlatformAggregatorFactory.getPlatformAggregator().getHeaders());
				    Scriptable jsHeaders = cx.newObject(sharedScope);
					Enumeration<String> keys = headers.keys();
					while (keys.hasMoreElements()) {
						String key = keys.nextElement();
				    	Object value = Context.javaToJS(headers.get(key), sharedScope);
					    ScriptableObject.putProperty(jsHeaders, key, value);
					}
					ScriptableObject.putProperty(sharedScope, "headers", jsHeaders); //$NON-NLS-1$
				}
			}
			
			// set up console object
			Console console = newConsole();
			Object jsConsole = Context.javaToJS(console, sharedScope);
			ScriptableObject.putProperty(sharedScope, "console", jsConsole); //$NON-NLS-1$
			
			cx.evaluateString(sharedScope, "var config = " +  //$NON-NLS-1$
				aggregator.substituteProps(configScript, new IAggregator.SubstitutionTransformer() {
					@Override
					public String transform(String name, String value) {
						// escape forward slashes for javascript literals
						return value.replace("\\", "\\\\"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}), getConfigUri().toString(), 1, null);
			config = (Scriptable)sharedScope.get("config", sharedScope); //$NON-NLS-1$
			if (config == Scriptable.NOT_FOUND) {
				throw new IllegalStateException("config is not defined."); //$NON-NLS-1$
			}
		} finally {
			Context.exit();
		}
		return config;
	}
	
	/**
	 * Initializes and returns the map of packages based on the information in
	 * the server-side config JavaScript
	 * 
	 * @param cfg
	 *            The parsed config JavaScript as a properties map
	 * @return the package map
	 * @throws URISyntaxException
	 */
	protected Map<String, IPackage> loadPackages(Scriptable cfg) throws URISyntaxException {
		Object obj = cfg.get(PACKAGES_CONFIGPARAM, cfg);
		Map<String, IPackage> packages = new HashMap<String, IPackage>();
		if (obj instanceof Scriptable) {
			for (Object id : ((Scriptable)obj).getIds()) {
				if (id instanceof Number) {
					Number i = (Number)id;
					Object pkg = ((Scriptable)obj).get((Integer)i, (Scriptable)obj);
					IPackage p = newPackage(pkg); 
					if (!packages.containsKey(p.getName())) {
						packages.put(p.getName(), p);
					}
				}
			}
		}
		return packages;
	}
	
	/**
	 * Initializes and returns the map of paths based on the information in the
	 * server-side config JavaScript
	 * 
	 * @param cfg
	 *            The parsed config JavaScript as a properties map
	 * @return the map of path-name/path-URI pairs
	 * @throws URISyntaxException
	 */
	protected Map<String, Location> loadPaths(Scriptable cfg) throws URISyntaxException {
		Object pathlocs = cfg.get(PATHS_CONFIGPARAM, cfg);
		Map<String, Location> paths = new HashMap<String, Location>();
		if (pathlocs instanceof Scriptable) {
			for (Object key : ((Scriptable)pathlocs).getIds()) {
				String name = Context.toString(key);
				
				if (!paths.containsKey(name) && key instanceof String) {
					Location location = loadLocation(((Scriptable)pathlocs).get(name, (Scriptable)pathlocs), false);
					paths.put(name, getBase().resolve(location));
				}
			}
		}
		return paths;
	}
	
	/**
	 * Initializes and returns the list of aliases defined in the server-side
	 * config JavaScript
	 * 
	 * @param cfg
	 *            The parsed config JavaScript as a properties map
	 * @return the list of aliases
	 * @throws IOException 
	 */
	protected List<IAlias> loadAliases(Scriptable cfg) throws IOException {
		Object aliasList = cfg.get(ALIASES_CONFIGPARAM, cfg);
		List<IAlias> aliases = new LinkedList<IAlias>();
		if (aliasList instanceof Scriptable) {
			for (Object id : ((Scriptable)aliasList).getIds()) {
				if (id instanceof Number) {
					Number i = (Number)id;
					Object entry = ((Scriptable)aliasList).get((Integer)i, (Scriptable)aliasList);
					if (entry instanceof Scriptable) {
						Scriptable vec = (Scriptable)entry;
						Object pattern = vec.get(0, vec);
						Object replacement = vec.get(1, vec);
						if (pattern == Scriptable.NOT_FOUND || replacement == Scriptable.NOT_FOUND) {
							throw new IllegalArgumentException(Context.toString(entry));
						}
						if (pattern instanceof Scriptable && "RegExp".equals(((Scriptable)pattern).getClassName())) { //$NON-NLS-1$
							String regexlit = Context.toString(pattern);
							String regex = regexlit.substring(1, regexlit.lastIndexOf("/")); //$NON-NLS-1$
							String flags = regexlit.substring(regexlit.lastIndexOf("/")+1); //$NON-NLS-1$
							int options = 0;
							if (flags.contains("i")) { //$NON-NLS-1$
								options |= Pattern.CASE_INSENSITIVE;
							}
							pattern = Pattern.compile(regex, options);
						} else {
							pattern = Context.toString(pattern);
						}
						if (!(replacement instanceof Scriptable) || !"Function".equals(((Scriptable)replacement).getClassName())) { //$NON-NLS-1$
							replacement = Context.toString(replacement);
						}
						aliases.add(newAlias(pattern, replacement));
					}
				}
			}
		}
		return aliases;
	}
	
	/**
	 * Initializes and returns the list of module dependencies specified in the
	 * server-side config JavaScript.
	 * 
	 * @param cfg
	 *            The parsed config JavaScript as a properties map
	 * @return the list of module dependencies
	 */
	protected List<String> loadDeps(Scriptable cfg) {
		Object depsList = cfg.get(DEPS_CONFIGPARAM, cfg);
		List<String> deps = new LinkedList<String>();
		if (depsList instanceof Scriptable) {
			for (Object id : ((Scriptable)depsList).getIds()) {
				if (id instanceof Number) {
					Number i = (Number)id;
					Object entry = ((Scriptable)depsList).get((Integer)i, (Scriptable)depsList);
					deps.add(Context.toString(entry));
				}
			}
		}
		return deps;
	}

	/**
	 * Initializes and returns the expires time from the server-side
	 * config JavaScript
	 * 
	 * @param cfg
	 *            The parsed config JavaScript as a properties map
	 * @return the expires time
	 */
	protected int loadExpires(Scriptable cfg) {
		int expires = 0;
		Object oExpires = cfg.get(EXPIRES_CONFIGPARAM, cfg);
		if (oExpires != Scriptable.NOT_FOUND) {
			try {
				expires = Integer.parseInt(Context.toString(oExpires));
			} catch (NumberFormatException ignore) {
				throw new IllegalArgumentException(EXPIRES_CONFIGPARAM+"="+oExpires); //$NON-NLS-1$
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
	 *            The parsed config JavaScript as a properties map
	 * @return true if files and folder under the base directory should be
	 *         scanned
	 */
	protected boolean loadDepsIncludeBaseUrl(Scriptable cfg) {
		boolean result = false;
		Object value = cfg.get(DEPSINCLUDEBASEURL_CONFIGPARAM, cfg);
		if (value != Scriptable.NOT_FOUND) {
			result = TypeUtil.asBoolean(Context.toString(value), false);
		}
		return result;
	}

	/**
	 * Initializes and returns the flag indicating if features not specified
	 * in the feature set from the request should be treated as false when 
	 * evaluating has conditionals in javascript code.  The default is false.
	 * 
	 * @param cfg
	 *            The parsed config JavaScript as a properties map
	 * @return True if un-specified features should be treated as false.
	 */
	protected boolean loadCoerceUndefinedToFalse(Scriptable cfg) {
		boolean result = false;
		Object value = cfg.get(COERCEUNDEFINEDTOFALSE_CONFIGPARAM, cfg);
		if (value != Scriptable.NOT_FOUND) {
			result = TypeUtil.asBoolean(Context.toString(value), false);
		}
		return result;
	}
	
	/**
	 * Initializes and returns the notice string specified by the {@code notice}
	 * property in the server-side config JavaScript.
	 * 
	 * @param cfg
	 *            The parsed config JavaScript as a properties map
	 * @return the {@code notice} property value
	 * @throws URISyntaxException 
	 */
	protected String loadNotice(Scriptable cfg) throws IOException, URISyntaxException {
		String notice = null;
		Object noticeObj = cfg.get(NOTICE_CONFIGPARAM, cfg);
		if (noticeObj != Scriptable.NOT_FOUND) {
			String noticeUriStr = Context.toString(noticeObj);
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
	 * Common routine to load text or js plugin delegators
	 */
	protected Set<String> loadPluginDelegators(Scriptable cfg, String name) {
		Set<String> result = null;
		Object delegators = cfg.get(name, cfg);
		if (delegators != Scriptable.NOT_FOUND  && delegators instanceof Scriptable) {
			result = new HashSet<String>();
			for (Object id : ((Scriptable)delegators).getIds()) {
				if (id instanceof Number) {
					Number i = (Number)id;
					Object entry = ((Scriptable)delegators).get((Integer)i, (Scriptable)delegators);
					result.add(entry.toString());
				}
			}
			result = Collections.unmodifiableSet(result);
		} else {
			result = Collections.emptySet();
		}
		return result;
	}
	
	protected Set<String> loadTextPluginDelegators(Scriptable cfg) {
		return loadPluginDelegators(cfg, TEXTPLUGINDELEGATORS_CONFIGPARAM);
	}
	
	protected Set<String> loadJsPluginDelegators(Scriptable cfg) {
		return loadPluginDelegators(cfg, JSPLUGINDELEGATORS_CONFIGPARAM);
	}

	protected Location loadLocation(Object locObj, boolean isFolder) throws URISyntaxException {
		Location result;
		if (locObj instanceof Scriptable) {
			Scriptable values = (Scriptable)locObj;
			Object obj = values.get(0, values);
			if (obj == Scriptable.NOT_FOUND) {
				throw new IllegalArgumentException(Context.toString(locObj));
			}
			String str = Context.toString(obj);
			if (isFolder && !str.endsWith("/")) {  //$NON-NLS-1$
				str += "/"; //$NON-NLS-1$
			}
			URI primary = new URI(str).normalize(), override = null;
			obj = values.get(1, values);
			Object extra = values.get(2, values);
			if (extra != Scriptable.NOT_FOUND) {
				throw new IllegalArgumentException(Context.toString(locObj));
			}
			if (obj != Scriptable.NOT_FOUND) {
				str = Context.toString(obj);
				if (isFolder && !str.endsWith("/")) {  //$NON-NLS-1$
					str += "/"; //$NON-NLS-1$
				}
				try {
					override = new URI(str).normalize();
				} catch (URISyntaxException e) {
					if (log.isLoggable(Level.WARNING)) {
						log.warning(MessageFormat.format(
								Messages.ConfigImpl_5,
								new Object[]{str, e.getMessage()}
						));
					}
				}
			}
			result = new Location(primary, override);
		} else {
			String str = Context.toString(locObj);
			if (isFolder && !str.endsWith("/")) {  //$NON-NLS-1$
				str += "/"; //$NON-NLS-1$
			}
			result = new Location(new URI(str).normalize());
		}
		return result;
	}
	/**
	 * Initializes and returns the string specified by the {@code cacheBust}
	 * property in the server-side config JavaScript.
	 * 
	 * @param cfg
	 *            The parsed config JavaScript as a properties map
	 * @return the {@code cacheBust} property value
	 */
	protected String loadCacheBust(Scriptable cfg) {
		String result = null;
		Object value = cfg.get(CACHEBUST_CONFIGPARAM, cfg);
		if (value != Scriptable.NOT_FOUND) {
			result = Context.toString(value);
		}
		return result;
		
	}
	
	/**
	 * Calls the registered config modifiers to give them an opportunity to
	 * modify the raw config before config properties are evaluated.
	 * 
	 * @param rawConfig
	 *            A map of the top level properties in the config JavaScript. Lower
	 *            level javascript arrays are represented as
	 *            {@code List<Object>} and lower level javascript objects
	 *            are represented as {@code Map<String, Object>}.
	 */
	protected void callConfigModifiers(Scriptable rawConfig) {
		if(PlatformAggregatorFactory.getPlatformAggregator() != null){
			Object[] refs = null;		
			refs = PlatformAggregatorFactory.getPlatformAggregator().getServiceReferences(IConfigModifier.class.getName(), "(name="+getAggregator().getName()+")");//$NON-NLS-1$ //$NON-NLS-2$
			
			if (refs != null) {
				for (Object ref : refs) {
					IConfigModifier modifier = 
						(IConfigModifier)PlatformAggregatorFactory.getPlatformAggregator().getService(ref);
					if (modifier != null) {
						try {
							modifier.modifyConfig(getAggregator(), rawConfig);
						} catch (Exception e) {
							if (log.isLoggable(Level.SEVERE)) {
								log.log(Level.SEVERE, e.getMessage(), e);
							}
						} finally {
							PlatformAggregatorFactory.getPlatformAggregator().ungetService(ref);
						}
					}
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return strConfig;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.options.IOptionsListener#optionsUpdated(com.ibm.jaggr.core.options.IOptions, long)
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
	 * @see com.ibm.jaggr.core.IShutdownListener#shutdown(com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		for (Object reg : serviceRegs) {
			if(PlatformAggregatorFactory.getPlatformAggregator() != null){
				PlatformAggregatorFactory.getPlatformAggregator().unRegisterService(reg);
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void registerServices() {	
	        // Register listeners
		if(PlatformAggregatorFactory.getPlatformAggregator() != null){
			Dictionary dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$			
			serviceRegs.add(PlatformAggregatorFactory.getPlatformAggregator().registerService(IShutdownListener.class.getName(), this, dict));
			dict = new Properties();
			dict.put("name", aggregator.getName()); //$NON-NLS-1$			
			serviceRegs.add(PlatformAggregatorFactory.getPlatformAggregator().registerService(IOptionsListener.class.getName(), this, dict));	
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
		private final Location location;
		private final String main;

		public Package(Object obj) throws URISyntaxException {
			// Defaults
			String main = null;
			if (obj instanceof Scriptable) {
				Scriptable data = (Scriptable)obj;
				Object nameObj = data.get(PKGNAME_CONFIGPARAM, data);
				if (nameObj == Scriptable.NOT_FOUND) {
					throw new IllegalArgumentException(Context.toString(obj));
				}
				name = Context.toString(nameObj);
				location = getBase().resolve(loadLocation(data.get(PKGLOCATION_CONFIGPARAM, data), true));
				Object mainObj = data.get(PKGMAIN_CONFIGPARAM, data);
				if (mainObj != Scriptable.NOT_FOUND) {
					main = Context.toString(mainObj);
				}
			} else {
				throw new IllegalArgumentException(obj.toString());
			}
			
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
		 * @see com.ibm.jaggr.core.config.IConfig.IPackage#getName()
		 */
		@Override
		public String getName() {
			return name;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.config.IConfig.IPackage#getLocation()
		 */
		@Override
		public Location getLocation() {
			return location;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.config.IConfig.IPackage#getMain()
		 */
		@Override
		public String getMain() {
			return main;
		}
	}
	
	protected static class Alias implements IAlias {

		private final Object pattern;
		private final Object replacement;
		
		public Alias(/* String | Pattern */Object pattern, /* String | Function */Object replacement) {
			// validate arguments
			if (!(pattern instanceof String) && !(pattern instanceof Pattern)) {
				throw new IllegalArgumentException(Context.toString(pattern));
			}
			if (!(replacement instanceof String) && !(replacement instanceof Function)) {
				throw new IllegalArgumentException(Context.toString(replacement));
			}
			// replacement can be a Script only if pattern is a regular expression
			if ((pattern instanceof String) && !(replacement instanceof String)) {
				throw new IllegalArgumentException(Context.toString(replacement));
			}
			this.pattern = pattern;
			this.replacement = replacement;
		}

		
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.config.IConfig.IAlias#getPattern()
		 */
		@Override
		public Object getPattern() {
			return pattern;
		}
		
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.config.IConfig.IAlias#getReplacement()
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
	static public class Console {
		private static final Logger console = Logger.getLogger(Console.class.getName());
		public void log(String msg) {console.info(msg);}
		public void info(String msg) {console.info(msg);}
		public void warn(String msg) {console.warning(msg);}
		public void error(String msg) {console.severe(msg);}
	}
}
