/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */
// This file is AMD Aggregator config for the Aggregator sample application 
{
	baseUrl: 'WebContent',
	
	packages: [
		{
			name: 'dojo',
			location: 'namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo-release-1.7.0-src/dojo'
		},
		{
			name: 'dijit',
			location: 'namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo-release-1.7.0-src/dijit'
		},
		{
			name: 'dojox',
			location: 'namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo-release-1.7.0-src/dojox'
		}
	],
	paths: {
		'dojo/dojo' : 'dojo-patched/dojo'	// http://bugs.dojotoolkit.org/ticket/14198 
	},
	
	depsIncludeBaseUrl: true,		// include baseUrl in scan for dependencies
	
	inlinedImageSizeThreshold: 1024,	// max image size to inline in CSS
	
	notice: 'notice.txt'

}