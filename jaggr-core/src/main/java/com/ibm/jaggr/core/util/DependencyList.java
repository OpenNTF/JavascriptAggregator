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

package com.ibm.jaggr.core.util;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Container class for the set of expanded dependencies for a list of modules.
 */
public class DependencyList {
	static final Logger log = Logger.getLogger(DependencyList.class.getName());

	/** regular expression for detecting if a plugin name is the has! plugin */
	static final Pattern hasPattern = Pattern.compile("(^|\\/)has$"); //$NON-NLS-1$

	/**
	 * The declaring source for the dependencies.  This information is included
	 * in the diagnostic details when <code>includeDetails</code> is true.
	 */
	private String source = null;
	/**
	 * The explicit dependencies (i.e. the modules specified in the names list, plus
	 * any additional modules resulting from has! plugin evaluation and alias
	 * resolution.
	 */
	private ModuleDeps explicitDeps = null;

	/**
	 * The expanded dependences for the modules in explicitDeps.  This does not
	 * include the modules specified in explicitDeps, but is is not exclusive of
	 * that list either (i.e. modules listed in explicitDeps may also be included
	 * here by virtue of being a dependency of another module).
	 */
	private ModuleDeps expandedDeps = null;

	/**
	 * The list of features that were evaluated when resolving any has! plugin
	 * expressions or aliases.  This can include the names of features not
	 * included in <code>features</code>.
	 */
	private final Set<String> dependentFeatures;

	/**
	 * The list of module names provided in the constructor
	 */
	private final Iterable<String> names;

	/**
	 * The {@link IAggregator} object from which we obtain references to other
	 * required objects used in processing the dependencies.
	 */
	private final IAggregator aggr;

	/**
	 * The features to be used in resolving has! plug-in
	 * expressions and aliases
	 */
	private final Features features;

	/**
	 * If true, then resolve aliases for the explicit dependencies.
	 * Note that alias resolution is always performed for the
	 * expanded dependencies.
	 */
	private boolean resolveAliases;

	/**
	 * Flag indicating whether or not to include dependency resolution details
	 * as comments in the results
	 */
	private final boolean includeDetails;

	/**
	 * Flag indicating whether or not to include dependencies specified in require() calls.
	 */
	private final boolean includeRequireDeps;


	/**
	 * Flag indicating if this object has been initialized.
	 */
	private boolean initialized = false;

	/**
	 * Optional label string to associate with this object
	 */
	private String label = null;

	/**
	 * If true, then undefined features should be treated as false for the purposes of
	 * resolving has! loader plugin conditionals.
	 */
	private boolean coerceUndefinedToFalse = false;


	/**
	 * Object constructor. Note that resolution of expanded dependencies is not
	 * done at object creation time, but rather, the first time that one of the
	 * accessors is called.
	 *
	 * @param source
	 *            The declaring source.  Included in diagnostic details.
	 * @param names
	 *            The list of normalized module ids for which expanded
	 *            dependencies are needed.
	 * @param aggr
	 *            The aggregator servlet
	 * @param features
	 *            The map of feature-name value pairs to use for resolving has!
	 *            plugin expressions and aliases
	 * @param resolveAliases
	 *            Flag indicating if alias resolution should be performed on the
	 *            module ids specified in <code>names</code>. (Note: alias
	 *            resolution is always performed for expanded dependencies.
	 * @param includeDetails
	 *            Flag indicating if diagnostic details should be included with
	 *            the expanded dependencies
	 */
	public DependencyList(String source, Iterable<String> names, IAggregator aggr, Features features,  boolean resolveAliases, boolean includeDetails) {
		this(source, names, aggr, features, resolveAliases, includeDetails, false);
	}

