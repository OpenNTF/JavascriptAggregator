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

package com.ibm.jaggr.core.cachekeygenerator;

import java.io.Serializable;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;

/**
 * Instances of CacheKeyGenerator are used to create request specific cache keys
 * for modules. These keys are used to select previously generated and cached
 * builds to satisfy subsequent requests for the same module which yield
 * identical keys.
 * <p>
 * Like Strings, CacheKeyGenerators are immutable. A cache key generator must
 * contain sufficient information within its instance data to be able to
 * generate a cache key for a request without referring to the module that the
 * generator is associated with. Because cache key generators are immutable,
 * they must not be affected by changes to the originating module source.
 * <p>
 * The requirement that CacheKeyGenerators be immutable allows cloneable objects
 * like layers and modules, which maintain references to cache key generators,
 * to clone the CacheKeyGenerators by reference, and so instances of
 * CacheKeyGenerator do not, themselves, need to be cloneable.
 * <p>
 * A layer is identified by the collection of modules that it aggregates. Each
 * layer manages a cache of layer builds, were each layer build aggregates a
 * collection of module builds that were built with different request
 * attributes. Associated with each layer is a map of CacheKeyGenerators where
 * the map key is a CacheKeyGeneator class name and the map value is a
 * CacheKeyGenerator object that was created by calling
 * {@link ICacheKeyGenerator#combine(ICacheKeyGenerator)} on the
 * CacheKeyGenerators associated with each module in the layer that have the
 * same CacheKeyGenerator class name. The cache keys obtained from each of these
 * per-class combined generators are then concatenated to form the cache key for
 * the layer build.
 * <p>
 * If a module builder implementation does not need to generate a cache key
 * because the builder's response data is invariant with regard to the request,
 * then it may provide a null CacheKeyGenerator.
 */
public interface ICacheKeyGenerator extends Serializable {

	/**
	 * Returns a string that will be used to identify cached responses for the
	 * specified request. The key should be prefixed with a short (2-5
	 * character) string ending with a colon which identifies this key
	 * generator.
	 * <p>
	 * For clarity's sake, keys should avoid using semi-colons since these are
	 * used as a separator when aggregating the keys from multiple cache key
	 * generators.
	 * <p>
	 * Note that {@link IOptions} properties do not need to be factored into the
	 * returned key since any changes in these properties invalidates cached
	 * responses and clears the module and layer caches.
	 * 
	 * @param request
	 *            The request object
	 * @return the cache key for the request
	 */
	public String generateKey(HttpServletRequest request);

	/**
	 * Combines this cache key generator with the specified cache key generator
	 * so that the cache key returned by the resulting generator can be used to
	 * identify a layer build containing the module(s) represented by this cache
	 * key generator as well as the module(s) represented by the specified cache
	 * key generator.
	 * <p>
	 * For most implementations, these two generators will be identical and a
	 * reference to the current object should be returned. Only implementations
	 * who's cache keys are dependent on module content or other factors
	 * external to the request object need to provide a non-trivial
	 * implementation for this method. An example would be a javascript minifier
	 * which performs has.js feature trimming of code based on the list of
	 * feature names provided in the request. Cache keys for this module might
	 * identify the feature conditionals that the module actually contains,
	 * allowing cached module builds to be reused for requests that specify the
	 * same values for the set of features that are of concern to the module
	 * while ignoring those that are not relevant. In this example, the combine
	 * method would create a new CacheKeyGenerator from the union of the feature
	 * name sets from this generator and the specified generator.
	 * <p>
	 * If either this cache key generator or the specified cached key generator
	 * is a provisional cache key generator and the other is not, the returned
	 * object must be a non-provisional cache key generator.
	 * <p>
	 * If combining this cache key generator with the specified cache key
	 * generator yields a cache key generator that produces identical cache keys
	 * as this cache key generator, then this cache key generator should be 
	 * returned.
	 * 
	 * @param other
	 *            A reference to the CacheKeyGenerator object that is to be
	 *            combined with this object. The Aggregator guarantees that
	 *            {@code other} will be of the same type as the current object,
	 *            so it is always safe to cast {@code other} to an instance of
	 *            the current object.
	 * 
	 * @return The combined CacheKeyGenerator, or this object if combining 
	 *         this object with <code>other</code> will produce a cache key
	 *         generator that generates the same keys as this object.
	 */
	public ICacheKeyGenerator combine(ICacheKeyGenerator other);

