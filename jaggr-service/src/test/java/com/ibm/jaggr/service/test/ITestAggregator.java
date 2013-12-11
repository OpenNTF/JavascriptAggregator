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

package com.ibm.jaggr.service.test;

import javax.servlet.http.HttpServlet;

import org.osgi.framework.BundleContext;

/**
 * Interface for the AMD Aggregator. Provides accessors to other aggregator
 * components and some service methods. Implementors of this interface extend
 * {@link HttpServlet}, so they are created by the framework when a servlet
 * implementing this interface is instanciated.
 * <p>
 * The aggregator registers as an OSGi service using this interface and the
 * servlet alias as the <code>name</code> service property.
 * <p>
 * When servicing requests, a reference to the aggregator may be obtained from
 * the request attribute named {@link #AGGREGATOR_REQATTRNAME}. It may also be
 * obtained from the session context attributes using the same name.
 */
public interface ITestAggregator extends com.ibm.jaggr.core.IAggregator {

	public BundleContext getBundleContext();
}
