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

define([
    'dojo/has',			// we use this 
    'dojo/_base/lang',  // dojox/encoding/digests/_base.js requires this but doesn't 
                        //   explicitly list it in its dependency list, so we need to.
    'dojox/encoding/digests/MD5', // used to encode the feature set
    'dojo/cookie',		// used to set the cookie
    './sniff'           // initializes basic set of features used by dojo
    ], 
function(has, lang, md5, cookie) {
	if (cookie.isSupported()) {
		has.add("combo-feature-cookie", function(){return true;});
	}
	// If we have an MD5 digest we can hash the has string, use the hash in the URL and put the has conditions in a cookie.
	// This saves space in the URL for times when the number of has conditions is lengthy.
	return {
		setCookie: function(hasArg, contextPath) {
			var matches = /^[^:\/]+:\/\/([^\/]+)(\/.*)?$/.exec(contextPath);
			if (matches) {
				if (matches.length > 1 && matches[1] === window.location.host) {
					// Same domain, so adjust contextPath to remove the domain part
					contextPath = (matches.length > 2) ? matches[2] : "/";
				} else {
					// Can't set cookie for a different domain, so specify feature 
					// set using URL query arg instead.
					return hasArg;
				}
			}
			// hasArg is expected to begin with 'has=' followed by the '|'
			// delimited list of features
			var haslist = null;		// clears the cookie if null
			var ret = hasArg;
			if (hasArg.indexOf("has=") === 0 && hasArg.indexOf("|") != -1) {
				haslist = hasArg.substring(4);
				ret = "hashash=" + md5(haslist, 1);
			}
			var domain = window.location.hostname,
			    numDots = domain.split(".").length-1;
			if (numDots === 1) {
				// Need at least 2 dots in the cookie domain for most browsers.
				// If there's only one, then we can add a leading dot to satisfy
				// this requirement.
				domain = '.' + domain;
			} else if (numDots === 0) {
				// If we have no dots (e.g. domain == 'localhost') then don't set
				// the domain property in the cookie.
				domain = null;
			}
			var args = {
				expire: haslist ? 1 : -1,
				path: contextPath
			};
			if (domain) {
				args.domain = domain;
			}
			cookie('has', haslist, args);
			return ret;
		}
	};
});