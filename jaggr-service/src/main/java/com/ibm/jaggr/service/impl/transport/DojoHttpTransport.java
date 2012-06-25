/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.transport;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.Status;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IAggregatorExtension;
import com.ibm.jaggr.service.IExtensionInitializer;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.impl.Activator;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.resource.AggregationResource;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.IResourceFactory;
import com.ibm.jaggr.service.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.service.transport.IHttpTransport;

/**
 * Implements the functionality specific for the Dojo Http Transport (supporting
 * the dojo AMD loader).
 */
public class DojoHttpTransport extends AbstractHttpTransport implements IHttpTransport, IExecutableExtension, IExtensionInitializer {
	private static final Logger log = Logger.getLogger(DojoHttpTransport.class.getName());
	
    static String comboUriStr = "namedbundleresource://" + Activator.BUNDLE_NAME + "/WebContent/dojo"; //$NON-NLS-1$ //$NON-NLS-2$
    static String textPluginProxyUriStr = comboUriStr + "/text"; //$NON-NLS-1$
    static String loaderExtensionPath = "/WebContent/dojo/loaderExt.js"; //$NON-NLS-1$
    static String[] loaderExtensionResources = {
    	"../loaderExtCommon.js", //$NON-NLS-1$
    	"./_loaderExt.js" //$NON-NLS-1$
    };
    static String dojo = "dojo"; //$NON-NLS-1$
    static String aggregatorTextPluginAlias = "__aggregator_text_plugin"; //$NON-NLS-1$
    static String dojoTextPluginAlias = "__original_text_plugin"; //$NON-NLS-1$
    static String dojoTextPluginAliasFullPath = dojo+"/"+dojoTextPluginAlias; //$NON-NLS-1$
    static String dojoTextPluginName = "text"; //$NON-NLS-1$
    static String dojoTextPluginFullPath = dojo+"/"+dojoTextPluginName; //$NON-NLS-1$

    private String pluginUniqueId = ""; //$NON-NLS-1$
    private URI comboUri;
    private List<String[]> clientConfigAliases = new LinkedList<String[]>();
    
    
    /**
     * Property accessor for the comboUriStr property
     * 
     * @return the combo URI string value
     */
    protected String getComboUriStr() {
    	return comboUriStr;
    }
    
    /**
     * Property accessor for the plugin unique id for this extension
     * 
     * @return the plugin unique id
     */
    protected String getPluginUniqueId() {
    	return pluginUniqueId;
    }
    
    /**
     * Property accessor for the loaderExtensionPath property
     * 
     * @return the loader extension path
     */
    protected String getLoaderExtensionPath() {
    	return loaderExtensionPath;
    }
    
    /**
     * Property accessor for the loaderExtensionResources property
     * 
     * @return the loader extension resources
     */
    protected String[] getLoaderExtensionResources() {
    	return loaderExtensionResources;
    }
    
    /**
     * Returns the list of client config aliases
     * 
     * @return the client aliases
     */
    protected List<String[]> getClientConfigAliases() {
    	return clientConfigAliases;
    }
    
    protected String getAggregatorTextPluginName() {
    	return getResourcePathId() + "/text";
    }
    
