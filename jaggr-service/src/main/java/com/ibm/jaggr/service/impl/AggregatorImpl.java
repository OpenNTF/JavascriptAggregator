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
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.cache.CacheControl;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.impl.AbstractAggregatorImpl;
import com.ibm.jaggr.core.impl.AggregatorExtension;
import com.ibm.jaggr.core.impl.options.OptionsImpl;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResourceConverterExtensionPoint;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.transport.IHttpTransportExtensionPoint;
import com.ibm.jaggr.core.util.AggregatorUtil;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.TypeUtil;
import com.ibm.jaggr.core.util.ZipUtil;

import com.ibm.jaggr.service.PlatformServicesImpl;
import com.ibm.jaggr.service.ServiceRegistrationOSGi;
import com.ibm.jaggr.service.util.BundleResolverFactory;
import com.ibm.jaggr.service.util.BundleUtil;
import com.ibm.jaggr.service.util.ConsoleHttpServletRequest;
import com.ibm.jaggr.service.util.ConsoleHttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.util.tracker.ServiceTracker;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

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
public class AggregatorImpl extends AbstractAggregatorImpl implements IExecutableExtension {
	private static final String sourceClass = AggregatorImpl.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);

	public static final String DISABLEBUNDLEIDDIRSCOPING_PROPNAME = "disableBundleIdDirScoping"; //$NON-NLS-1$
	public static final String CACHEPRIMERBUNDLENAME_CONFIGPARAM = "cachePrimerBundleName"; //$NON-NLS-1$
	public static final String CACHEBUST_HEADER = "cacheBust"; //$NON-NLS-1$
	public static final String TRASHDIRNAME = "trash";  //$NON-NLS-1$
	public static final String TEMPDIRFORMAT = "tmp{0}"; //$NON-NLS-1$
	public static final String MANIFEST_TEMPLATE = "manifest.template"; //$NON-NLS-1$
	public static final String JAGGR_CACHE_DIRECTORY = "JAGGR-Cache/"; //$NON-NLS-1$

	protected Bundle contributingBundle;
	private ServiceTracker executorsServiceTracker = null;
	private ServiceTracker variableResolverServiceTracker = null;
	private File workdir = null;
	private IOptions options = null;
	private boolean isShuttingDown = false;


	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IAggregator#getWorkingDirectory()
	 */
	@Override
	public File getWorkingDirectory() {
		return workdir;
	}

	@Override
	public IOptions getOptions() {
		return options;
	}

	@Override
	public IExecutors getExecutors() {
		return (IExecutors) executorsServiceTracker.getService();
	}

	public Bundle getContributingBundle() {
		return contributingBundle;
	}

	public Map<String, String> getConfigMap(IConfigurationElement configElem) {
		Map<String, String> configMap = new HashMap<String, String>();
		if (configElem.getAttribute("alias") != null) { //$NON-NLS-1$
			configMap.put("alias", configElem.getAttribute("alias")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return configMap;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.AbstractAggregatorImpl#init(javax.servlet.ServletConfig)
	 */
	public void init(ServletConfig servletConfig) throws ServletException {
		// If contributing bundle has a mime.types file in the META-INF directory, then
		// add the contents to the map
		super.init(servletConfig);
		URL url = getContributingBundle().getResource("META-INF/mime.types"); //$NON-NLS-1$
		if (url != null) {
			try {
				StringWriter writer = new StringWriter();
				CopyUtil.copy(url.openStream(), writer);
				super.fileTypeMap.addMimeTypes(writer.toString());
			} catch (IOException e) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, e.getMessage(), e);
				}
			}
		}
		Properties dict = new Properties();
		dict.put("name", getName()); //$NON-NLS-1$
		registrations.add(new ServiceRegistrationOSGi(Activator.getBundleContext().registerService(
				IAggregator.class.getName(), this, dict)));

	}

	public InitParams getConfigInitParams(IConfigurationElement configElem) {
		Map<String, String[]> configInitParams = new HashMap<String, String[]>();
		if (configElem.getChildren("init-param") != null) { //$NON-NLS-1$
			IConfigurationElement[] children = configElem.getChildren("init-param"); //$NON-NLS-1$
			for (IConfigurationElement child : children) {
				String name = child.getAttribute("name"); //$NON-NLS-1$
				String value = child.getAttribute("value"); //$NON-NLS-1$
				String[] current = configInitParams.get(name);
				String[] newValue;
				if (current != null) {
					newValue = Arrays.copyOf(current, current.length+1);
					newValue[current.length] = value;
				} else {
					newValue = new String[]{value};
				}
				configInitParams.put(name, newValue);
			}
		}
		List<InitParam> initParams = new LinkedList<InitParam>();
		for (Entry<String, String[]> child : configInitParams.entrySet()) {
			String name = (String)child.getKey();
			String values[] = (String[])child.getValue();
			for (String value : values) {
				initParams.add(new InitParam(name, value, this));
			}
		}
		return new InitParams(initParams);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
	 */
	@Override
	public void setInitializationData(IConfigurationElement configElem, String propertyName,
			Object data) throws CoreException {

		final String sourceMethod = "setInitializationData"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{configElem, propertyName, data});
		}
		contributingBundle = Platform.getBundle(configElem.getNamespaceIdentifier());
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
		configMap = getConfigMap(configElem);
		initParams = getConfigInitParams(configElem);

		try {
			BundleContext bundleContext = Activator.getBundleContext();
			platformServices = new PlatformServicesImpl(contributingBundle);
			name = getAggregatorName(configMap);

			// Make sure there isn't already an instance of the aggregator registered for the current name
			if (getPlatformServices().getServiceReferences(
					IAggregator.class.getName(),
					"(name=" + getName() + ")") != null) { //$NON-NLS-1$ //$NON-NLS-2$
				throw new IllegalStateException("Name already registered - " + name); //$NON-NLS-1$
			}
			registerLayerListener();
			executorsServiceTracker = getExecutorsServiceTracker(bundleContext);
			variableResolverServiceTracker = getVariableResolverServiceTracker(bundleContext);
			initExtensions(configElem);
			initOptions(initParams);
			IConfig config = newConfig();
			initWorkingDirectory(configMap, config); // this must be after initOptions
			primeCache(config);
			super.initialize(config);

		} catch (Exception e) {
			throw new CoreException(
					new Status(Status.ERROR, configElem.getNamespaceIdentifier(),
							e.getMessage(), e)
					);
		}

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	@Override
	synchronized protected void shutdown(){
		final String sourceMethod = "shutdown"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod);
		}
		// Because HttpServlet.destroy() isn't guaranteed to be called when our bundle is stopped,
		// shutdown is also called from our Activator.stop() method.  This check makes sure we don't
		// execute shutdown processing more than once for a given servlet.
		if (!isShuttingDown) {
			isShuttingDown = true;
			super.shutdown();
			executorsServiceTracker.close();
			variableResolverServiceTracker.close();
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
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
		if (getContributingBundle() != null) {
			propValue = getContributingBundle().getBundleContext().getProperty(propName);
		} else {
			propValue = super.getPropValue(propName);
		}
		return propValue;
	}

	/**
	 * Initialize the working directory for the servlet.  The working directory is in the plugin's
	 * workspace directory (returned by {@link Platform#getStateLocation(Bundle)} and is
	 * qualified with the id of the contributing bundle (so that multiple versions can co-exist
	 * in the framework without stepping on each other and so that new versions will start with
	 * a clean cache).
	 *
	 * @param configMap
	 *            Map of config name/value pairs
	 * @param config
	 *            aggregator config object
	 *
	 * @throws FileNotFoundException
	 */
	protected void initWorkingDirectory(Map<String, String> configMap, IConfig config) throws FileNotFoundException {
		final String sourceMethod = "initWorkingDirectory"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{configMap});
		}
		String versionString = Long.toString(contributingBundle.getBundleId());
		if (TypeUtil.asBoolean(config.getProperty(DISABLEBUNDLEIDDIRSCOPING_PROPNAME, null)) ||
			TypeUtil.asBoolean(getOptions().getOption(DISABLEBUNDLEIDDIRSCOPING_PROPNAME))) {
			versionString = null;
		}
		// Add the list of bundle ids with the same symbolic name as the contributing bundle so
		// that the subdirectories for any bundles still installed on the system won't be deleted.
		Collection<String> versionsToRetain = new HashSet<String>();
		Bundle[] bundles = Platform.getBundles(contributingBundle.getSymbolicName(), null);
		for (Bundle bundle : bundles) {
			versionsToRetain.add(Long.toString(bundle.getBundleId()));
		}

		File baseDir = new File(Platform.getStateLocation(contributingBundle).toFile(), "JAGGR"); //$NON-NLS-1$
		baseDir.mkdir();
		workdir = super.initWorkingDirectory(baseDir, configMap, versionString, versionsToRetain);

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	protected void initOptions(InitParams initParams) throws InvalidSyntaxException {
		List<String> values = initParams.getValues(InitParams.OPTIONS_INITPARAM);
		if (values != null && values.size() > 0) {
			String value = values.get(0);
			final File file = new File(value);
			options = new OptionsImpl(true, this) {
				@Override public File getPropsFile() { return file; }
			};
			if (log.isLoggable(Level.INFO)) {
				log.info(
						MessageFormat.format(Messages.AggregatorImpl_1,new Object[] {file.toString()})
						);
			}
		} else {
			options = new OptionsImpl(true, this);
		}
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
			log.entering(sourceClass, sourceMethod, new Object[]{configElem});
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
		 *  Init the resource converter extensions
		 */
		extensionIds = getInitParams().getValues(InitParams.RESOURCECONVERTERS_INITPARAM);
		for (String extensionId : extensionIds) {
			IExtension extension = registry.getExtension(
					IResourceConverterExtensionPoint.NAMESPACE,
					IResourceConverterExtensionPoint.NAME,
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
			log.exiting(sourceClass, sourceMethod);
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
			log.entering(sourceClass, sourceMethod, new Object[]{extensions});
		}
		for (IExtension extension : extensions) {
			for (IConfigurationElement member : extension.getConfigurationElements()) {
				try {
					// make sure the contributing bundle is started.
					Bundle contributingBundle = Platform.getBundle(member.getNamespaceIdentifier());
					if (contributingBundle != null && contributingBundle.getState() != Bundle.ACTIVE && contributingBundle.getState() != Bundle.STARTING) {
						contributingBundle.start();
					}
					Object ext = member.createExecutableExtension("class"); //$NON-NLS-1$
					Properties props = new Properties();
					for (String attributeName : member.getAttributeNames()) {
						props.put(attributeName, member.getAttribute(attributeName));
					}
					InitParams initParams = getConfigInitParams(member);
					registerExtension(
							new AggregatorExtension(ext, props, initParams,
									extension.getExtensionPointUniqueIdentifier(),
									extension.getUniqueIdentifier(),
									this), null
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
			log.exiting(sourceClass, sourceMethod);
		}
	}

	/**
	 * Determines if the current cache data is valid and if not, then attempts to prime the
	 * cache using a config specified bundle.
	 *
	 * @param config the config object
	 * @throws IOException
	 */
	void primeCache(IConfig config) throws IOException {
		final String sourceMethod = "primeCache"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{config});
		}

		String cachePrimerBundleName = null;

		// Get config param.  If not specified, then return
		Object cachePrimerBundleNameObj = config.getProperty(CACHEPRIMERBUNDLENAME_CONFIGPARAM, String.class);
		if (cachePrimerBundleNameObj instanceof String) {
			cachePrimerBundleName = (String)cachePrimerBundleNameObj;
		}
		if (cachePrimerBundleName == null) {
			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod, CACHEPRIMERBUNDLENAME_CONFIGPARAM + " not specified."); //$NON-NLS-1$
			}
			return;
		}

		// De-serialize the cache control object and validate the cache against the current deployment
		boolean cacheValid = true;
		File controlFile = new File(getWorkingDirectory(), CacheControl.CONTROL_SERIALIZATION_FILENAME);
		ObjectInputStream is = null;
		if (!controlFile.exists()) {
			cacheValid = false;
		} else {
			CacheControl control = null;
			try {
				is = new ObjectInputStream(new FileInputStream(controlFile));
				control = (CacheControl)is.readObject();
				if (!control.getOptionsMap().equals(getOptions().getOptionsMap()) ||
					!control.getRawConfig().equals(config.toString())) {
					cacheValid = false;
				}
				if (control.getInitStamp() != 0) {
					if (isTraceLogging) {
						log.exiting(sourceClass, sourceMethod, "Deployment contains customizations"); //$NON-NLS-1$
					}
					return;
				}
			} catch (Exception ex) {
				if (log.isLoggable(Level.WARNING)) {
					log.logp(Level.WARNING, sourceClass, sourceMethod, ex.getMessage(), ex);
				}
			} finally {
				if (is != null) IOUtils.closeQuietly(is);
			}
		}
		if (cacheValid) {
			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod, "Cache is valid"); //$NON-NLS-1$
			}
			return;
		}

		// Try to load the cache primer bundle
		Bundle bundle = BundleResolverFactory.getResolver(contributingBundle).getBundle(cachePrimerBundleName);
		if (bundle == null) {
			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod, "Bundle " + cachePrimerBundleName + " not loaded"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			return;
		}
		// Verify that the cache bust header in the bundle matches the current cache bust value
		String cacheBust = (String)bundle.getHeaders().get(CACHEBUST_HEADER);
		if (!AggregatorUtil.getCacheBust(config, options).equals(cacheBust)) {
			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod, "Stale cache primer bundle"); //$NON-NLS-1$
			}
			return;
		}
		// Get the bundle file location
		String loc = bundle.getLocation();
		if (isTraceLogging) {
			log.logp(Level.FINER, sourceClass, sourceMethod, "Cache primer bundle location = " + loc); //$NON-NLS-1$
		}

		final File sourceDir = getWorkingDirectory();
		File[] files = sourceDir.listFiles();
		// If the cache directory has content, then remove it
		if (files.length > 0) {
			// Move content to be deleted to the trash directory so it can be deleted asynchronously
			// We don't want file deletions to delay server startup.
			File trashDir = new File(sourceDir, TRASHDIRNAME);

			// create the trash directory if it doesn't already exist
			if (!trashDir.exists()) {
				trashDir.mkdir();
			}

			// Find a name for the target directory in the trash directory.  We need to avoid
			// name collisions when moving files in the event that the trash directory already
			// has content.
			Set<String> names = new HashSet<String>(Arrays.asList(trashDir.list()));
			String targetDirName = MessageFormat.format(TEMPDIRFORMAT, new Object[]{0});
			for (int i = 0; i < names.size()+1; i++) {
				String testName = MessageFormat.format(TEMPDIRFORMAT, new Object[]{i});
				if (!names.contains(testName)) {
					targetDirName = testName;
					break;
				}
			}
			// Create the target directory
			File targetDir = new File(trashDir, targetDirName);
			targetDir.mkdir();
			// Move files from the cache directory to the target directory
			for (File file : files) {
				File targetFile = new File(targetDir, file.getName());
				if (TRASHDIRNAME.equals(targetFile.getName())) {
					continue;
				}
				if (!file.renameTo(targetFile)) {
					throw new IOException("Move failed: " + file.getAbsolutePath() + " => " + targetFile.getAbsolutePath()); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			// Delete the trash directory asynchronously
			getExecutors().getFileDeleteExecutor().schedule(new Runnable() {
				@Override
				public void run() {
					try {
						FileUtils.deleteDirectory(new File(sourceDir, TRASHDIRNAME));
					} catch (IOException ex) {
						if (log.isLoggable(Level.WARNING)) {
							log.logp(Level.WARNING, sourceClass, sourceMethod, ex.getMessage(), ex);
						}
					}
				}
			}, 30, TimeUnit.SECONDS);
		}
		// Now unzip the cache primer to the cache directory
		BundleUtil.extract(bundle, getWorkingDirectory(), JAGGR_CACHE_DIRECTORY);

		if (log.isLoggable(Level.INFO)) {
			log.logp(Level.INFO, sourceClass, sourceMethod,
					MessageFormat.format(Messages.AggregatorImpl_2, new Object[]{cachePrimerBundleName})
			);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	/**
	 * Command handler to create a cache primer bundle containing the contents of the cache
	 * directory.
	 *
	 * @param bundleSymbolicName
	 *            the symbolic name of the bundle to be created
	 * @param bundleFileName
	 *            the filename of the bundle to be created
	 * @return the string to be displayed in the console (the fully qualified filename of the
	 *         bundle if successful or an error message otherwise)
	 * @throws IOException
	 */
	public String createCacheBundle(String bundleSymbolicName, String bundleFileName) throws IOException {
		final String sourceMethod = "createCacheBundle"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{bundleSymbolicName, bundleFileName});
		}
		// Serialize the cache
		getCacheManager().serializeCache();

		// De-serialize the control file to obtain the cache control data
		File controlFile = new File(getWorkingDirectory(), CacheControl.CONTROL_SERIALIZATION_FILENAME);
		CacheControl control = null;
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(controlFile));;
		try {
			control = (CacheControl)ois.readObject();
		} catch (Exception ex) {
			throw new IOException(ex);
		} finally {
			IOUtils.closeQuietly(ois);
		}
		if (control.getInitStamp() != 0) {
			return Messages.AggregatorImpl_3;
		}
		// create the bundle manifest
		InputStream is = AggregatorImpl.class.getClassLoader().getResourceAsStream(MANIFEST_TEMPLATE);
		StringWriter writer = new StringWriter();
		CopyUtil.copy(is, writer);
		String template = writer.toString();
		String manifest = MessageFormat.format(template, new Object[]{
				Long.toString(new Date().getTime()),
				getContributingBundle().getHeaders().get("Bundle-Version"), //$NON-NLS-1$
				bundleSymbolicName,
				AggregatorUtil.getCacheBust(this)
		});
		// create the jar
		File bundleFile = new File(bundleFileName);
		ZipUtil.Packer packer = new ZipUtil.Packer();
		packer.open(bundleFile);
		try {
			packer.packDirectory(getWorkingDirectory(), JAGGR_CACHE_DIRECTORY);
			packer.packEntryFromStream("META-INF/MANIFEST.MF", new ByteArrayInputStream(manifest.getBytes("UTF-8")), new Date().getTime()); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			packer.close();
		}
		String result = bundleFile.getCanonicalPath();

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, result);
		}
		return result;
	}

	/**
	 * Implementation of eponymous console command. Provided to allow cache priming requests to be
	 * issued via the server console by automation scripts.
	 *
	 * @param requestUrl
	 *            the URL to process
	 * @return the status code as a string
	 * @throws IOException
	 * @throws ServletException
	 */
	public String processRequestUrl(String requestUrl) throws IOException, ServletException {
		ConsoleHttpServletRequest req = new ConsoleHttpServletRequest(getServletConfig().getServletContext(), requestUrl);
		OutputStream nulOutputStream = new OutputStream() {
			@Override public void write(int b) throws IOException {}
		};
		ConsoleHttpServletResponse resp = new ConsoleHttpServletResponse(nulOutputStream);
		doGet(req, resp);
		return Integer.toString(resp.getStatus());
	}

}

