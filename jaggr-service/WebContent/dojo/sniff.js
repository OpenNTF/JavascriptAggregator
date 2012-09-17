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