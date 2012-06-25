/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;

/**
 * Versions of stream copy utils that also close the streams when
 * the copy is done.
 */
public class CopyUtil {

	public static void copy(InputStream in, OutputStream out) throws IOException {
        try {
        	IOUtils.copy(in, out);
        } finally {
        	try { out.close(); } catch (Exception ignore){};
        	try { in.close(); } catch (Exception ignore){};
        }
    }

	public static void copy(Reader reader, Writer writer) throws IOException {
		try {
			IOUtils.copy(reader, writer);
		} finally {
			try { reader.close(); } catch (Exception ignore) {}
			try { writer.close(); } catch (Exception ignore) {}
		}
	}
	
	public static void copy(Reader reader, OutputStream out) throws IOException {
		InputStream in = new ReaderInputStream(reader, "UTF-8"); //$NON-NLS-1$
		try {
			IOUtils.copy(in, out);
		} finally {
			try { in.close(); } catch (Exception ignore) {}
			try { out.close(); } catch (Exception ignore) {}
		}
	}

	public static void copy(InputStream in, Writer writer) throws IOException {
		Reader reader = new InputStreamReader(in, "UTF-8"); //$NON-NLS-1$
		try {
			IOUtils.copy(reader, writer);
		} finally {
			try { reader.close(); } catch (Exception ignore) {}
			try { writer.close(); } catch (Exception ignore) {}
		}
	}
	
	public static void copy(String str, Writer writer) throws IOException {
		Reader reader = new StringReader(str);
		try {
			IOUtils.copy(reader, writer);
		} finally {
			try { reader.close(); } catch (Exception ignore) {}
			try { writer.close(); } catch (Exception ignore) {}
		}
	}

	public static void copy(String str, OutputStream out) throws IOException {
		Reader reader = new StringReader(str);
		try {
			IOUtils.copy(reader, out);
		} finally {
			try { reader.close(); } catch (Exception ignore) {}
			try { out.close(); } catch (Exception ignore) {}
		}
	}
}
