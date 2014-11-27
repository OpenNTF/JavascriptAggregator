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
package com.ibm.jaggr.core.transport;

import com.ibm.jaggr.core.BadRequestException;

import java.util.List;

/**
 * This interface encapsulates the modules specified in the request which should be included in the
 * response. The response may also contain nested dependencies of these modules, depending upon
 * configuration and options.
 * <p>
 * The result returned by the {@link #toString()} method is intended to be used as the cache key for
 * layers matching the same requested modules and should not require decoding of the encoded modules
 * names to produce.
 *
 */
public interface IRequestedModuleNames {

	/**
	 * Returns the list of modules requested by the Aggregator. These are modules requested by
	 * Aggregator generated requests and typically require decoding of the request information. As a
	 * performance optimization, the decoding of the request may be deferred until this method is
	 * called in attempts to try and avoid decoding if the request is already cached and the cached
	 * layer can be located using the value returned by {@link #toString()}.
	 *
	 * @return the requested modules.
	 * @throws BadRequestException
	 */
	public List<String> getModules() throws BadRequestException;

	/**
	 * Returns the list of modules specified by the <code>deps</code> request parameter. Deps are
	 * specified in application generated requests to load a server-expanded layer. Dep modules are
	 * included in the layer and are automatically required by the loader. The name of this property
	 * was chosen so as to mirror the <code>deps</code> AMD config property.
	 *
	 * @return the list of dep modules
	 * @throws BadRequestException
	 */
	public List<String> getDeps() throws BadRequestException;

	/**
	 * Returns the list of modules specified by the <code>preloads</code> request parameter.
	 * Preloads are specified in application generated requests to load a server-expanded layer.
	 * Preload modules are included in the layer but are not activated until required by the
	 * application.
	 *
	 * @return the list of preload modules
	 * @throws BadRequestException
	 */
	public List<String> getPreloads() throws BadRequestException;

	/**
	 * Returns the list of script modules specified by the <code>scripts</code> request parameter.
	 * Script modules are specified in application generated requests to load a boot layer. Script
	 * modules are non-AMD modules that are included at the beginning of a layer. These typically
	 * include the AMD loader config, the Aggregator loader config extension and the AMD loader. It
	 * can also include any other non-AMD script files needed by the application, as long as the
	 * file can be located using the AMD configuration.
	 *
	 * @return the list of script modules
	 * @throws BadRequestException
	 */
	public List<String> getScripts() throws BadRequestException;

	/**
	 * Returns the list of modules specified by the <code>excludes</code> request parameter.
	 * Excludes are specified in application generated requests to load a static layer.  The
	 * excluded modules, and their expanded dependencies, will not be included in the aggregated
	 * response.
	 *
	 * @return the list of excluded modules
	 * @throws BadRequestException
	 */
	public List<String> getExcludes() throws BadRequestException;

	/**
	 * @return a (possibly encoded) string representation of the requested modules.
	 */
	@Override
	public String toString();

}
