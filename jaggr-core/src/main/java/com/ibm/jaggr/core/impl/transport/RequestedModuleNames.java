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

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.transport.IRequestedModuleNames;
import com.ibm.jaggr.core.util.TypeUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;


/**
 * Implements the {@link IRequestedModuleNames} interface for the http transport.
 * <p>
 * This implementation defers decoding of encoded module lists until {@link #getModules()} is
 * called.  {@link #toString()} returns a string representation of the encoded names to be
 * used for cache key identification, thereby avoiding the need to decode the request if a
 * layer specifying the same modules is already in the layer cache.
 */
class RequestedModuleNames implements IRequestedModuleNames {
	private static final Logger log = Logger.getLogger(RequestedModuleNames.class.getName());

	private static final Pattern DECODE_JSON = Pattern.compile("([!()|*<>])"); //$NON-NLS-1$
	private static final Pattern REQUOTE_JSON = Pattern.compile("([{,:])([^{},:\"]+)([},:])"); //$NON-NLS-1$

	protected static final String CONFIGPROP_IDLISTHASHERRMODULE = "idListHashErrorModule"; //$NON-NLS-1$

	private List<String> modules = null;
	private List<String> excludesEncoded = null;
	private List<String> deps = Collections.emptyList();
	private List<String> preloads = Collections.emptyList();
	private List<String> scripts = Collections.emptyList();
	private List<String> excludes = Collections.emptyList();
	private String strRep = null;
	private String moduleQueryArg;
	private String excludeEncQueryArg;
	private final List<String> idList;
	private final byte[] idListHash;
	private byte[] base64decodedIdList = null;
	private byte[] base64decodedExcludeIdList = null;
	private int count = 0;
	// Instance of this object live only for the duration of a request, so ok to query trace logging flag in constructor
	private final boolean isTraceLogging = log.isLoggable(Level.FINER);

