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
import com.ibm.jaggr.core.IServiceProviderExtensionPoint;
import com.ibm.jaggr.core.IVariableResolver;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.impl.AbstractAggregatorImpl;
import com.ibm.jaggr.core.impl.AggregatorExtension;
import com.ibm.jaggr.core.impl.Messages;
import com.ibm.jaggr.core.impl.options.OptionsImpl;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.transport.IHttpTransportExtensionPoint;
import com.ibm.jaggr.core.util.TypeUtil;

import com.ibm.jaggr.service.PlatformServicesImpl;
import com.ibm.jaggr.service.ServiceRegistrationOSGi;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation for IAggregator and HttpServlet interfaces.
 *
 * Note that despite the fact that HttpServlet (which this class extends)
 * implements Serializable, attempts to serialize instances of this class will
 * fail due to the fact that not all instance data is serializable. The
 * assumption is that because instances of this class are created by the OSGi
 * Framework, and the framework itself does not support serialization, then no
 * attempts will be made to serialize instances of this class.
 */
@SuppressWarnings("serial")
public class AggregatorImpl extends AbstractAggregatorImpl implements IExecutableExtension, BundleListener {

	private static final Logger log = Logger.getLogger(AggregatorImpl.class.getName());

	public static final String DISABLEBUNDLEIDDIRSOPING_PROPNAME = "disableBundleIdDirScoping"; //$NON-NLS-1$

	protected Bundle bundle;
	private ServiceTracker optionsServiceTracker = null;
	private ServiceTracker executorsServiceTracker = null;
	private ServiceTracker variableResolverServiceTracker = null;
	private File workdir = null;


	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getWorkingDirectory()
	 */
	@Override
	public File getWorkingDirectory() {
		return workdir;
	}

	@Override
	public IOptions getOptions() {
		return (localOptions != null) ? localOptions : (IOptions) optionsServiceTracker.getService();
	}

	@Override
	public IExecutors getExecutors() {
		return (IExecutors) executorsServiceTracker.getService();
	}

	public BundleContext getBundleContext() {
		return bundle != null ? bundle.getBundleContext() : null;
	}

