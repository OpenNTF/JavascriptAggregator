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
package com.ibm.jaggr.service.impl.resource;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.FrameworkWiring;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves bundles based on framework wiring of the contributing bundle (the bundle that
 * loaded the aggregator bundle).  When a bundle is requested by symbolic name, and there
 * is more than one version of the bundle in the framework, then this class will resolve
 * which of the available bundles is wired to the contributing bundle, or the dependency
 * of the contributing bundle that pulled in the requested bundle.  In this way, bundles
 * selected by the namedbundleresource protocol will be resolved in the same way that they
 * are resolved by the class loader.
 */
public class BundleResolver {
	static final Logger log = Logger.getLogger(BundleResolver.class.getName());

	/**
	 * The bundle that loaded the aggregator bundle.
	 */
	private final Bundle contributingBundle;

	/**
	 * The wiring object for the framework
	 */
	private FrameworkWiring fw = null;

	/**
	 * Object constructor
	 *
	 * @param contributingBundle
	 *            The bundle that loaded the aggregator bundle.
	 */
	public BundleResolver(Bundle contributingBundle) {
		final String sourceMethod = "<ctor>"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(BundleResolver.class.getName(), sourceMethod, new Object[]{contributingBundle});
		}
		this.contributingBundle = contributingBundle;
		Bundle systemBundle = contributingBundle.getBundleContext().getBundle(0);
		try {
			/*
			 * Get the Method object for Bundle.adapt() method using Java reflection.
			 * Need this to invoke the method because we compile with down-level libraries
			 * that don't include the method, however, we can be sure that it's available
			 * if the platform has the FrameworkWiring class (i.e. this class loads).
			 */
			Method adaptMethod = Bundle.class.getMethod("adapt", new Class[]{Class.class}); //$NON-NLS-1$
			/*
			 * Get the FrameworkWiring object by adapting the system bundle.
			 */
			fw = (FrameworkWiring)adaptMethod.invoke(systemBundle, new Object[]{FrameworkWiring.class});
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
			throw new RuntimeException(e);
		}

		if (isTraceLogging) {
			log.exiting(BundleResolver.class.getName(), sourceMethod);
		}
	}

	/**
	 * Returns the bundle with the requested symbolic name that is wired to the contributing bundle,
	 * or the bundle with the latest version if unable to determine wiring.
	 *
	 * @param symbolicName
	 * @return the requested bundle
	 */
	public Bundle getBundle(String symbolicName) {
		final String sourceMethod = "getBundle"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(BundleResolver.class.getName(), sourceMethod, new Object[]{symbolicName});
		}
		Bundle[] candidates = Platform.getBundles(symbolicName, null);
		if (isTraceLogging) {
			log.finer("candidate bundles = " + candidates); //$NON-NLS-1$
		}
		if (candidates == null || candidates.length == 0) {
			if (isTraceLogging) {
				log.exiting(BundleResolver.class.getName(), sourceMethod, null);
			}
			return null;
		}
		if (candidates.length == 1) {
			// Only one choice, so return it
			if (isTraceLogging) {
				log.exiting(BundleResolver.class.getName(), sourceMethod, candidates[0]);
			}
			return candidates[0];
		}
		if (fw == null) {
			if (isTraceLogging) {
				log.finer("Framework wiring not available.  Returning bundle with latest version"); //$NON-NLS-1$
				log.exiting(BundleResolver.class.getName(), sourceMethod, candidates[0]);
			}
			return candidates[0];
		}
		Bundle result = null;
		// For each candidate bundle, check to see if it's wired, directly or indirectly, to the contributing bundle
		for (Bundle candidate : candidates) {
			Collection<Bundle> bundles = fw.getDependencyClosure(Arrays.asList(new Bundle[]{candidate}));
			for (Bundle bundle : bundles) {
				if (bundle == contributingBundle) {
					// found wired bundle.  Return the candidate.
					result = candidate;
					break;
				}
			}
			if (result != null) {
				break;
			}
		}
		if (result == null) {
			if (isTraceLogging) {
				log.finer("No wired bundle found amoung candidates.  Returning bundle with most recent version."); //$NON-NLS-1$
			}
			result =  candidates[0];
		}
		if (isTraceLogging) {
			log.exiting(BundleResolver.class.getName(), sourceMethod, result);
		}
		return result;
	}

}
