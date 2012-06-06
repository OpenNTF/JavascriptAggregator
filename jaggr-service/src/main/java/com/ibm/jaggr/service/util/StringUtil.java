/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.jaggr.service.readers.JavaScriptEscapingReader;

public class StringUtil {

	private static final Logger log = Logger.getLogger(StringUtil.class.getName());
	
	static public String escapeForJavaScript(String in) {
		if (in == null) {
			return null;
		}
		StringWriter writer = new StringWriter();
		try {
			CopyUtil.copy(new JavaScriptEscapingReader(new StringReader(in)), writer);
		} catch (IOException e) {
			// Shouldn't happen since we're dealing only with strings
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		return writer.toString();
	}
}
