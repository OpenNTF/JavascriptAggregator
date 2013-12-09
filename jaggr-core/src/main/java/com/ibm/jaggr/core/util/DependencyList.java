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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;

/**
 * Container class for the set of expanded dependencies for a list of modules. 
 */
public class DependencyList {
	static final Logger log = Logger.getLogger(DependencyList.class.getName());

	/** regular expression for detecting if a plugin name is the has! plugin */
	static final Pattern hasPattern = Pattern.compile("(^|\\/)has$"); //$NON-NLS-1$

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
	private Set<String> dependentFeatures = Collections.emptySet();
	
	/**
	 * The list of module names provided in the constructor
	 */
	private final Iterable<String> names;
	
	/**
	 * The {@link IDependencies} object used to resolved dependencies
	 */
	private final IDependencies deps;
	
	/**
	 * The {@link IConfig} object used to resolve aliases
	 */
	private final IConfig config;
	
	/**
	 * The features to be used in resolving has! plug-in 
	 * expressions and aliases
	 */
	private final Features features;
	
	/**
	 * Flag indicating whether or not to include dependency resolution details
	 * as comments in the results
	 */
	private final boolean includeDetails;
	
	/**
	 * True if has! plugin branching should be performed
	 */
	private final boolean performHasBranching;

	/**
	 * Flag indicating if this object has been initialized.
	 */
	private boolean initialized = false;
	
	/**
	 * Optional label string to associate with this object
	 */
	private String label = null;
	
	/**
	 * Object constructor.  Note that resolution of expanded dependencies
	 * is not done at object creation time, but rather, the first time
	 * that one of the accessors is called.
	 * 
	 * @param names
	 *            The list of normalized module names for which expanded
	 *            dependencies are needed.
	 * @param config
	 *            The config object to use for resolving aliases
	 * @param deps
	 *            The dependencies object to use for expanding dependencies
	 * @param features
	 *            The map of feature-name value pairs to use for resolving has!
	 *            plugin expressions and aliases
	 * @param includeDetails
	 *            Flag indicating if diagnostic details should be included with
	 *            the expanded dependencies
	 * @param performHasBranching
	 *            Flag indicating if has branching should be performed during
	 *            require list expansion
	 */
	@SuppressWarnings("unchecked")
	public DependencyList(Iterable<String> names, IConfig config, IDependencies deps, Features features, boolean includeDetails, boolean performHasBranching) {
		this.names = (Iterable<String>) (names != null ? names : Collections.emptySet());
		this.deps = deps;
		this.config = config;
		this.features = features;
		this.includeDetails = includeDetails;
		this.performHasBranching = performHasBranching;
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
	 */
	public ModuleDeps getExplicitDeps() throws IOException {
		if (!initialized) {
			initialize();
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
	 */
	public ModuleDeps getExpandedDeps() throws IOException { 
		if (!initialized) {
			initialize();
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
	 */
	public Set<String> getDependentFeatures() throws IOException { 
		if (!initialized) {
			initialize();
		}
		return dependentFeatures; 
	}
	
	/**
	 * Associates an arbitrary text string with this object.  
	 * 
	 * @param label The string to associate with this object
	 */
	public void setLabel(String label) {
		this.label = label;
	}
	
	/**
	 * Returns the label set by {@link #setLabel(String)}
	 * @return The previously set label string
	 */
	public String getLabel() {
		return label;
	}

	/**
	 * Internal method called to resolve dependencies the first time one of the
	 * accessor methods is called.  This is done in order to make object creation
	 * light-weight and to avoid the possibility of throwing an exception in the
	 * object constructor.
	 */
	private synchronized void initialize() throws IOException {
		if (initialized) {
			return;
		}
		try {
			dependentFeatures = new HashSet<String>();
			/*
			 * Use LinkedHashMaps to maintain ordering of expanded dependencies
			 * as some types of modules (i.e. css) are sensitive to the order
			 * that modules are required relative to one another.
			 */
			explicitDeps = new ModuleDeps();
			expandedDeps = new ModuleDeps();
			for (String name : names) {
				StringBuffer sb1 = null, sb2 = null;
				if (includeDetails) {
					sb1 = new StringBuffer(Messages.DependencyList_0);
					sb2 = new StringBuffer();
				}
				int idx = (name != null) ? name.indexOf("!") : -1; //$NON-NLS-1$
				String resolved = config.resolve(name, features, dependentFeatures, sb2, true);
				String pluginName = idx > 0 ? name.substring(0, idx) : null;
				if (resolved != null && !resolved.equals(name)) {
					explicitDeps.add(name, new ModuleDepInfo(null, null, sb1 != null ? sb1.toString() : null));
					explicitDeps.add(resolved, new ModuleDepInfo(null, null, sb2 != null ? sb2.toString() : null));
					name = resolved;
				} else {
					explicitDeps.add(name, new ModuleDepInfo(null, null, sb1 != null ? sb1.append(sb2).toString() : null));
				}
				if (pluginName != null) {
					String resolvedPluginName = config.resolve(pluginName, features, dependentFeatures, null, true);
					expandedDeps.add(resolvedPluginName, new ModuleDepInfo(null, null, includeDetails ? Messages.DependencyList_1 : null));
					expandedDeps.addAll(
							deps.getExpandedDependencies(resolvedPluginName, features, dependentFeatures, includeDetails, performHasBranching));
				}
				if (name != null && name.length() > 0) {
					idx = name.indexOf("!"); //$NON-NLS-1$
					if (idx == -1) { 
						expandedDeps.addAll(
								deps.getExpandedDependencies(name, features, dependentFeatures, includeDetails, performHasBranching));
					} else {
						if (hasPattern.matcher(name.substring(0, idx)).find()) {
							explicitDeps.addAll(
								new HasNode(name.substring(idx+1)).evaluateAll(
										pluginName, 
										features, 
										dependentFeatures, 
										null,
										includeDetails ? Messages.DependencyList_0 : null
								)
							);
						}
						// If a plugin module is specified, then add it and its expanded dependencies.
						
						String pluginName2 = name.substring(0, idx);
						if (!pluginName2.equals(pluginName)) {
							String resolvedPluginName2 = config.resolve(pluginName2, features, dependentFeatures, null, true);
							expandedDeps.add(resolvedPluginName2, new ModuleDepInfo(null, null, includeDetails ? Messages.DependencyList_1 : null));
							expandedDeps.addAll(
									deps.getExpandedDependencies(resolvedPluginName2, features, dependentFeatures, includeDetails, performHasBranching));
						}
					}
				}
			}
		} finally {
			initialized = true;
		}
	}
}
