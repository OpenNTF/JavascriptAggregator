/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.readers;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


/**
 * This class aggregates a set of {@link Future}<code>&lt;</code>{@link 
 * com.ibm.jaggr.service.readers.ModuleBuildReader IModule.Build}<code>&gt;</code> 
 * objects and allows them to be read sequentially in the order that they 
 * occur in the list. The read method will block on a Future until its 
 * reader is available and then read the data from that reader until all 
 * the data has been read. It will then move on to the next Future in the 
 * list and do the same, until all the readers have been read from. When 
 * the last reader has no more data, then -1 is returned from the read 
 * method.
 */
public class BuildListReader extends AggregationReader {
	final Iterator<Future<ModuleBuildReader>> iter;
	boolean hasErrors = false;
	IOException savedException = null;

	/**
	 * @param list
	 *            The list of Futures from which to obtain the readers
	 */
	public BuildListReader(List<Future<ModuleBuildReader>> list) {
		super();
		this.iter = list.iterator();
	}

	/**
	 * Gets the reader from the next {@link Future} in the list and
	 * records if any readers are for error responses.  If any exceptions
	 * are encountered, then we save the first exception and wait for 
	 * the rest of the Future tasks to complete and then we'll throw
	 * the saved exception once all of the future tasks have finished. 
	 * 
	 * @return The next reader
	 * @throws IOException
	 */
	@Override
	protected Reader getNextInputReader() throws IOException {
		if (!iter.hasNext()) {
			if (savedException != null) {
				throw savedException;
			}
			return null;
		}
		Future<ModuleBuildReader> future = iter.next();
		if (future == null) {
			if (savedException != null) {
				throw savedException;
			}
			return null;
		}
		ModuleBuildReader reader = null;
		try {
			reader = future.get();
		} catch (InterruptedException e) {
			if (savedException == null) {
				// Only save first exception
				savedException = new IOException(e);
			}
			return new StringReader(""); //$NON-NLS-1$
		} catch (ExecutionException e) {
			if (savedException == null) {
				// Only save first exception
				Throwable t = e.getCause();
				savedException = (t instanceof IOException) ?
					(IOException)t : new IOException(t);
			}
			return new StringReader(""); //$NON-NLS-1$
		}
		if (reader.isError()) {
			hasErrors = true;
		}
		return reader;
		
	}
	/**
	 * Returns true if any of the ModuleReaders in this Future list had errors.
	 * This flag is valid only after all the futures in the list have completed
	 * and the contents of each reader have been read.
	 * 
	 * @return True if any reader had errors
	 */
	public boolean hasErrors() {
		return hasErrors;
	}
}