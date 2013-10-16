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

package com.ibm.jaggr.service.console.commands;

import java.util.Dictionary;

import org.eclipse.osgi.framework.console.CommandInterpreter;
import org.osgi.framework.Bundle;

public class CommandInterpreterWrapper implements CommandInterpreter {
	
	@Override
	public void println(Object arg) {
		System.out.println(arg);
	}
	
	@Override
	public void println() {
		System.out.println();
	}
	
	@Override
	public void printStackTrace(Throwable t) {
		t.printStackTrace(System.out);
	}
	
	@Override
	public void printDictionary(@SuppressWarnings("rawtypes") Dictionary arg0, String arg1) {
		throw new RuntimeException("Not Implemented."); //$NON-NLS-1$
	}
	
	@Override
	public void printBundleResource(Bundle arg0, String arg1) {
		throw new RuntimeException("Not Implemented."); //$NON-NLS-1$
	}
	
	@Override
	public void print(Object arg) {
		System.out.print(arg);
	}
	
	@Override
	public String nextArgument() {
		return null;
	}
	
	@Override
	public Object execute(String arg0) {
		throw new RuntimeException("Not Implemented."); //$NON-NLS-1$
	}
	
}
