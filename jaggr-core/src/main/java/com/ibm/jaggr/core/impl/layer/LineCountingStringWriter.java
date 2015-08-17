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
package com.ibm.jaggr.core.impl.layer;

import java.io.StringWriter;

/**
 * Extends {@link StringWriter} to provide real-time line and column counts for data that has so far
 * been written to the writer.
 */
public class LineCountingStringWriter extends StringWriter {

	private int line = 0;
	private int column = 0;
	private boolean ignoreNewLine = false;

	public LineCountingStringWriter() {
		super();
	}

	public LineCountingStringWriter(int initialSize) {
		super(initialSize);
	}

	/* (non-Javadoc)
	 * @see java.io.StringWriter#write(int)
	 */
	@Override
    public void write(int c) {
        append((char)c);
    }

	/* (non-Javadoc)
	 * @see java.io.StringWriter#write(char[], int, int)
	 */
	@Override
    public void write(char cbuf[], int off, int len) {
        if ((off < 0) || (off > cbuf.length) || (len < 0) ||
            ((off + len) > cbuf.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        for (int i = 0; i < len; i++) {
        	append(cbuf[off + i]);
        }
    }

	/* (non-Javadoc)
	 * @see java.io.StringWriter#write(java.lang.String)
	 */
	@Override
    public void write(String str) {
		int len = str.length();
		for (int i = 0; i < len; i++) {
			append(str.charAt(i));
		}
    }

	/* (non-Javadoc)
	 * @see java.io.StringWriter#write(java.lang.String, int, int)
	 */
	@Override
    public void write(String str, int off, int len)  {
        append(str.substring(off, off + len));
    }

	/* (non-Javadoc)
	 * @see java.io.StringWriter#append(java.lang.CharSequence)
	 */
	@Override
    public StringWriter append(CharSequence csq) {
        if (csq == null)
            append("null"); //$NON-NLS-1$
        else {
    		int len = csq.length();
    		for (int i = 0; i < len; i++) {
    			append(csq.charAt(i));
    		}
        }
        return this;
    }

	/* (non-Javadoc)
	 * @see java.io.StringWriter#append(java.lang.CharSequence, int, int)
	 */
	@Override
    public StringWriter append(CharSequence csq, int start, int end) {
        CharSequence cs = (csq == null ? "null" : csq); //$NON-NLS-1$
        append(cs.subSequence(start, end).toString());
        return this;
    }



    /* (non-Javadoc)
     * @see java.io.StringWriter#append(char)
     */
    @Override
    public StringWriter append(char c) {
		if (c == '\n') {
			if (!ignoreNewLine) {
				++line;
				column = 0;
			}
			ignoreNewLine = false;
		} else if (c == '\r'){
			// \r\n sequence should be treated as a single line feed so set a flag
			// to indicate that if the next character is a /n then we should ignore it
			ignoreNewLine = true;
			++line;
			column = 0;
		} else {
			ignoreNewLine = false;
			++column;
		}
        super.write(c);
        return this;
    }

    /**
     * @return the current line count (zero-based).
     */
    public int getLine() {
    	return line;
    }

    /**
     * @return the current column (zero-based).
     */
    public int getColumn() {
    	return column;
    }



}
