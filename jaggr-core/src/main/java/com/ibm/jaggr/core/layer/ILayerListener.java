/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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

package com.ibm.jaggr.core.layer;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;

import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * Listener interface for Layer events. To receive notification of layer events,
 * register an instance of the implementing class as an OSGi service. The
 * listener registration should specify a filter using the name property with
 * the value obtained by calling {@link IAggregator#getName()}.
 */
public interface ILayerListener {

	enum EventType {
		/**
		 * This event fires before any of the module builders for the layer are
		 * started.
		 */
		BEGIN_LAYER,

		/**
		 * This event fires after all of the module builders have completed
		 * successfully and their output has been added to the layer.
		 */
		END_LAYER,

		/**
		 * Fires before the first AMD module is added to the layer, but after
		 * all non-AMD scripts (if any) have been included.  Non-AMD scripts
		 * may be included in application requested boot layers using the
		 * <code>scripts</code> URL query arg.
		 */
		BEGIN_AMD,

		/**
		 * This event fires before each module is added to the layer.
		 */
		BEGIN_MODULE
	}

	/**
	 * Listener notification callback that is called for the event specified by
	 * <code>type</code>. If the returned string is not null, then the value
	 * will be added to the response stream either before, or after, the layer
	 * content, depending on the event type.
	 *
	 * @param type
	 *            Indicates whether a layer is starting or finishing.
	 * @param request
	 *            The HTTP request object.
	 * @param modules
	 *            The list of modules in the layer.  Note that modules added to
	 *            the layer by module builders using the
	 *            {@link ModuleBuild#addExtraModule(String)}
	 *            in the list.  For the BEGIN_MODULE event, the list contains
	 *            only the single module that is being added to the layer.
	 * @param dependentFeatures
	 *            Output - If the returned value depends on any features specified
	 *            in the request, then those features should be added to
	 *            <code>dependentFeatures</code>.  These will be included in the
	 *            construction of the cache key for the layer.
	 * @return The string to be added to the response, or null.
	 */
	public String layerBeginEndNotifier(EventType type, HttpServletRequest request,
			List<IModule> modules, Set<String> dependentFeatures);

}
