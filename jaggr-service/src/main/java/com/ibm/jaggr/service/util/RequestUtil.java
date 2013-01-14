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

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.transport.IHttpTransport;

public class RequestUtil {

	public static boolean isIgnoreCached(HttpServletRequest request) {
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		return (options.isDevelopmentMode() || options.isDebugMode()) &&
				TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOCACHE_REQATTRNAME));
		
	}
	
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
}
