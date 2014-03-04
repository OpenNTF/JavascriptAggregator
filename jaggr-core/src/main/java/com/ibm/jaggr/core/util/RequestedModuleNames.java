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

import com.ibm.jaggr.core.config.IConfig;

import java.util.Collections;
import java.util.List;

/**
 * This class encapsulates the list of module names specified in a request. There are two types of
 * requests that are supported. The first uses only the <code>modules</code> property to specify the
 * list of AMD modules requested by the loader on the client. The second type of request is an
 * application generated request used to load the initial bootstrap layer for the application and
 * uses the <code>modules</code>, <code>deps</code> and/or <code>preloads</code> properties to
 * specify non-AMD script files as well as AMD modules to include in the bootstrap layer.
 * <p>
 * See the javadoc for the property setters for more details.
 */
public class RequestedModuleNames {

	private List<String> modules = Collections.emptyList();
	private List<String> deps = Collections.emptyList();
	private List<String> preloads = Collections.emptyList();
	private String strRep = null;

	/**
	 * @return the value set by {@link #setModules(List)}
	 */
	public List<String> getModules() {
		return modules;
	}

	/**
	 * Sets the list of modules that, together with their expanded dependencies, will be included in
	 * the response. For Aggregator generated requests, this is the list of AMD modules requested by
	 * the AMD loader. For Application generated requests, this specifies the list of non-AMD script
	 * files (typically including the AMD loader-config, the Aggregator loader extension javascript
	 * and the AMD loader itself) to include in the boot layer along with the AMD modules specified
	 * using {@link #setDeps(List)} and {@link #setPreloads(List)}.
	 *
	 * @param modules
	 */
	public void setModules(List<String> modules) {
		if (modules == null) {
			throw new NullPointerException();
		}
		this.modules = modules;
	}

	/**
	 * @return the value set by {@link #setDeps(List)}
	 */
	public List<String> getDeps() {
		return deps;
	}

	/**
	 * Sets the list of modules ids that, together with their expanded dependencies, will be
	 * included in the boot layer response. Upon loading the response on the client, the modules set
	 * using this method will be initialized using a synthetic require() call.
	 * <p>
	 * Typically used to load a bootstrap layer of modules using the same request that is used to
	 * load the loader and the loader config. It is not used for aggregator generated requests. In
	 * this scenario, the loader together with any non-AMD modules like the client-side loader
	 * config and the AMD loader itself are specified using {@link #setModules(List)} and the top
	 * level AMD modules (typically, the same as those specified in the <code>deps</code> property
	 * of the AMD loader config) are set using this method.
	 * <p>
	 * Note: Modules specifying a loader plugin will be included in the response only if the loader
	 * plugin is specified in the {@link IConfig#TEXTPLUGINDELEGATORS_CONFIGPARAM} config param, or
	 * the {@link IConfig#JSPLUGINDELEGATORS_CONFIGPARAM} config param. For the {@code has} loader
	 * plugin, the module will be included only if the feature specified by the plugin is defined in
	 * the feature set specified in the request.
	 *
	 * @param deps
	 *          the list of boot layer modules to require
	 */
	public void setDeps(List<String> deps) {
		if (modules == null) {
			throw new NullPointerException();
		}
		this.deps = deps;
	}

	/**
	 * @return the value set by {@link #setPreloads(List)}
	 */
	public List<String> getPreloads() {
		return preloads;
	}

	/**
	 * Sets the list of modules ids that, together with their expanded dependencies, will be
	 * included in the boot layer response. The modules are not initialized upon loading on the
	 * client (unless they are direct or indirect dependencies of modules specified by
	 * {@link #setDeps(List)}, but are available in the loader's module cache for immediate
	 * resolution when needed.
	 * <p>
	 * Typically used to load a bootstrap layer of modules using the same request that is used to
	 * load the loader and the loader config. It is not used for aggregator generated requests. It
	 * is used to preload modules that are not immediately required by the modules specified using
	 * {@link #setDeps(List)} but will result in extra HTTP requests to load them if not preloaded
	 * (typically, loader plugins fall into this category).
	 * <p>
	 * Note: Modules specifying a loader plugin will be included in the response only if the loader
	 * plugin is specified in the {@link IConfig#TEXTPLUGINDELEGATORS_CONFIGPARAM} config param, or
	 * the {@link IConfig#JSPLUGINDELEGATORS_CONFIGPARAM} config param. For the {@code has} loader
	 * plugin, the module will be included only if the feature specified by the plugin is defined in
	 * the feature set specified in the request.
	 *
	 * @param preloads
	 *            the list of boot layer modules to preload
	 */
	public void setPreloads(List<String> preloads) {
		if (modules == null) {
			throw new NullPointerException();
		}
		this.preloads = preloads;
	}

	/**
	 * Sets the value returned by this object's {@link #toString()} method.
	 *
	 * @param strRep
	 *            the string value of this object
	 */
	public void setString(String strRep) {
		this.strRep = strRep;
	}

	/**
	 * Returns a string representation of the object. If one hasn't been specified using
	 * {@link #setString(String)}, then the string is constructed from the <code>modules</code>,
	 * <code>deps</code> and <code>preloads</code> lists.
	 */
	@Override
	public String toString() {
		String result = null;
		if (strRep != null) {
			result = strRep;
		} else {
			StringBuffer sb = new StringBuffer();
			if (modules != null && !modules.isEmpty()) {
				sb.append(modules);
			}
			if (deps != null && !deps.isEmpty()) {
				sb.append(sb.length() > 0 ? ";":"").append("deps:").append(deps); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			if (preloads != null && !preloads.isEmpty()) {
				sb.append(sb.length() > 0 ? ";":"").append("preloads:").append(preloads); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			result = sb.toString();
		}
		return result;
	}


}
