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

package com.ibm.jaggr.core.readers;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;


/**
 * This class aggregates a set of {@link ModuleBuildReader IModule.Build}
 * objects and allows them to be read sequentially in the order that they occur
 * in the list.
 */
public class BuildListReader extends AggregationReader {
	final Iterator<ModuleBuildReader> iter;
	boolean hasErrors = false;
	IOException savedException = null;

	/**
	 * @param list
	 *            The list of build readers
	 */
	public BuildListReader(List<ModuleBuildReader> list) {
		super();
		this.iter = list.iterator();
	}

	/**
	 * Gets the next reader from the next reader in the list and 
	 * records if any readers are for error responses.  
	 * 
	 * @return The next reader
	 */
	@Override
	protected Reader getNextInputReader() throws IOException {
		if (!iter.hasNext()) {
			return null;
		}
		ModuleBuildReader reader = iter.next();
		if (reader == null) {
			return null;
		}
		if (reader.isError()) {
			hasErrors = true;
		}
		return reader;
		
	}
	/**
	 * Returns true if any of the ModuleReaders in this Future list had errors.
	 * This flag is valid only after all the futures in the list have completed
	 * and the contents of each reader have been read.
	 * 
	 * @return True if any reader had errors
	 */
	public boolean hasErrors() {
		return hasErrors;
	}
}