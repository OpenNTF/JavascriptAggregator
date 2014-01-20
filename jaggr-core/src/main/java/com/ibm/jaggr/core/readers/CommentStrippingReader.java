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

package com.ibm.jaggr.core.readers;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

/**
 * A reader that filters out block and line comments.
 * <p>
 * Note: This is not a full function comment stripper for JavaScript files.
 * In particular, it doesn't support JavaScript regular expressions, and
 * may improperly recognize comment start and end tokens inside regular
 * expressions.  It does the job for what we need, though, which is removing
 * comments from CSS and server-side JSON files.  
 */
public class CommentStrippingReader extends Reader {
	PushbackReader reader;
	boolean closed = false;
	boolean inSQ = false;
	boolean inDQ = false;
	int ch = ' ';
	
	public CommentStrippingReader(Reader reader) {
		this.reader = new PushbackReader(reader);
	}

	@Override
	public int read() throws IOException {
		if (closed) {
			throw new IOException("Attempt to read from a closed reader"); //$NON-NLS-1$
		}
		while (true) {
			int chPrev = ch;
			ch = reader.read();
			if (ch == -1) {
				return ch;
			}
			
			// Peek at the next character in the stream.
			int chNext = reader.read();
			if (chNext != -1) {
				// Can't push back eof
				reader.unread(chNext);
			}
			
			if (inSQ) {
				if (ch == '\'' && chPrev != '\\') {
					inSQ = false;
				}
				return ch;
			}
			if (inDQ) {
				if (ch == '\"' && chPrev != '\\') {
					inDQ = false;
				}
				return ch;
			}
			inDQ = ch == '\"';
			inSQ = ch == '\'';

			if (ch == '/') {
	            if (chNext == '*') {
	            	reader.read();
	            	// consume characters until the end of the block
	            	while (true) {
	            		ch = reader.read();
	            		if (ch == -1) {
	            			break;
	            		}
	            		if (ch != '*') {
	            			continue;
	            		}
	            		ch = reader.read();
	            		if (ch == '/') {
	            			break;
	            		}
	            		if (ch != -1) {
	            			// can't push back eof
	            			reader.unread(ch);
	            		} 
	            	}
	                continue;
	            }
	            if (chNext == '/') {
	            	reader.read();
	            	// consume characters until the end of the line
	            	while (true) {
	            		ch = reader.read();
	            		if (ch == '\n' || ch == '\r' || ch == -1) {
	            			break;
	            		}
	            	}
	            }
			}
	        return ch;
		}
	}
	
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		int i, ch = 0;
		for (i = 0; i < len; i++) {
			ch = read();
			if (ch == -1) {
				break;
			}
			cbuf[off+i] = (char)ch;
		}
		return (i == 0 && ch == -1) ? -1 : i;
	}

	@Override
	public int read(char[] cbuf) throws IOException {
		return read(cbuf, 0, cbuf.length);
	}
	
	@Override
	public void close() throws IOException {
		closed = true;
		reader.close();
		
	}
}
