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
	
		// cache bust string 
		cacheBust: [null, "cb"],
	
		// show filenames in responses
		showFilenames: [null, "fn"],
	
		// ignore cache files on client and on server
		noCache: [null, "nc"],
		
		// perform has branching in require list expansion
		hasBranching: [null, "hb"]
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
	 * Default add filter aggregates all modules
	 * 
	 * @return true if the the module should be included in the request
	 *         to the aggregator
	 */
	addFilter = function(prefix, name, url) {return true;},

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

	/**
	 * Folds the list of module names provided in asNames into a compacted
	 * list that minimizes redundant path names.
	 * 
	 * @param asNames the list of modules
	 * @param opt_depmap
	 * @returns the folded module names list
	 */
	foldModuleNames = function(asNames, opt_depmap) {
		// Fold modules paths into an object to reduce duplication
		var oFolded = {},
		    oPrefixes = {},
		    iPrefixes = 0;
		for (var i = 0, name; !!(name = asNames[i]); i++) {
			// This list of invalid chars should be the same as the list used
			// on the server in PathUtil.invalidChars.
			if (/[{},:|<>*]/.test(name)) {
				throw new Error("Invalid module name: " + name);
			}
			var dep = opt_depmap[name];
			var segments = name.split('/');
			var len = segments.length;
			
			var oChild = oFolded;
			for (var j = 0; j < len; j++) {
				var segment = segments[j];
				if (j == len - 1) {
					if (dep.prefix) {
						var idx = oPrefixes[dep.prefix];
						if (!idx && idx !== 0) {
							oPrefixes[dep.prefix] = iPrefixes++;
						}
					}
					oChild[segment] = dep.prefix ? [i, oPrefixes[dep.prefix]] : i;
					// Fix up asNames name.  We use this later to tell the loader we've loaded resource.
					// It's expecting the name to be what it was when it came in.  ex: combo/text!foo/bar.txt not foo/bar.txt
					if (dep.prefix) {
						asNames[i] = [dep.prefix, '!', dep.name].join('');
					}
				}
				else if (segment != '.') {
					var old = oChild[segment];
					if (typeof(old) == 'number') {
						oChild = oChild[segment] = {'.': old};
					} else {
						oChild = oChild[segment] || (oChild[segment] = {});
					}
				}
			}
		}
		if (iPrefixes) {
			oFolded[pluginPrefixesPropName] = oPrefixes;
		}
		return oFolded;
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
		
		return (hasArr.length ? ('has='+hasArr.join("|")): "");
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
