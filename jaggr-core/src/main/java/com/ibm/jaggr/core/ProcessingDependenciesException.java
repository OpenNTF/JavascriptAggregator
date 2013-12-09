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

package com.ibm.jaggr.core;

import java.io.IOException;


/**
 * Thrown when the aggregator is servicing a request the dependency graph is
 * in the process of being built/validated, and development mode is enabled.
 */
public class ProcessingDependenciesException extends IOException {
	private static final long serialVersionUID = -8334623620467466846L;

}
