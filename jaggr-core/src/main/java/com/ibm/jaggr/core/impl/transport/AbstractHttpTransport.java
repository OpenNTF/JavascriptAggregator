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
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfigModifier;
import com.ibm.jaggr.core.impl.PlatformAggregatorFactory;
import com.ibm.jaggr.core.readers.AggregationReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.TypeUtil;

/**
 * Implements common functionality useful for all Http Transport implementation
 * and defines abstract methods that subclasses need to implement
 */
public abstract class AbstractHttpTransport implements IHttpTransport, IConfigModifier, IShutdownListener {
	private static final Logger log = Logger.getLogger(AbstractDojoHttpTransport.class.getName());

	public static final String PATH_ATTRNAME = "path"; //$NON-NLS-1$
	public static final String PATHS_PROPNAME = "paths"; //$NON-NLS-1$
	
	public static final String REQUESTEDMODULES_REQPARAM = "modules"; //$NON-NLS-1$
	public static final String REQUESTEDMODULESCOUNT_REQPARAM = "count"; //$NON-NLS-1$
	public static final String REQUIRED_REQPARAM = "required"; //$NON-NLS-1$

	public static final String FEATUREMAP_REQPARAM = "has"; //$NON-NLS-1$
	public static final String FEATUREMAPHASH_REQPARAM = "hashash"; //$NON-NLS-1$

	public static final String[] OPTIMIZATIONLEVEL_REQPARAMS = {"optimize", "opt"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] EXPANDREQUIRELISTS_REQPARAMS = {"expandRequire", "re" //$NON-NLS-1$ //$NON-NLS-2$
		,"reqexp"	// backwards compatibility, to be removed. //$NON-NLS-1$
	};

	public static final String[] EXPORTMODULENAMES_REQPARAMS = {"exportNames", "en"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] SHOWFILENAMES_REQPARAMS = {"showFilenames", "fn"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] NOCACHE_REQPARAMS = {"noCache", "nc"}; //$NON-NLS-1$ //$NON-NLS-2$

	public static final String[] REQUESTEDLOCALES_REQPARAMS = {"locales", "locs"}; //$NON-NLS-1$ //$NON-NLS-2$
	
	public static final String[] HASPLUGINBRANCHING_REQPARAMS = {"hasBranching", "hb"}; //$NON-NLS-1$ //$NON-NLS-2$ 
	
	public static final String CONFIGVARNAME_REQPARAM = "configVarName"; //$NON-NLS-1$
	
	public static final String LAYERCONTRIBUTIONSTATE_REQATTRNAME = AbstractHttpTransport.class.getName() + ".LayerContributionState"; //$NON-NLS-1$
	

	/** A cache of folded module list strings to expanded file name lists.  Used by LayerImpl cache */
    private Map<String, Collection<String>> _encJsonMap = new ConcurrentHashMap<String, Collection<String>>();
    
    private static Pattern DECODE_JSON = Pattern.compile("([!()|*<>])"); //$NON-NLS-1$
    private static Pattern REQUOTE_JSON = Pattern.compile("([{,:])([^{},:\"]+)([},:])"); //$NON-NLS-1$

    protected String resourcePathId;
    private Object configModifierReg;
    private IAggregator aggregator = null;
    private List<String> extensionContributions = new LinkedList<String>();

    
    
    
    /**
     * Returns the URI to the folder containing the javascript resources
     * for this transport.
     * 
     * @return the combo resource URI
     */
    protected abstract URI getComboUri();
    
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IHttpTransport#decorateRequest(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void decorateRequest(HttpServletRequest request) throws IOException {
		
		// Get module list from request
		request.setAttribute(REQUESTEDMODULES_REQATTRNAME, getModuleListFromRequest(request));
		
		// Get the feature list, if any
		request.setAttribute(FEATUREMAP_REQATTRNAME, getFeaturesFromRequest(request));
		
		request.setAttribute(OPTIMIZATIONLEVEL_REQATTRNAME, getOptimizationLevelFromRequest(request));
		
