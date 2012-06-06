/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */
(function() {
	var href = location.href,
		isDebugLoader = /[&?]debugLoader=1/.test(href);
	
	// make sure we use the global require object
	this.require = {
		packages: [
	   		{
	   			name: 'dojo',
	   			location: '../dojo',
	   			lib: '.'
	   		},
	   		{
	   			name: 'dijit',
	   			location: '../dijit',
	   			lib: '.'
	   		},
	   		{
	   			name: 'dojox',
	   			location: '../dojox',
	   			lib: '.'
	   		}
	   	],
	   	
	   	paths: {
	   		js: "../res/js"
	   	},
	   	
		has: {
			"dojo-error-api" : isDebugLoader,
			"dojo-trace-api" : isDebugLoader,
			"host-browser" : 1
		},
		
		// Enable the desired loader tracing options 
		trace: {
			"loader-inject":isDebugLoader,
			"loader-inject-combo": isDebugLoader,
			"loader-inject-combo-reject": isDebugLoader,
			"loader-define":isDebugLoader,
			"loader-exec-module":isDebugLoader,
			//"loader-run-factory":isDebugLoader,
			//"loader-finish-exec":isDebugLoader,
			"loader-define-module":isDebugLoader
		},

		// This option only has meaning if has.dojo-timeout-api is enabled
		waitSeconds: 10,
	
		combo: {
			contextPath: "/testaggr",
			expandRequire: true
		},
		
		deps: [
	       "dojo/ready",
	       "dojo/parser", 
	       "dijit/layout/TabContainer",
	       "js/LazyContentPane",
	       "js/css!dijit/themes/claro/claro.css" 
		],
		
		callback: function(ready, parser) {
			ready(function() {
				parser.parse();
			});
		}
	};
	
	// get combo propery overrides from URL
	for (var s in {optimize:0,expandRequire:0,showFilenames:0,noCache:0,exportNames:0}) {
		var regex = new RegExp("[&?]"+s+"=([^&]*)","i");
		var result = regex.exec(href) || [];
		if (result.length > 1) {
			require.combo[s] = result[1];
		}
	}
	
})();