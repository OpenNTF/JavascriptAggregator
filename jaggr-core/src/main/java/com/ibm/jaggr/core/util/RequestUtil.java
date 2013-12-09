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

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.transport.IHttpTransport;

public class RequestUtil {

	/**
	 * @param request
	 * @return True if the server side cached should be ignored
	 */
	public static boolean isIgnoreCached(HttpServletRequest request) {
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		return (options.isDevelopmentMode() || options.isDebugMode()) &&
				TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOCACHE_REQATTRNAME));
		
	}
	
	/**
	 * @param request
	 * @return True if the response should be gzip encoded
	 */
	public static boolean isGzipEncoding(HttpServletRequest request) {
		boolean result = false;
		String accept = request.getHeader("Accept-Encoding"); //$NON-NLS-1$
        if (accept != null)
        	accept = accept.toLowerCase();
        if (accept != null && accept.contains("gzip") && !accept.contains("gzip;q=0")) { //$NON-NLS-1$ //$NON-NLS-2$
        	result = true;
        }
        return result;
	}

	/**
	 * Static class method for determining if require list explosion should be performed.
	 *  
	 * @param request The http request object
	 * @return True if require list explosion should be performed.
	 */
	public static boolean isExplodeRequires(HttpServletRequest request) {
		boolean result = false;
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		Boolean reqattr = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME));
		result = (options == null || !options.isDisableRequireListExpansion()) 
				&& reqattr != null && reqattr
				&& aggr.getDependencies() != null;
		return result;
	}

	/**
	 * Static method for determining if has filtering should be performed.
	 * 
	 * @param request The http request object
	 * @return True if has filtering should be performed
	 */
	public static boolean isHasFiltering(HttpServletRequest request) {
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		return (options != null) ? !options.isDisableHasFiltering() : true;
	}

	/**
	 * @param request
	 * @return True if require expansion logging should be enabled for the request
	 */
	public static boolean isRequireExpLogging(HttpServletRequest request) {
		boolean result = false;
		if (isExplodeRequires(request) ) {
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			IOptions options = aggr.getOptions();
			if (options.isDebugMode() || options.isDevelopmentMode()) {
				result = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPANDREQLOGGING_REQATTRNAME));
			}
		}
		return result;
	}

	/**
	 * @param request
	 * @return True if module names should be exported in the response
	 */
	public static boolean isExportModuleName(HttpServletRequest request) {
		Boolean value = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME));
		return value != null ? value : false;
	}

	/**
	 * @param request
	 * @return True if has! plugin branching should be performed as part of
	 *         require list expansion
	 */
	public static boolean isPerformHasBranching(HttpServletRequest request) {
		boolean result = false;
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		if (!options.isDisableHasPluginBranching()) {
			result = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.HASPLUGINBRANCHING_REQATTRNAME));
		}
		return result;
	}
}
