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

package com.ibm.jaggr.core.impl.transport;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfig.Location;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.AggregationResource;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.TypeUtil;

/**
 * Implements the functionality specific for the Dojo Http Transport (supporting
 * the dojo AMD loader).
 */
public abstract class AbstractDojoHttpTransport extends AbstractHttpTransport implements IHttpTransport, IExtensionInitializer {
	private static final Logger log = Logger.getLogger(AbstractDojoHttpTransport.class.getName());
	
    static final String[] loaderExtensionResources = {
    	"../loaderExtCommon.js", //$NON-NLS-1$
    	"./_loaderExt.js" //$NON-NLS-1$
    };
    static final String dojo = "dojo"; //$NON-NLS-1$
    public static final String aggregatorTextPluginAlias = "__aggregator_text_plugin"; //$NON-NLS-1$
    public static final String dojoTextPluginAlias = "__original_text_plugin"; //$NON-NLS-1$
    public static final String dojoTextPluginAliasFullPath = dojo+"/"+dojoTextPluginAlias; //$NON-NLS-1$
    static final String dojoTextPluginName = "text"; //$NON-NLS-1$
    public static final String dojoTextPluginFullPath = dojo+"/"+dojoTextPluginName; //$NON-NLS-1$
    static final URI dojoPluginUri;

    static {
    	try {
    		dojoPluginUri = new URI("./"+dojoTextPluginName); //$NON-NLS-1$
    	} catch (URISyntaxException e) {
    		// Should never happen
    		throw new RuntimeException(e);
    	}
    }

    
    protected URI comboUri;
    protected String pluginUniqueId = ""; //$NON-NLS-1$
    private List<String[]> clientConfigAliases = new LinkedList<String[]>();
    
    /**
     * Property accessor for the comboUriStr property
     * 
     * @return the combo URI string value
     */
    protected abstract String getComboUriStr();
    
    /**
     * Property accessor for the TextPluginProxyUriStr property
     * 
     * @return the TextPluginProxy URI string value
     */
    
    protected abstract String getTextPluginProxyUriStr();
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
    protected abstract String getLoaderExtensionPath();
    
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
    public List<String[]> getClientConfigAliases() {
    	return clientConfigAliases;
    }
    
    protected String getAggregatorTextPluginName() {
    	return getResourcePathId() + "/text"; //$NON-NLS-1$
    }
    
