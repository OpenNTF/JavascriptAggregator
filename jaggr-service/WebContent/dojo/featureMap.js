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
    "dojo/has", 
    "combo/featureList", 	// not a physical module.  Resolved by JAGGR 
                            //  to return array of dependent features 
    "dojox/encoding/base64"
], function(has, featureList, base64) {
	var result = {
		getQueryString: function(hasArg) {
			// hasArg is expected to begin with 'has=' followed by the '*'
			// delimited list of features
			if (hasArg.indexOf("has=") === 0 && hasArg.indexOf("*") != -1) {
				var hasList = hasArg.substring(4).split('*'), hasMap = {};
				for (var i = 0; i < hasList.length; i++) {
					var value = hasList[i].charAt(0) != "!";
					hasMap[hasList[i].substring(value?0:1)] = value;
				}
				// Build trit map.  5 trits per byte
				var b = 0, bytes = [];
				for (i = 0; i < featureList.length; i++) {
					var mod = i % 5,
					    value = hasMap[featureList[i]],
					    trit = (value === null) ? 2 /* don't care */ : (value ? 1 : 0);
					b += trit * Math.pow(3, mod);
					if (mod == 4 || i == featureList.length-1) {
						bytes.push(b);
						b = 0;
					}
				}
				// Make the base64 encoded string URL safe.
				encoded = base64.encode(bytes).replace(/[+=\/]/g, function(c) {
					return (c=='+')?'-':((c=='/')?'_':'');
				});
				hasArg = "hasEnc=" + encoded;
			}
			return hasArg;
		}
	};
	if (featureList && featureList.length > 0) {
		has.add("combo-feature-map", function(){return result;});
	}
	return result;
});