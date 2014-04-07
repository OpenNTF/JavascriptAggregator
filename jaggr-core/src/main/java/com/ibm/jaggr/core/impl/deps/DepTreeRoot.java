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

package com.ibm.jaggr.core.impl.deps;

import com.ibm.jaggr.core.config.IConfig;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;

@SuppressWarnings("serial")
public class DepTreeRoot extends DepTreeNode {

	private IConfig config;

	public DepTreeRoot(IConfig config) {
		super("", null); //$NON-NLS-1$
		this.config = config;
	}

	@Override
	public IConfig getConfig() {
		return config;
	}

	@Override
	public DepTreeNode getRoot() {
		return this;
	}
	/**
	 * Instances of this object should not be serialized, but the
	 * base class is serializable, so we need to throw an
	 * exception here if an attempt is made to serialize this class.
	 *
	 * @param out The {@link ObjectOutputStream} to read from
	 * @throws IOException
	 * @throws NotSerializableException
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		throw new NotSerializableException();
	}
}