    /* (non-Javadoc)
     * @see com.ibm.jaggr.core.transport.AbstractHttpTransport#getLayerContribution(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType, java.lang.String)
     */
    @Override
	public String getLayerContribution(HttpServletRequest request,
			LayerContributionType type, Object arg) {

    	super.validateLayerContributionState(request, type, arg);
    	
    	// Implement wrapping of modules required by dojo loader for modules that 
    	// are loaded with the loader.
    	switch (type) {
		case BEGIN_REQUIRED_MODULES:
			return "require({cache:{"; //$NON-NLS-1$
		case BEFORE_FIRST_REQUIRED_MODULE:
			return getBeforeRequiredModule(request, arg.toString());
		case BEFORE_SUBSEQUENT_REQUIRED_MODULE:
			return "," + getBeforeRequiredModule(request, arg.toString()); //$NON-NLS-1$
		case AFTER_REQUIRED_MODULE:
			return getAfterRequiredModule(request, arg.toString());
		case END_REQUIRED_MODULES:
			{
				StringBuffer sb = new StringBuffer();
				sb.append("}});require({cache:{}});require(["); //$NON-NLS-1$ 
				int i = 0;
				@SuppressWarnings("unchecked")
				Set<String> requiredModules = (Set<String>)arg;
				for (String name : requiredModules) {
					sb.append(i++ > 0 ? "," : "").append("\"").append(name).append("\""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ 
				}
				sb.append("]);"); //$NON-NLS-1$
				return sb.toString();
			}
		default:
		}
		return null;
	}
    
    static final Pattern urlId = Pattern.compile("^[a-zA-Z]+\\:\\/\\/"); //$NON-NLS-1$
    /* (non-Javadoc)
     * @see com.ibm.jaggr.core.impl.transport.AbstractHttpTransport#isServerExpandable(javax.servlet.http.HttpServletRequest, java.lang.String)
     */
    @Override
	public boolean isServerExpandable(HttpServletRequest request, String mid) {
    	int idx = mid.indexOf("!"); //$NON-NLS-1$
    	String plugin = idx != -1 ? mid.substring(0, idx) : null;
    	String name = idx != -1 ? mid.substring(idx+1) : mid;
    	if (name.startsWith("/") || urlId.matcher(name).find() || name.contains("?")) { //$NON-NLS-1$ //$NON-NLS-2$
    		return false;
    	}
    	if (plugin != null) {
    		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
    		if (!aggr.getConfig().getTextPluginDelegators().contains(plugin) &&
    			!aggr.getConfig().getJsPluginDelegators().contains(plugin)) {
    			return false;
    		}
    	}
		return true;
	}

	protected String getBeforeRequiredModule(HttpServletRequest request, String mid) {
    	String result;
    	int idx = mid.indexOf("!"); //$NON-NLS-1$
    	if (idx == -1) {
    		result = "\"" + mid + "\":function(){"; //$NON-NLS-1$ //$NON-NLS-2$
    	} else {
    		String plugin = mid.substring(0, idx);
    		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
    		IConfig config = aggr.getConfig();
    		if (config.getTextPluginDelegators().contains(plugin)) {
    			result = "\"url:" + mid.substring(idx+1) + "\":"; //$NON-NLS-1$ //$NON-NLS-2$
    		} else if (config.getJsPluginDelegators().contains(plugin)) {
    			result = "\"" + mid.substring(idx+1) + "\":function(){"; //$NON-NLS-1$ //$NON-NLS-2$
    		} else {
    			result = "\"" + mid + "\":function(){"; //$NON-NLS-1$ //$NON-NLS-2$
    		}
    	}
    	return result;
    }
    
    protected String getAfterRequiredModule(HttpServletRequest request, String mid) {
    	int idx = mid.indexOf("!"); //$NON-NLS-1$
    	String plugin = idx == -1 ? null : mid.substring(0, idx);
    	String result = "}";//$NON-NLS-1$
    	if (plugin != null) {
    		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
    		IConfig config = aggr.getConfig();
    		if (config.getTextPluginDelegators().contains(plugin)) {
    			result = ""; //$NON-NLS-1$
    		}
    	}
    	return result;
    }

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.AbstractHttpTransport#getCacheKeyGenerators()
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
	 * @see com.ibm.jaggr.core.transport.AbstractHttpTransport#getComboUri()
	 */
	@Override 
	protected URI getComboUri() {
		return comboUri;
	}
	
	@Override
	public void decorateRequest(HttpServletRequest request) throws IOException {
		super.decorateRequest(request);
		if (request.getAttribute(IHttpTransport.REQUIRED_REQATTRNAME) != null) {
			// If we're building a pre-boot layer, then don't adorn text strings
			// and don't export module names
			request.setAttribute(IHttpTransport.NOTEXTADORN_REQATTRNAME, Boolean.TRUE);
			request.setAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.FALSE);
		}
		if (!(TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME)) &&
				(OptimizationLevel)request.getAttribute(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME) != OptimizationLevel.NONE ||
				request.getAttribute(IHttpTransport.REQUIRED_REQATTRNAME) != null)) {
			// If we're not exporting module names and we aren't doing server side expansion
			// of dependencies (i.e. using a prebuild cache), then we can't expand i18n
			// resources.
			request.setAttribute(IHttpTransport.NOADDMODULES_REQATTRNAME, true);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.AbstractHttpTransport#getDynamicLoaderExtensionJavaScript()
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
		  .append(Boolean.toString(options.isDisableHasFiltering()))
		  .append("};\r\n"); //$NON-NLS-1$
		
		// add in the super class's contribution
		sb.append(super.getDynamicLoaderExtensionJavaScript());
		
		return sb.toString();
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfigModifier#modifyConfig(com.ibm.jaggr.core.IAggregator, java.util.Map)
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
		Object textPluginDelegatorsObj = config.get(IConfig.TEXTPLUGINDELEGATORS_CONFIGPARAM, config);
		Object jsPluginDelegatorsObj = config.get(IConfig.JSPLUGINDELEGATORS_CONFIGPARAM, config);
		
		Scriptable paths = (pathsObj != null && pathsObj instanceof Scriptable) ? (Scriptable)pathsObj : null;
		Scriptable packages = (packagesObj != null && packagesObj instanceof Scriptable) ? (Scriptable)packagesObj : null;
		Scriptable aliases = (aliasesObj != null && aliasesObj instanceof Scriptable) ? (Scriptable)aliasesObj : null;
		Scriptable textPluginDelegators = (textPluginDelegatorsObj != null && textPluginDelegatorsObj instanceof Scriptable) ? (Scriptable)textPluginDelegatorsObj : null;
		Scriptable jsPluginDelegators = (jsPluginDelegatorsObj != null && jsPluginDelegatorsObj instanceof Scriptable) ? (Scriptable)jsPluginDelegatorsObj : null;
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
								if (!str.endsWith("/")) str += '/'; //$NON-NLS-1$
								URI primary = new URI(str).normalize(), override = null;
								Object obj = values.get(1, values);
								if (obj != Scriptable.NOT_FOUND) {
									str = Context.toString(obj);
									if (!str.endsWith("/")) str += '/'; //$NON-NLS-1$
									try {
										override = new URI(str).normalize();
									} catch (URISyntaxException ignore) {}
								}
								dojoLoc = new Location(primary, override);
							} else {
								String str = Context.toString(dojoLocObj);
								if (!str.endsWith("/")) str += '/'; //$NON-NLS-1$
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
				paths = (Scriptable)config.get(IConfig.PATHS_CONFIGPARAM, null);
			}
			if (aliases == null) {
				config.put(IConfig.ALIASES_CONFIGPARAM, config, context.newArray(config, 0));
				aliases = (Scriptable)config.get(IConfig.ALIASES_CONFIGPARAM, null);
			}
			if (textPluginDelegators == null) {
				config.put(IConfig.TEXTPLUGINDELEGATORS_CONFIGPARAM, config, context.newArray(config, 0));
				textPluginDelegators = (Scriptable)config.get(IConfig.TEXTPLUGINDELEGATORS_CONFIGPARAM, null);
			}
			if (jsPluginDelegators == null) {
				config.put(IConfig.JSPLUGINDELEGATORS_CONFIGPARAM, config, context.newArray(config, 0));
				jsPluginDelegators = (Scriptable)config.get(IConfig.JSPLUGINDELEGATORS_CONFIGPARAM, null);
			}
	
			// Specify paths entry to map dojo/text to our text plugin proxy
			if (log.isLoggable(Level.INFO)) {
				log.info(MessageFormat.format(
						Messages.DojoHttpTransport_3, new Object[]{dojoTextPluginFullPath, getTextPluginProxyUriStr()}
				));
			}
		
			paths.put(dojoTextPluginFullPath, paths, getTextPluginProxyUriStr());
			
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
			
			max = -1;
			for (Object id : textPluginDelegators.getIds()) {
				if (id instanceof Number) max = Math.max(max, (Integer)((Number)id));
			}
			textPluginDelegators.put(max+1, textPluginDelegators, "dojo/text"); //$NON-NLS-1$
			jsPluginDelegators.put(max+1, jsPluginDelegators, "dojo/i18n"); //$NON-NLS-1$
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
	public class LoaderExtensionResourceFactory implements IResourceFactory {

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResourceFactory#handles(java.net.URI)
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
		 * @see com.ibm.jaggr.core.resource.IResourceFactory#newResource(java.net.URI)
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