	/**
	 * Object constructor. Note that resolution of expanded dependencies is not
	 * done at object creation time, but rather, the first time that one of the
	 * accessors is called.
	 *
	 * @param source
	 *            The declaring source.  Included in diagnostic details.
	 * @param names
	 *            The list of normalized module ids for which expanded
	 *            dependencies are needed.
	 * @param aggr
	 *            The aggregator servlet
	 * @param features
	 *            The map of feature-name value pairs to use for resolving has!
	 *            plugin expressions and aliases
	 * @param resolveAliases
	 *            Flag indicating if alias resolution should be performed on the
	 *            module ids specified in <code>names</code>. (Note: alias
	 *            resolution is always performed for expanded dependencies.
	 * @param includeDetails
	 *            Flag indicating if diagnostic details should be included with
	 *            the expanded dependencies
	 * @param includeRequireDeps
	 *            Flag indicating whether or not to include dependencies specified
	 *            in require() calls.
	 */
	@SuppressWarnings("unchecked")
	public DependencyList(String source, Iterable<String> names, IAggregator aggr, Features features,  boolean resolveAliases, boolean includeDetails, boolean includeRequireDeps) {
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		final String methodName = "<ctor>"; //$NON-NLS-1$
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName, new Object[]{names, aggr, features, resolveAliases, includeDetails});
		}
		this.source = source;
		this.names = (Iterable<String>) (names != null ? names : Collections.emptySet());
		this.aggr = aggr;
		this.features = features;
		this.resolveAliases = resolveAliases;
		this.includeDetails = includeDetails;
		this.includeRequireDeps = includeRequireDeps;
		this.dependentFeatures = new HashSet<String>();
		if (entryExitLogging) {
			log.exiting(DependencyList.class.getName(), methodName);
		}
	}

	/**
	 * Constructs a DependencyList from pre-specified values
	 *
	 * @param explicitDeps
	 * @param expandedDeps
	 * @param dependentFeatures
	 */
	public DependencyList(ModuleDeps explicitDeps, ModuleDeps expandedDeps, Set<String> dependentFeatures) {
		this.explicitDeps = explicitDeps;
		this.expandedDeps = expandedDeps;
		this.dependentFeatures = dependentFeatures;
		initialized = true;
		names = null;
		includeDetails = false;
		features = null;
		aggr = null;
		includeRequireDeps = false;
	}

	/**
	 * Returns the explicit dependencies for the modules specified in
	 * <code>names</code>. This includes the specified modules, plus any module
	 * names resulting from resolving has! plugin expressions and aliases for
	 * the specified modules.
	 * <p>
	 * The set of explicit dependencies is the key set of the returned map. The
	 * value set of the map contains diagnostic details regarding the dependency
	 * expansion of each module if <code>includeDetails</code> was true when the
	 * object was constructed. Otherwise, the value objects will all be null.
	 *
	 * @return The explicit dependencies for the modules specified in
	 *         <code>names</code>.
	 * @throws IOException
	 */
	public ModuleDeps getExplicitDeps() throws IOException {
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		final String methodName = "getExplicitDeps"; //$NON-NLS-1$
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName);
		}
		if (!initialized) {
			initialize();
		}
		if (entryExitLogging) {
			log.exiting(DependencyList.class.getName(), methodName, explicitDeps);
		}
		return explicitDeps;
	}

	/**
	 * Returns the set of expanded dependencies for the modules specified in
	 * <code>names</code>. This set generally does not include the modules
	 * specified in <code>names</names>, but is
	 * is not exclusive of that list either.  A module specified in <code>names</code>
	 * may be included in the result by virtue of being a dependency of another
	 * module.
	 * <p>
	 * The set of expanded names is the key set of the returned map. The value
	 * set of the map contains diagnostic details regarding the dependency
	 * expansion of each module if <code>includeDetails</code> was true when the
	 * object was constructed. Otherwise, the value objects will all be null.
	 *
	 * @return The expanded dependencies for the modules specified in
	 *         <code>names</code>.
	 * @throws IOException
	 */
	public ModuleDeps getExpandedDeps() throws IOException {
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		final String methodName = "getExpandedDeps"; //$NON-NLS-1$
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName);
		}
		if (!initialized) {
			initialize();
		}
		if (entryExitLogging) {
			log.exiting(DependencyList.class.getName(), methodName, expandedDeps);
		}
		return expandedDeps;
	}

	/**
	 * Returns the set of features that were discovered when evaluating has!
	 * plugin expressions or aliases when expanding the dependencies.  This
	 * information may be required by the cache manager in order to properly
	 * idenify cached responses.
	 *
	 * @return The set of discovered feature names.
	 * @throws IOException
	 */
	public Set<String> getDependentFeatures() throws IOException {
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		final String methodName = "getDependentFeatures"; //$NON-NLS-1$
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName);
		}
		if (!initialized) {
			initialize();
		}
		if (entryExitLogging) {
			log.exiting(DependencyList.class.getName(), methodName, dependentFeatures);
		}
		return dependentFeatures;
	}

	/**
	 * Associates an arbitrary text string with this object.
	 *
	 * @param label The string to associate with this object
	 */
	public void setLabel(String label) {
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		final String methodName = "setLabel"; //$NON-NLS-1$
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName, new Object[]{label});
		}
		this.label = label;
		if (entryExitLogging) {
			log.exiting(DependencyList.class.getName(), methodName);
		}
	}

	public void setCoerceUndefinedToFalse(boolean state) {
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		final String methodName = "setCoerceUndefinedToFalse"; //$NON-NLS-1$
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName, new Object[]{state});
		}
		this.coerceUndefinedToFalse = state;
		if (entryExitLogging) {
			log.exiting(DependencyList.class.getName(), methodName);
		}
	}

	/**
	 * Returns the label set by {@link #setLabel(String)}
	 * @return The previously set label string
	 */
	public String getLabel() {
		if (log.isLoggable(Level.FINER)) {
			final String methodName = "getLabel"; //$NON-NLS-1$
			log.entering(DependencyList.class.getName(), methodName);
			log.exiting(DependencyList.class.getName(), methodName, label);
		}
		return label;
	}

	/**
	 * Internal method called to resolve dependencies the first time one of the
	 * accessor methods is called.  This is done in order to make object creation
	 * light-weight and to avoid the possibility of throwing an exception in the
	 * object constructor.
	 * @throws IOException
	 */
	synchronized void initialize() throws IOException {
		if (initialized) {
			return;
		}
		final boolean traceLogging = log.isLoggable(Level.FINEST);
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		final String methodName = "initialize"; //$NON-NLS-1$
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName);
		}
		// A call to getDeclaredDependencies is made to ensure that the time stamp calculated to mark the beginning of finding the expanded
		//dependencies is done only after forming the dependency map is completed.
		aggr.getDependencies().getDelcaredDependencies("require"); //$NON-NLS-1$
		long stamp = aggr.getDependencies().getLastModified();  // save time stamp
		try {
			explicitDeps = new ModuleDeps();
			expandedDeps = new ModuleDeps();
			if (traceLogging) {
				log.finest("dependent features = " + dependentFeatures); //$NON-NLS-1$
			}
			for (String name : names) {
				processDep(name, explicitDeps, null, new HashSet<String>(), null);
			}
			// Now expand the explicit dependencies
			resolveAliases = true;
			for (Map.Entry<String, ModuleDepInfo> entry : explicitDeps.entrySet()) {
				expandDependencies(entry.getKey(), entry.getValue(), expandedDeps);
			}
			expandedDeps.keySet().removeAll(IDependencies.excludes);

			// Resolve feature conditionals based on the specified feature set.  This is
			// necessary because we don't specify features when doing has! plugin branching
			// so that dependent features that are discovered by has! plugin branching don't
			// vary based on the specified features.
			explicitDeps.resolveWith(features, coerceUndefinedToFalse);
			expandedDeps.resolveWith(features, coerceUndefinedToFalse);


			if (traceLogging) {
				log.finest("explicitDeps after applying features: " + explicitDeps); //$NON-NLS-1$
				log.finest("expandedDeps after applying features: " + expandedDeps); //$NON-NLS-1$
			}
			if (stamp != aggr.getDependencies().getLastModified()) {
				// if time stamp has changed, that means that dependencies have been
				// updated while we were processing them.  Throw an exception to avoid
				// caching the response with possibly corrupt dependency info.
				throw new IllegalStateException("" + stamp + "!=" + aggr.getDependencies().getLastModified()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} finally {
			initialized = true;
		}
		if (entryExitLogging) {
			log.exiting(DependencyList.class.getName(), methodName);
		}
	}

	/**
	 * Expands the nested dependencies for the specified module
	 *
	 * @param name
	 *            the name of the module who's dependencies are to be expanded.
	 *            Alias name resolution and has! loader plugin branching is
	 *            assumed to have been already performed.
	 * @param depInfo
	 *            the {@link ModuleDepInfo} for the module, obtained from processing
	 *            the module's explicit dependencies
	 * @param expandedDependencies
	 *            Output - the map that the expanded dependencies are written to.
	 * @throws IOException
	 */
	void expandDependencies(String name, ModuleDepInfo depInfo, ModuleDeps expandedDependencies) throws IOException {
		final String methodName = "expandDependencies"; //$NON-NLS-1$
		final boolean traceLogging = log.isLoggable(Level.FINEST);
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName, new Object[]{name, depInfo, expandedDependencies});
		}

		List<String> dependencies = new ArrayList<String>();
		List<String> declaredDeps = aggr.getDependencies().getDelcaredDependencies(name);
		if (traceLogging) {
			log.finest("declaredDeps for " + name + " = " + declaredDeps); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (declaredDeps != null) {
			dependencies.addAll(declaredDeps);
		}
		if (includeRequireDeps) {
			List<String> requireDeps = aggr.getDependencies().getRequireDependencies(name);
			if (requireDeps != null && requireDeps.size() > 0) {
				if (traceLogging) {
					log.finest("requireDeps for " + name + " = " + requireDeps); //$NON-NLS-1$ //$NON-NLS-2$
				}
				dependencies.addAll(requireDeps);
			}
		}
		if (dependencies != null) {
			for (String dep : dependencies) {
				ModuleDeps moduleDeps = new ModuleDeps();
				processDep(dep, moduleDeps, depInfo, new HashSet<String>(), name);
				for (Map.Entry<String, ModuleDepInfo> entry : moduleDeps.entrySet()) {
					if (traceLogging) {
						log.finest("Adding " + entry + " to expandedDependencies"); //$NON-NLS-1$ //$NON-NLS-2$
					}
					if (expandedDependencies.add(entry.getKey(), new ModuleDepInfo(entry.getValue()))) {
						expandDependencies(entry.getKey(), entry.getValue(), expandedDependencies);
					}
				}
			}
		}
		if (entryExitLogging) {
			log.exiting(DependencyList.class.getName(), methodName);
		}
	}

	/**
	 * Handles initial processing of explicit dependencies, including alias
	 * resolution and has! loader plugin branching/resolution.
	 *
	 * @param name
	 *            The explicit dependency to process
	 * @param deps
	 *            Output - one or more {@link ModuleDepInfo} objects are added
	 *            to <code>deps</code> for each module specified by
	 *            <code>name</code>. Plugin dependencies and has! loader plugin
	 *            branching can result in multiple entries being added to
	 *            <code>deps</code>.
	 * @param callerInfo
	 *            {@link ModuleDepInfo} object specifying feature conditionals
	 *            that should be ANDed with this module. Used in has! loader
	 *            plugin branching when this method is called recursively.
	 * @param recursionCheck
	 *            Set of module names used to break recursion loops
	 * @param dependee
	 *            Use by require expansion logging.  Specifies the name of the
	 *            module that includes this module in its dependencies.
	 */
	void processDep(String name, ModuleDeps deps, ModuleDepInfo callerInfo, Set<String> recursionCheck, String dependee) {
		final String methodName = "processDep"; //$NON-NLS-1$
		final boolean traceLogging = log.isLoggable(Level.FINEST);
		final boolean entryExitLogging = log.isLoggable(Level.FINER);
		if (entryExitLogging) {
			log.entering(DependencyList.class.getName(), methodName, new Object[]{deps, name, callerInfo});
		}

		boolean performHasBranching = !aggr.getOptions().isDisableHasPluginBranching();
		if (traceLogging && !performHasBranching) {
			log.finest("Has branching is disabled."); //$NON-NLS-1$
		}
		StringBuffer sb = includeDetails ? new StringBuffer() : null;
		String comment = null, resolved = null;

		// If a plugin is specified, save the plguin name in case alias resolution or
		// has! loader plugin resolution eliminates the plugin from the module id.
		int idx = (name != null) ? name.indexOf("!") : -1; //$NON-NLS-1$
		String pluginName = idx > 0 ? name.substring(0, idx) : null;

		resolved = aggr.getConfig().resolve(name, features, dependentFeatures, sb, resolveAliases, !performHasBranching);
		if (traceLogging) {
			log.finest("Module name \"" + name + "resolved to \"" + resolved + "\"."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		if (includeDetails) {
			comment = ((dependee == null) ?
					MessageFormat.format(Messages.DependencyList_5, source) :
						MessageFormat.format(Messages.DependencyList_4, dependee)) + sb.toString();
		}
		if (resolved != null && resolved.length() > 0 && !resolved.equals(name)) {
			name = resolved;
			if (recursionCheck.contains(name)) {
				if (log.isLoggable(Level.WARNING)) {
					log.warning(MessageFormat.format(
							Messages.DependencyList_3,
							new Object[] {name,	recursionCheck}
							));
				}
				return;
			}
			recursionCheck.add(name);
		}
		ModuleDepInfo info = callerInfo != null ?
				new ModuleDepInfo(callerInfo, dependee == null ? null : comment) :
					new ModuleDepInfo(null, null, comment);

				if (traceLogging) {
					log.finest("pluginName = " + pluginName); //$NON-NLS-1$
				}
				// check for plugin again in case one was introduced by config aliasing.
				idx = (name != null) ? name.indexOf("!") : -1; //$NON-NLS-1$
				if (idx > 0) {
					pluginName = name.substring(0, idx);
				}
				if (pluginName != null) {
					processDep(pluginName, deps,
							callerInfo != null ?
									new ModuleDepInfo(callerInfo, Messages.DependencyList_1) :
										new ModuleDepInfo(null, null, Messages.DependencyList_1),
										recursionCheck != null ? new HashSet<String>(recursionCheck) : null,
												dependee);
					if (performHasBranching) {
						if (hasPattern.matcher(pluginName).find()) {
							HasNode hasNode = new HasNode(name.substring(idx+1));
							if (traceLogging) {
								log.finest("hasNode = " + hasNode); //$NON-NLS-1$
							}
							ModuleDeps hasDeps = hasNode.evaluateAll(
									pluginName,
									// Specify empty feature set so that dependent features discovered
									// by has! plugin branching will not vary depending on the specified
									// features.
									Features.emptyFeatures,
									dependentFeatures,
									callerInfo,
									includeDetails ? MessageFormat.format(
											Messages.DependencyList_2,
											new Object[]{name})
											: null
									);
							if (traceLogging) {
								log.finest("hasDeps = " + hasDeps); //$NON-NLS-1$
							}
							for (Map.Entry<String, ModuleDepInfo> entry : hasDeps.entrySet()) {
								processDep(entry.getKey(), deps, entry.getValue(),
										recursionCheck != null ? new HashSet<String>(recursionCheck) : null,
												dependee);
							}
						} else {
							if (traceLogging) {
								log.finest("Adding module \"" + name + "\" with ModuleDepInfo: " + info + " to result deps - 3"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							}
							deps.add(name, info);
						}
					} else {
						if (traceLogging) {
							log.finest("Adding module \"" + name + "\" with ModuleDepInfo: " + info + " to result deps - 2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						}
						deps.add(name, info);
					}
				} else {
					if (traceLogging) {
						log.finest("Adding module \"" + name + "\" with ModuleDepInfo: " + info + " to result deps - 1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					}
					deps.add(name, info);
				}
				if (entryExitLogging) {
					log.exiting(DependencyList.class.getName(), methodName);
				}
	}
}
