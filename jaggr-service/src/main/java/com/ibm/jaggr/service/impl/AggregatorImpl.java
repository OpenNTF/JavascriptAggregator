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

import java.io.File;
import java.io.FileNotFoundException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.regex.Matcher;

import org.apache.commons.io.FileUtils;
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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IVariableResolver;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.impl.AbstractAggregatorImpl;
import com.ibm.jaggr.core.impl.AggregatorLayerListener;
import com.ibm.jaggr.core.impl.Messages;
import com.ibm.jaggr.core.impl.OverrideFoldersTreeWalker;
import com.ibm.jaggr.core.impl.PlatformAggregatorFactory;
import com.ibm.jaggr.core.impl.deps.DependenciesImpl;
import com.ibm.jaggr.core.impl.options.OptionsImpl;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransportExtensionPoint;

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
@SuppressWarnings({ "serial" })
public class AggregatorImpl extends AbstractAggregatorImpl implements IExecutableExtension, BundleListener {

	/**
	 * Default value for resourcefactories init-param
	 */
	protected static final String DEFAULT_RESOURCEFACTORIES =
		"com.ibm.jaggr.service.default.resourcefactories"; //$NON-NLS-1$

	/**
	 * Default value for modulebuilders init-param
	 */
	protected static final String DEFAULT_MODULEBUILDERS =
		"com.ibm.jaggr.service.default.modulebuilders"; //$NON-NLS-1$

	/**
	 * Default value for httptransport init-param
	 */
	protected static final String DEFAULT_HTTPTRANSPORT =
		"com.ibm.jaggr.service.dojo.httptransport"; //$NON-NLS-1$
    
	protected Bundle bundle;
	protected ServiceTracker optionsServiceTracker;
    protected ServiceTracker executorsServiceTracker;
	protected ServiceTracker variableResolverServiceTracker;
	
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
        try {
    		BundleContext bundleContext = contributingBundle.getBundleContext();
    		((com.ibm.jaggr.service.PlatformServicesImpl)(PlatformAggregatorFactory.getPlatformAggregator())).setBundleContext(bundleContext);
    		bundle = bundleContext.getBundle();
            name = getAggregatorName(configElem);
            initParams = getInitParams(configElem);
       		executorsServiceTracker = getExecutorsServiceTracker(bundleContext);
       		variableResolverServiceTracker = getVariableResolverServiceTracker(bundleContext);
            initOptions(initParams);
       		workdir = initWorkingDirectory( // this must be after initOptions
       				Platform.getStateLocation(getBundleContext().getBundle()).toFile(),
       				configElem
       		);
        	initExtensions(configElem);

        	// create the config.  Keep it local so it won't be seen by deps and cacheMgr
        	// until after we check for customization last-mods.  Then we'll set the config
        	// in the instance data and call the config listeners.
	        IConfig config = newConfig();

	        // Check last-modified times of resources in the overrides folders.  These resources
	        // are considered to be dynamic in a production environment and we want to
	        // detect new/changed resources in these folders on startup so that we can clear
	        // caches, etc.
	        OverrideFoldersTreeWalker walker = new OverrideFoldersTreeWalker(this, config);
        	walker.walkTree();
        	deps = newDependencies(walker.getLastModifiedJS());
			cacheMgr = newCacheManager(walker.getLastModified());
	        this.config = config;
			// Notify listeners
			notifyConfigListeners(1);

			bundleContext.addBundleListener(this);
        } catch (Exception e) {
			throw new CoreException(
					new Status(Status.ERROR, configElem.getNamespaceIdentifier(),
							e.getMessage(), e)
				);
		}
        Hashtable<String, String> dict = new Hashtable<String, String>();
        //Properties dict = new Properties();			
        dict.put("name", getName()); //$NON-NLS-1$
        registrations.add(getBundleContext().registerService(
        		IAggregator.class.getName(), this, dict));

