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
package com.ibm.jaggr.service.util;

import com.ibm.jaggr.core.NotFoundException;

import com.ibm.jaggr.service.IBundleResolver;

import org.apache.commons.codec.binary.Base64;
import org.osgi.framework.Bundle;

import java.security.MessageDigest;
import java.util.Dictionary;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class providing core (non-mozilla) functionality for generating a hash based on
 * the bundle header properties of a list of bundles.  This class is callable by
 * external packages (e.g. servlets) that wish to generate cache bust strings
 * in the same manner as the aggregator.
 */
public class BundleVersionsHashBase {
	private static final Logger log = Logger.getLogger(BundleVersionsHashBase.class.getName());

	private Bundle contributingBundle = null;		// The contributing bundle
	private IBundleResolver bundleResolver;			// The bundle resolver

	/**
	 * Default constructor
	 */
	public BundleVersionsHashBase() {
		bundleResolver = BundleResolverFactory.getResolver(null);
	}

	/**
	 * @param contributingBundle
	 */
	public BundleVersionsHashBase(Bundle contributingBundle) {
		setContributingBundle(contributingBundle);
	}

	/**
	 * @return the contributing bundle
	 */
	public Bundle getContributingBundle() {
		return contributingBundle;
	}

	/**
	 * @param contributingBundle
	 */
	public void setContributingBundle(Bundle contributingBundle) {
		this.contributingBundle = contributingBundle;
		bundleResolver = BundleResolverFactory.getResolver(contributingBundle);
	}

	/**
	 * Returns the bundle headers for the specified bundle.
	 *
	 * @param bundleName
	 *            the bundle name
	 * @return the bundle headers for the bundle.
	 * @throws NotFoundException if no matching bundle is found.
	 */
	private Dictionary<?, ?> getBundleHeaders(String bundleName) throws NotFoundException {
		final String sourceMethod = "getBundleHeaders"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(BundleVersionsHashBase.class.getName(), sourceMethod, new Object[]{bundleName});
		}

		Bundle result = ".".equals(bundleName) ? contributingBundle : bundleResolver.getBundle(bundleName); //$NON-NLS-1$

		if (result == null) {
			throw new NotFoundException("Bundle " + bundleName + " not found."); //$NON-NLS-1$ //$NON-NLS-2$
		}
		if (isTraceLogging) {
			log.exiting(BundleVersionsHashBase.class.getName(), sourceMethod, result.getHeaders());
		}
		return result.getHeaders();
	}
	/**
	 * Returns an MD5 hash of the concatenated bundle header values from each of the specified bundles.
	 * If any of the specified bundles are not found, a {@link NotFoundException} is thrown.
	 *
	 * @param headerNames the bundle header values to include in the hash
	 * @param bundleNames the bundle names to include in the hash
	 *
	 * @return the computed hash
	 * @throws NotFoundException
	 */
	public String generateHash(String[] headerNames, String[] bundleNames) throws NotFoundException {
		final String sourceMethod = "generateCacheBustHash"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(BundleVersionsHashBase.class.getName(), sourceMethod);
		}
		StringBuffer sb = new StringBuffer();
		for (String bundleName : bundleNames) {
			Dictionary<?, ?> bundleHeaders = getBundleHeaders(bundleName);
			for (String headerName : headerNames) {
				Object value = bundleHeaders.get(headerName);
				if (isTraceLogging) {
					log.finer("Bundle = " + bundleName + ", Header name = " + headerName + ", Header value = " + value); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				sb.append(sb.length() == 0 ? "" : ",").append(value); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		String result = null;
		if (sb.length() > 0) {
			MessageDigest md = null;
			try {
				md = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
				result = Base64.encodeBase64URLSafeString(md
						.digest(sb.toString().getBytes("UTF-8"))); //$NON-NLS-1$
			} catch (Exception e) {
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}
		if (isTraceLogging) {
			log.exiting(BundleVersionsHashBase.class.getName(), sourceMethod, result);
			;
		}
		return result;
	}
}
