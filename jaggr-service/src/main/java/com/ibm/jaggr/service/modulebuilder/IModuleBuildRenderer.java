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

package com.ibm.jaggr.service.modulebuilder;

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.resource.IResource;

/**
 * If the object returned by
 * {@link IModuleBuilder#build(String, IResource, HttpServletRequest, List)}
 * implements this interface, then the object's <code>renderBuild</code> method
 * will be called to obtain the build output string which will be included in
 * the layer that is being assembled. Otherwise, the object's
 * <code>toString</code> method is called.
 * <p>
 * For example, The JavaScript module builder uses this method to render module
 * list expansion in require calls after filtering the list using the loaded
 * dependencies for the layer (specified in a request attribute in the request
 * object).
 * <p>
 * Build renderers are called during layer assembly at the time that a module
 * build is inserted into a layer. Build renderers should avoid time-consuming
 * operations such as file I/O, or blocking on other threads. These operations
 * should be done during the module's
 * {@link IModuleBuilder#build(String, IResource, HttpServletRequest, List)}
 * method. When a build renderer is needed, the module builder should construct
 * the build renderer in such a way that all the information needed to render
 * the build (minus information provided in the request) is encapsulated in the
 * build renderer.
 * <p>
 * If the rendered build is dependent upon features specified in the request
 * which may not be accounted for by the build's {@link ICacheKeyGenerator},
 * then the module builder should add the features to
 * <code>dependentFeatures</code> so that they will be included in the
 * construction of the cache key for the layer.
 */
public interface IModuleBuildRenderer {
	
	/**
	 * Called to render the build for the specified request.
	 * 
	 * @param request
	 *            the request object
	 * @param dependent
	 *            features - Output. Features that will be included in the
	 *            construction of the cache key for the layer being assembled.
	 * @return the rendered build
	 */
	public String renderBuild(HttpServletRequest request, Set<String> dependentFeatures);
}
