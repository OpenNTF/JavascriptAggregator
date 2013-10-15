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

package com.ibm.jaggr.service.impl.modulebuilder.javascript;

import com.ibm.jaggr.service.ProcessingDependenciesException;

public class RuntimeProcessingDependenciesException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	/**
	 * This class is thrown by custom compiler pass modules when a
	 * {@link ProcessingDependenciesException is caught. The interface for
	 * custom compiler pass modules doesn't allow them to throw checked
	 * exceptions, so this class inherits from RuntimeException. Instances of
	 * this exception are caught by the JavaScript module builder which in turn
	 * throws {@link ProcessingDependenciesException}.
	 */
	RuntimeProcessingDependenciesException(ProcessingDependenciesException cause) {
		super(cause);
	}
}
