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

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class used to assist with making modifications to javascript source files
 * relative to the locations of element corresponding to nodes in a parsed AST tree.
 */
public class JSSource {
	private static final Logger log = Logger.getLogger(JSSource.class.getName());

	static private class LineInfo {
		LineInfo(String line) {
			originalStr = str = line;
			edits = null;
		}
		String str;
		final String originalStr;
		List<Integer> edits; // list of offset/length pairs
	}

	private List<LineInfo> lines = new ArrayList<LineInfo>();
	private boolean modified = false;
	private final String originalSource;
	private final String mid;		// for trace logging
	private final boolean isTraceLogging;

	public JSSource(String source, String mid) throws IOException {
		final String sourceMethod = "<ctor>"; //$NON-NLS-1$
		isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(JSSource.class.getName(), sourceMethod, new Object[]{"<content omitted>", mid}); //$NON-NLS-1$
		}
		originalSource = source;
		this.mid = mid;
		BufferedReader rdr = new BufferedReader(new StringReader(source));
		while (true) {
			String line = rdr.readLine();
			if (line != null) {
				lines.add(new LineInfo(line));
				continue;
			}
			break;
		}
		if (isTraceLogging) {
			log.exiting(JSSource.class.getName(), sourceMethod, lines.size() + " lines read from " + mid); //$NON-NLS-1$
		}
	}

	/**
	 * Inserts the specified string into the source at the specified location
	 *
	 * @param str
	 *            the string to insert
	 * @param lineno
	 *            the line number to insert to
	 * @param colno
	 *            the column number to insert to
	 */
	public void insert(String str, int lineno, int colno) {
		final String sourceMethod = "insert"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(JSSource.class.getName(), sourceMethod, new Object[]{str, lineno, colno});
		}
		PositionLocator locator = new PositionLocator(lineno, colno);
		locator.insert(str);
		if (isTraceLogging) {
			log.exiting(JSSource.class.getName(), sourceMethod, "String \"" + str + "\" inserted at " + mid + "(" + lineno + "," + colno + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}
	}

	@Override
	public String toString() {
		final String sourceMethod = "toString"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(JSSource.class.getName(), sourceMethod);
		}
		String result = originalSource;
		if (modified) {
			StringBuffer sb = new StringBuffer();
			int i = 0;
			for (LineInfo line : lines) {
				sb.append(i++ == 0 ? "" : "\r\n").append(line.str); //$NON-NLS-1$ //$NON-NLS-2$
			}
			result = sb.toString();
		}
		if (isTraceLogging) {
			// output only modified lines
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < lines.size(); i++) {
				LineInfo line  = lines.get(i);
				if (line.str != line.originalStr) {
					sb.append("line ").append(i+1).append(": ").append(line.originalStr) //$NON-NLS-1$ //$NON-NLS-2$
					  .append(" --> ").append(line.str).append("\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			sb.insert(0, sb.length() == 0 ? ("No lines modified for " + mid) : ("Displaying only modified lines for " + mid + ":\r\n")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			log.exiting(JSSource.class.getName(), sourceMethod, sb.toString());
		}
		return result;
	}

	/**
	 * Locates the array literal specified by the <code>array</code> node, in the source using the
	 * source location information provided by the node, and inserts <code>str</code> as a string
	 * literal (quoted) in the source at the end of the array.
	 *
	 * @param array
	 *           Node with source position information
	 * @param str
	 *           the string to insert into the source at the end of the specified array
	 */
	public void appendToArrayLit(Node array, String str) {
		final String sourceMethod = "appendToArrayLit"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(JSSource.class.getName(), sourceMethod, new Object[]{array, str});
		}
		// Get the node for the last element in the array
		Node lastChild = array.getLastChild();

		// If token is not a string, then punt
		if (lastChild.getType() != Token.STRING && lastChild.getType() != Token.NAME) {
			if (log.isLoggable(Level.WARNING)) {
				log.warning("Last element of array at " + mid + "(" + array.getLineno() + "," + array.getCharno() + ") is type " + array.getType()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}
			if (isTraceLogging) {
				log.exiting(JSSource.class.getName(), sourceMethod);
			}
			return;
		}
		int len = lastChild.getString().length() + (lastChild.getType() == Token.STRING ? 2 : 0);  // add 2 for string quotes

		// Search the source for the closing array bracket ']', skipping over
		// whitespace and comments.  In order to be valid javascript, the next
		// token must be the closing bracket.
		int lineno = lastChild.getLineno();
		int charno = lastChild.getCharno() + len;
		PositionLocator pos = new PositionLocator(lineno, charno);
		if (pos.findNextJSToken() == ']') {
			insert(",\"" + str + "\"", pos.getLineno(), pos.getCharno()); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			if (log.isLoggable(Level.WARNING)) {
				log.warning("Closing array bracket not found in " + mid + "(" + lineno + "," + charno + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			}
		}
		if (isTraceLogging) {
			log.exiting(JSSource.class.getName(), sourceMethod);
		}
	}

	/**
	 * Encapsulates a position in the source file and provides methods to search for elements
	 * starting from the start position, and to modify the source.
	 */
	private class PositionLocator {
		private int lineno;
		private int colno;

		PositionLocator(int startLineno, int startCharno) {
			lineno = startLineno;
			colno = startCharno;
			if (lineno < 1 || lineno > lines.size()) {
				throw new IllegalStateException();
			}
			LineInfo line = lines.get(lineno-1);
			if (colno < 0 || colno > line.originalStr.length()) {
				throw new IllegalStateException();
			}
		}

		int getLineno() { return lineno; }
		int getCharno() { return colno; }

		/**
		 * Inserts the specified string into the source at the current location
		 *
		 * @param str
		 *            the string to insert.
		 */
		public void insert(String str) {
			LineInfo line = lines.get(lineno-1);
			int originalColno = colno;
			if (line.edits == null) {
				line.edits = new ArrayList<Integer>();
			}
			Iterator<Integer> iter = line.edits.iterator();
			// Adjust colno to account for prior insertions
			while (iter.hasNext()) {
				int offset = iter.next();
				int count = iter.next();
				if (offset <= colno) {
					colno += count;
				} else {
					break;
				}
			}
			// insert the string at position colno
			line.str = line.str.substring(0, colno) + str + line.str.substring(colno);
			line.edits.add(originalColno);
			line.edits.add(str.length());
			modified = true;
		}

		/**
		 * Searches for the next non-whitespace or comment character in the source file starting
		 * at the current position.  The position values are updated with the location of the
		 * found character.
		 *
		 * @return the next javascript character, or 0 if none found.
		 */
		char findNextJSToken() {
			char ch = 0;
			boolean inBlockComment = false;
			while (lineno <= lines.size()) {
				LineInfo line = lines.get(lineno-1);
				while (colno < line.originalStr.length()) {
					ch = line.originalStr.charAt(colno);
					if (!inBlockComment && ch == '/' && colno+1 < line.originalStr.length()) {
						if (line.originalStr.charAt(colno+1) == '/') {
							// start of line comment.  Skip to next line
							ch = 0;
							break;
						} else if (line.originalStr.charAt(colno+1) == '*') {
							// start of block comment.  Skip to end of comment block
							colno+=2;;
							inBlockComment = true;
							continue;
						}
					} else if (inBlockComment && ch == '*' && colno+1 < line.originalStr.length()) {
						if (line.originalStr.charAt(colno+1) == '/') {
							colno+=2;
							inBlockComment = false;
							ch = 0;
							continue;
						}
					}
					if (!inBlockComment && !Character.isWhitespace(ch) && ch != 0) {
						break;
					}
					colno++;
				}
				if (!inBlockComment && !Character.isWhitespace(ch) && ch != 0) {
					break;
				}
				lineno++;
				colno = 0;
			}
			return ch;
		}
	}
}