    /* (non-Javadoc)
     * @see com.ibm.jaggr.service.transport.AbstractHttpTransport#getLayerContribution(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.service.transport.IHttpTransport.LayerContributionType, java.lang.String)
     */
    @Override
	public String getLayerContribution(HttpServletRequest request,
			LayerContributionType type, String mid) {
    	
    	// Implement wrapping of modules required by dojo loader for modules that 
    	// are loaded with the loader.
		switch (type) {
		case BEGIN_REQUIRED_MODULES:
			return "require({cache:{"; //$NON-NLS-1$
		case BEFORE_FIRST_REQUIRED_MODULE:
			return "\"" + mid + "\":function(){"; //$NON-NLS-1$ //$NON-NLS-2$
		case BEFORE_SUBSEQUENT_REQUIRED_MODULE:
			return ",\"" + mid + "\":function(){"; //$NON-NLS-1$ //$NON-NLS-2$
		case AFTER_REQUIRED_MODULE:
			return "}"; //$NON-NLS-1$
		case END_REQUIRED_MODULES:
			{
				StringBuffer sb = new StringBuffer();
				sb.append("}});require({cache:{}});require(["); //$NON-NLS-1$ 
				int i = 0;
				for (String name : mid.split(",")) { //$NON-NLS-1$ 
					sb.append(i++ > 0 ? "," : "").append("\"").append(name).append("\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ 
				}
				sb.append("]);"); //$NON-NLS-1$
				return sb.toString();
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.AbstractHttpTransport#getCacheKeyGenerators()
	 */
	@Override
	public ICacheKeyGenerator[] getCacheKeyGenerators() {
		/*
		 * The content generated by this transport is invariant with regard
		 * to request parameters (for a given set of modules) so we don't
		 * need to provide a cache key generator.
		 */
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.AbstractHttpTransport#getComboUri()
	 */
	@Override 
	protected URI getComboUri() {
		return comboUri;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.AbstractHttpTransport#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
	 */
	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName,
			Object data) throws CoreException {
		
		// Save this extension's pluginUniqueId
		pluginUniqueId = config.getDeclaringExtension().getUniqueIdentifier();

		// Initialize the combo uri
		super.setInitializationData(config, propertyName, data);
		try {
			comboUri = new URI(getComboUriStr()); 
		} catch (URISyntaxException e) {
			throw new CoreException(
					new Status(Status.ERROR, config.getNamespaceIdentifier(), 
							e.getMessage(), e)
				);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.AbstractHttpTransport#initialize(com.ibm.jaggr.service.IAggregator, com.ibm.jaggr.service.IAggregatorExtension, com.ibm.jaggr.service.IExtensionInitializer.IExtensionRegistrar)
	 */
	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension extension, IExtensionRegistrar reg) {
		super.initialize(aggregator, extension, reg);

		// Get first resource factory extension so we can add to beginning of list
    	Iterable<IAggregatorExtension> resourceFactoryExtensions = aggregator.getResourceFactoryExtensions();
    	IAggregatorExtension first = resourceFactoryExtensions.iterator().next();
    	
    	// Register the loaderExt resource factory
    	Properties attributes = new Properties();
    	attributes.put("scheme", "namedbundleresource"); //$NON-NLS-1$ //$NON-NLS-2$
		reg.registerExtension(
				new LoaderExtensionResourceFactory(), 
				attributes,
				IResourceFactoryExtensionPoint.ID,
				getPluginUniqueId(),
				first);
		
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.transport.AbstractHttpTransport#getDynamicLoaderExtensionJavaScript()
	 */
	@Override
	protected String getDynamicLoaderExtensionJavaScript() {
		StringBuffer sb = new StringBuffer();
		sb.append("plugins[\"") //$NON-NLS-1$
		  .append(getResourcePathId())
		  .append("/text") //$NON-NLS-1$
		  .append("\"] = 1;\r\n"); //$NON-NLS-1$
		for (String[] alias : getClientConfigAliases()) {
			sb.append("aliases.push([\"" + alias[0] + "\", \"" + alias[1] + "\"]);\r\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		// Add server option settings that we care about
		IOptions options = getAggregator().getOptions();
		sb.append("combo.serverOptions={skipHasFiltering:") //$NON-NLS-1$
		  .append(Boolean.toString(options.isSkipHasFiltering()))
		  .append("};\r\n"); //$NON-NLS-1$
		
		// add in the super class's contribution
		sb.append(super.getDynamicLoaderExtensionJavaScript());
		
		return sb.toString();
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfigModifier#modifyConfig(com.ibm.jaggr.service.IAggregator, java.util.Map)
	 */
	/**
	 * Add paths and aliases mappings to the config for the dojo text plugin.
	 * <p>
	 * The reason this is a bit complex is that in order to support text plugin
	 * resources that specify an absolute path (e.g.
	 * dojo/text!http://server.com/resource.html), we use server-side config
	 * paths entries to map dojo/text to a proxy plugin module provided by this
	 * transport. The proxy plugin module examines the module id of the
	 * requested resource, and if the id is absolute, it delegates to the dojo
	 * text plugin which can still be accessed from the client using an
	 * alternative path which we set up below. If the module id is relative,
	 * then the proxy plugin module delegates to the aggregator text plugin
	 * (which is a pseudo plugin that resolves to the aggregator itself).
	 * <p>
	 * In addition to the paths mappings, aliases are used, both in the
	 * server-side config and in the client config, to allow the proxy plugin
	 * module to refer to the dojo text plugin module and the aggregator text
	 * plugin via static names, allowing the actual names to be dynamic without
	 * requiring us to dynamically modify the contents of the proxy plugin
	 * resource itself.  This method sets the server-side config aliases
	 * and sets up the client-side config aliases to be set in 
	 * {@link #contributeLoaderExtensionJavaScript(String)}.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void modifyConfig(IAggregator aggregator, Map<String, Object> config) {
		// let the superclass do its thing
		super.modifyConfig(aggregator, config);
		
		// Get the server-side config properties we need to start with
		Map<String, String> paths = (Map<String, String>)config.get(IConfig.PATHS_CONFIGPARAM);
		List<Object> packages = (List<Object>)config.get(IConfig.PACKAGES_CONFIGPARAM);
		List<Object> aliases = (List<Object>)config.get(IConfig.ALIASES_CONFIGPARAM);
		
		// Get the URI for the location of the dojo package on the server
		String dojoLocUriStr = null;
		if (packages != null) {
			for (Object pkgObj : packages) {
				Map<String, String> pkg = (Map<String, String>)pkgObj;
				if (dojo.equals(pkg.get(IConfig.PKGNAME_CONFIGPARAM))) {
					dojoLocUriStr = pkg.get(IConfig.PKGLOCATION_CONFIGPARAM);
					if (dojoLocUriStr == null) {
						dojoLocUriStr = ""; //$NON-NLS-1$
					}
					break;
				}
			}
		}
		// Bail if we can't find dojo
		if (dojoLocUriStr == null) {
			if (log.isLoggable(Level.WARNING)) {
				log.warning(Messages.DojoHttpTransport_0);
			}
			return;
		}
		
		if (paths != null && paths.get(dojoTextPluginFullPath) != null) {
			// if config overrides dojo/text, then bail
			if (log.isLoggable(Level.INFO)) {
				log.info(MessageFormat.format(Messages.DojoHttpTransport_2,
						new Object[]{dojoTextPluginFullPath}));
			}
			return;
		}

		// Create the paths and aliases config properties if necessary
		if (paths == null) {
			config.put(IConfig.PATHS_CONFIGPARAM, new HashMap<String, String>());
			paths = (Map<String, String>)config.get(IConfig.PATHS_CONFIGPARAM);
		}
		if (aliases == null) {
			config.put(IConfig.ALIASES_CONFIGPARAM, new ArrayList<Object>());
			aliases = (List<Object>)config.get(IConfig.ALIASES_CONFIGPARAM);
		}

		// Specify paths entry to map dojo/text to our text plugin proxy
		if (log.isLoggable(Level.INFO)) {
			log.info(MessageFormat.format(
					Messages.DojoHttpTransport_3,
					new Object[]{dojoTextPluginFullPath, textPluginProxyUriStr}
			));
		}
		paths.put(dojoTextPluginFullPath, textPluginProxyUriStr);
		
		// Specify paths entry to map the dojo text plugin alias name to the original
		// dojo text plugin
		String dojoTextPluginUriStr = dojoLocUriStr + 
				(dojoLocUriStr.length() == 0 || dojoLocUriStr.endsWith("/") ? "" : "/") + dojoTextPluginName; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if (log.isLoggable(Level.INFO)) {
			log.info(MessageFormat.format(
					Messages.DojoHttpTransport_3,
					new Object[]{dojoTextPluginAliasFullPath, dojoTextPluginUriStr}
			));
		}
		paths.put(dojoTextPluginAliasFullPath, dojoTextPluginUriStr);
		
		// Specify an alias mapping for the static alias used
		// by the proxy module to the name which includes the path dojo/ so that
		// relative module ids in dojo/text will resolve correctly.
		// 
		// We need this in the server-side config so that the server can follow
		// module dependencies from the plugin proxy
		if (log.isLoggable(Level.INFO)) {
			log.info(MessageFormat.format(
					Messages.DojoHttpTransport_4,
					new Object[]{dojoTextPluginAlias, dojoTextPluginAliasFullPath}
			));
		}
		List<String> alias = new ArrayList<String>(2);
		alias.add(0, dojoTextPluginAlias);
		alias.add(1, dojoTextPluginAliasFullPath);
		aliases.add(alias);
		
		// Add alias definitions to be specified in client-side config
		getClientConfigAliases().add(new String[]{alias.get(0), alias.get(1)});
		// Add alias mapping for aggregator text plugin alias used in the text plugin proxy
		// to the actual aggregator text plugin name as determined from the module builder
		// definitions
		getClientConfigAliases().add(new String[]{aggregatorTextPluginAlias, getAggregatorTextPluginName()});
	}
	
	/**
	 * Resource factory that creates a
	 * {@link AbstractHttpTransport.LoaderExtensionResource} for the dojo http
	 * transport when the loader extension resource URI is requested
	 */
	private class LoaderExtensionResourceFactory implements IResourceFactory {

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceFactory#handles(java.net.URI)
		 */
		@Override
		public boolean handles(URI uri) {
			String path = uri.getPath();
			if (path.equals(getLoaderExtensionPath())) {
				if (uri.getScheme().equals(getComboUri().getScheme()) 
						&& uri.getHost().equals(getComboUri().getHost())) {
					return true;
				}
			}
			return false;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.service.resource.IResourceFactory#newResource(java.net.URI)
		 */
		@Override
		public IResource newResource(URI uri) {
			String path = uri.getPath();
			if (!path.equals(getLoaderExtensionPath())) {
				throw new UnsupportedOperationException(uri.toString());
			}
			List<IResource> aggregate = new ArrayList<IResource>(2);
			for (String res : getLoaderExtensionResources()) {
				aggregate.add(getAggregator().newResource(uri.resolve(res)));
			}
			return new LoaderExtensionResource(new AggregationResource(uri, aggregate));
		}
	}
}
