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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.framework.console.CommandInterpreter;
import org.eclipse.osgi.framework.console.CommandProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
* @see <a href="http://karaf.apache.org/manual/latest-2.2.x/developers-guide/extending-console.html">http://karaf.apache.org/manual/latest-2.2.x/developers-guide/extending-console.html</a>
*/
public abstract class AbstractOsgiCommandSupport extends OsgiCommandSupport {
	
	@Override
	protected Object doExecute() throws Exception {
		Bundle bundle = Platform.getBundle(com.ibm.jaggr.service.impl.Activator.BUNDLE_NAME);
		String result;
		if (bundle != null && bundle.getState() == Bundle.ACTIVE) {
			BundleContext context = bundle.getBundleContext();
			ServiceReference<?>[] refs = context.getServiceReferences(CommandProvider.class.getName(),
					"(name=com.ibm.jaggr.core.IAggregator)"); //$NON-NLS-1$
	
			if (refs != null && refs.length > 0) {
				CommandProvider provider = (CommandProvider)context.getService(refs[0]);
				try {
					result = exec(provider);
				} catch (Throwable t) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					t.printStackTrace(pw);
					result = sw.toString();
				} finally {
					context.ungetService(refs[0]);
				}
			} else { 
				result = "CommandProvider service for aggregator is not registered"; //$NON-NLS-1$
			}
		} else {
		  result = "Bundle " + com.ibm.jaggr.service.impl.Activator.BUNDLE_NAME + " is not started."; //$NON-NLS-1$ //$NON-NLS-2$
		}
		return result;
	}

	protected abstract String exec(CommandProvider provider) throws Exception;
	
	/**
	 * Invokes the aggregator command processor using reflection in order avoid framework dependencies
	 * on the aggregator classes.
	 * 
	 * @param provider an instance of the aggregator command processor
	 * @param interpreter the command interpreter
	 * @return the command response
	 * @throws Exception
	 */
	protected String invoke(CommandProvider provider, CommandInterpreterWrapper interpreter) throws Exception {
		Method method = provider.getClass().getMethod("_aggregator", new Class[]{CommandInterpreter.class}); //$NON-NLS-1$
		method.invoke(provider, new Object[]{interpreter});
		return interpreter.getOutput();
	}
}
