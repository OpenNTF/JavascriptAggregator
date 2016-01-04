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

package com.ibm.jaggr.blueprint;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;

import org.eclipse.osgi.framework.console.CommandInterpreter;
import org.osgi.framework.Bundle;

public class CommandInterpreterWrapper implements CommandInterpreter {

	private final Iterator<String> iter;
	private final StringWriter writer;
	private final PrintWriter out;

	public CommandInterpreterWrapper(List<String> _args) {
		List<String> args = new ArrayList<String>(_args);
		args.add(null);		// add terminator
		iter = args.iterator();
		writer = new StringWriter();
		out = new PrintWriter(writer);
		
	}

	public String getOutput() {
	  out.flush();
	  return writer.toString();
	}
	
	@Override
	public void println(Object arg) {
	  out.println(arg == null ? "null" : arg.toString());
	}

	@Override
	public void println() {
		out.println();
	}

	@Override
	public void printStackTrace(Throwable t) {
		t.printStackTrace(out);
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
		out.print(arg);
	}

	@Override
	public String nextArgument() {
		return iter.next();
	}

	@Override
	public Object execute(String arg0) {
		throw new RuntimeException("Not Implemented."); //$NON-NLS-1$
	}

}
