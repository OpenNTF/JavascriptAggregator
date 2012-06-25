/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.config;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.jaggr.service.InitParams;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.util.Features;

/**
 * This interface encapsulates an aggregator configuration. An aggregator
 * configuration specifies the AMD module paths, packages and aliases that are
 * used by an application, by relating module ids to server resource URIs. The
 * configuration is specified using a JSON file who's structure and format
 * closely mirrors the client side AMD loader config JSON, except that instead
 * of relating module ids to resource on the web, it relates module ids to
 * resource on the server.
 * <p>
 * The static JSON configuration file is specified by the <code>config</code>
 * init-param in the servlet definition.
 * <p>
 * In addition to data specified in a static JSON file, applications may
 * optionally modify the configuration at runtime by registering an
 * implementation of {@link IConfigModifier} as an OSGi service with the
 * <code>name</code> parameter set to the aggregator name.
 * <p>
 * The URIs used in this interface can point to any type of resource accessible
 * on the server, but there must be a {@link IResource} implementation available
 * that supports the URI scheme.
 * <p>
 * In addition to locating server resources requested by the client-side loader,
 * the module id to resource URI mappings specified in this config are used when
 * scanning for modules in order to build the dependency graph for expanding
 * module dependencies. Only the <code>paths</code> and <code>packages</code>
 * properties are used for this purpose, so these properties must include all
 * the module paths used in the application. The base URI property is only used
 * to resolve relative paths specified in <code>paths</code> and
 * <code>packages</code>. It is <b>NOT</b> scanned for modules when building the
 * dependency graph.
 */
public interface IConfig {

