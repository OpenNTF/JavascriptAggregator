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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.transport.IHttpTransport;

import javax.servlet.http.HttpServletRequest;

public class RequestUtil {

	/**
	 * @param request
	 * @return True if the server side cached should be ignored
	 */
	public static boolean isIgnoreCached(HttpServletRequest request) {
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		return  options != null && (options.isDevelopmentMode() || options.isDebugMode()) &&
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
		if (isIncludeRequireDeps(request)) {
			// don't expand require deps if we're including them in the response.
			return false;
		}
		boolean result = false;
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		IConfig config = aggr.getConfig();
		Boolean reqattr = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME));
		result = (options == null || !options.isDisableRequireListExpansion())
				&& (config == null || !isServerExpandedLayers(request))
				&& reqattr != null && reqattr;
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
	 * @deprecated this method is deprecated in favor of
	 *             {@link #isDependencyExpansionLogging(HttpServletRequest)}
	 */
	public static boolean isRequireExpLogging(HttpServletRequest request) {
		boolean result = false;
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		if (options.isDebugMode() || options.isDevelopmentMode()) {
			result = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPANDREQLOGGING_REQATTRNAME));
		}
		return result;
	}

	/**
	 * @param request
	 * @return True if dependency expansion logging should be enabled for the request. Dependency
	 *         expansion logging is output as console.log() statements in the response.
	 */
	@SuppressWarnings("deprecation")
	public static boolean isDependencyExpansionLogging(HttpServletRequest request) {
		boolean result = false;
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		if (options.isDebugMode() || options.isDevelopmentMode()) {
			result = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.DEPENDENCYEXPANSIONLOGGING_REQATTRNAME)) ||
					TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPANDREQLOGGING_REQATTRNAME));
		}
		return result;
	}

	/**
	 * @param request
	 * @return True if module names should be exported in the response
	 */
	public static boolean isExportModuleName(HttpServletRequest request) {
		return TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME));
	}

	/**
	 * @param request
	 * @return True if require call dependencies should be included when expanding a modules's
	 *         dependencies.
	 */
	public static boolean isIncludeRequireDeps(HttpServletRequest request) {
		return TypeUtil.asBoolean(request.getAttribute(IHttpTransport.INCLUDEREQUIREDEPS_REQATTRNAME));
	}

	/**
	 * @param request
	 * @return True if both branches of a has! loader plugin expression for an undefined feature
	 *         should be included when doing server-side expansion of module dependencies.
	 */
	public static boolean isIncludeUndefinedFeatureDeps(HttpServletRequest request) {
		return TypeUtil.asBoolean(request.getAttribute(IHttpTransport.INCLUDEUNDEFINEDFEATUREDEPS_REQATTRNAME));
	}

	/**
	 * @param request
	 * @return True if an error response should be returned if the requested modules or their
	 *         dependencies include nls modules.
	 */
	public static boolean isAssertNoNLS(HttpServletRequest request) {
		return TypeUtil.asBoolean(request.getAttribute(IHttpTransport.ASSERTNONLS_REQATTRNAME));
	}

	public static boolean isServerExpandedLayers(HttpServletRequest request) {
		return TypeUtil.asBoolean(request.getAttribute(IHttpTransport.SERVEREXPANDLAYERS_REQATTRNAME));
	}

	/**
	 * @param request
	 *            the request object
	 *
	 * @return True if source maps are enabled for this request
	 */
	public static boolean isSourceMapsEnabled(HttpServletRequest request) {
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		boolean result = false;
		if (options.isSourceMapsEnabled()) {
			result = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.GENERATESOURCEMAPS_REQATTRNAME));
		}
		return result;
	}

	/**
	 * @param request
	 *            the request object
	 *
	 * @return True if this is a request for a source map
	 */
	public static boolean isSourceMapRequest(HttpServletRequest request) {
		String pathInfo = request.getPathInfo();
		boolean result = false;
		if (pathInfo != null) {
			if (pathInfo.endsWith("/" + ILayer.SOURCEMAP_RESOURSE_PATHCOMP)) { //$NON-NLS-1$
				result = true;
			}
		}
		return result;
	}
}
