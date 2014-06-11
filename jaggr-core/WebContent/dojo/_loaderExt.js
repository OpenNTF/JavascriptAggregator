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
 *  AMD combo loader extention for the Dojo AMD loader.
 *  <p>
 *  This file is combined with loaderExtCommon.js, and with dynamically 
 *  injected javascript code, by the Dojo HttpTransport extension on the
 *  aggregator when the combo/loaderExt.js pseudo resource is 
 *  requested.
 */
(function() {
var depmap = {},
    deps = [],
    userConfig = (function(){
		// make sure we're looking at global dojoConfig etc.
		return this.dojoConfig || this.djConfig || this.require;
	})(),

    combo = userConfig.combo,
    
    // The context path of the aggregator service
    contextPath = combo.contextPath,

    // ( 4k/4096 with a buffer just in case - 96) 
    // Set to 0 to disable url length checks.
    maxUrlLength = (typeof(combo.maxUrlLength) === 'undefined') ?
            // IE doesn't cache responses for request URLs greater than about 2K
			(/MSIE (\d+\.\d+);/.test(navigator.userAgent) ? 2000 : 4000) :
			combo.maxUrlLength,

	// The following vars are referenced by javascript code injected by the 
	// server.
    plugins = (combo.plugins = combo.plugins || {}),
    aliases = (userConfig.aliases = userConfig.aliases || []),
    // Flag indicating whether or not has features sent to the server should
    // include features that evaluate to undefined
    includeUndefinedFeatures,

    // Map of module name to number id pairs
    moduleIdMap = {};

// Copy config params from the combo config property
for (var s in params) {
	if (typeof combo[s] !== 'undefined') {
		params[s][0] = combo[s];
	}
}

extraArgs = combo.extraArgs || {};
userConfig.has = userConfig.has || {};

//By default, don't include config- features since the loader uses the has map as a dumping 
//ground for anything specified in the config.
featureFilter = combo.featureFilter || function(name) { return !/^config-/.test(name);};

addFilter = combo.addFilter || addFilter;

// Set this so that the loader won't synchronously call require.callback
userConfig.has["dojo-built"] = true;

userConfig.async = true;		// use async loader

//Enable the combo api
userConfig.has['dojo-combo-api'] = 1;
userConfig.has['dojo-sync-loader'] = 0;

/**
 * urlProcessor to add query locale query arg
 */
urlProcessors.push(function(url){
	
    if (typeof dojo !== 'undefined' && dojo.locale && !/[?&]locs=/.test(url)) {
        url+=('&locs='+[dojo.locale].concat(userConfig.extraLocales || []).join(','));
    }
    return url;
});

// require.combo needs to be defined before this code is loaded
combo.done = function(load, config, opt_deps) {
	var hasArg = "", base64,
	    sendRequest = function(load, config, opt_deps) {
			var mids = [];
			opt_deps = opt_deps || deps;
			for (var i = 0, dep; !!(dep = opt_deps[i]); i++) {
				mids[i] = dep.prefix ? (dep.prefix + "!" + dep.name) : dep.name;
			}
			
			var url = contextPath;
			url = addRequestedModulesToUrl(url, opt_deps, moduleIdMap, base64 ? base64.encode : null);
			url += (hasArg ? '&' + hasArg : "");
			
			// Allow any externally provided URL processors to make their contribution
			// to the URL
			for (i = 0; i < urlProcessors.length; i++) {
				url = urlProcessors[i](url);
			}
			
			if (config.has("dojo-trace-api")) {
				config.trace("loader-inject-combo", [mids.join(', ')]);
			}
			if (maxUrlLength && url.length > maxUrlLength) {
				var parta = opt_deps.slice(0, opt_deps.length/2),
				    partb = opt_deps.slice(opt_deps.length/2, opt_deps.length);
				deps = [];
				depmap = {};
				sendRequest(load, config, parta);
				sendRequest(load, config, partb);
			} else {
				if (deps === opt_deps) {
					// we have not split the module list to trim url size, so we can clear this safely.
					// otherwise clearing these is the responsibility of the initial function.
					deps = [];
					depmap = {};
				}
				load(mids, url);			
			}
	    };
	
	// Get base64 decoder
	try {
		base64 = require('dojox/encoding/base64');
	} catch (ignore) {}

	if (!combo.serverOptions.skipHasFiltering) {
		if (typeof includeUndefinedFeatures == 'undefined') {
			// Test to determine if we can include features that evaluate to undefined.
			// If simply querying a feature puts the feature in the cache, then we
			// can't send features that evaluate to undefined to the server.
			// (Note: this behavior exists in early versions of dojo 1.7)
			var test_feature = 'combo-test-for-undefined';
			config.has(test_feature);
			includeUndefinedFeatures = !(test_feature in config.has.cache);
		}
		hasArg = computeHasArg(config.has, config.has.cache, includeUndefinedFeatures);
	}
		
	// If sending the feature set in a cookie is enabled, then try to 
	// set the cookie.
	var featureMap = null, featureCookie = null;
	if (!!(featureMap = config.has("combo-feature-map"))) {
		hasArg = featureMap.getQueryString(hasArg);
	} else if (!!(featureCookie = config.has("combo-feature-cookie"))) {
		hasArg = featureCookie.setCookie(hasArg, contextPath);
	}

	sendRequest(load, config, opt_deps);
};

combo.add = function (prefix, name, url, config) {
	if (/^(\/)|([^:\/]+:[\/]{2})/.test(name) || config.cache[name] || !addFilter(prefix, name, url)) {
		return false;
	}
	if (!depmap[name] && (!prefix || prefix in plugins)) {
		deps.push(depmap[name] = {
			prefix: prefix,
			name: name
		});
	}
	
	var canHandle = !!depmap[name];
	if (!canHandle && config.has("dojo-trace-api")) {
		config.trace("loader-inject-combo-reject", ["can't handle: " + prefix + "!" + name]);
	}
	return canHandle;
};

/*
 * If ary is null, then hash specifies the module id list hash on the server.
 * If ary is not null, then it specifies the module ids to register and hash is ignored
 */
combo.reg = function(ary, hash) {
	if (ary === null) {
		for (var s in moduleIdMap) {
			if (moduleIdMap.hasOwnProperty(s)) {
				throw new Error("Can't set hash");
			}
		}
		moduleIdMap["**idListHash**"] = hash;
		return;
	}
	registerModuleNameIds(ary, moduleIdMap);
};

combo.getIdMap = function() {
	// return a copy of the object
	// Note that this function is used only called unit tests and diagnostic tools, so 
	// the potentially poor performance of the converting the string to/form json is not
	// and issue.
	return JSON.parse(JSON.stringify(moduleIdMap));
};

setTimeout(function() {
	if (userConfig.deps) {
		require(userConfig.deps, function() {
			if (userConfig.callback) {
				userConfig.callback.apply(this, arguments);
			}
		});
	} else if (userConfig.callback) {
		userConfig.callback();
	}
}, 0);
})();
