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

package com.ibm.jaggr.osgi;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.osgi.framework.console.CommandInterpreter;

/**
 * Implmentation of a Writer that sends output to a 
 * {@link CommandInterpreter} using its 
 * {@link CommandInterpreter#println()} method.
 */
public class ConsoleWriter extends Writer {
	
	private CommandInterpreter ci;
	private StringBuffer sb;
	private boolean closed; 
	
	public  ConsoleWriter(CommandInterpreter ci) {
		this.ci = ci;
		this.sb = new StringBuffer();
		this.closed = false;
	}

	/* (non-Javadoc)
	 * @see java.io.Writer#write(char[], int, int)
	 */
	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		if (closed) {
			throw new IOException("Console writer is closed."); //$NON-NLS-1$
		}
		for (int i = 0; i < len; i++) {
			char ch = cbuf[i];
			switch (ch) {
			case '\r':
				break;
			case '\n':
				ci.println(sb.toString());
				sb.setLength(0);
				break;
			default:
				sb.append(ch);
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.io.Writer#flush()
	 */
	@Override
	public void flush() throws IOException {
		if (closed) {
			throw new IOException("Console writer is closed."); //$NON-NLS-1$
		}
		ci.println(sb.toString());
		sb.setLength(0);
	}

	/* (non-Javadoc)
	 * @see java.io.Writer#close()
	 */
	@Override
	public void close() throws IOException {
		closed = true;
	}
}