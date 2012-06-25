/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.readers;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.Collection;

/**
 * A reader that will escape characters from another reader's stream by
 * preceding them with a back slash '\' character.
 */
public class CharacterEscapingReader extends PushbackReader {
	boolean escaped;
	Collection<Character> charsToEscape;

	/**
	 * Constructs a CharacterEscapingReader from an input reader and a
	 * collection of characters to escape.
	 * 
	 * @param in
	 *            The input reader
	 * @param charsToEscape
	 *            The characters to escape in the input reader's stream
	 */
	public CharacterEscapingReader(Reader in,
			Collection<Character> charsToEscape) {
		super(in);
		escaped = false;
		this.charsToEscape = charsToEscape;
	}

	/**
	 * Reads a single character.
	 * 
	 * @return The character read, or -1 if the end of the stream has been
	 *         reached
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	public int read() throws IOException {
		int ch = super.read();
		if (!escaped && charsToEscape.contains((char) ch)) {
			// special case for non-displayable characters
			switch (ch) {
			case '\n':
				ch = 'n';
				break;
			case '\r':
				ch = 'r';
				break;
			case '\t':
				ch = 't';
				break;
			}
			super.unread(ch);
			escaped = true;
			ch = '\\';
		} else {
			escaped = false;
		}
		return ch;
	}

	/**
	 * Reads characters into a portion of an array.
	 * 
	 * @param cbuf
	 *            - Destination buffer
	 * @param off
	 *            - Offset at which to start writing characters
	 * @param len
	 *            - Maximum number of characters to read
	 * @return The character read, or -1 if the end of the stream has been
	 *         reached
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		int bytesread = 0;
		while (len-- > 0) {
			int ch = read();
			if (ch == -1) {
				return (bytesread > 0) ? bytesread : -1;
			}
			cbuf[off++] = (char) ch;
			bytesread++;
		}
		return bytesread;
	}

	/**
	 * This method always throws an <code>IOException</code>
	 * 
	 * @param c
	 *            The int value representing the character to be pushed back
	 * @throws IOException
	 *             always
	 */
	@Override
	public void unread(int c) throws IOException {
		throw new IOException("unread not supported"); //$NON-NLS-1$
	}

	/**
	 * This method always throws an <code>IOException</code>
	 * 
	 * @param buf
	 *            Character array to push back
	 * @throws IOException
	 *             always
	 */
	@Override
	public void unread(char[] buf) throws IOException {
		throw new IOException("unread not supported"); //$NON-NLS-1$
	}

	/**
	 * This method always throws an <code>IOException</code>
	 * 
	 * @param buf
	 *            Character array
	 * @param off
	 *            Offset of first character to push back
	 * @param len
	 *            Number of characters to push back
	 * 
	 * @throws IOException
	 *             always
	 */
	@Override
	public void unread(char[] buf, int off, int len) throws IOException {
		throw new IOException("unread not supported"); //$NON-NLS-1$
	}

}
