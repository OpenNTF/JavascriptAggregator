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

package com.ibm.jaggr.service.impl.transport;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.Status;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IAggregatorExtension;
import com.ibm.jaggr.service.IExtensionInitializer;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.config.IConfig.Location;
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
    static URI dojoPluginUri;

    static {
    	try {
    		dojoPluginUri = new URI("./"+dojoTextPluginName);
    	} catch (URISyntaxException e) {
    		// Should never happen
    		throw new RuntimeException(e);
    	}
    }

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
	public List<ICacheKeyGenerator> getCacheKeyGenerators() {
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
	@Override
	public void modifyConfig(IAggregator aggregator, Scriptable config) {
		// let the superclass do its thing
		super.modifyConfig(aggregator, config);
		
		// Get the server-side config properties we need to start with
		Object pathsObj = config.get(IConfig.PATHS_CONFIGPARAM, config);
		Object packagesObj = config.get(IConfig.PACKAGES_CONFIGPARAM, config);
		Object aliasesObj = config.get(IConfig.ALIASES_CONFIGPARAM, config);
		
		Scriptable paths = (pathsObj != null && pathsObj instanceof Scriptable) ? (Scriptable)pathsObj : null;
		Scriptable packages = (packagesObj != null && packagesObj instanceof Scriptable) ? (Scriptable)packagesObj : null;
		Scriptable aliases = (aliasesObj != null && aliasesObj instanceof Scriptable) ? (Scriptable)aliasesObj : null;
		// Get the URI for the location of the dojo package on the server
		IConfig.Location dojoLoc = null;
		if (packages != null) {
			for (Object id : packages.getIds()) {
				if (!(id instanceof Number)) {
					continue;
				}
				Number i = (Number)id;
				Object pkgObj = packages.get((Integer)i, packages);
				if (pkgObj instanceof Scriptable) {
					Scriptable pkg = (Scriptable)pkgObj;
					if (dojo.equals(pkg.get(IConfig.PKGNAME_CONFIGPARAM, pkg).toString())) {
						try {
							Object dojoLocObj = pkg.get(IConfig.PKGLOCATION_CONFIGPARAM, pkg);
							if (dojoLoc == Scriptable.NOT_FOUND) {
								dojoLoc = new IConfig.Location(new URI("."), null); //$NON-NLS-1$
							} else if (dojoLocObj instanceof Scriptable) {
								Scriptable values = (Scriptable)dojoLocObj;
								String str = Context.toString(values.get(0, values));
								if (!str.endsWith("/")) str += '/';
								URI primary = new URI(str).normalize(), override = null;
								Object obj = values.get(1, values);
								if (obj != Scriptable.NOT_FOUND) {
									str = Context.toString(obj);
									if (!str.endsWith("/")) str += '/';
									try {
										override = new URI(str).normalize();
									} catch (URISyntaxException ignore) {}
								}
								dojoLoc = new Location(primary, override);
							} else {
								String str = Context.toString(dojoLocObj);
								if (!str.endsWith("/")) str += '/';
								dojoLoc = new Location(new URI(str).normalize());
							}
						} catch (URISyntaxException e) {
							if (log.isLoggable(Level.WARNING)) {
								log.log(Level.WARNING, e.getMessage(), e);
							}
						}
					}
				}
			}
		}
		// Bail if we can't find dojo
		if (dojoLoc == null) {
			if (log.isLoggable(Level.WARNING)) {
				log.warning(Messages.DojoHttpTransport_0);
			}
			return;
		}
		
		if (paths != null && paths.get(dojoTextPluginFullPath, paths) != Scriptable.NOT_FOUND) {
			// if config overrides dojo/text, then bail
			if (log.isLoggable(Level.INFO)) {
				log.info(MessageFormat.format(Messages.DojoHttpTransport_2,
						new Object[]{dojoTextPluginFullPath}));
			}
			return;
		}

		Context context = Context.enter();
		try {
			// Create the paths and aliases config properties if necessary
			if (paths == null) {
				config.put(IConfig.PATHS_CONFIGPARAM, config, context.newObject(config));
				paths = (Scriptable)config.get(IConfig.PATHS_CONFIGPARAM, paths);
			}
			if (aliases == null) {
				config.put(IConfig.ALIASES_CONFIGPARAM, config, context.newArray(config, 0));
				aliases = (Scriptable)config.get(IConfig.ALIASES_CONFIGPARAM, paths);
			}
	
			// Specify paths entry to map dojo/text to our text plugin proxy
			if (log.isLoggable(Level.INFO)) {
				log.info(MessageFormat.format(
						Messages.DojoHttpTransport_3,
						new Object[]{dojoTextPluginFullPath, textPluginProxyUriStr}
				));
			}
		
			paths.put(dojoTextPluginFullPath, paths, textPluginProxyUriStr);
			
			// Specify paths entry to map the dojo text plugin alias name to the original
			// dojo text plugin
			Scriptable dojoTextPluginPath = context.newArray(config, 2);
			URI primary = dojoLoc.getPrimary(), override = dojoLoc.getOverride(); 
			primary = primary.resolve(dojoPluginUri);
			if (override != null) {
				override = override.resolve(dojoPluginUri);
			}
			dojoTextPluginPath.put(0, dojoTextPluginPath, primary.toString());
			if (override != null) {
				dojoTextPluginPath.put(1, dojoTextPluginPath, override.toString());
			}
			if (log.isLoggable(Level.INFO)) {
				log.info(MessageFormat.format(
						Messages.DojoHttpTransport_3,
						new Object[]{dojoTextPluginAliasFullPath, Context.toString(dojoTextPluginPath)}
				));
			}
			paths.put(dojoTextPluginAliasFullPath, paths, dojoTextPluginPath);
			
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
			Scriptable alias = context.newArray(config, 3);
			alias.put(0, alias, dojoTextPluginAlias);
			alias.put(1, alias, dojoTextPluginAliasFullPath);
			// Not easy to determine the size of an array using Scriptable
			int max = -1;
			for (Object id : aliases.getIds()) {
				if (id instanceof Number) max = Math.max(max, (Integer)((Number)id));
			}
			aliases.put(max+1, aliases, alias);
		} finally {
			Context.exit();
		}
		
		// Add alias definitions to be specified in client-side config
		getClientConfigAliases().add(new String[]{dojoTextPluginAlias, dojoTextPluginAliasFullPath});
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
