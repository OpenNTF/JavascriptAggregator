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

import com.ibm.jaggr.core.util.ConsoleService.ConsoleWriter;

import org.apache.felix.service.command.CommandSession;

public class CSConsoleWriter implements ConsoleWriter {

	private final CommandSession cs;
	
	public CSConsoleWriter(CommandSession cs) {
		this.cs = cs;
	}
	
	@Override
	public void println(String msg) {
		cs.getConsole().println(msg);
	}

	@Override
	public void print(String msg) {
		cs.getConsole().print(msg);
	}

}
