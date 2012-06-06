/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SynchronousExecutor extends ThreadPoolExecutor {
	
	public SynchronousExecutor() {
		super(0, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
	}

	@Override
	public void execute(Runnable command) {
		command.run();
	}

}
