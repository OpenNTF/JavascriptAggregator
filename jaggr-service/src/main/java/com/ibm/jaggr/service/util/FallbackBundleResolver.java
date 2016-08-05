/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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
package com.ibm.jaggr.service.util;

import com.ibm.jaggr.service.IBundleResolver;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bundle resolver to use when FrameworkWiring package is not available.
 */
public class FallbackBundleResolver implements IBundleResolver {

	static final Logger log = Logger.getLogger(FallbackBundleResolver.class.getName());

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.impl.IBundleResolver#getBundle(java.lang.String)
	 */
	@Override
	public Bundle getBundle(String bundleName) {
		final String sourceMethod = "getBundle"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(FallbackBundleResolver.class.getName(), sourceMethod, new Object[]{bundleName});
		}
		Bundle result = Platform.getBundle(bundleName);
		if (isTraceLogging) {
			log.exiting(FallbackBundleResolver.class.getName(), sourceMethod, result);
		}
		return result;
	}

}
