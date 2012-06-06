/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

define(["dojo/has", "dojo/_base/sniff"], function(has) {
	/*
	 * Maximize the aggregator's ability to trim javascript code
	 * based on has.js feature detection by ensuring that there is
	 * an entry in the has cache for each browser feature that is 
	 * handled by dojo/_base/sniff.
	 * 
	 * Note:  This file will require frequent updating to keep pace
	 * with changes to dojo/_base/sniff
	 */
	var baseFeatures = [
		"opera", 
		"air", 
		"khtml",
		"webkit",
		"chrome",
		"mac",
		"safari",
		"mozilla",
		"ie",
		"ff",
		"quirks",
		"ios",
		"android"
	]; 
	
	for (var i = 0; i < baseFeatures.length; i++) {
		var s = baseFeatures[i];
		if (typeof has(s) === "undefined") {
			// This doesn't change the value returned by has() for this 
			// feature, but it does ensure that there is an entry in the 
			// has cache so that we know the feature is defined.
			has.add(s, has(s));
		}
	}
	return has;
});