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

package com.ibm.jaggr.core.modulebuilder;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.resource.IResource;

/**
 * Result wrapper class for built content. Instances of this object are
 * returned by
 * {@link IModuleBuilder#build(String, IResource, HttpServletRequest, List)}.
 */
public final class ModuleBuild {
	private Object buildOutput;
	private List<IModule> before;
	private List<IModule> after;
	private List<ICacheKeyGenerator> keyGenerators;
	private boolean error;

	/**
	 * Convenience constructor utilizing a null cache key generator and no
	 * error.
	 * 
	 * @param buildOutput
	 *            The built output for the module
	 */
	public ModuleBuild(Object buildOutput) {
		this(buildOutput, null, false);
	}

	/**
	 * Full arguments constructor
	 * 
	 * @param buildOutput
	 *            The build output for the module
	 * @param keyGens
	 *            Array of cache key generators. If a non-provisional cache key
	 *            generator was supplied in the preceding call for this same
	 *            request to
	 *            {@link IModuleBuilder#getCacheKeyGenerators(IAggregator)} , or
	 *            the built output for the module is invariant with regard to
	 *            the request, then <code>keyGens</code> may be null.
	 * @param error
	 *            True if an error occurred while generating the build
	 */
	public ModuleBuild(Object buildOutput, List<ICacheKeyGenerator> keyGens, boolean error) {
		this.buildOutput = buildOutput;
		this.keyGenerators = keyGens;
		this.before = this.after = null;
		this.error = error;
		if (keyGens != null && KeyGenUtil.isProvisional(keyGens)) {
			throw new IllegalStateException();
		}
	}

	/**
	 * Returns the built (processed and minified) output for this request,
	 * as an AMD module string.
	 * 
	 * @return the build outupt
	 */
	public Object getBuildOutput() {
		return buildOutput;
	}

	/**
	 * Returns the non-provisional cache key generator for this module. Only
	 * required if
	 * {@link IModuleBuilder#getCacheKeyGenerators(IAggregator)}
	 * returned a provisional cache key generator for the same request.
	 * 
	 * @return The cache key generator
	 */
	public List<ICacheKeyGenerator> getCacheKeyGenerators() {
		return keyGenerators;
	}

	/**
	 * Returns true if this build is an error response. Error responses are
	 * not cached, either on the server or on the client.
	 * 
	 * @return True if a build error occurred
	 */
	public boolean isError() {
		return error;
	}
	
	/**
	 * Adds the specified module to the list of before modules.
	 * <p>
	 * Before modules are included in the layer build that contains this
	 * module build ahead of this module build.
	 * 
	 * @param module The module to add to the before list
	 */
	public void addBefore(IModule module) {
		if (before == null) {
			before = new LinkedList<IModule>();
		}
		before.add(module);
	}

	/**
	 * Adds the specified module to the list of after modules.
	 * <p>
	 * After modules are included in the layer build that contains this
	 * module build following this module build.
	 * 
	 * @param module The module to add to the after list
	 */
	public void addAfter(IModule module) {
		if (after == null) {
			after = new LinkedList<IModule>();
		}
		after.add(module);
	}
	
	/**
	 * Returns the list of additional modules that should be included ahead 
	 * of this module build in the layer
	 * 
	 * @return The list of before modules.
	 */
	public List<IModule> getBefore() {
		return before == null ? Collections.<IModule>emptyList() : Collections.unmodifiableList(before);
	}
	
	/**
	 * Returns the list of additional modules that should be included following 
	 * this module build in the layer
	 * 
	 * @return The list of after modules.
	 */
	public List<IModule> getAfter() {
		return after == null ? Collections.<IModule>emptyList() : Collections.unmodifiableList(after);
	}
}