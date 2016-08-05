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

package com.ibm.jaggr.core.util;

public class ConsoleService {

	private static ThreadLocal<ConsoleWriter> console_threadLocal = new ThreadLocal<ConsoleWriter>();

	private ConsoleWriter console;

	public ConsoleService() {
		console = console_threadLocal.get();
	}

	public ConsoleService(ConsoleWriter console) {
		this.console = console;
		console_threadLocal.set(console);
	}

	public ConsoleService(ConsoleService other) {
		this.console = other.console;
		console_threadLocal.set(other.console);
	}

	public void println(String msg) {
		if (console != null) {
			console.println(msg);
		}
	}

	public void print(String msg) {
		if (console != null) {
			console.print(msg);
		}
	}

	public void close() {
		console = null;
		console_threadLocal.set(null);
	}

	static public interface ConsoleWriter {

		public void println(String msg);

		public void print(String msg);

	}
}