		String value = getParameter(request, EXPANDREQUIRELISTS_REQPARAMS);
		if ("log".equals(value)) { //$NON-NLS-1$
			request.setAttribute(EXPANDREQLOGGING_REQATTRNAME, Boolean.TRUE);
			request.setAttribute(EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		} else {
	   		request.setAttribute(EXPANDREQUIRELISTS_REQATTRNAME, TypeUtil.asBoolean(value));
		}
   		request.setAttribute(EXPORTMODULENAMES_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, EXPORTMODULENAMES_REQPARAMS), true));
   		
   		request.setAttribute(SHOWFILENAMES_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, SHOWFILENAMES_REQPARAMS)));
   		
   		request.setAttribute(NOCACHE_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, NOCACHE_REQPARAMS)));
   		
   		request.setAttribute(HASPLUGINBRANCHING_REQATTRNAME, TypeUtil.asBoolean(getParameter(request, HASPLUGINBRANCHING_REQPARAMS), true));
   		
   		request.setAttribute(REQUESTEDLOCALES_REQATTRNAME, getRequestedLocales(request));

   		if (request.getParameter(CONFIGVARNAME_REQPARAM) != null) {
   			request.setAttribute(CONFIGVARNAME_REQATTRNAME, request.getParameter(CONFIGVARNAME_REQPARAM));
   		}
	}
	
	/**
	 * Unfolds the folded module list in the request into a {@code Collection<String>}
	 * of module names.
	 * 
	 * @param request the request object
	 * @return the Collection of module names
	 * @throws IOException
	 */
	protected Collection<String> getModuleListFromRequest(HttpServletRequest request) throws IOException {
		List<String> moduleList = new LinkedList<String>();
        String moduleQueryArg = request.getParameter(REQUESTEDMODULES_REQPARAM);
        String countParam = request.getParameter(REQUESTEDMODULESCOUNT_REQPARAM);
        int count = 0;
        if (countParam != null) {
        	count = Integer.parseInt(request.getParameter(REQUESTEDMODULESCOUNT_REQPARAM));
        }
        
        if (moduleQueryArg == null) {
        	return Collections.emptySet();
        }
        
        try {
			moduleQueryArg = URLDecoder.decode(moduleQueryArg, "UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			throw new BadRequestException(e.getMessage());
		}

		if (count > 0) {
	        if (_encJsonMap.containsKey(moduleQueryArg))
	            moduleList.addAll(_encJsonMap.get(moduleQueryArg));
	        else {
	        	try {
	        		moduleList.addAll(Arrays.asList(unfoldModules(decodeModules(moduleQueryArg), count)));
	        	} catch (JSONException e) {
	        		throw new IOException(e);
	        	}
	            
	            // Save buildReader so we don't have to do this again.
	            _encJsonMap.put(moduleQueryArg, moduleList);
	        }
    	} else {  
        	// Hand crafted URL; get module names from one or more module query args
    		moduleList.addAll(Arrays.asList(moduleQueryArg.split("\\s*,\\s*", 0))); //$NON-NLS-1$
    		String required = request.getParameter(REQUIRED_REQPARAM);
    		if (required != null) {
    			Set<String> requiredSet = new HashSet<String>(Arrays.asList(required.split("\\s*,\\s*"))); //$NON-NLS-1$
    			request.setAttribute(REQUIRED_REQATTRNAME, Collections.unmodifiableSet(requiredSet));
    		}
    	}
		return Collections.unmodifiableCollection(new ModuleList(moduleList, moduleQueryArg));
	}
	
	/**
	 * Returns the requested locales as a collection of locale strings
	 * 
	 * @param request the request object
	 * @return the locale strings 
	 */
	protected Collection<String> getRequestedLocales(HttpServletRequest request) {
		String[] locales;
		String sLocales = getParameter(request, REQUESTEDLOCALES_REQPARAMS);
		if (sLocales != null) {
			locales = sLocales.split(","); //$NON-NLS-1$
		} else {
			locales = new String[0];
		}
		return Collections.unmodifiableCollection(Arrays.asList(locales));
		
	}
	
    /**
     *  Decode JSON object encoded for url transport.
     *  Enforces ordering of object keys and mangles JSON format to prevent encoding of frequently used characters.
     *  Assumes that keynames and values are valid filenames, and do not contain illegal filename chars.
     *  See http://www.w3.org/Addressing/rfc1738.txt for small set of safe chars.  
     */
    protected  JSONObject decodeModules(String encstr) throws IOException {
        StringBuffer json = new StringBuffer(encstr.length() * 2);
        Matcher m = DECODE_JSON.matcher(encstr);
        while (m.find()) {
            String match = m.group(1);
            if (match.equals("!")) //$NON-NLS-1$
                m.appendReplacement(json, ":");     //$NON-NLS-1$
            else if (match.equals("("))     //$NON-NLS-1$
                m.appendReplacement(json, "{"); //$NON-NLS-1$
            else if (match.equals(")"))     //$NON-NLS-1$
                m.appendReplacement(json, "}"); //$NON-NLS-1$
            else if (match.equals("|"))     //$NON-NLS-1$
                m.appendReplacement(json, "!"); //$NON-NLS-1$
            else if (match.equals("*"))     //$NON-NLS-1$
                m.appendReplacement(json, ","); //$NON-NLS-1$
            else if (match.equals("<"))     //$NON-NLS-1$
                m.appendReplacement(json, "("); //$NON-NLS-1$
            else if (match.equals(">"))     //$NON-NLS-1$
                m.appendReplacement(json, ")"); //$NON-NLS-1$
        }
        m.appendTail(json);
        JSONObject decoded = null;
        String jsonstr = json.toString();
        jsonstr = REQUOTE_JSON.matcher(jsonstr).replaceAll("$1\"$2\"$3"); // matches all keys //$NON-NLS-1$
        jsonstr = REQUOTE_JSON.matcher(jsonstr).replaceAll("$1\"$2\"$3"); // matches all values //$NON-NLS-1$
        try {
        	decoded = new JSONObject(jsonstr);
        } catch (JSONException e) {
        	throw new BadRequestException(e);
		}
        return decoded;
    }
    
	/**
	 * Regular expression for a non-path property (i.e. auxiliary information or processing
	 * instruction) of a folded path json object.
	 */
	static public Pattern NON_PATH_PROP_PATTERN = Pattern.compile("^/[^/]+/$"); //$NON-NLS-1$
	
	/**
	 * Name of folded path json property used to identify the names of loader
	 * plugin prefixes and their ordinals used in the folded path.  This must
	 * match the value of pluginPrefixesPropName in loaderExtCommon.js.  The
	 * slashes (/) ensure that the name won't collide with a real path name.
	 */
	static public String PLUGIN_PREFIXES_PROP_NAME = "/pre/"; //$NON-NLS-1$
	
	/**
	 * Unfolds a folded module name list into a String array of unfolded names
	 * <p>
	 * The returned list must be sorted the same way it was requested ordering
	 * modules in the same way as in the companion js extension to amd loader
	 * order provided in the folded module leaf
	 * 
	 * @param modules
	 *            The folded module name list
	 * @param count
	 *            The count of modules in the list
	 * @return The unfolded module name list
	 */
	protected String[] unfoldModules(JSONObject modules, int count) throws IOException, JSONException {
		String[] ret = new String[count];
		Iterator<?> it = modules.keys();
		String[] prefixes = null;
		if (modules.containsKey(PLUGIN_PREFIXES_PROP_NAME)) {
			@SuppressWarnings("unchecked")
			Map<String, String> oPrefixes = (Map<String, String>) modules.get(PLUGIN_PREFIXES_PROP_NAME);
			prefixes = new String[oPrefixes.size()];
			for (String key : oPrefixes.keySet()) {
				prefixes[Integer.parseInt(oPrefixes.get(key))] = key;
			}
		}
		while (it.hasNext()) {
			String key = (String) it.next();
			if (!NON_PATH_PROP_PATTERN.matcher(key).find()) {
				unfoldModulesHelper(modules.get(key), key, prefixes, ret);
			}
		}
		return ret;
	}
    
    /**
     * Helper routine to unfold folded module names 
     * 
     * @param obj
     * @param path
     * @param modules
     */
    protected void unfoldModulesHelper(Object obj, String path, String[] aPrefixes, String[] modules) throws IOException, JSONException {
        if (obj instanceof JSONObject) {
            JSONObject jsonobj = (JSONObject)obj;
            Iterator<?> it = jsonobj.keySet().iterator();
            while (it.hasNext()) {
                String key = (String)it.next();
                String newpath = path + "/" + key;  //$NON-NLS-1$
                unfoldModulesHelper(jsonobj.get(key), newpath, aPrefixes, modules);
            }
        }
        else if (obj instanceof String){
            String[] values = ((String)obj).split("-"); //$NON-NLS-1$
            try {
                modules[Integer.parseInt(values[0])] = values.length > 1 ? 
                	((aPrefixes != null ? 
                			aPrefixes[Integer.parseInt(values[1])] : values[1]) 
                		+ "!" + path) : //$NON-NLS-1$
                	path;
            } catch (Exception e) {
            	if (log.isLoggable(Level.SEVERE))
            		log.log(Level.SEVERE, e.getMessage(), e);
            }
        } else {
        	throw new BadRequestException();
        }
    }
    
    /**
     * Returns a map containing the has-condition/value pairs specified in the request
     * 
     * @param request The http request object
     * @return The map containing the has-condition/value pairs.
     * @throws  
     */
    public static Features getFeaturesFromRequest(HttpServletRequest request) throws IOException {
		Features features = new Features();
		String has  = getHasConditionsFromRequest(request);
		if (has != null) {
			if (log.isLoggable(Level.FINEST))
				log.finest("Adding has parameters from request: " + has); //$NON-NLS-1$

  			for (String s : has.split("[;|]")) { //$NON-NLS-1$
  				boolean value = true;
  				if (s.startsWith("!")) { //$NON-NLS-1$
  					s = s.substring(1);
  					value = false;
  				}
  				features.put(s, value);
  			}
  			if (log.isLoggable(Level.FINEST))
				log.finest("features = " + features.toString()); //$NON-NLS-1$
		}
		return features.unmodifiableFeatures();
    }

	/**
	 * This method checks the request for the has conditions which may either be contained in URL 
	 * query arguments or in a cookie sent from the client.
	 * 
	 * @return The has conditions from the request.
	 * @throws UnsupportedEncodingException 
	 */
	public static String getHasConditionsFromRequest(HttpServletRequest request) throws IOException {
		String ret = null;
		if (request.getParameter(FEATUREMAPHASH_REQPARAM) != null) {
			// The cookie called 'has' contains the has conditions
			Cookie[] cookies = request.getCookies();
			if (cookies != null) {
				for (int i = 0; ret == null && i < cookies.length; i++) {
					Cookie cookie = cookies[i];
					if (cookie.getName().equals(FEATUREMAP_REQPARAM) && cookie.getValue() != null) {
						ret = URLDecoder.decode(cookie.getValue(), "US-ASCII"); //$NON-NLS-1$
						break;
					}
				}
			}
			if (ret == null) {
				if (log.isLoggable(Level.WARNING)) {
					StringBuffer url = request.getRequestURL();
					if (url != null) {	// might be null if using mock request for unit testing
						url.append("?").append(request.getQueryString()).toString(); //$NON-NLS-1$
						log.warning(MessageFormat.format(
								Messages.AbstractHttpTransport_0,
								new Object[]{url, request.getHeader("User-Agent")})); //$NON-NLS-1$
					}
				}
			}
		}
		else
			ret = request.getParameter(FEATUREMAP_REQPARAM);
		
		return ret;
	}
    
	/**
	 * Returns the value of the requested parameter from the request, or null
	 * 
	 * @param request
	 *            the request object
	 * @param aliases
	 *            array of query arg names by which the request may be specified
	 * @return the value of the param, or null if it is not specified under the
	 *         specified names
	 */
	protected static String getParameter(HttpServletRequest request, String[] aliases) {
		Map<String, String[]> params = request.getParameterMap();
		String result = null;
		for (Map.Entry<String, String[]> entry : params.entrySet()) {
			String name = entry.getKey();
			for (String alias : aliases) {
				if (alias.equalsIgnoreCase(name)) {
					String[] values = entry.getValue();
					result = values[values.length-1];	// return last value in array
				}
			}
		}
		return result;
	}

	/**
	 * Returns the requested optimization level from the request.
	 * 
	 * @param request the request object
	 * @return the optimization level specified in the request
	 */
	protected OptimizationLevel getOptimizationLevelFromRequest(HttpServletRequest request) {
        // Get the optimization level specified in the request and set the ComilationLevel
        String optimize = getParameter(request, OPTIMIZATIONLEVEL_REQPARAMS);
        OptimizationLevel level = OptimizationLevel.SIMPLE;
        if (optimize != null && !optimize.equals("")) { //$NON-NLS-1$
            if (optimize.equalsIgnoreCase("whitespace")) //$NON-NLS-1$
                level = OptimizationLevel.WHITESPACE;
            else if (optimize.equalsIgnoreCase("advanced")) //$NON-NLS-1$
                level = OptimizationLevel.ADVANCED;
            else if (optimize.equalsIgnoreCase("none")) //$NON-NLS-1$
                level = OptimizationLevel.NONE;
        }
        return level;
	}
    
	/**
	 * Extends the default implementation of {@link LinkedList} to override
	 * the {@code toString) method (used in generating layer cache keys) so 
	 * that we can return the folded module name list (allowing for more
	 * compact cache keys).
	 */
	protected static class ModuleList extends LinkedList<String> {
		private static final long serialVersionUID = 1520863743688358581L;

		private String stringized;

		ModuleList(List<String> source, String stringized) {
			super(source);
			this.stringized = stringized;
		}
		
		/* (non-Javadoc)
		 * @see java.util.AbstractCollection#toString()
		 */
		@Override
		public String toString() {
			return (stringized != null) ? stringized : super.toString();
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfigModifier#modifyConfig(com.ibm.jaggr.core.IAggregator, java.util.Map)
	 */
	@Override
	public void modifyConfig(IAggregator aggregator, Scriptable config) {
		// The server-side AMD config has been updated.  Add an entry to the 
		// {@code paths} property to map the resource (combo) path to the 
		// location of the combo resources on the server.
		Context context = Context.enter();
		try {
			Object pathsObj = config.get(PATHS_PROPNAME, config);
			if (pathsObj == Scriptable.NOT_FOUND) {
				// If not present, add it.
				config.put("paths", config, context.newObject(config)); //$NON-NLS-1$
				pathsObj = (Scriptable)config.get(PATHS_PROPNAME, config);
			}
			((Scriptable)pathsObj).put(getResourcePathId(), (Scriptable)pathsObj, getComboUri().toString());
		} finally {
			Context.exit();
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IExtensionInitializer#initialize(com.ibm.jaggr.core.IAggregator, com.ibm.jaggr.core.IAggregatorExtension, com.ibm.jaggr.core.IExtensionInitializer.IExtensionRegistrar)
	 */
	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension extension, IExtensionRegistrar reg) {
		this.aggregator = aggregator;

		// register a config listener so that we get notified of changes to 
		// the server-side AMD config file.
		String name = aggregator.getName();
		Hashtable<String, String> dict = new Hashtable<String, String>();
		//Properties dict = new Properties();
		dict.put("name", name); //$NON-NLS-1$    	
    	configModifierReg = PlatformAggregatorFactory.getPlatformAggregator().registerService(IConfigModifier.class.getName(), this, dict);		
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IShutdownListener#shutdown(com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		// unregister our config modifier
		if (configModifierReg != null) {
			PlatformAggregatorFactory.getPlatformAggregator().unRegisterService(configModifierReg);
		}
	}

	/**
	 * Returns the aggregator instance that created this transport
	 * 
	 * @return the aggregator
	 */
	protected IAggregator getAggregator() {
		return aggregator;
	}
	
	/**
	 * Returns the resource (combo) path id specified by the transport's
	 * plugin extension {@code path} attribute
	 * 
	 * @return the resource path id
	 */
	protected String getResourcePathId() {
		return resourcePathId;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IHttpTransport#contributeLoaderExtensionJavaScript(java.lang.String)
	 */
	@Override
	public void contributeLoaderExtensionJavaScript(String contribution) {
		extensionContributions.add(contribution);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IHttpTransport#getLayerContribution(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType, java.lang.String)
	 */
	@Override
	public abstract String getLayerContribution(HttpServletRequest request,
			LayerContributionType type, Object arg);

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IHttpTransport#isServerExpandable(javax.servlet.http.HttpServletRequest, java.lang.String)
	 */
	public abstract boolean isServerExpandable(HttpServletRequest request, String mid);

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IHttpTransport#getCacheKeyGenerators()
	 */
	@Override
	public abstract List<ICacheKeyGenerator> getCacheKeyGenerators();
	
	/**
	 * Returns the extension contributions that have been registered with this
	 * transport
	 * 
	 * @return the extension contributions
	 */
	protected List<String> getExtensionContributions() {
		return extensionContributions;
	}
	
	/**
	 * Returns the dynamic portion of the loader extension javascript for this
	 * transport.  This includes all registered extension contributions.
	 * 
	 * @return the dynamic portion of the loader extension javascript
	 */
	protected String getDynamicLoaderExtensionJavaScript() {
		StringBuffer sb = new StringBuffer();
		for (String contribution : getExtensionContributions()) {
			sb.append(contribution).append("\r\n"); //$NON-NLS-1$
		}
		String cacheBust = aggregator.getConfig().getCacheBust();
		String optionsCb = aggregator.getOptions().getCacheBust();
		if (optionsCb != null && optionsCb.length() > 0) {
			cacheBust = (cacheBust != null && cacheBust.length() > 0) ? 
					(cacheBust + "-" + optionsCb) : optionsCb; //$NON-NLS-1$
		}
		if (cacheBust != null && cacheBust.length() > 0) {
			sb.append("if (!combo.cacheBust){combo.cacheBust = '") //$NON-NLS-1$
				.append(cacheBust).append("';}\r\n"); //$NON-NLS-1$
		}
		return sb.toString();
	}
	
	/**
	 * Validate the {@link LayerContributionState} and argument type specified
	 * in a call to
	 * {@link #getLayerContribution(HttpServletRequest, com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType, Object)}
	 * 
	 * @param request
	 *            The http request object
	 * @param type
	 *            The layer contribution (see
	 *            {@link IHttpTransport.LayerContributionType})
	 * @param arg
	 *            The argument value
	 */
    protected void validateLayerContributionState(HttpServletRequest request, 
    		LayerContributionType type, Object arg) {

    	LayerContributionType previousType = (LayerContributionType)request.getAttribute(LAYERCONTRIBUTIONSTATE_REQATTRNAME);
    	switch (type) {
    	case BEGIN_RESPONSE:
    		if (previousType != null) {
   				throw new IllegalStateException();
    		}
    		break;
    	case BEGIN_MODULES:
    		if (previousType != LayerContributionType.BEGIN_RESPONSE) {
    			throw new IllegalStateException();
    		}
    		break;
    	case BEFORE_FIRST_MODULE:
    		if (previousType != LayerContributionType.BEGIN_MODULES || 
    			!(arg instanceof String)) {
    			throw new IllegalStateException();
    		}
    		break;
    	case BEFORE_SUBSEQUENT_MODULE:
    		if (previousType != LayerContributionType.AFTER_MODULE || 
    			!(arg instanceof String)) {
    			throw new IllegalStateException();
    		}
    		break;
    	case AFTER_MODULE:
    		if (previousType != LayerContributionType.BEFORE_FIRST_MODULE &&
				previousType != LayerContributionType.BEFORE_SUBSEQUENT_MODULE ||
				!(arg instanceof String)) {
				throw new IllegalStateException();
			}
			break;
    	case END_MODULES:
    		if (previousType != LayerContributionType.AFTER_MODULE) {
    			throw new IllegalStateException();
    		}
    		break;
    	case BEGIN_REQUIRED_MODULES:
    		if (previousType != LayerContributionType.BEGIN_RESPONSE &&
    			previousType != LayerContributionType.END_MODULES ||
    			!(arg instanceof Set)) {
    			throw new IllegalStateException();
    		}
    		break;
    	case BEFORE_FIRST_REQUIRED_MODULE:
    		if (previousType != LayerContributionType.BEGIN_REQUIRED_MODULES || 
    			!(arg instanceof String)) {
    			throw new IllegalStateException();
    		}
    		break;
    	case BEFORE_SUBSEQUENT_REQUIRED_MODULE:
    		if (previousType != LayerContributionType.AFTER_REQUIRED_MODULE || 
    			!(arg instanceof String)) {
   			    throw new IllegalStateException();
    		}
    		break;
    	case AFTER_REQUIRED_MODULE:
    		if (previousType != LayerContributionType.BEFORE_FIRST_REQUIRED_MODULE &&
    			previousType != LayerContributionType.BEFORE_SUBSEQUENT_REQUIRED_MODULE || 
    			!(arg instanceof String)) {
				throw new IllegalStateException();
			}
			break;
    	case END_REQUIRED_MODULES:
    		if (previousType != LayerContributionType.AFTER_REQUIRED_MODULE ||
    			!(arg instanceof Set)) {
    			throw new IllegalStateException();
    		}
    		break;
    	case END_RESPONSE:
    		if (previousType != LayerContributionType.END_MODULES &&
   			    previousType != LayerContributionType.END_REQUIRED_MODULES) {
   			    throw new IllegalStateException();
    		}
    		break;
    	}
    	request.setAttribute(LAYERCONTRIBUTIONSTATE_REQATTRNAME, type);
    }
    
	/**
	 * Implementation of an {@link IResource} that aggregates the various 
	 * sources (dynamic and static content) of the loader extension 
	 * javascript for this transport
	 */
	protected class LoaderExtensionResource implements IResource, IResourceVisitor.Resource {
		IResource res;
		
		public LoaderExtensionResource(IResource res) {
			this.res = res;
		}
		
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getURI()
		 */
		@Override
		public URI getURI() {
			return res.getURI();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#exists()
		 */
		@Override
		public boolean exists() {
			return res.exists();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#lastModified()
		 */
		@Override
		public long lastModified() {
			return res.lastModified();
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return new ReaderInputStream(getReader(), "UTF-8"); //$NON-NLS-1$
		}
		
		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#getReader()
		 */
		@Override
		public Reader getReader() throws IOException {
			
			// Return an aggregation reader for the loader extension javascript
			return new AggregationReader(
					"(function(){", //$NON-NLS-1$
					res.getReader(),
					getDynamicLoaderExtensionJavaScript(),
					"})();"); //$NON-NLS-1$
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#walkTree(com.ibm.jaggr.core.resource.IResourceVisitor)
		 */
		@Override
		public void walkTree(IResourceVisitor visitor) throws IOException {
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#asVisitorResource()
		 */
		@Override
		public Resource asVisitorResource() throws IOException {
			return this;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResourceVisitor.Resource#isFolder()
		 */
		@Override
		public boolean isFolder() {
			return false;
		}

		/* (non-Javadoc)
		 * @see com.ibm.jaggr.core.resource.IResource#resolve(java.lang.String)
		 */
		@Override
		public IResource resolve(String relative) {
			throw new UnsupportedOperationException();
		}
	}

}
