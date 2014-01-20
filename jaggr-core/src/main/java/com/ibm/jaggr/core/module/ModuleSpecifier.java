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

import com.ibm.jaggr.core.transport.IHttpTransport;

/**
 * An enum specifying the possible sources from which a module 
 * is specified.
 */
public enum ModuleSpecifier {
	/**
	 * The module was specified by {@link IHttpTransport#REQUESTEDMODULES_REQATTRNAME},
	 * or is a dependency of such a module
	 */
	MODULES,	
	/**
	 * The module was specified by {@link IHttpTransport#REQUIRED_REQATTRNAME},
	 * or is a dependency of such a module
	 */
	REQUIRED,
	/**
	 * The module was added by a module builder.
	 */
	BUILD_ADDED
}
