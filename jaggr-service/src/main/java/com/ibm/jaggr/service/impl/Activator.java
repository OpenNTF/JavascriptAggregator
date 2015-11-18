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

package com.ibm.jaggr.service.impl;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.impl.executors.ExecutorsImpl;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Activator extends Plugin implements BundleActivator {

	private static final Logger log = Logger.getLogger(Activator.class.getName());

	/**
	 * This is a hack to work around the absence of the functionality provided by
	 * FrameworkUtil.getBundle() on pre-4.3 versions of OSGi.  We hard-code the
	 * bundle name as a string that can be accessed by other classes in the bundle
	 * in a static way.  This is used to filter services to bundle scope.
	 */
	public static String BUNDLE_NAME = "com.ibm.jaggr.service"; //$NON-NLS-1$
	private static BundleContext context = null;

	private Collection<ServiceRegistration<?>> serviceRegistrations;
	private IExecutors executors = null;

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugin#start(org.osgi.framework.BundleContext)
	 */
	@Override
	public void start(BundleContext context) throws Exception {
		final String sourceMethod = "start"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(Activator.class.getName(), sourceMethod, new Object[]{context});
		}
		super.start(context);

		// Verify the bundle id.
		if (!context.getBundle().getSymbolicName().equals(BUNDLE_NAME)) {
			throw new IllegalStateException();
		}
		serviceRegistrations = new LinkedList<ServiceRegistration<?>>();
		Activator.context = context;
		Dictionary<String, String> dict = new Hashtable<String, String>();
		dict.put("name", BUNDLE_NAME); //$NON-NLS-1$

		ServiceRegistration<?> commandProviderReg = registerCommandProvider();
		if (commandProviderReg != null) {
			serviceRegistrations.add(commandProviderReg);
		}
		// Create the executors provider.  The executors provider is created by the
		// activator primarily to allow executors to be shared by all of the
		// aggregators created by this bundle.
		dict = new Hashtable<String, String>();
		dict.put("name", BUNDLE_NAME); //$NON-NLS-1$
		executors = newExecutors();
		serviceRegistrations.add(
				context.registerService(IExecutors.class.getName(), executors, dict));

		if (isTraceLogging) {
			log.exiting(Activator.class.getName(), sourceMethod);
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.Plugin#stop(org.osgi.framework.BundleContext)
	 */
	@Override
	public void stop(BundleContext context) throws Exception {
		final String sourceMethod = "stop"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(Activator.class.getName(), sourceMethod, new Object[]{context});
		}
		super.stop(context);
		// Shutdown the aggregator instances
		ServiceReference<?>[] refs = context.getServiceReferences(IAggregator.class.getName(), null);
		if (refs != null) {
			for (ServiceReference<?> ref : refs) {
				AggregatorImpl aggr = (AggregatorImpl)context.getService(ref);
				if (aggr != null) {
					try {
						aggr.shutdown();
					} catch (Exception e) {
						if (log.isLoggable(Level.SEVERE)) {
							log.log(Level.SEVERE, e.getMessage(), e);
						}
					}
				}
			}
		}

		for (ServiceRegistration<?> reg : serviceRegistrations) {
			reg.unregister();
		}
		if (executors != null) {
			executors.shutdown();
		}
		this.executors = null;
		Activator.context = null;

		if (isTraceLogging) {
			log.exiting(Activator.class.getName(), sourceMethod);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.impl.CommandProvider#getBundleContext()
	 */
	static public BundleContext getBundleContext() {
		return context;
	}

	protected IExecutors newExecutors() {
		return new ExecutorsImpl();
	}

	protected ServiceRegistration<?> registerCommandProvider() throws InvalidSyntaxException {
		final String sourceMethod = "registerCommandProvider"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(Activator.class.getName(), sourceMethod);
		}

		ServiceRegistration<?> result = null;
		Dictionary<String, Object> dict = new Hashtable<String, Object>();
		// If CommandProcessor service is available, then register the felix command processor
		// Note: must avoid references to felix classes in this module
		ServiceReference<?> commandProcessorSR =
				context.getServiceReference("org.apache.felix.service.command.CommandProcessor"); //$NON-NLS-1$
		if (commandProcessorSR != null) {
			// See if a command provider is already registered
			ServiceReference<?>[] refs = context.getServiceReferences(
					AggregatorCommandProviderGogo.class.getName(),
					"(osgi.command.scope=aggregator)"); //$NON-NLS-1$
			if (refs == null || refs.length == 0) {
				dict = new Hashtable<String, Object>();
				dict.put("osgi.command.scope", "aggregator"); //$NON-NLS-1$ //$NON-NLS-2$
				dict.put("osgi.command.function", AggregatorCommandProvider.COMMANDS); //$NON-NLS-1$
				result = context.registerService(
						AggregatorCommandProviderGogo.class.getName(),
						new AggregatorCommandProviderGogo(context),
						dict);
			}
		} else {
			// See if a command provider is already registered
			ServiceReference<?>[] refs = context.getServiceReferences(
					org.eclipse.osgi.framework.console.CommandProvider.class.getName(),
					"(name=" + IAggregator.class.getName() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			if (refs == null || refs.length == 0) {
				// Register the command provider that will handle console commands
				dict = new Hashtable<String, Object>();
				dict.put("name", IAggregator.class.getName()); //$NON-NLS-1$
				result =
						context.registerService(
								org.eclipse.osgi.framework.console.CommandProvider.class.getName(),
								new AggregatorCommandProvider(context), dict);
			}
		}
		if (isTraceLogging) {
			log.exiting(Activator.class.getName(), sourceMethod, result);
		}
		return result;
	}
}
