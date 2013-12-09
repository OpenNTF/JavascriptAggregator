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

package com.ibm.jaggr.core;

import javax.servlet.http.HttpServlet;

/**
 * Listener interface for shutdown notification. To receive shutdown
 * notification, register the implementing class as an OSGi service, specifying
 * the name of the aggregator under the <code>name</code> property in the
 * service properties.
 */
public interface IShutdownListener {

	/**
	 * This method is called from within the aggregator's
	 * {@link HttpServlet#destroy()} method to notify listeners that the servlet
	 * is shutting down.
	 * 
	 * @param aggregator
	 *            The aggregator that is shutting down
	 */
	public void shutdown(IAggregator aggregator);
}