	/**
	 * @param request
	 *            the HTTP request object
	 * @param idList
	 *            list of module names used for module name id encoding
	 * @param idListHash
	 *            hash of the idList - used to validate encoding of requests
	 * @throws IOException
	 */
	RequestedModuleNames(HttpServletRequest request, List<String> idList, byte[] idListHash) throws IOException {
		final String sourceMethod = "<ctor>"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod, new Object[]{request.getQueryString(), "<omitted>", TypeUtil.byteArray2String(idListHash)}); //$NON-NLS-1$
		}
		this.idList = idList;
		this.idListHash = idListHash;
		moduleQueryArg = request.getParameter(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM);
		String moduleIdsQueryArg = request.getParameter(AbstractHttpTransport.REQUESTEDMODULEIDS_REQPARAM);
		excludeEncQueryArg = request.getParameter(AbstractHttpTransport.EXCLUDEENC_REQPARAM);
		String excludeIdQueryArg = request.getParameter(AbstractHttpTransport.EXCLUDEIDS_REQPARAM);
		String countParam = request.getParameter(AbstractHttpTransport.REQUESTEDMODULESCOUNT_REQPARAM);
		if (moduleQueryArg == null) moduleQueryArg = ""; //$NON-NLS-1$
		if (moduleIdsQueryArg == null) moduleIdsQueryArg = ""; //$NON-NLS-1$
		try {
			// Validate parameter combination.  Requests must be loader generated, specifying
			// count and one of modules or moduleIds, or application generated, specifying
			// one or more of scripts, deps and preloads.
			if (countParam != null) {
				if (moduleQueryArg.length() == 0 && moduleIdsQueryArg.length() == 0) {
					throw new BadRequestException(request.getQueryString());
				}
				count = Integer.parseInt(request.getParameter(AbstractHttpTransport.REQUESTEDMODULESCOUNT_REQPARAM));
				// put a reasonable upper limit on the value of count
				if (count < 1 || count > AbstractHttpTransport.REQUESTED_MODULES_MAX_COUNT) {
					throw new BadRequestException("count:" + count); //$NON-NLS-1$
				}
			}
			if (moduleIdsQueryArg.length() > 0) {
				if (countParam == null) {
					throw new BadRequestException(request.getQueryString());
				}
				// Decode the id list so we can validate the id list hash
				base64decodedIdList = Base64.decodeBase64(moduleIdsQueryArg);
				if (isTraceLogging) {
					log.finer("decoded = " + TypeUtil.byteArray2String(base64decodedIdList)); //$NON-NLS-1$
				}

				// Strip off hash code so we can validate
				byte[] hash = Arrays.copyOf(base64decodedIdList, idListHash.length);
				if (!Arrays.equals(hash, idListHash)) {
					if (isTraceLogging) {
						log.finer("Invalid hash in request" + TypeUtil.byteArray2String(hash)); //$NON-NLS-1$
					}
					/*
					 * The hash in the request doesn't match the current hash, probably due to the request
					 * being sent by stale cached javascript.  See if config specifies a module to return in
					 * this case.  If not, the just throw BadRequestException.
					 */
					IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
					Object errModule = aggr.getConfig().getProperty(CONFIGPROP_IDLISTHASHERRMODULE, String.class);
					if (errModule instanceof String) {
						// Config specifies a module to return as the response (should be non-AMD).  Set
						// the nocache attribute and set the specified module as the requested module.
						// We use the 'scripts' property instead of 'modules' to ensure that any require
						// list expansion done for the response will be done in line, avoiding use of the
						// module id table.  This ensures that we won't get another id list hash error
						// if the error module calls require() (after cleaning up loader state to
						// account for requested modules that won't arrive) in order to load additional
						// resources such as nls strings.
						request.setAttribute(ILayer.NOCACHE_RESPONSE_REQATTRNAME, Boolean.TRUE);
						modules = Collections.emptyList();
						scripts = Arrays.asList(new String[]{errModule.toString()});
						count = 0;;
						strRep = "scripts:" + scripts.toString(); //$NON-NLS-1$
						if (log.isLoggable(Level.FINER)) {
							log.exiting(RequestedModuleNames.class.getName(), sourceMethod, this);
						}
						return;
					}
					// No handler specified.  Throw an exception.
					throw new BadRequestException("Invalid mid list hash"); //$NON-NLS-1$
				}

			}

			if (moduleQueryArg.length() > 0) {
				if (countParam == null && moduleIdsQueryArg.length() > 0) {  // allow empty count param for deprecated use of modules
					throw new BadRequestException(request.getQueryString());
				}
				try {
					moduleQueryArg = URLDecoder.decode(moduleQueryArg, "UTF-8"); //$NON-NLS-1$
				} catch (UnsupportedEncodingException e) {
					throw new BadRequestException(e.getMessage());
				}
			}

			if (excludeIdQueryArg != null) {
				if (countParam == null) {
					throw new BadRequestException(request.getQueryString());
				}
				// Decode the id list so we can validate the id list hash
				base64decodedExcludeIdList = Base64.decodeBase64(excludeIdQueryArg);
			}

			if (excludeEncQueryArg != null) {
				if (countParam == null) {
					throw new BadRequestException(request.getQueryString());
				}
				try {
					excludeEncQueryArg = URLDecoder.decode(excludeEncQueryArg, "UTF-8"); //$NON-NLS-1$
				} catch (UnsupportedEncodingException e) {
					throw new BadRequestException(e.getMessage());
				}
			}

			if (count > 0) {
				// Defer decoding the module lists from the request until it is asked for.
				// For now, just set the value returned by toString().
				StringBuffer sb = new StringBuffer();
				sb.append(moduleQueryArg != null ? moduleQueryArg : "").append(":") //$NON-NLS-1$ //$NON-NLS-2$
				  .append(moduleIdsQueryArg != null ? moduleIdsQueryArg : "").append(":") //$NON-NLS-1$ //$NON-NLS-2$
				  .append(excludeEncQueryArg != null ? excludeEncQueryArg : "").append(":") //$NON-NLS-1$ //$NON-NLS-2$
				  .append(excludeIdQueryArg != null ? excludeIdQueryArg : ""); //$NON-NLS-1$
				strRep = sb.toString();

			} else if (moduleQueryArg.length() > 0){
				// Hand crafted URL; get module names from one or more module query args (deprecated)
				scripts = Collections.unmodifiableList(Arrays.asList(moduleQueryArg.split("\\s*,\\s*", 0))); //$NON-NLS-1$
				modules = Collections.emptyList();
				// Set request attribute to warn about use of deprecated param
				IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
				if (aggr.getOptions().isDebugMode() || aggr.getOptions().isDevelopmentMode()) {
					request.setAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG, Boolean.TRUE);
				}
			}
		} catch (NumberFormatException ex) {
			throw new BadRequestException(ex.getMessage(), ex);
		}
		// Get the deprecated require list
		@SuppressWarnings("deprecation")
		List<String> required = getNameListFromQueryArg(request, AbstractHttpTransport.REQUIRED_REQPARAM);
		if (required != null) {
			if (countParam != null || moduleIdsQueryArg.length() > 0 || moduleQueryArg.length() > 0) {
				throw new BadRequestException(request.getQueryString());
			}
			deps = required;
			// Log console warning about deprecated query arg if in debug/dev mode
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			if (aggr.getOptions().isDebugMode() || aggr.getOptions().isDevelopmentMode()) {
				request.setAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG, Boolean.TRUE);
			}
		}

		// Get the scripts list
		List<String> names = getNameListFromQueryArg(request, AbstractHttpTransport.SCRIPTS_REQPARAM);
		if (names != null) {
			if (countParam != null || moduleIdsQueryArg.length() > 0 || moduleQueryArg.length() > 0 || required != null) {
				throw new BadRequestException(request.getQueryString());
			}
			scripts = Collections.unmodifiableList(names);
		}
		names = getNameListFromQueryArg(request, AbstractHttpTransport.DEPS_REQPARAM);
		if (names != null) {
			if (countParam != null || moduleIdsQueryArg.length() > 0 || moduleQueryArg.length() > 0 || required != null) {
				throw new BadRequestException(request.getQueryString());
			}
			deps = Collections.unmodifiableList(names);
		}
		names = getNameListFromQueryArg(request, AbstractHttpTransport.PRELOADS_REQPARAM);
		if (names != null) {
			if (countParam != null || moduleIdsQueryArg.length() > 0 || moduleQueryArg.length() > 0 || required != null) {
				throw new BadRequestException(request.getQueryString());
			}
			preloads = Collections.unmodifiableList(names);
		}
		names = getNameListFromQueryArg(request, AbstractHttpTransport.EXCLUDES_REQPARAM);
		if (names != null) {
			if (countParam != null || moduleIdsQueryArg.length() > 0 || moduleQueryArg.length() > 0 || required != null) {
				throw new BadRequestException(request.getQueryString());
			}
			excludes = Collections.unmodifiableList(names);
		}
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, this);
		}
	}

	protected List<String> getNameListFromQueryArg(HttpServletRequest request, String argName) throws IOException {
		final String sourceMethod = "getNameListFromQueryArgs"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod, new Object[]{request.getQueryString(), argName});
		}
		List<String> nameList = null;
		String argValue = request.getParameter(argName);
		if (argValue != null) {
			nameList = new LinkedList<String>();
			nameList.addAll(Arrays.asList(argValue.split("\\s*,\\s*", 0))); //$NON-NLS-1$
		}
		List<String> result = nameList != null ? Collections.unmodifiableList(nameList) : null;
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, result);
		}
		return result;
	}

	/**
	 *  Decode JSON object encoded for url transport.
	 *  Enforces ordering of object keys and mangles JSON format to prevent encoding of frequently used characters.
	 *  Assumes that keynames and values are valid filenames, and do not contain illegal filename chars.
	 *  See http://www.w3.org/Addressing/rfc1738.txt for small set of safe chars.
	 *
	 * @param encstr
	 *            the encoded module name list
	 * @return the decoded string as a JSON object
	 *
	 * @throws IOException
	 * @throws JSONException
	 */
	protected  JSONObject decodeModules(String encstr) throws IOException, JSONException {
		final String sourceMethod = "decodeModules"; //$NON-NLS-1$
		JSONObject result = null;
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod, new Object[]{encstr});
		}
		if (encstr == null || encstr.length() == 0) {
			result = new JSONObject("{}"); //$NON-NLS-1$
		} else {
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
			String jsonstr = json.toString();
			jsonstr = REQUOTE_JSON.matcher(jsonstr).replaceAll("$1\"$2\"$3"); // matches all keys //$NON-NLS-1$
			jsonstr = REQUOTE_JSON.matcher(jsonstr).replaceAll("$1\"$2\"$3"); // matches all values //$NON-NLS-1$
			result = new JSONObject(jsonstr);
		}
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, result);
		}
		return result;
	}

	/**
	 * Decodes the module names specified by {@code encoded} and adds the module names to the
	 * appropriate positions in {@code resultArray}. {@code encoded} is specified as a base64
	 * encoded array of bytes. The byte array consists of a hash code followed by a base flag (1
	 * byte) indicating whether the id map was encoded using 16-bit or 32-bit values, followed by
	 * the id map which consists of a sequence of segments, with each segment having the form:
	 * <p>
	 * <code>[position][count][moduleid-1][moduleid-2]...[moduleid-(count-1)]</code>
	 * <p>
	 * where position specifies the position in the module list of the first module in the segment,
	 * count specifies the number of modules in the segment who's positions contiguously follow the
	 * start position, and the module ids specify the ids for the modules from the id map. Position
	 * and count are 16-bit or 32-bit numbers depending on the base flag, and the module ids are
	 * specified as follows:
	 * <p>
	 * <code>[id]|[0][plugin id][id]</code>
	 * <p>
	 * If the module name doesn't specify a loader plugin, then it is represented by the id for the
	 * module name. If the module name specifies a loader plugin, then it is represetned by a zero,
	 * followed by the id for the loader plugin, followed by the id for the module name without the
	 * loader plugin. All values are 16-bit or 32-bit numbers, depending on the base flag.
	 * <p>
	 * The hash code that precedes the list is composed of the hash code bytes which represent the
	 * hash of the entire module id list on the server. This hash was provided by the transport in
	 * the dynamic loader extension javascript.
	 *
	 * @param decoded
	 *            the base64 decoded id list
	 * @param sparseArray
	 *            Output - the sparse array to which the decoded module names will be added
	 * @param hasIdListHash
	 *            true if decoded is prefixed with the id list hash and 32-bit flag
	 * @throws IOException
	 */
	protected void decodeModuleIds(byte[] decoded, Map<Integer, String> sparseArray, boolean hasIdListHash) throws IOException {
		final String sourceMethod = "decodeModuleIds"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod, new Object[]{decoded, sparseArray});
		}
		if (decoded != null) {
			// strip off base flag
			boolean use32BitEncoding = hasIdListHash ? decoded[idListHash.length] == 1 : false;

			// convert to int array
			int start = hasIdListHash ? idListHash.length+1 : 0;
			int elemSize = use32BitEncoding ? 4 : 2;
			int[] intArray = new int[(decoded.length-start)/elemSize];
			for (int i = 0; i < intArray.length; i ++) {
				int j = start+i*elemSize;
				if (use32BitEncoding) {
					intArray[i] = (((int)(decoded[j])&0xFF) << 24) + (((int)(decoded[j+1])&0xFF) << 16) +
					              (((int)(decoded[j+2])&0xFF) << 8) + ((int)(decoded[j+3])&0xFF);
				} else {
					intArray[i] = (((int)(decoded[j])&0xFF) << 8) + ((int)(decoded[j+1])&0xFF);
				}
			}
			if (isTraceLogging) {
				log.finer("ids = " + Arrays.asList(intArray).toString()); //$NON-NLS-1$
			}
			for (int i = 0, position = -1, length = 0; i < intArray.length;) {
				if (position == -1) {
					// read the position and length values
					position = intArray[i++];
					length = intArray[i++];
				}
				for (int j = 0; j < length; j++) {
					String pluginName = null, moduleName = null;
					int id = intArray[i++];
					if (id == 0) {
						// 0 means the next two ints specify plugin and modulename
						id = intArray[i++];
						pluginName = idList.get(id);
						if (pluginName == null) {
							throw new BadRequestException();
						}
						id = intArray[i++];
						moduleName = id != 0 ? idList.get(id) : ""; //$NON-NLS-1$

					} else {
						moduleName = idList.get(id);
					}
					if (moduleName == null) {
						throw new BadRequestException();
					}
					if (sparseArray.get(position+j) != null) {
						throw new BadRequestException();
					}
					sparseArray.put(position+j, (pluginName != null ? (pluginName + "!") : "") + moduleName); //$NON-NLS-1$ //$NON-NLS-2$
				}
				position = -1;
			}
		}
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, sparseArray);
		}
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
	 * @param sparseArray
	 *            The result array.  Note that there may be holes in the result
	 *            array when this method is done because some of the modules may
	 *            have been specified using a different mechanism.
	 * @throws IOException
	 * @throws JSONException
	 */
	protected void unfoldModules(JSONObject modules, Map<Integer, String> sparseArray) throws IOException, JSONException {
		final String sourceMethod = "unfoldModules"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod, new Object[]{modules, sparseArray});
		}
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
				unfoldModulesHelper(modules.get(key), key, prefixes, sparseArray);
			}
		}
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, sparseArray);
		}
	}

	/**
	 * Helper routine to unfold folded module names
	 *
	 * @param obj
	 *            The folded path list of names, as a string or JSON object
	 * @param path
	 *            The reference path
	 * @param aPrefixes
	 *            Array of loader plugin prefixes
	 * @param modules
	 *            Output - the list of unfolded modlue names as a sparse array implemented
	 *            using a map with integer keys
	 * @throws IOException
	 * @throws JSONException
	 */
	protected void unfoldModulesHelper(Object obj, String path, String[] aPrefixes, Map<Integer, String> modules) throws IOException, JSONException {
		final String sourceMethod = "unfoldModulesHelper"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod, new Object[]{
				obj, path,
				aPrefixes != null ? Arrays.asList(aPrefixes) : null, modules});
		}
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
			int idx = Integer.parseInt(values[0]);
			if (modules.get(idx) != null) {
				throw new BadRequestException();
			}
			modules.put(idx, values.length > 1 ?
					((aPrefixes != null ?
							aPrefixes[Integer.parseInt(values[1])] : values[1])
							+ "!" + path) : //$NON-NLS-1$
								path);
		} else {
			throw new BadRequestException();
		}
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, modules);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IRequestedModuleNames#getDeps()
	 */
	@Override
	public List<String> getDeps() {
		final String sourceMethod = "getDeps"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod);
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, deps);
		}
		return deps;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IRequestedModuleNames#getModules()
	 */
	@Override
	public List<String> getModules() throws BadRequestException {
		final String sourceMethod = "getModules"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod);
		}
		if (modules == null) {
			modules = decodeModules(moduleQueryArg, base64decodedIdList, true);
			if (modules.size() != count) {
				throw new BadRequestException();
			}
		}
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, modules);
		}
		return modules;
	}

	/**
	 * Returns the list of modules specified by the <code>expEx</code> and/or the
	 * <code>exIds</code> request parameter.  Require expansion excludes are
	 * specified in Aggregator generated requests initiated by application generated
	 * require() calls.  The specified modules and their dependencies will be
	 * excluded from any expanded require lists.
	 * <p>
	 *
	 * @return the list of modules to be excluded from require list expansion
	 * @throws BadRequestException
	 */
	protected List<String> getExcludesEncoded() throws BadRequestException {
		final String sourceMethod = "getRequireExpansionExcludes"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod);
		}
		if (excludesEncoded == null) {
			excludesEncoded = decodeModules(excludeEncQueryArg, base64decodedExcludeIdList, false);
		}
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, excludesEncoded);
		}
		return excludesEncoded;
	}

	protected List<String> decodeModules(String names, byte[] idList, boolean hasIdListHash) throws BadRequestException {
		final String sourceMethod = "decodeModules"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod, new Object[]{names, idList, hasIdListHash});
		}
		Map<Integer, String> sparseArray = new TreeMap<Integer, String>();
		try {
			unfoldModules(decodeModules(names), sparseArray);
			decodeModuleIds(idList, sparseArray, hasIdListHash);
		} catch (JSONException ex) {
			throw new BadRequestException(ex);
		} catch (IOException ex) {
			throw new BadRequestException(ex);
		}
		// make sure no empty slots
		for (int i = 0; i < sparseArray.size(); i++) {
			if (sparseArray.get(i) == null) {
				throw new BadRequestException();
			}
		}
		List<String> result = Collections.unmodifiableList(new ArrayList<String>(sparseArray.values()));

		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, result);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IRequestedModuleNames#getPreloads()
	 */
	@Override
	public List<String> getPreloads() {
		final String sourceMethod = "getPreloads"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod);
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, preloads);
		}
		return preloads;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IRequestedModuleNames#getScripts()
	 */
	@Override
	public List<String> getScripts() {
		final String sourceMethod = "getScripts"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod);
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, scripts);
		}
		return scripts;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IRequestedModuleNames#getExcludes()
	 */
	@Override
	public List<String> getExcludes() throws BadRequestException {
		final String sourceMethod = "getExcludes"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod);
		}
		List<String> result = excludes;
		List<String> encodedExcludes = getExcludesEncoded();
		if (!encodedExcludes.isEmpty()) {
			result = encodedExcludes;
		if (isTraceLogging);
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, result);
		}
		return result;
	}


	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.transport.IRequestedModuleNames#toString()
	 */
	@Override
	public String toString() {
		return toString(false);
	}

	/**
	 * Returns the string representation of the requested names.
	 *
	 * @param decode
	 *            - if true, then the decoded names will be returned.
	 * @return the string representation of this object
	 */
	public String toString(boolean decode) {
		final String sourceMethod = "toString"; //$NON-NLS-1$
		if (isTraceLogging) {
			log.entering(RequestedModuleNames.class.getName(), sourceMethod);
		}
		String result = null;
		if (strRep != null && !decode) {
			result = strRep;
		} else {
			StringBuffer sb = new StringBuffer();
			try {
				Collection<String> names = getModules();
				if (!names.isEmpty()) {
					sb.append("modules:").append(names); //$NON-NLS-1$
				}
				names = getScripts();
				if (!names.isEmpty()) {
					sb.append(sb.length() > 0 ? "; ":"").append("scripts:").append(names); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				names = getDeps();
				if (!names.isEmpty()) {
					sb.append(sb.length() > 0 ? "; ":"").append("deps:").append(names); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				names = getPreloads();
				if (!names.isEmpty()) {
					sb.append(sb.length() > 0 ? "; ":"").append("preloads:").append(names); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				names = getExcludes();
				if (!names.isEmpty()) {
					sb.append(sb.length() > 0 ? "; ":"").append("excludes:").append(names); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			} catch (BadRequestException e) {

			}
			result = sb.toString();


		}
		if (isTraceLogging) {
			log.exiting(RequestedModuleNames.class.getName(), sourceMethod, result);
		}
		return result;
	}
}