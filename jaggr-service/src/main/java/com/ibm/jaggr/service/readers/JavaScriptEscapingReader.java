/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.readers;

import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;

public class JavaScriptEscapingReader extends CharacterEscapingReader {
	/** 
	 * Set of characters that need to be escaped when they appear
	 * within javascript strings.
	 */
	public static final Collection<Character> escapeChars = Arrays
			.asList(new Character[] { '\'', '\n', '\r', '\\' });
	

	public JavaScriptEscapingReader(Reader reader) {
		super(reader, escapeChars);
	}

}
