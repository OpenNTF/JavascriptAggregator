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

package com.ibm.jaggr.core.modulebuilder;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.layer.ILayerListener;

/**
 * If the object returned by {@link ModuleBuild#getBuildOutput()} implements 
 * this interface, then the object's <code>renderBuild</code> method will be
 * called to obtain the build output string which will be included in the 
 * layer that is being assembled.  Otherwise, the object's <code>toString</code>
 * method is called.
 * <p>
 * For example, The JavaScript module builder uses this method to render 
 * module list expansion in require calls after filtering the list using the
 * loaded dependencies for the layer (specified in a request attribute in the 
 * request object).
 * <p>
 * If the rendered build is dependent upon features specified in the request
 * which may not be accounted for by the build's {@link ICacheKeyGenerator}, then
 * the module builder should register an {@link ILayerListener} service so that
 * the dependent features can be specified using the listener's notifier method. 
 */
public interface IModuleBuildRenderer {
	
	/**
	 * Called to render the build for the specified request.  
	 * 
	 * @param request the request object
	 * @return the rendered build
	 */
	public String renderBuild(HttpServletRequest request);
}
