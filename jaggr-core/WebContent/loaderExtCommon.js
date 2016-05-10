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

/**
 * Common javascript code for Http Transport loader extensions
 */

// Map of default properties.  Keys are property names, values are
// array of value/query-arg pairs.
var params = {
		// True if the aggregator should expand the dependency list in 
		//  require calls to include nested dependencies
		expandRequire: [null, "re"],
	
		// True if aggregator should add module name to parameter list of define()
		// call for anonymous modules.
		exportNames: [true, "en"],
	
		// optimize can equal "none" | "whitespace" | "simple"(default)
		optimize: ["simple", "opt"],
	
		// show filenames in responses
		showFilenames: [null, "fn"],
	
		// ignore cache files on client and on server
		noCache: [null, "nc"],
		
		// perform has branching in require list expansion
		hasBranching: [null, "hb"],
		
		// True if the aggregator should include expanded dependencies of 
		// requested modules in the response.  Takes precedence over 
		// expandRequire if both are specified
		serverExpandLayers: [false, "sel"],
		
		// Enable requests for source maps
		sourceMaps: [null, "sm"],
		
		// True if the aggregator should output dependency expansion
		// logging to the browser console
		depExpLog: [null, "depExpLog"]
	},

	extraArgs = {},
	
	/**
	 * Default feature filter allows all features
	 * 
	 * @return true if the specified feature should be included
	 *         in the list of features sent to the aggregator
	 */
	featureFilter = function(feature) {return true;},
	
	/**
	 * Array of functions that process a url, returning the new,
	 * updated url.  When building the URL to the aggregator 
	 * servlet, the transport will call each of the functions
	 * in this array, in turn, to add their contribution to the 
	 * URL.
	 */
	urlProcessors = [],
	
	/**
	 * Name of folded path json property used to identify the names of loader
	 * plugin prefixes and their ordinals used in the folded path.  This must
	 * match the value of AbstractHttpTransport.PLUGIN_PREFIXES_PROP_NAME.  The
	 * slashes (/) ensure that the name won't collide with a real path name.
	 */
	pluginPrefixesPropName = "/pre/",

	sizeofObject = function(obj) {
		var size = 0;
		for (var s in obj) {
			if (obj.hasOwnProperty(s)) {
				size++;
			}
		}
		return size;
	},
	
	/**
	 * Adds the module specified by dep to the list of folded module names
	 * in oFolded.  
	 * 
	 * @param dep
	 *            object containing the module information
	 * @param position
	 *            the position in the requested modules list for the module
	 * @param oFolded
	 *            the folded module as an object of the form {dir1:{foo:0, bar:[1,0]}}
	 *            where the obect nesting is representative of the module path hierarchy 
	 *            and an ordinal value represents the position of the module in the 
	 *            module list and an ordinal pair array value represents the list position
	 *            and plugin ordinal (in oPrefixes) for modules that specify a plugin.
	 * @param oPrefixes
	 *            map of module prefixes/ordinal pairs.  If dep specifies a loader
	 *            plugin, then the plugin name and its ordinal are added to this
	 *            map.
	 */
	addFoldedModuleName = function(dep, position, oFolded, oPrefixes) {
		var name = dep.name,
		    segments = name.split('/'),
		    len = segments.length,
		    oChild = oFolded;
		
		for (var i = 0; i < len; i++) {
			var segment = segments[i];
			if (i == len - 1) {
				// Last segment.  finalize the insertion.
				if (dep.prefix) {
					var idx = oPrefixes[dep.prefix];
					if (!idx && idx !== 0) {
						oPrefixes[dep.prefix] = sizeofObject(oPrefixes);
						oFolded[pluginPrefixesPropName] = oPrefixes;
					}
				}
				var key = segment;
				if (oChild[key]) {
					if (typeof oChild[key] === 'number') {
						throw new Error("Duplicate name assignment: " + name);
					}
					oChild = oChild[key];
					key = '.';
				}
				oChild[key] = dep.prefix ? [position, oPrefixes[dep.prefix]] : position;
			}
			else if (segment != '.') {
				// add path component to folded modules
				var old = oChild[segment];
				if (typeof(old) == 'number') {
					oChild = oChild[segment] = {'.': old};
				} else {
					oChild = oChild[segment] || (oChild[segment] = {});
				}
			}
		}
	},
	
	/**
	 * Adds the module specified by dep to the encoded module id list at the specified
	 * list position.  The encoded module id list uses the mapping of module name to 
	 * unique ids provided by the server in moduleIdMap.  The encoded id list consists
	 * of a sequence of segments, with each segment having the form:
	 * <p><code>[position][count][moduleid-1][moduleid-2]...[moduleid-(count-1)]</code>
	 * <p>
	 * where position specifies the position in the module list of the first module
	 * in the segment, count specifies the number of modules in the segment who's positions
	 * contiguously follow the start position, and
	 * the module ids specify the ids for the modules from the id map.  Position and 
	 * count are 16-bit numbers, and the module ids are specified as follows:
	 * <p><code>[id]|[0][plugin id][id]</code>
	 * <p>
	 * If the module name doesn't specify a loader plugin, then it is represented by 
	 * the id for the module name.  If the module name specifies a loader plugin, then
	 * it is represetned by a zero, followed by the id for the loader plugin, followed
	 * by the id for the module name without the loader plugin.  All values are 16-bit 
	 * numbers.  If an id is greater than what can be represented in a 16-bit number, 
	 * then the module is not added to the encoded list.
	 * 
	 * @param dep
	 *            object containing the module information
	 * @param position
	 *            the position in the requested modules list for the module
	 * @param encodedIds
	 *            the encoded id list that that the module specified by dep
	 *            will be added to
	 * @param moduleIdMap
	 *            The mapping of module name to numeric module id provided by
	 *            the server.
	 * @return true if the module was added to the encoded list
	 */
	addModuleIdEncoded = function(dep, position, encodedIds, moduleIdMap) {
		
		var nameId = moduleIdMap[dep.name], result = false,
			pluginNameId = dep.prefix ? moduleIdMap[dep.prefix] : 0;
			
		// validate ids
		if (nameId && (dep.prefix && pluginNameId || !dep.prefix)) {				
			// encodedIds.segStartIdx holds the index in the array for the start of 
			// the current segment.
			if (!encodedIds.length) {
				encodedIds.segStartIdx = 0;
				encodedIds.push(position);
				encodedIds.push(1);
			} else {
				// We have an existing segment.  Determine if the current module
				// can be added to the existing segment or if we need to start 
				// a new one.
				var segStartIdx = encodedIds.segStartIdx,
				    start = encodedIds[segStartIdx],
				    len = encodedIds[segStartIdx+1];
				if (start + len == position) {
					// Add to current segment
					encodedIds[segStartIdx+1]++;
				} else {
					// Start a new segment
					encodedIds.segStartIdx = encodedIds.length;
					encodedIds.push(position);
					encodedIds.push(1);
				}
			}
			// Now add the module id(s)
			if (pluginNameId) {
				encodedIds.push(0);
				encodedIds.push(pluginNameId);
			}
			encodedIds.push(nameId);
			result = true;
		}
		return result;
	},

	/**
	 *  Encode JSON object for url transport.
	 *	Enforces ordering of object keys and mangles JSON format to prevent encoding of frequently used characters.
	 *	Assumes that keynames and values are valid filenames, and do not contain illegal filename chars.
	 *	See http://www.w3.org/Addressing/rfc1738.txt for small set of safe chars.  
	 */
	encodeModules = function(object) {
		var asEnc = [], n = 0;
		var recurse = function(parent) {
			asEnc[n++] = '(';
			
			// We sort the children here so that they always appear in the same order for each request.
			// js does not enforce an order on iterating through map keys, so we make sure we do it the same way all the time.
			var children = [], i = 0;
			for (var childname in parent) {
				if (parent.hasOwnProperty(childname)) {
					children[i++] = childname;
				}
			}
			if (children.length > 1) {
				children.sort();
			}
			for (i = 0; (childname = children[i]) !== undefined; i++) {
				var child = parent[childname];
				// '!' is a valid but rare filename character, change it to '|' so that we can use '!' for encoding
				// The same goes for '(' and ')'
				asEnc[n++] = ( childname === '' ? '""' : childname.replace(/[!]/g,'|').replace(/[(]/g,'<').replace(/[)]/g,'>') ) + '!';
				if (child instanceof Array) {
					asEnc[n++] = child[0] + '-' + child[1];
				} else if (typeof(child) == 'object') {
					recurse(child);
				} else {
					asEnc[n++] = child;
				}
				asEnc[n++] = '*'; // really a comma (,) but commas get encoded.
			}
			if (asEnc[n-1] == '*') {
				n--;
			}
			asEnc[n++] = ')';
		};
		recurse(object);
		
		return asEnc.join('');
	},
	
	/**
	 * Performs base64 encoding of the encoded module id list
	 * 
	 * @param ids
	 *            the encoded module id list as a 32-bit or 16-bit number array.
	 * @param encoder
	 *            the base64 encoder
	 * @return the URL safe base64 encoded representation of the number array
	 */
	base64EncodeModuleIds = function(ids, encoder, idListHash) {
		// First, determine the max id.  If max id is less than 64k, then we can
		// use 16-bit encoding.  Otherwise, we need to use 32-bit encoding.
		var use32BitEncoding = false;
		for (var i = 0; i < ids.length; i++) {
			if (ids[i] > 0xFFFF) {
				use32BitEncoding = true;
				break;
			}
		}
		// convert from number array to byte array for base64 encoding
		var bytes = [];
		for (i = 0; i < ids.length; i++) {
			if (use32BitEncoding) {
				bytes.push((ids[i] >> 24) & 0xFF);
				bytes.push((ids[i] >> 16) & 0xFF);
			}
			bytes.push((ids[i] >> 8) & 0xFF);
			bytes.push(ids[i] & 0xFF);
		}
		// now add the module id list hash and 32-bit flag to the beginning of the data
		if (idListHash) {
			bytes = idListHash.concat(use32BitEncoding ? 1 : 0, bytes);
		}
		// do the encoding, converting for URL safe characters
		return encoder(bytes).replace(/[+=\/]/g, function(c) {
			return (c=='+')?'-':((c=='/')?'_':'');
		});
	},
	
	/**
	 * Adds the list of modules specified in opt_deps to the request as 
	 * request URL query args.  For each module in the list, we will try
	 * to add it to the encoded module id list first because that consumes 
	 * less URL space per module.  If we can't do that because we don't
	 * have a number id for the module, then we add it to the folded module
	 * list.  The module list is re-assembled from these two lists on 
	 * the server.
	 * 
	 * @param url
	 *             the URL to add the requested modules to
	 * @param argNames
	 *             a two-element array specifying the folded module names query arg
	 *             followed by the id list query arg
	 * @param opt_deps
	 *             the list of modules as dep objects (i.e. {name:, prefix:})
	 * @param moduleIdMap
	 *             the mapping of module name to number ids provided by the server
	 * @param base64Encoder
	 *             the base64 encoder to use for encoding the encoded module id
	 *             list.
	 */
	addModulesToUrl = function(url, argNames, opt_deps, moduleIdMap, base64Encoder) {
		var oFolded = {},
		    oPrefixes = {},
		    ids = [],
		    hash = argNames[1] === 'moduleIds' && moduleIdMap["**idListHash**"];

		for (var i = 0, dep; !!(dep = opt_deps[i]); i++) {
			// This list of invalid chars should be the same as the list used
			// on the server in PathUtil.invalidChars.
			if (/[{},:|<>*]/.test(dep.name)) {
				throw new Error("Invalid module name: " + name);
			}
			if (!base64Encoder || !hash || !addModuleIdEncoded(dep, i, ids, moduleIdMap)) {
				addFoldedModuleName(dep, i, oFolded, oPrefixes);
			}
		}
		url +=(argNames[0] === 'modules' ? ((url.indexOf("?") === -1 ? "?" : "&") + "count=" + i) : "" ) + 
		      (sizeofObject(oFolded) ? ("&"+argNames[0]+"="+encodeURIComponent(encodeModules(oFolded))):"");
		
		if (ids.length || hash) {
			// There are encoded module ids or we need to provide the  hash
			url += "&"+argNames[1]+"=" + base64EncodeModuleIds(ids, base64Encoder, hash);
		}
		return url;
	},

	/**
	 * Builds the has argument to put in a URL.  In the case that we have dojo/cookie and dojox's MD5 module, it
	 * will place the has-list in a cookie and put the hash of the has-list in the url. 
	 */
	computeHasArg = function(has, cache, includeUndefined) {
		var hasArr = [], n = 0;
		for (var s in cache) {
			if (cache.hasOwnProperty(s) && featureFilter(s)) {
				var value;
				try {
					value = has(s);
				} catch (e) {
					if (console) {
						console.warn("Exception thrown evaluating feature " + s + ".");
						console.warn(e);
					}
				}
				if (includeUndefined || (typeof value !== 'undefined')) {
					hasArr[n++] = (!value ? '!' : '') + s;
				}
			}
		}
		hasArr.sort();  // All has args must be represented in the same order for every request.
		
		return (hasArr.length ? ('has='+hasArr.join("*")): "");
	},
	
	moduleIdsFromHasLoaderExpression = function(expression, moduleIds) {
		var tokens = expression.match(/[\?:]|[^:\?]*/g), i = 0,
		    get = function(){
				var term = tokens[i++];
				if(term == ":") {
					return;
				} else if (tokens[i++] == "?") {
					get();
					get();
					return;
				}
				if (term) moduleIds.push(term);
			};
		return get();
	},
	
	registerModuleNameIds = function(ary, moduleIdMap) {
		// registers module-name/[module-numeric-id pairs provided by the server
		// This function is called directly by aggregator responses.
		// ary is a two element array of the form 
		// [[[module-names],[module-names]...],[[numeric-ids],[numeric-ids]...]]]
		var nameAry = ary[0];
		var idAry = ary[1],
		    add = function(name, id) {
				var idx = name.lastIndexOf("!");
				if (idx !== -1) {
					name = name.substring(idx+1);
				}
				if (!(name in moduleIdMap && moduleIdMap[name])) {
					moduleIdMap[name] = id;
				} else {
					if (moduleIdMap[name] !== id) {
						throw new Error("Module name id re-assignment: " + name);
					}
				}
			};
		for (var i = 0; i < nameAry.length; i++) {
			var names = nameAry[i];
			var ids = idAry[i], id;
			for (var j = 0; j < names.length; j++) {
				var name = names[j];
				if (/(^|\/)has!/.test(name)) {
					var mids = [], exprIds = ids[j];
					moduleIdsFromHasLoaderExpression(name, mids);
					if (mids.length == 1 && typeof ids[j].length !== "undefined" || mids.length > 1 && mids.length !== ids[j].length) {
						if (console) console.warn("invalid module name id specifier: " + name + " = " + ids[j]);
						continue;
					}
					for (var k = 0; k < mids.length; k++) {
						add(mids[k], (mids.length > 1) ? ids[j][k] : ids[j]);
					}
				} else {
					add(name, ids[j]);
				}
			}
		}
	},
	
	/**
	 * Parses the provided href and returns an object map of the query args in the href.
	 * This routine does not support multivalued query args.  If a query arg appears more 
	 * than once, the value of the last instance will be represented in the map.  
	 * 
	 * The names of query args are converted to lower case in the property map.
	 */
	parseQueryArgs = function(href) {
		var result = {};
		if (href) {
			var args = href.split('?')[1];
			if (args) {
				var argsAry = args.split('&');
				for (var i = 0; i < argsAry.length; i++) {
					var argAry = argsAry[i].split('=');
					result[argAry[0].toLowerCase()] = argAry[1];
				}
			}
		}
		return result;
	};

urlProcessors.push(function(url) {
	for (var s in params) {
		if (params[s][0] !== null) {
			url += ('&'+params[s][1]+'='+params[s][0]);
		}
	}
	// Add extra arguments
	for (s in extraArgs) {
		if (extraArgs.hasOwnProperty(s)) {
			url += ('&'+s+'='+extraArgs[s]);
		}
	}
	return url;
});
