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

import java.io.Serializable;


/**
 * Thread safe implementation of a latching counter that will
 * set a latch whenever the counter is decremented to zero
 * or a negative value.  
 */
public class LatchingCounter implements Serializable {
	private static final long serialVersionUID = 3248231124104936653L;

	private volatile boolean latched = false;
	private volatile int counter = 0;

	public LatchingCounter(int initialCount) {
		if (initialCount < 0) {
			throw new IllegalStateException();
		}
		counter = initialCount;
	}
	
	public LatchingCounter(LatchingCounter other) {
		synchronized (other) {
			latched = other.latched;
			counter = other.counter;
		}
	}
	
	public synchronized boolean decrement() {
		if (--counter <= 0) {
			latched = true;
		}
		return latched;
	}
	
	public synchronized boolean increment() {
		counter++;
		return latched;
	}
	
	public synchronized boolean latchIfZero() {
		if (counter == 0) {
			latched = true;
		}
		return latched;
	}
	
	public boolean isLatched() {
		return latched;
	}
	
	public int getCount() {
		return counter;
	}
}

