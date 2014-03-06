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

package com.ibm.jaggr.core.deps;

import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.config.IConfig;

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
	 * Returns an {@link Iterable} over the module names for witch dependency
	 * information is contained in the dependency map. Calling
	 * {@link #getDelcaredDependencies(String)} or
	 * {@link #getDependentFeatures(String)} with any of the module names
	 * returned by this method is guaranteed to return a non-null value.
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
