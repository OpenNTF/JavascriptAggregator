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

package com.ibm.jaggr.core.impl.layer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class VariableGZIPOutputStream extends GZIPOutputStream {

	public VariableGZIPOutputStream(OutputStream out) throws IOException {
		super(out);
	}
	public VariableGZIPOutputStream(OutputStream out, int size) throws IOException {
		super(out, size);
	}

	public void setLevel(int level) {
		def.setLevel(level);
	}
}
