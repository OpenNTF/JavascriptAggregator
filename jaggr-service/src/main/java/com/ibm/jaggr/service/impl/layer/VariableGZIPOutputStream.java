/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.layer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class VariableGZIPOutputStream extends GZIPOutputStream {

	public VariableGZIPOutputStream(OutputStream out) throws IOException {
		super(out);
	}
	public VariableGZIPOutputStream(OutputStream out, int size) throws IOException {
		super(out, size);
	}
	
	public void setLevel(int level) {
		def.setLevel(level);
	}
}
