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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.jaggr.core.readers.JavaScriptEscapingReader;

public class StringUtil {

	private static final Logger log = Logger.getLogger(StringUtil.class.getName());
	
	static public String escapeForJavaScript(String in) {
		if (in == null) {
			return null;
		}
		StringWriter writer = new StringWriter();
		try {
			CopyUtil.copy(new JavaScriptEscapingReader(new StringReader(in)), writer);
		} catch (IOException e) {
			// Shouldn't happen since we're dealing only with strings
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		return writer.toString();
	}
}
