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

	public static int copy(InputStream in, OutputStream out) throws IOException {
        try {
        	return IOUtils.copy(in, out);
        } finally {
        	try { out.close(); } catch (Exception ignore){};
        	try { in.close(); } catch (Exception ignore){};
        }
    }

	public static int copy(Reader reader, Writer writer) throws IOException {
		try {
			return IOUtils.copy(reader, writer);
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
