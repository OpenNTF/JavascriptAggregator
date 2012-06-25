/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple class to provide an ever increasing sequence number.
 */
public class SequenceNumberProvider {
	
	static private final long START = 2L;
	
	static private final AtomicLong sequenceNumber = new AtomicLong(START);
	
	/**
	 * Don't allow this class to be instantiated.
	 */
	private SequenceNumberProvider() {}
	
	/**
	 * Returns an ever increasing value each time it's called
	 * 
	 * @return the new value
	 */
	static public long incrementAndGetSequenceNumber() {
		return sequenceNumber.incrementAndGet();
	}
}
