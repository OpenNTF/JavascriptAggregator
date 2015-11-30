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

package com.ibm.jaggr.blueprint;

import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.eclipse.osgi.framework.console.CommandProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import com.ibm.jaggr.service.impl.AggregatorCommandProvider;

/**
* @see <a href="http://karaf.apache.org/manual/latest-2.2.x/developers-guide/extending-console.html">http://karaf.apache.org/manual/latest-2.2.x/developers-guide/extending-console.html</a>
*/
public abstract class AbstractOsgiCommandSupport extends OsgiCommandSupport {

	@Override
	protected Object doExecute() throws Exception {
		BundleContext context = getBundleContext();
		ServiceReference<?>[] refs = context.getServiceReferences(CommandProvider.class.getName(), null);

		AggregatorCommandProvider provider = null;
		if (refs != null) {
			for (ServiceReference<?> ref : refs) {
				CommandProvider p = getService(CommandProvider.class, ref);
				if (p instanceof AggregatorCommandProvider)
					provider = (AggregatorCommandProvider)p;
				break;
			}
		}

		if (provider != null)
			exec(provider);

		return null;
	}

	protected abstract void exec(AggregatorCommandProvider provider) throws Exception;
}
