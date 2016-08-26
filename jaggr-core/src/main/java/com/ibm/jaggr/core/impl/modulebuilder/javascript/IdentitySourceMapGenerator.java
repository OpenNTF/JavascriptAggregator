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

package com.ibm.jaggr.core.impl.modulebuilder.javascript;

import com.google.debugging.sourcemap.FilePosition;
import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Source map generator for an identity source map (the mapped file is identical to the input
 * source).
 */
public class IdentitySourceMapGenerator {
	private static final String sourceClass = IdentitySourceMapGenerator.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);

	final private String name;
	final private String source;
	final private CompilerOptions compiler_options;
	private SourceMapGeneratorV3 sgen;
	private int currentLine;
	private int currentChar;
	private int[] lineLengths;

	public IdentitySourceMapGenerator(String name, String source, CompilerOptions compiler_options) {
		this.name = name;
		this.source = source;
		this.compiler_options = compiler_options;
		this.compiler_options.setIdeMode(true);	// so Node.getLength() will have a value
	}

	/**
	 * Generate the identity source map and return the result.
	 *
	 * @return the identity source map
	 * @throws IOException
	 */
	public String generateSourceMap() throws IOException {
		final String sourceMethod = "generateSourceMap"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{name});
		}
		String result = null;
		lineLengths = getLineLengths();
		Compiler compiler = new Compiler();
		sgen = new SourceMapGeneratorV3();
		compiler.initOptions(compiler_options);
		currentLine = currentChar = 0;
		Node node = compiler.parse(JSSourceFile.fromCode(name, source));
		if (compiler.hasErrors()) {
			if (log.isLoggable(Level.WARNING)) {
				JSError[] errors = compiler.getErrors();
				for (JSError error : errors) {
					log.logp(Level.WARNING, sourceClass, sourceMethod, error.toString());
				}
			}
		}
		if (node != null) {
			processNode(node);
			StringWriter writer = new StringWriter();
			sgen.appendTo(writer, name);
			result = writer.toString();
		}
		sgen = null;
		lineLengths = null;
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, result);
		}
		return result;
	}

	/**
	 * Recursively processes the input node, adding a mapping for the node to the source map
	 * generator.
	 *
	 * @param node
	 *            the node to map
	 * @throws IOException
	 */
	private void processNode(Node node) throws IOException {
		for (Node cursor = node.getFirstChild(); cursor != null; cursor = cursor.getNext()) {
			int lineno = cursor.getLineno()-1;	// adjust for rhino line numbers being 1 based
			int charno = cursor.getCharno();
			if (lineno > currentLine || lineno == currentLine && charno > currentChar) {
				currentLine = lineno;
				currentChar = charno;
			}
			FilePosition endPosition = getEndPosition(cursor);
			sgen.addMapping(name, null,
					new FilePosition(currentLine, currentChar),
					new FilePosition(currentLine, currentChar),
					endPosition);
			if (cursor.hasChildren()) {
				processNode(cursor);
			}
		}
	}

	/**
	 * Returns a {@link FilePosition} for the end position of the input node
	 *
	 * @param node
	 *            the node whose end position we are after.  Note that the Closure
	 *            compiler provides node length information only if ide mode is
	 *            true.
	 * @return the end position of the node
	 * @throws IOException
	 */
	private FilePosition getEndPosition(Node node) throws IOException {
		int length = node.getLength();
		int lineno = currentLine;
		int charno = currentChar;
		while (charno + length > lineLengths[lineno]) {
			length -= (lineLengths[lineno]-charno);
			lineno++;
			charno = 0;
			if (lineno >= lineLengths.length) {
				// Node has an invalid length
				return new FilePosition(currentLine, currentChar);
			}
		}
		charno += length;
		return new FilePosition(lineno, charno);
	}

	/**
	 * Returns an array of integers representing the line lengths (including CRLF) of each of the
	 * lines in the input source.
	 *
	 * @return array of line length integers (one based)
	 */
	private int[] getLineLengths() {
		List<Integer> lineLengthsList = new ArrayList<Integer>();
		int linelength = 0;
		for (int i = 0; i < source.length(); i++) {
			linelength++;
			char ch = source.charAt(i);
			if (ch == '\n') {
				lineLengthsList.add(linelength);
				linelength = 0;
			}
		}
		lineLengthsList.add(linelength);
		int[] result = new int[lineLengthsList.size()];
		for (int i = 0; i < lineLengthsList.size(); i++) {
			result[i] = lineLengthsList.get(i);
		}
		return result;

	}
}
