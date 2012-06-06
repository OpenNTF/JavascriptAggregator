/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.modulebuilder;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.resource.IResource;

/**
 * Result wrapper class for built content. Instances of this object are
 * returned by
 * {@link IModuleBuilder#build(String, IResource, HttpServletRequest, ICacheKeyGenerator[])}.
 */
public final class ModuleBuild {
	private String buildOutput;
	private ICacheKeyGenerator[] keyGenerators;
	private boolean error;

	/**
	 * Convenience constructor utilizing a null cache key generator and no
	 * error.
	 * 
	 * @param buildOutput
	 *            The built output for the module
	 */
	public ModuleBuild(String buildOutput) {
		this(buildOutput, null, false);
	}

	/**
	 * Full arguments constructor
	 * 
	 * @param buildOutput
	 *            The build output for the module
	 * @param keyGens
	 *            Array of cache key generators. If a non-provisional
	 *            cache key generator was supplied in the preceding call for
	 *            this same request to
	 *            {@link IModuleBuilder#getCacheKeyGenerators(IAggregator)} , or
	 *            the built output for the module is invariant with regard to
	 *            the request, then <code>keyGens</code> may be null.
	 * @param error
	 *            True if an error occurred while generating the build
	 */
	public ModuleBuild(String buildOutput, ICacheKeyGenerator[] keyGens, boolean error) {
		this.buildOutput = buildOutput;
		this.keyGenerators = keyGens;
		this.error = error;
		if (keyGens != null && KeyGenUtil.isProvisional(keyGens)) {
			throw new IllegalStateException();
		}
	}

	/**
	 * Returns the built (processed and minified) output for this request,
	 * as an AMD module string.
	 * 
	 * @return the build outupt
	 */
	public String getBuildOutput() {
		return buildOutput;
	}

	/**
	 * Returns the non-provisional cache key generator for this module. Only
	 * required if
	 * {@link IModuleBuilder#getCacheKeyGenerators(IAggregator)}
	 * returned a provisional cache key generator for the same request.
	 * 
	 * @return The cache key generator
	 */
	public ICacheKeyGenerator[] getCacheKeyGenerators() {
		return keyGenerators;
	}

	/**
	 * Returns true if this build is an error response. Error responses are
	 * not cached, either on the server or on the client.
	 * 
	 * @return True if a build error occurred
	 */
	public boolean isError() {
		return error;
	}
}