	/**
	 * This method returns the (possibly empty) list of constituent cache key
	 * generators belonging to a composite cache key generator. If this cache
	 * key generator is not a composite cache key generator, then it is referred
	 * to as an identity cache key generator, and should return a value of null
	 * for this method.
	 * <p>
	 * Composite cache key generators are useful for controlling the output of
	 * other cache key generators in ways that depend on aspects of the current
	 * request that are outside the scope of the key generator being controlled.
	 * Composite cache key generators must adhere to the following restrictions:
	 * <ul>
	 * <li>
	 * All of the cache key generators returned by this method must be identity
	 * cache key generators.</li>
	 * <li>
	 * The output of the {@link #generateKey(HttpServletRequest)} method for
	 * this cache key generator should be the same as the combined (semi-colon
	 * delimited) output of the {@code generateKey} methods for the cache key
	 * generators returned by this method.</li>
	 * </ul>
	 * The layer manager will call this method to decompose composite cache key
	 * generators into their constituent key generators so that the cache key
	 * generators contributing to a layer cache key may be combined more
	 * effectively.
	 * 
	 * @param request
	 *            The request object
	 * @return Array of constituent cache key generators, or null.
	 */
	public List<ICacheKeyGenerator> getCacheKeyGenerators(HttpServletRequest request);
	
	/**
	 * Instances of this object can be provisional. If cache keys for the module
	 * associated with this generator need to depend on the content of the
	 * module source or other factors external to the request object which might
	 * require blocking I/O in order to determine, then
	 * {@link IModuleBuilder#getCacheKeyGenerators(IAggregator)} should return a
	 * provisional cache key generator, which generates keys based on
	 * information available in the request only. The builder should then
	 * provide a non-provisional cache key generator in the {@link ModuleBuild}
	 * object returned from
	 * {@link IModuleBuilder#build(String, IResource, HttpServletRequest, List)}
	 * when it is subsequently called by a separate worker thread for the same
	 * request.
	 * <p>
	 * The cache key returned by a provisional cache key generator must be
	 * either more specific than, or as specific as, the cache key returned by a
	 * non-provisional cache key generator for the same module with the same
	 * request parameters. The requirement is that if a provisional cache key
	 * generator returns identical keys for two different requests, then the
	 * non-provisional cache key generator must also return identical keys for
	 * the same two requests. The converse is not a requirement.
	 * <p>
	 * Provisional cache key generators are used to allow the module cache
	 * manager to cache module builds while they are still pending, potentially
	 * allowing an in-progress build to satisfy other outstanding requests. This
	 * need arises when information about the contents of the module, which is
	 * gained in the process of building it, can be used to broaden the scope of
	 * the key by ignoring request parameters or features that are not
	 * applicable to the module as determined by its content.
	 * 
	 * @return True if this is a provisional cache key generator
	 */
	public boolean isProvisional();
	
	/**
	 * Cache key generators should implement this function to return a short
	 * keyword identifier for the generator (typically the same keyword used 
	 * in generated keys) plus a string representation of the instance data 
	 * that is used in constructing the keys (if any).
	 * 
	 * @return The string representation of the generator
	 */
	@Override
	public String toString();
	
	/**
	 * This method should return true if this object's {@link #generateKey(HttpServletRequest)}
	 * method will always return the same value as <code>other</code> for the same 
	 * {@link HttpServletRequest} object.  Classes that implement this method should
	 * implement {@link Object#hashCode()} as well.
	 * 
	 * @return True if this object is logically equal to <code>other</code>
	 */
	@Override
	public boolean equals(Object other);
	
}