	public Map<String, String> getConfigMap(IConfigurationElement configElem) {
		Map<String, String> configMap = new HashMap<String, String>();
		if (configElem.getAttribute("alias") != null) { //$NON-NLS-1$
			configMap.put("alias", configElem.getAttribute("alias")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return configMap;
	}

	public Map<String, String> getConfigInitParams(IConfigurationElement configElem) {
		Map<String, String> configInitParams = new HashMap<String, String>();
		if (configElem.getChildren("init-param") != null) { //$NON-NLS-1$
			IConfigurationElement[] children = configElem.getChildren("init-param"); //$NON-NLS-1$
			for (IConfigurationElement child : children) {
				String name = child.getAttribute("name"); //$NON-NLS-1$
				String value = child.getAttribute("value"); //$NON-NLS-1$
				configInitParams.put(name, value);
			}
		}
		return configInitParams;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
	 */
	@Override
	public void setInitializationData(IConfigurationElement configElem, String propertyName,
			Object data) throws CoreException {

		Bundle contributingBundle = Platform.getBundle(configElem.getNamespaceIdentifier());
		if (contributingBundle.getState() != Bundle.ACTIVE) {
			try {
				contributingBundle.start();
			} catch (BundleException e) {
				throw new CoreException(
						new Status(Status.ERROR, configElem.getNamespaceIdentifier(),
								e.getMessage(), e)
						);
			}
		}

		Map<String, String> configMap = new HashMap<String, String>();
		Map<String, String> configInitParams = new HashMap<String, String>();
		configMap = getConfigMap(configElem);
		configInitParams = getConfigInitParams(configElem);

		try {
			BundleContext bundleContext = contributingBundle.getBundleContext();
			platformServices = new PlatformServicesImpl(bundleContext);
			bundle = bundleContext.getBundle();
			initParams = getInitParams(configInitParams);
			name = getAggregatorName(configMap);

			// Make sure there isn't already an instance of the aggregator registered for the current name
			if (getPlatformServices().getServiceReferences(
					IAggregator.class.getName(),
					"(name=" + getName() + ")") != null) { //$NON-NLS-1$ //$NON-NLS-2$
				throw new IllegalStateException("Name already registered - " + name);
			}
			registerLayerListener();
			initOptions(initParams);
			executorsServiceTracker = getExecutorsServiceTracker(bundleContext);
			variableResolverServiceTracker = getVariableResolverServiceTracker(bundleContext);
			initExtensions(configElem);
			initialize(configMap, configInitParams);

			String versionString = Long.toString(bundleContext.getBundle().getBundleId());
			if (TypeUtil.asBoolean(getConfig().getProperty(DISABLEBUNDLEIDDIRSOPING_PROPNAME, null)) ||
				TypeUtil.asBoolean(getOptions().getOption(DISABLEBUNDLEIDDIRSOPING_PROPNAME))) {
				versionString = null;
			}
			workdir = initWorkingDirectory( // this must be after initOptions
					Platform.getStateLocation(bundleContext.getBundle()).toFile(), configMap,
					versionString);

			// Notify listeners
			notifyConfigListeners(1);

			bundleContext.addBundleListener(this);
		} catch (Exception e) {
			throw new CoreException(
					new Status(Status.ERROR, configElem.getNamespaceIdentifier(),
							e.getMessage(), e)
					);
		}

		Properties dict = new Properties();
		dict.put("name", getName()); //$NON-NLS-1$
		registrations.add(new ServiceRegistrationOSGi(getBundleContext().registerService(
				IAggregator.class.getName(), this, dict)));
	}

	@Override
	synchronized protected void shutdown(){
		super.shutdown();
		BundleContext bundleContext = getBundleContext();
		bundleContext.removeBundleListener(this);
		optionsServiceTracker.close();
		executorsServiceTracker.close();
		variableResolverServiceTracker.close();
	}

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleListener#bundleChanged(org.osgi.framework.BundleEvent)
	 */
	@Override
	public void bundleChanged(BundleEvent event) {
		if (event.getType() == BundleEvent.STOPPING) {
			shutdown();
		}
	}



	/**
	 * Returns the name for the bundle containing the servlet code.  This is used
	 * to look up services like IOptions and IExecutors that are registered by the
	 * bundle activator.
	 *
	 * @return The servlet bundle name.
	 */
	protected String getServletBundleName() {
		return Activator.BUNDLE_NAME;
	}

	public String getPropValue (String propName){
		String propValue = null;
		if (getBundleContext() != null) {
			propValue = getBundleContext().getProperty(propName);
		} else {
			propValue = System.getProperty(propName);
		}
		if (propValue == null && variableResolverServiceTracker != null) {
			ServiceReference[] refs = variableResolverServiceTracker.getServiceReferences();
			if (refs != null) {
				for (ServiceReference sr : refs) {
					IVariableResolver resolver = (IVariableResolver)getBundleContext().getService(sr);
					try {
						propValue = resolver.resolve(propName);
						if (propValue != null) {
							break;
						}
					} finally {
						getBundleContext().ungetService(sr);
					}
				}
			}
		}
		return propValue;
	}

	protected void initOptions(InitParams initParams) throws InvalidSyntaxException {
		optionsServiceTracker = getOptionsServiceTracker(getBundleContext());
		String registrationName = getServletBundleName();
		List<String> values = initParams.getValues(InitParams.OPTIONS_INITPARAM);
		if (values != null && values.size() > 0) {
			String value = values.get(0);
			final File file = new File(substituteProps(value));
			if (file.exists()) {
				registrationName = registrationName + ":" + getName(); //$NON-NLS-1$
				localOptions = new OptionsImpl(registrationName, true, this) {
					@Override public File getPropsFile() { return file; }
				};
				if (log.isLoggable(Level.INFO)) {
					log.info(
							MessageFormat.format(Messages.CustomOptionsFile,new Object[] {file.toString()})
							);
				}
			}
		}
		Properties dict = new Properties();
		dict.put("name", registrationName); //$NON-NLS-1$
		registrations.add(new ServiceRegistrationOSGi(getBundleContext().registerService(
				IOptionsListener.class.getName(), this, dict)));

	}



	/**
	 * Returns an opened ServiceTracker for the Aggregator options.  Aggregator options
	 * are created by the bundle activator and are shared by all Aggregator instances
	 * created from the same bundle.
	 *
	 * @param bundleContext The contributing bundle context
	 * @return The opened service tracker
	 * @throws InvalidSyntaxException
	 */
	protected ServiceTracker getOptionsServiceTracker(BundleContext bundleContext) throws InvalidSyntaxException {
		ServiceTracker tracker = new ServiceTracker(
				bundleContext,
				bundleContext.createFilter(
						"(&(" + Constants.OBJECTCLASS + "=" + IOptions.class.getName() +  //$NON-NLS-1$ //$NON-NLS-2$
						")(name=" + getServletBundleName() + "))"), //$NON-NLS-1$ //$NON-NLS-2$
						null);
		tracker.open();
		return tracker;
	}

	/**
	 * Returns an opened ServiceTracker for the Aggregator exectors provider.
	 * The executors provider is are created by the bundle activator and is
	 * shared by all Aggregator instances created from the same bundle.
	 *
	 * @param bundleContext
	 *            The contributing bundle context
	 * @return The opened service tracker
	 * @throws InvalidSyntaxException
	 */
	protected ServiceTracker getExecutorsServiceTracker(BundleContext bundleContext) throws InvalidSyntaxException {
		ServiceTracker tracker = new ServiceTracker(
				bundleContext,
				bundleContext.createFilter(
						"(&(" + Constants.OBJECTCLASS + "=" + IExecutors.class.getName() +  //$NON-NLS-1$ //$NON-NLS-2$
						")(name=" + getServletBundleName() + "))"), //$NON-NLS-1$ //$NON-NLS-2$
						null);
		tracker.open();
		return tracker;
	}

	protected ServiceTracker getVariableResolverServiceTracker(BundleContext bundleContext) throws InvalidSyntaxException {
		ServiceTracker tracker = new ServiceTracker(bundleContext, IVariableResolver.class.getName(), null);
		tracker.open();
		return tracker;
	}

	/**
	 * Loads and initializes the resource factory, module builder, service provider and
	 * http transport extensions specified in the configuration element for this aggregator
	 *
	 * @param configElem The configuration element
	 * @throws CoreException
	 * @throws NotFoundException
	 */
	protected void initExtensions(IConfigurationElement configElem) throws CoreException, NotFoundException {
		final String sourceMethod = "initExtensions"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorImpl.class.getName(), sourceMethod, new Object[]{configElem});
		}
		/*
		 *  Init the resource factory extensions
		 */
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		Collection<String> extensionIds = getInitParams().getValues(InitParams.RESOURCEFACTORIES_INITPARAM);
		if (extensionIds.size() == 0) {
			extensionIds.add(DEFAULT_RESOURCEFACTORIES);
		}

		List<IExtension> extensions = new ArrayList<IExtension>();
		for (String extensionId : extensionIds) {
			IExtension extension = registry.getExtension(
					IResourceFactoryExtensionPoint.NAMESPACE,
					IResourceFactoryExtensionPoint.NAME,
					extensionId);
			if (extension == null) {
				throw new NotFoundException(extensionId);
			}
			extensions.add(extension);
		}

		/*
		 *  Init the module builder extensions
		 */
		extensionIds = getInitParams().getValues(InitParams.MODULEBUILDERS_INITPARAM);
		if (extensionIds.size() == 0) {
			extensionIds.add(DEFAULT_MODULEBUILDERS);
		}
		for (String extensionId : extensionIds) {
			IExtension extension = registry.getExtension(
					IModuleBuilderExtensionPoint.NAMESPACE,
					IModuleBuilderExtensionPoint.NAME,
					extensionId);
			if (extension == null) {
				throw new NotFoundException(extensionId);
			}
			extensions.add(extension);
		}

		/*
		 * Init the http transport extension
		 */
		{
			extensionIds = getInitParams().getValues(InitParams.TRANSPORT_INITPARAM);
			if (extensionIds.size() == 0) {
				extensionIds.add(DEFAULT_HTTPTRANSPORT);
			}
			if (extensionIds.size() != 1) {
				throw new IllegalStateException(extensionIds.toString());
			}
			IExtension extension = registry.getExtension(
					IHttpTransportExtensionPoint.NAMESPACE,
					IHttpTransportExtensionPoint.NAME,
					extensionIds.iterator().next());
			if (extension == null) {
				throw new NotFoundException(extensionIds.iterator().next());
			}
			extensions.add(extension);
		}
		/*
		 *  Init the serviceprovider extensions
		 */
		extensionIds = getInitParams().getValues(InitParams.SERVICEPROVIDERS_INITPARAM);
		for (String extensionId : extensionIds) {
			IExtension extension = registry.getExtension(
					IServiceProviderExtensionPoint.NAMESPACE,
					IServiceProviderExtensionPoint.NAME,
					extensionId);
			if (extension == null) {
				throw new NotFoundException(extensionId);
			}
			extensions.add(extension);
		}

		initExtensions(extensions.toArray(new IExtension[extensions.size()]));

		/*
		 *  Now call setAggregator on the loaded extensions starting with the
		 *  transport and then the rest of the extensions.
		 */
		ExtensionRegistrar reg = new ExtensionRegistrar();
		callExtensionInitializers(getExtensions(null), reg);
		reg.closeRegistration();

		if (isTraceLogging) {
			log.exiting(AggregatorImpl.class.getName(), sourceMethod);
		}
	}

	/**
	 * Common routine to initialize {@link IExtension} objects with the aggregator.  Instantiates the
	 * extension instances registers the instances with the aggregator.
	 *
	 * @param extensions
	 *            array of {@link IExtension} objects to be initialized
	 */
	protected void initExtensions(IExtension[] extensions) {
		final String sourceMethod = "initExtension"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(AggregatorImpl.class.getName(), sourceMethod, new Object[]{extensions});
		}
		for (IExtension extension : extensions) {
			for (IConfigurationElement member : extension.getConfigurationElements()) {
				try {
					// make sure the contributing bundle is started.
					Bundle contributingBundle = Platform.getBundle(member.getNamespaceIdentifier());
					if (contributingBundle.getState() != Bundle.ACTIVE && contributingBundle.getState() != Bundle.STARTING) {
						contributingBundle.start();
					}
					Object ext = member.createExecutableExtension("class"); //$NON-NLS-1$
					Properties props = new Properties();
					for (String attributeName : member.getAttributeNames()) {
						props.put(attributeName, member.getAttribute(attributeName));
					}
					IConfigurationElement[] children = member.getChildren("init-param"); //$NON-NLS-1$
					for( IConfigurationElement child : children) {
						props.put(child.getAttribute("name"), child.getAttribute("value")); //$NON-NLS-1$ //$NON-NLS-2$
					}
					registerExtension(
							new AggregatorExtension(ext, props,
									extension.getExtensionPointUniqueIdentifier(),
									extension.getUniqueIdentifier()
									), null
							);
				} catch (CoreException ex) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, ex.getMessage(), ex);
					}
				} catch (BundleException ex) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, ex.getMessage(), ex);
					}
				}
			}
		}
		if (isTraceLogging) {
			log.exiting(AggregatorImpl.class.getName(), sourceMethod);
		}
	}
}

