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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Iterator;

/**
 * This class aggregates multiple inputs into a single Reader stream. The inputs
 * are provided as an instance of Iterable<Object> or as a variable length
 * parameter list of objects. For each object, if the object is an instance of
 * Reader or InputStream, then the contents of the reader or the stream is
 * incorporated into this reader, otherwise, the result of calling the object's
 * toString() method is incorporated into this reader.
 * <p>
 * This reader will frequently return less than the number of bytes (including
 * zero bytes) than was requested as a result of transitioning between input
 * sources. Only a return value of -1 indicates that no more data is available.
 */
public class AggregationReader extends Reader {

	boolean eof = false;
	boolean closed = false;
	Reader curr = null;
	Iterator<Object> iter;

	/**
	 * Default constructor.  Subclasses using this constructor must
	 * provide an implementation for {@link #getNextInputReader()}.  
	 */
	protected AggregationReader() {
		iter = null;
	}
	
	@SuppressWarnings("unchecked")
	public AggregationReader(Object ... objects) {
		if (objects.length == 1 && objects[0] instanceof Iterable) {
			this.iter = ((Iterable<Object>)objects[0]).iterator();
		} else {
			this.iter = Arrays.asList(objects).iterator();
		}
	}
	/**
	 * Read data from each of the readers in turn until all the data has been
	 * read.
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Reader#read(char[], int, int)
	 */
	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		try {
			if (closed) {
				throw new IOException("Attempt to read from a closed stream"); //$NON-NLS-1$
			}
			if (eof) {
				return -1;
			}
			if (curr == null) {
				curr = getNextInputReader();
				if (curr == null) {
					eof = true;
					return -1;
				}
			}
			int size, bytesRead = 0, bytesToRead = len;
			while (!eof && bytesRead < bytesToRead) {
				size = curr
						.read(cbuf, off + bytesRead, bytesToRead - bytesRead);
				if (size == -1) {
					curr.close();
					curr = getNextInputReader();
					if (curr == null) {
						eof = true;
					}
				} else {
					bytesRead += size;
				}
			}
			return bytesRead;
		} catch (IOException e) {
			throw e;
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	/**
	 * Closes this reader.
	 */
	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Reader#close()
	 */
	@Override
	public void close() throws IOException {
		if (curr != null) {
			curr.close();
		}
		Reader reader;
		while ((reader = getNextInputReader()) != null) {
			reader.close();
		}
		closed = true;
	}

	/**
	 * Returns the next reader in the iteration.  If the object is not
	 * a Reader or an InputStream, then returns a StringReader for the 
	 * object's toString() result.
	 * 
	 * @return The next reader in the iteration
	 * @throws IOException
	 */
	protected Reader getNextInputReader() throws IOException {
		if (iter == null) {
			return null;
		}
		if (!iter.hasNext()) {
			return null;
		}
		Object o = iter.next();
		if (o instanceof Reader) {
			return (Reader)o;
		} else if (o instanceof InputStream) {
			return new InputStreamReader((InputStream)o, "UTF-8"); //$NON-NLS-1$
		}
		return new StringReader(o.toString());
	}
}
