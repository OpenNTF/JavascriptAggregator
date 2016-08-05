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
import com.ibm.jaggr.service.impl.resource.BundleResourceFactory;

import org.osgi.framework.Bundle;

import java.lang.reflect.Constructor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BundleResolverFactory {
	static final Logger log = Logger.getLogger(BundleResolverFactory.class.getName());

	static IBundleResolver defaultBundleResolver = new FallbackBundleResolver();

	static public IBundleResolver getResolver(Bundle contributingBundle) {
		final String sourceMethod = "getResolver"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(BundleResolverFactory.class.getName(), sourceMethod, new Object[]{contributingBundle});
		}
		IBundleResolver resolver = defaultBundleResolver;
		// Try to load the BundleResolver class (will fail if not on OSGi 4.3).
		try {
			if (contributingBundle != null) {	// contributing bundle can be null when unit testing
				Class<?> resolverClass = Class.forName("com.ibm.jaggr.service.util.FrameworkWiringBundleResolver"); //$NON-NLS-1$
				Constructor<?> ctor = resolverClass.getConstructor(new Class[]{Bundle.class});
				resolver = (IBundleResolver)ctor.newInstance(new Object[]{contributingBundle});
			}
		} catch (ClassNotFoundException e) {
			if (log.isLoggable(Level.FINE)) {
				log.fine("Down level version of OSGi.  Bundle wiring support not available."); //$NON-NLS-1$
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		if (isTraceLogging) {
			log.exiting(BundleResourceFactory.class.getName(), sourceMethod, resolver);
		}
		return resolver;
	}
}
