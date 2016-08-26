/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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

import java.io.Serializable;

/**
 * This class encapsulates information relating to the source map for a module
 */
public class SourceMap implements Serializable {
	private static final long serialVersionUID = -1635289447136069250L;

	public final String name;
	public final String source;
	public final String map;

	public SourceMap(String name, String source, String map) {
		this.name = name;
		this.source = source;
		this.map = map;
	}
}
