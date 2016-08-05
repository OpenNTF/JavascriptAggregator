/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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

package com.ibm.jaggr.core.deps;

import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * This class encapsulates the dependency graph of a collection of AMD modules.
 * The modules included in the graph are those modules found when scanning the
 * locations returned by {@link IConfig#getPaths()} and
 * {@link IConfig#getPackageLocations()}. The dependencies of a module are those
 * modules listed in the require list of the define() function call within the
 * module. The expanded dependencies for a module include the explicitly
 * declared dependencies, plus all of the nested dependencies obtained by
 * including the dependencies of the explicitly dependent modules, plus their
 * dependencies, and so on.
 */
public interface IDependencies {
	/**
	 * Module names to be excluded when expanding. These are pseudo modules that
	 * don't resolve to actual module resources.
	 */
	public static final Collection<String> excludes = Arrays
			.asList(new String[] { "require", "exports", "module" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

	/**
	 * Default non-JavaScript file extensions to include in the scanned dependencies names list returned
	 * by {@link #getDependencyNames()}.
	 */
	public static final String[] defaultNonJSExtensions = new String[]{"html", "css"}; //$NON-NLS-1$ //$NON-NLS-2$

	/**
	 * Name of config property specifying optional additional file extension names to include in the
	 * scanned dependencies name list returned by {@link #getDependencyNames()}.
	 */
	public static final String nonJSExtensionsCfgPropName = "depScanIncludeExtensions"; //$NON-NLS-1$

	/**
	 * Returns the dependencies for the specified module. This is the list of
	 * modules listed in the module's define function. The module ids are
	 * normalized to remove relative paths.
	 *
	 * @param mid
	 *            The module id
	 * @return An unmodifiable list of explicitly specified dependent modules,
	 *         or null if the specified module was not found when the
	 *         dependencies were processed.
	 * @throws ProcessingDependenciesException
	 */
	List<String> getDelcaredDependencies(String mid)
			throws ProcessingDependenciesException;

	/**
	 * Returns the list of modules specified in the dependency lists of any require calls contained
	 * within the specified module. If the specified module does not contain any require calls, then
	 * an empty list is returned.
	 * <p>
	 * Note that no attempt is made to determine if the require call will ever be executed by the
	 * client by looking for dead code, etc. Any require calls that appear within the module will be
	 * used in collecting require dependencies.
	 *
	 * @param mid
	 *            The module id
	 * @return An unmodifiable list of require call dependencies, or null if the specified module
	 *         was not found when the dependencies were processed.
	 * @throws ProcessingDependenciesException
	 */
	List<String> getRequireDependencies(String mid)
			throws ProcessingDependenciesException;

	/**
	 * Returns the list of dependent features for the specified module. This
	 * includes features specified as string literals in has() function calls,
	 * as well as features specified using a has! loader plugin in require and
	 * define dependency lists.
	 * <p>
	 * Note that not all feature names used in has() function calls may be
	 * returned in this list. Only features that are used in has() function
	 * calls who's return value is used as a boolean are included (e.g.
	 * <code>if (has('isIE')) { </code>). If the result of the has function call
	 * is used as another type, then the feature may not be be included in the
	 * returned list (e.g. <code>var isIE = has('isIE'); </code> or
	 * <code>if (has('isIE') < 8) { </code>). This is because feature values are
	 * specified in aggregator requests as booleans, so aggregator optimizations
	 * based on defined features may not be safely performed if the result of a
	 * feature evaluation is used as a type other than boolean.
	 * <p>
	 *
	 * @param
	 *            mid the module id
	 * @return an unmodifiable list of the dependent features, or null if the
	 *         specified module was not found when the dependencies were
	 *         processed.
	 * @throws ProcessingDependenciesException
	 */
	public List<String> getDependentFeatures(String mid)
			throws ProcessingDependenciesException;

	/**
	 * Returns the resource URI for the specified module id. Module ids returned by
	 * {@link #getDependencyNames()} will return a non-null value. Any other value passed for
	 * <code>mid</code> will result in a return value of null.
	 *
	 * @param mid the module id
	 *
	 * @return the resource URI for the module, or null
	 * @throws ProcessingDependenciesException
	 */
	public URI getURI(String mid)
			throws ProcessingDependenciesException;


	/**
	 * Returns an {@link Iterable} over the module names that were scanned while building the
	 * dependency map. This includes JavaScript modules as well as selected non-JavaScript modules.
	 * <p>
	 * Non-JavaScript modules included in the result include the following:
	 * <ul>
	 * <li>modules with extensions specified in {@link #defaultNonJSExtensions}</li>
	 * <li>modules with extensions specified in the server-side config property named
	 * {@link #nonJSExtensionsCfgPropName}</li>
	 * <li>modules with extensions specified by the
	 * {@link IModuleBuilderExtensionPoint#EXTENSION_ATTRIBUTE} config property for the registered
	 * module builders</li>
	 * </ul>
	 * Only JavaScript modules have dependencies.  If a non-JavaScript module included in the
	 * result is passed to {@link #getDelcaredDependencies(String)} or {@link #getDependentFeatures(String)},
	 * an empty list will be returned.
	 *
	 * @return the module names in the dependency map.
	 * @throws ProcessingDependenciesException
	 */
	public Iterable<String> getDependencyNames()
			throws ProcessingDependenciesException;

	/**
	 * Returns the cumulative last-modified date of these dependencies which was
	 * determined the last time the dependencies were created or validated. In
	 * general, changes to source files which do not affect the dependencies do
	 * not cause this value to be updated, however, the addition or removal of
	 * dependencies within a dependent file cause the value returned from this
	 * method to reflect the most recent last-modified date of those files
	 * containing such changes at the time that they are detected.
	 *
	 * @return The cumulative last modified date of the module dependencies
	 */
	public long getLastModified();

	/**
	 * Validates the dependency graph by scanning the directories returned by
	 * {@link IConfig#getPaths()} and {@link IConfig#getPackageLocations()}
	 * looking for new/changed/removed files and updating the dependency graph,
	 * including the dependency graph last-modified timestamp, as needed.
	 *
	 * @param clean
	 *            If true, then any cached or previously calculated dependency
	 *            graph is discarded and a new graph is generated from scratch.
	 *            Use this option if file or directory last-modified dates
	 *            cannot be relied upon to accurately reflect the changed state
	 *            of the module files included in the graph.
	 */
	public void validateDeps(boolean clean);

}
