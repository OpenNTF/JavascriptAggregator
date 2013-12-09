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

package com.ibm.jaggr.core.impl;

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.TypeUtil;

public class AggregatorLayerListener implements ILayerListener {

	public static final String PREAMBLEFMT = "\n/*-------- %s --------*/\n"; //$NON-NLS-1$
	
	private IAggregator aggregator;
	
	public AggregatorLayerListener(IAggregator aggregator) {
		this.aggregator = aggregator;
	}

	@Override
	public String layerBeginEndNotifier(EventType type,
			HttpServletRequest request, List<IModule> modules,
			Set<String> dependentFeatures) {
		if (type == EventType.BEGIN_LAYER) {
			StringBuffer sb = new StringBuffer();
			// Add the application specified notice to the beginning of the response
	        String notice = aggregator.getConfig().getNotice();
	        if (notice != null) {
				sb.append(notice).append("\r\n"); //$NON-NLS-1$
	        }
	        // If development mode is enabled, say so
	        IOptions options = aggregator.getOptions();
			if (options.isDevelopmentMode() || options.isDebugMode()) {
				sb.append("/* ") //$NON-NLS-1$
				  .append(options.isDevelopmentMode() ? 
						  com.ibm.jaggr.core.impl.layer.Messages.LayerImpl_1 : 
				          com.ibm.jaggr.core.impl.layer.Messages.LayerImpl_2)
				  .append(" */\r\n"); //$NON-NLS-1$ 
			}
			return sb.toString();
		} else if (type == EventType.BEGIN_MODULE) {
	        IOptions options = aggregator.getOptions();
			// Include the filename preamble if requested.
			if ((options.isDebugMode() || options.isDevelopmentMode()) && TypeUtil.asBoolean(request.getAttribute(IHttpTransport.SHOWFILENAMES_REQATTRNAME))) {
				return String.format(PREAMBLEFMT, modules.get(0).getURI().toString());
			}
		}		
		return null;
	}

}
