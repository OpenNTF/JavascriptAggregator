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

package com.ibm.jaggr.core.module;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICache;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.resource.IResource;

/**
 * IModule objects are responsible for creating and organizing collections of
 * cached module builds for a given module. Module builds may be request
 * specific, and can provide a cache key generator to be used by the layer
 * manager, which assembles multiple module builds into cached layer builds, 
 * to combine the cache keys for individual module builds into a cache key
 * that can be used for all the modules in the layer.
 * <p>
 * Collections of IModule objects and {@link ILayer} objects together constitute
 * the cache metadata encapsulated by an {@link ICache} object. Instances of
 * this object are serialized, through serialization of the containing
 * {@link ICache} object, by the cache manager when it periodically saves a
 * snapshot of the cache metadata to disk. Serialization is actually performed
 * on clones of the objects in the cache in order to avoid performance
 * degradation which would be caused by locking of critical sections during file
 * I/O.
 * <p>
 * Instances of {@code IModule} are created by calling {@link IAggregator#newModule(String, URI)}.
 * <p>
 * IModules objects are cloneable and serializable.
 */
public interface IModule extends Serializable {

	/**
	 * Returns a <code>{@link Future}&lt;{@link ModuleBuildReader}&gt;</code> to the
	 * module build for the specified request. The build object is returned
	 * asynchronously in order to facilitate concurrent processing of module
	 * builds.
	 * 
	 * @param request
	 *            The HTTP request object
	 * @return A {@link Future} to a {@link ModuleBuildReader} object that will be available
	 *         at some point in the future.
	 * @throws IOException
	 */
	public Future<ModuleBuildReader> getBuild(HttpServletRequest request) throws IOException;

	/**
	 * Delete any cache files associated with this module
	 * 
	 * @param mgr
	 *            The cache manager. Modules can use
	 *            {@link ICacheManager#deleteFileDelayed(String)} to
	 *            asynchronously schedule cache files for deletion following a
	 *            delay period, avoiding the potential for synchronization
	 *            issues associated with the use of cache files by multiple
	 *            threads.
	 */
	public void clearCached(ICacheManager mgr);
	
	/**
	 * Returns the module id for this module.  The id includes the plugin name,
	 * if any, and the module name separated by a '!'.
	 * 
	 * @return The module id
	 */
	public String getModuleId();

	/**
	 * Returns the module name.  The module name is the name part of the module id,
	 * excluding the plugin name.
	 * 
	 * @return the module name
	 */
	public String getModuleName();
	
	/**
	 * Returns the plugin name.  The plugin name is the part of the module id
	 * preceding the '!' character.
	 * 
	 * @return the plugin name.
	 */
	public String getPluginName();
	/**
	 * Returns the source url for this module
	 * 
	 * @return The url
	 */
	public URI getURI();
	
	/**
	 * This method is provided so that the module can function as a runtime cache 
	 * for the {@link IResource} object associated with the module URI that was 
	 * used to construct this instance.
	 * <p>
	 * Note that instances of {@code IModule} are both Serializable 
	 * and Cloneable, yet instances of {@code IResource} are neither, so the module
	 * must not attempt to save the cached {@code IResource} object to its 
	 * persistent state, nor to object clones.

	 * @param aggregator the aggregator.  The module may use the {@link IAggregator#newResource(URI)}
	 * method to obtain a new instance of the {@code IResource}.
	 * @return the {@code IResource} object for this module's URI.
	 */
	public IResource getResource(IAggregator aggregator);
}
