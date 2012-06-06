/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
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
			// hasArg is expected to begin with 'has=' followed by the semi-colon
			// delimited list of features
			var haslist = null;		// clears the cookie if null
			var ret = hasArg;
			if (hasArg.indexOf("has=") === 0 && hasArg.indexOf(";") != -1) {
				haslist = hasArg.substring(4);
				ret = "hashash=" + md5(haslist, 1);
			}
			cookie('has', haslist, {
				expire: haslist ? 1 : -1,
				domain: window.location.host,
				path: contextPath
			});
			return ret;
		}
	};
});