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
package com.ibm.jaggr.service.util;

import org.eclipse.osgi.framework.console.CommandInterpreter;

import com.ibm.jaggr.core.util.ConsoleService.ConsoleWriter;

public class CIConsoleWriter implements ConsoleWriter {

	private final CommandInterpreter ci;

	public CIConsoleWriter(CommandInterpreter ci) {
		this.ci = ci;
	}
	
	@Override
	public void println(String msg) {
		ci.println(msg);
	}

	@Override
	public void print(String msg) {
		ci.print(msg);
	}
	
}