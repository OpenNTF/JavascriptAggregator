/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
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