        registerLayerListener();
	}

	

	/* (non-Javadoc)
	 * @see org.osgi.framework.BundleListener#bundleChanged(org.osgi.framework.BundleEvent)
	 */
	@Override
    synchronized protected void shutdown(){
    	super.shutdown();
    	BundleContext bundleContext = getBundleContext();
    	bundleContext.removeBundleListener(this);
		optionsServiceTracker.close();
		executorsServiceTracker.close();
		variableResolverServiceTracker.close();
    }
    
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

	



	/* (non-Javadoc)
	 * @see com.ibm.servlets.amd.aggregator.IAggregator#substituteProps(java.lang.String, com.ibm.servlets.amd.aggregator.IAggregator.SubstitutionTransformer)
	 */
	@Override
	public String substituteProps(String str, SubstitutionTransformer transformer) {
		if (str == null) {
			return null;
		}
		StringBuffer buf = new StringBuffer();
		Matcher matcher = pattern.matcher(str);
	    while ( matcher.find() ) {
	    	String propName = matcher.group(1);
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
	    	if (propValue != null) {
	    		if (transformer != null) {
	    			propValue = transformer.transform(propName, propValue);
	    		}
	    		matcher.appendReplacement(
	    				buf,
	    				propValue
	    					.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
	    					.replace("$", "\\$")  //$NON-NLS-1$ //$NON-NLS-2$
	    		);
	    	} else {
	    		matcher.appendReplacement(buf, "\\${"+propName+"}"); //$NON-NLS-1$ //$NON-NLS-2$
	    	}
	    }
	    matcher.appendTail(buf);
	    return buf.toString();
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
   				localOptions = new OptionsImpl(registrationName, true) {
   					@Override public File getPropsFile() { return file; }
   				};
   				if (log.isLoggable(Level.INFO)) {
   					log.info(
   						MessageFormat.format(Messages.CustomOptionsFile,new Object[] {file.toString()})
   					);
   				}
   			}
   		}
   		Hashtable<String, String> dict = new Hashtable<String, String>();
        //Properties dict = new Properties();
        dict.put("name", registrationName); //$NON-NLS-1$
        registrations.add(getBundleContext().registerService(
        		IOptionsListener.class.getName(), this, dict));

	}

	/**
	 * Loads and initializes the resource factory, module builder and
	 * http transport extensions specified in the configuration
	 * element for this aggregator
	 *
	 * @param configElem The configuration element
	 * @throws CoreException
	 * @throws NotFoundException
	 */
	protected void initExtensions(IConfigurationElement configElem) throws CoreException, NotFoundException {
    	/*
    	 *  Init the resource factory extensions
    	 */
    	Collection<String> resourceFactories = getInitParams().getValues(InitParams.RESOURCEFACTORIES_INITPARAM);
    	if (resourceFactories.size() == 0) {
    		resourceFactories.add(DEFAULT_RESOURCEFACTORIES);
    	}
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        for (String resourceFactory : resourceFactories) {
	        IExtension extension = registry.getExtension(
	        		IResourceFactoryExtensionPoint.NAMESPACE,
	        		IResourceFactoryExtensionPoint.NAME,
	        		resourceFactory);
	        if (extension == null) {
	        	throw new NotFoundException(resourceFactory);
	        }
			for (IConfigurationElement member : extension.getConfigurationElements()) {
	            IResourceFactory factory = (IResourceFactory)member.createExecutableExtension("class"); //$NON-NLS-1$
	            Properties props = new Properties();
            	for (String name : member.getAttributeNames()) {
            		props.put(name, member.getAttribute(name));
            	}
            	registerResourceFactory(new AggregatorExtension(extension, factory, props), null);
			}
        }

        /*
         *  Init the module builder extensions
         */
        Collection<String> moduleBuilders = getInitParams().getValues(InitParams.MODULEBUILDERS_INITPARAM);
        if (moduleBuilders.size() == 0) {
        	moduleBuilders.add(DEFAULT_MODULEBUILDERS);
        }
        for (String moduleBuilder : moduleBuilders) {
        	IExtension extension = registry.getExtension(
        			IModuleBuilderExtensionPoint.NAMESPACE,
        			IModuleBuilderExtensionPoint.NAME,
        			moduleBuilder);
	        if (extension == null) {
	        	throw new NotFoundException(moduleBuilder);
	        }
			for (IConfigurationElement member : extension.getConfigurationElements()) {
            	IModuleBuilder builder = (IModuleBuilder)member.createExecutableExtension("class"); //$NON-NLS-1$
            	Properties props = new Properties();
            	for (String name : member.getAttributeNames()) {
            		props.put(name, member.getAttribute(name));
            	}
            	registerModuleBuilder(new AggregatorExtension(extension, builder, props), null);
			}
        }

        /*
         * Init the http transport extension
         */
        Collection<String> transports = getInitParams().getValues(InitParams.TRANSPORT_INITPARAM);
        if (transports.size() == 0) {
        	transports.add(DEFAULT_HTTPTRANSPORT);
        }
        if (transports.size() != 1) {
        	throw new IllegalStateException(transports.toString());
        }
        String transportName = transports.iterator().next();
    	IExtension extension = registry.getExtension(
    			IHttpTransportExtensionPoint.NAMESPACE,
    			IHttpTransportExtensionPoint.NAME,
    			transportName);
        if (extension == null) {
        	throw new NotFoundException(transportName);
        }
		IConfigurationElement member = extension.getConfigurationElements()[0];
    	Properties props = new Properties();
    	IHttpTransport transport = (IHttpTransport)member.createExecutableExtension("class"); //$NON-NLS-1$
    	for (String attrname : member.getAttributeNames()) {
    		props.put(attrname, member.getAttribute(attrname));
    	}
    	registerHttpTransport(new AggregatorExtension(extension, transport, props));

    	/*
    	 *  Now call setAggregator on the loaded extensions starting with the
    	 *  transport and then the rest of the extensions.
    	 */
    	ExtensionRegistrar reg = new ExtensionRegistrar();
    	callExtensionInitializers(Arrays.asList(new IAggregatorExtension[]{getHttpTransportExtension()}), reg);
    	callExtensionInitializers(getResourceFactoryExtensions(), reg);
    	callExtensionInitializers(getModuleBuilderExtensions(), reg);
    	reg.open = false;
    }
	
	/**
	 * Returns the working directory for this aggregator.
	 * <p>
	 * This method is called during aggregator intialization.  Subclasses may
	 * override this method to initialize the aggregator using a different
	 * working directory.  Use the public {@link #getWorkingDirectory()} method
	 * to get the working directory from an initialized aggregator.
	 *
	 * @param configElem
	 *            The configuration element. Not used by this class but provided
	 *            for use by subclasses.
	 * @return The {@code File} object for the working directory
	 * @throws FileNotFoundException
	 */
	public File initWorkingDirectory(File defaultLocation, IConfigurationElement configElem) throws FileNotFoundException {
		String dirName = getOptions().getCacheDirectory();
		File dirFile = null;
		if (dirName == null) {
			dirFile = defaultLocation;
		} else {
			// Make sure the path exists
			dirFile = new File(dirName);
			dirFile.mkdirs();
		}
		if (!dirFile.exists()) {
			throw new FileNotFoundException(dirFile.toString());
		}
        // Create a directory using the alias name within the contributing bundle's working
        // directory
		File workDir = new File(dirFile, getName());
        // Create a bundle-version specific subdirectory.  If the directory doesn't exist, assume
        // the bundle has been updated and clean out the workDir to remove all stale cache files.
		File servletDir = new File(workDir, Long.toString(getBundleContext().getBundle().getBundleId()));
		if (!servletDir.exists()) {
			FileUtils.deleteQuietly(workDir);
		}
   		servletDir.mkdirs();
   		if (!servletDir.exists()) {
   			throw new FileNotFoundException(servletDir.getAbsolutePath());
   		}
   		return servletDir;
	}

	/**
	 * Returns the name for this aggregator
	 * <p>
	 * This method is called during aggregator intialization.  Subclasses may
	 * override this method to initialize the aggregator using a different
	 * name.  Use the public {@link AggregatorImpl#getName()} method
	 * to get the name of an initialized aggregator.
	 *
	 * @param configElem
	 *            The configuration element.
	 * @return The aggregator name
	 */
	protected String getAggregatorName(IConfigurationElement configElem) {
    	// trim leading and trailing '/'
		String alias = configElem.getAttribute("alias"); //$NON-NLS-1$
        while (alias.charAt(0) == '/')
        	alias = alias.substring(1);
        while (alias.charAt(alias.length()-1) == '/')
        	alias = alias.substring(0, alias.length()-1);
        return alias;
	}

	/**
	 * Returns the init params for this aggregator
	 * <p>
	 * This method is called during aggregator intialization.  Subclasses may
	 * override this method to initialize the aggregator using different
	 * init params.  Use the public {@link AggregatorImpl#getInitParams()} method
	 * to get the init params for an initialized aggregator.
	 *
	 * @param configElem
	 *            The configuration element.
	 * @return The init params
	 */
	protected InitParams getInitParams(IConfigurationElement configElem) {
        List<InitParam> initParams = new LinkedList<InitParam>();
        IConfigurationElement[] children = configElem.getChildren("init-param"); //$NON-NLS-1$
        for (IConfigurationElement child : children) {
        	String name = child.getAttribute("name"); //$NON-NLS-1$
        	String value = child.getAttribute("value"); //$NON-NLS-1$
        	initParams.add(new InitParam(name, value));
        }
        return new InitParams(initParams);
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
	 * Instantiates a new dependencies object
	 *
	 * @return The new dependencies
	 */
	protected IDependencies newDependencies(long stamp) {
		return new DependenciesImpl(this, stamp);
	}

	/**
	 * Registers the layer listener
	 */
	protected void registerLayerListener() {
        Properties dict = new Properties();
        dict.put("name", getName()); //$NON-NLS-1$
        registrations.add(getBundleContext().registerService(
        		ILayerListener.class.getName(), new AggregatorLayerListener(this), dict));
	}
	
	
}

