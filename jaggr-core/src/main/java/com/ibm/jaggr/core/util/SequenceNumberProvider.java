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
