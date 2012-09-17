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

public class ConsoleService {

	private static ThreadLocal<CommandInterpreter> ci_threadLocal = new ThreadLocal<CommandInterpreter>();

	private CommandInterpreter ci = null;

	public ConsoleService() {
		ci = ci_threadLocal.get();
	}
	
	public ConsoleService(CommandInterpreter ci) {
		this.ci = ci;
		ci_threadLocal.set(ci);
	}

	public ConsoleService(ConsoleService other) {
		this.ci = other.ci;
		ci_threadLocal.set(other.ci);
	}
	
	public void println(String msg) {
		if (ci != null) {
			ci.println(msg);
		}
	}

	public void print(String msg) {
		if (ci != null) {
			ci.print(msg);
		}
	}
	
	public void close() {
		ci = null;
		ci_threadLocal.set(null);
	}
}
