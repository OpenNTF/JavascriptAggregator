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

package com.ibm.jaggr.core.cachekeygenerator;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.TypeUtil;

/**
 * Simple cache key generator for export names option. Used by multiple
 * builders.
 */
public final class ExportNamesCacheKeyGenerator extends
		AbstractCacheKeyGenerator {
	private static final long serialVersionUID = -1888066677992730471L;
	
	private static final String eyecatcher = "expn"; //$NON-NLS-1$

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cachekeygenerator.AbstractCacheKeyGenerator#generateKey(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public String generateKey(HttpServletRequest request) {
		boolean exportNames = TypeUtil.asBoolean(request
				.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME));
		return eyecatcher + ":" + (exportNames ? "1" : "0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	@Override 
	public String toString() {
		return eyecatcher;
	}
	
	@Override
	public boolean equals(Object other) {
		return other != null && this.getClass().equals(other.getClass());
	}
	
	@Override
	public int hashCode() {
		return this.getClass().hashCode();
	}
}
