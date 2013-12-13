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

package com.ibm.jaggr.service.deps;

import java.util.Arrays;
import java.util.Collection;

import com.ibm.jaggr.service.ProcessingDependenciesException;
import com.ibm.jaggr.service.config.IConfig;

/**
 * This class encapsulates the dependency graph of a collection of AMD modules.
 * The modules included in the graph are those modules found when scanning the
 * locations returned by {@link IConfig#getPaths()()} and
 * {@link IConfig#getPackageLocations()}. The dependencies of a module are those
 * modules listed in the require list of the define() function call within the
 * module. The expanded dependencies for a module include the explicitly
 * declared dependencies, plus all of the nested dependencies obtained by
 * including the dependencies of the explicitly dependent modules, plus their
 * dependencies, and so on.
 */
public interface IDependencies {
	/**
	 * Module names to be excluded when expanding.  These are pseudo modules that
	 * don't resolve to actual module resources. 
	 */
	public static final Collection<String> excludes = 
		Arrays.asList(new String[]{"require", "exports", "module"}); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	
	/**
	 * Returns the dependencies for the specified module. This is the
	 * list of modules listed in the module's define function. The module ids
	 * are normalized to remove relative paths.
	 * 
	 * @param mid
	 *            The module id
	 * @return The list of explicitly specified dependent modules, or null if
	 *         the specified module was not found when the dependencies were
	 *         processed.
	 */
	String[] getDelcaredDependencies(String mid) throws ProcessingDependenciesException;
	
	/**
	 * Returns the cumulative last-modified date of these dependencies which was
	 * determined the last time the dependencies were created or validated. In
	 * general, changes to source files which do not affect the dependencies
	 * do not cause this value to be updated, however, the addition or
	 * removal of dependencies within a dependent file cause the value
	 * returned from this method to reflect the most recent last-modified date
	 * of those files containing such changes at the time that they are
	 * detected.
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