	/**
	 * Static constant specifying the name of the {@code packages} config param
	 */
	public static final String PACKAGES_CONFIGPARAM = "packages"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code paths} config param
	 */
	public static final String PATHS_CONFIGPARAM = "paths"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code aliases} config param
	 */
	public static final String ALIASES_CONFIGPARAM = "aliases"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code deps} config param
	 */
	public static final String DEPS_CONFIGPARAM = "deps"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code baseUrl} config param
	 */
	public static final String BASEURL_CONFIGPARAM = "baseUrl"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code notice} config param
	 */
	public static final String NOTICE_CONFIGPARAM = "notice"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the packages' config param
	 * {@code name} property
	 */
	public static final String PKGNAME_CONFIGPARAM = "name"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the packages' config param
	 * {@code location} property
	 */
	public static final String PKGLOCATION_CONFIGPARAM = "location"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the packages' config param
	 * {@code main} property
	 */
	public static final String PKGMAIN_CONFIGPARAM = "main"; //$NON-NLS-1$

	/**
	 * Static constant specifying the {@code main} property's default value
	 */
	public static final String PKGMAIN_DEFAULT = "./main"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code depsIncludeBaseUrl}
	 * config param
	 */
	public static final String DEPSINCLUDEBASEURL_CONFIGPARAM = "depsIncludeBaseUrl"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code coerceUndefinedToFalse}
	 * config param
	 */
	public static final String COERCEUNDEFINEDTOFALSE_CONFIGPARAM = "coerceUndefinedToFalse"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code expires} config param
	 */
	public static final String EXPIRES_CONFIGPARAM = "expires"; //$NON-NLS-1$

	/**
	 * Static constant specifying the name of the {@code cacheBust} config param
	 */
	public static final String CACHEBUST_CONFIGPARAM = "cacheBust"; //$NON-NLS-1$

	/**
	 * Returns the value of the {@code baseUrl} config param. This is the base
	 * URI to use for all relative URIs specified in this config. If baseUrl is
	 * not absolute, then it is assumed to be relative to the root of the bundle
	 * defining the servlet. Note that the property name specifies Url, with a
	 * lower-case L, instead of URI with an upper-case i, to maintain symmetry
	 * with the client-side config.
	 * 
	 * Files and folders located under the folder specified by baseUrl are not
	 * scanned when the Aggregator builds the module dependency map used for
	 * require list expansion unless the {@code depsIncludeBaseUrl} config param
	 * is specified with a value of true.
	 * 
	 * @return The base URI
	 */
	public URI getBase();

	/**
	 * Returns the path mappings for module names to resource URIs specified by
	 * the {@code paths} config param. The path URIs are assumed to be relative
	 * to baseUrl, unless the URI starts with a "/" or specfies a protocol.
	 * 
	 * @return The paths map
	 */
	public Map<String, URI> getPaths();

	/**
	 * Returns the packages specified by the {@code packages} config param.
	 * 
	 * @return The packages map
	 * @see IPackage
	 */
	public Map<String, IPackage> getPackages();

	/**
	 * Returns the list of aliases specified by the {@code aliases} config
	 * param.
	 * 
	 * @return The list of aliases
	 */
	public List<IAlias> getAliases();

	/**
	 * Returns the value of the {@code depsIncludeBaseUrl} config param.
	 * <p>
	 * If specified with a value of true, then the files and folders under the
	 * directory specified by baseUrl will be scanned when the Aggregator builds
	 * the module dependency map used for require list expansion. The default
	 * value is false.
	 * 
	 * @return The value of the {@code depsIncludeBaseUrl} config param
	 */
	public boolean isDepsIncludeBaseUrl();

	/**
	 * Returns the value of the {@code coerceUndefinedToFalse} config param.
	 * <p>
	 * If true, then the Aggregator will treat undefined features as if they
	 * were defined with a value of false. This applies to has.js feature
	 * trimming of javascript code. If this value is false, then has.js
	 * conditionals for undefined features are left unchanged. If the value is
	 * true, then conditionals involving undefined features are trimmed based on
	 * the value of the feature evaluating to false. The default value for this
	 * option is false.
	 * 
	 * @return the value of the {@code coerceUndefinedToFalse} config param
	 */
	public boolean isCoerceUndefinedToFalse();

	/**
	 * Returns the value of the {@code expires} config param as an integer.
	 * <p>
	 * If specified, this value will be used to specified in the
	 * Cache-Control:max-age response header. If the value is zero (the
	 * default), then the Cache-Control:maz-age header will not be specified in
	 * the response.
	 * 
	 * @return The value of the {@code expires} config param or zero
	 */
	public int getExpires();

	/**
	 * Returns the value of the {@code deps} config param as a list of strings.
	 * <p>
	 * This property is analogous to the client-side AMD config deps property,
	 * which specifies the modules that should be loaded before the
	 * application's require.callback method is called. On the server, this
	 * property is used to trim the list of expanded dependencies for all
	 * require list expansions. Any modules listed here, along with the nested
	 * dependencies of the listed modules, will be excluded from expanded
	 * require lists for all require() calls in the application. In general, the
	 * client-side and server-side AMD configs should both specify the same
	 * modules in the deps property, although, this is not a requirement.
	 * <p>
	 * If the deps property is not specified in the config, then this function
	 * returns an empty list.
	 * 
	 * @return The list of module names specified in the {@code deps} config
	 *         param
	 */
	public List<String> getDeps();

	/**
	 * Returns the content of the resource specified by the {@code notice}
	 * config param.
	 * <p>
	 * This resource contains the notice text that will be output at the
	 * beginning of every Aggregator response. The notice text should be in the
	 * form of a javascript block comment. The Aggregator does not do any
	 * processing on the notice text.
	 * 
	 * @return the notice string
	 */
	public String getNotice();

	/**
	 * Returns the value of the {@code cacheBust} config param.
	 * <p>
	 * This is an arbitrary string specified by the application that is
	 * associated with the serialized meta-data for aggregator caches and the
	 * module dependency maps. When these data structures are de-serialized on
	 * server restarts, the saved value is compared against the value that is
	 * read from the current config, and if the values don't match, then the
	 * de-serialized data is discarded and the caches and dependency maps are
	 * deleted and rebuilt.
	 * 
	 * @return The value of the {@code cacheBust} config param
	 */
	public String getCacheBust();

	/**
	 * Returns the URI to the resource on the server for the specified module
	 * id, or null if the resource cannot be located.
	 * 
	 * @param mid
	 *            The module id
	 * @return The URI for the module.
	 */
	public URI locateModuleResource(String mid);

	/**
	 * Returns an ordered mapping of module ids to resource URIs for all of the
	 * <code>paths</code> and defined in this config.
	 * 
	 * @return The defined paths
	 */
	public Map<String, URI> getPathURIs();

	/**
	 * Returns an ordered mapping of module ids to resource URIs for all of the
	 * <code>packages</code> and defined in this config. The entries include
	 * URIs defined by both package location and package main properties.
	 * 
	 * @return The defined package URIs
	 */
	public Map<String, URI> getPackageURIs();

	/**
	 * Returns the last-modified date of the config JSON file at the time that
	 * it was read in order to produce this config object.
	 * 
	 * @return The last-modified date
	 */
	public long lastModified();

	/**
	 * Returns the URI for the config JSON file from which this config was read.
	 * 
	 * @return The config JSON URI
	 */
	public URI getConfigUri();

	/**
	 * Returns a clone of the raw config data from the config JSON, after the
	 * config has been modified by any {@link IConfigModifier} services that
	 * have been registered for the aggregator that this config is associated
	 * with.
	 * <p>
	 * The returned object is a deep clone, so changes to any element in the
	 * returned data will not affect the properties of this object.
	 * <p>
	 * In the returned object, JavaScript objects are represented using
	 * <code>{@link Map}&lt;{@link String},
	 * {@link Object}&gt;</code>, JavaScript arrays are represented using
	 * <code>{@link List}&lt;{@link Object}&gt;</code>. JavaScript regular
	 * expressions and JavaScript functions are represented using a JSON proxy
	 * object as shown in the examples below. JavaScript booleans, numbers and
	 * strings are represented as {@link Boolean}, {@link Double} and
	 * {@link String}, respectively.
	 * <p>
	 * JSON proxy objects are used to represent JavaScript object types that
	 * don't have a natural representation in Java. Following are examples of
	 * JSON proxy objects for JavaScript functions and regular expressions.
	 * <ul>
	 * <li>
	 * <code>{"$$JSONProxy$$":"regexp","regexp":'/^(.*)\\/foo\\/(.*)$/i"}</code>
	 * </li>
	 * <li>
	 * <code>{"$$JSONProxy$$":"function", "function":"function($0, $1, $2){return $1+'/bar/'+$2;}"}</code>
	 * </li>
	 * </ul>
	 * <p>
	 * JSON proxy objects are used only in the raw config returned by this
	 * method. They are not needed in the original config definition since it is
	 * processed by a JavaScript interpreter that fully supports the JavaScript
	 * language. They are used for the raw config JSON in order to avoid
	 * exposing internal types used by the interpreter, and to make it easier
	 * for config modifiers to manipulate the config.
	 * <p>
	 * Raw config objects are serializable, and the
	 * {@link Object#equals(Object)} method may be used to compare one config
	 * with another to determine if they represent the same config data.
	 * 
	 * @return The raw config data for this config object
	 * @see IConfigModifier
	 */
	public Map<String, Object> getRawConfig();

	/**
	 * Resolves a module id by applying the following mappings in the indicated
	 * order.
	 * <ul>
	 * <li>
	 * If the module id specifies a has plugin and the has feature(s) are
	 * contained in the feature set provided in the request, then evaluate the
	 * has plugin expression to obtain the mapped module id.</li>
	 * <li>
	 * Call any alias resolvers to resolve module name aliases</li>
	 * <li>
	 * If the module id matches a package name, then map to the package main
	 * module id.</li>
	 * </ul>
	 * 
	 * @param name
	 *            The module name to resolve
	 * @param features
	 *            Features that are defined in the request
	 * @param dependentFeatures
	 *            Output - Set of feature names that the returned value is
	 *            conditioned on. Used for cache management.
	 * @param sb
	 *            If not null, then a reference to a string buffer that can be
	 *            used to specify debug/diagnostic information
	 *            about the module name resolution. For example, the resolver may
	 *            indicate that alias resolution was not performed due to a
	 *            missing required feature.
	 * 
	 * @return The resolved module id.  If the module id is unchanged, then
	 *         {@code name} should be returned.
	 * 
	 */
	public String resolve(String name, Features features,
			Set<String> dependentFeatures, StringBuffer sb);

	/**
	 * Interface for a config alias. Aliases are resolved by the aggreagtor when
	 * building the module dependency graph for the purpose of expanding require
	 * list dependencies. In general, any aliases specified in the client loader
	 * config should also be specified in the aggregator config.
	 */
	public interface IAlias {

		/**
		 * A String or an implementation specific representation of a JavaScript
		 * regular expression.
		 * 
		 * @return The pattern that is applied to the module id.
		 */
		public Object getPattern();

		/**
		 * A string or an implementation specific representation of a JavaScript
		 * replacement function. 
		 * <p>
		 * For javascript replacement functions, the javascript {@code has()} function
		 * is defined for the feature set provided in the request.  The replacement
		 * function script may also query the {@code options} object which contains the 
		 * {@link IOptions} properties and the {@code initParams} object which contains
		 * the {@link InitParams} properties.
		 * <p>
		 *  The replacement may be a Script only if
		 * {@link #getPattern()} returns a Pattern.
		 * 
		 * @return The replacement
		 */
		public Object getReplacement();
	}

	/**
	 * Interface for a server-side AMD module package. The definition of
	 * server-side packages mirrors the definition of client-side packages (see
	 * <a href="http://requirejs.org/docs/api.html#packages">Packages</a>),
	 * except that instead of mapping module ids to resources on the web, the
	 * server-side package maps module ids to resources on the server.
	 */
	public interface IPackage {

		/**
		 * Returns the value of the {@code name} property of the package.
		 * 
		 * @return The package name
		 */
		public String getName();

		/**
		 * Returns the value of the {@code location} property of the package.
		 * <p>
		 * Locations are relative to the baseUrl configuration value, unless
		 * they contain a protocol or start with a front slash (/).
		 * 
		 * @return The package location on the server
		 */
		public URI getLocation();

		/**
		 * Returns value of the {@code main} propery of the package.
		 * <p>
		 * The main module is returned when someone does a require for the
		 * package. The default value is {@code ./main}, so it needs to be specified only if it
		 * differs from the default. Note that unlike {@code location}, which specifies
		 * the URI to a resource on the server, main specifies a module id that
		 * is mapped to a server resource using the defined paths and packages.
		 * It may specify a relative module id, in which case it is combined
		 * with the package name, or a non-relative module id, in which case it
		 * is used as specified.
		 * 
		 * @return The value of the {@code main} property of the package, or 
		 * {@code ./main} if no main property is specified.
		 */
		public String getMain();
	}
}
