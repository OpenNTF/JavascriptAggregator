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
	"dojo/has!dojo-combo-api?combo/featureList",    // not a physical module.  Resolved by JAGGR 
                                                    //  to return array of dependent features 
    "dojox/encoding/base64"
], function(has, featureList, base64) {
	var result = {
		getQueryString: function(hasArg) {
			// hasArg is expected to begin with 'has=' followed by the '*'
			// delimited list of features
			if (hasArg && hasArg.indexOf("has=") === 0 && hasArg.indexOf("*") != -1) {
				var hasList = hasArg.substring(4).split('*'), hasMap = {}, value;
				for (var i = 0; i < hasList.length; i++) {
					value = hasList[i].charAt(0) != "!";
					hasMap[hasList[i].substring(value?0:1)] = value;
				}
				// Build trit map.  5 trits per byte
				var b = 0, bytes = [], len = featureList.length, trit;
				// First two bytes of the array specify the list size
				bytes.push(len & 0xFF);
				bytes.push((len & 0xFF00) >> 8);
				for (i = 0; i < len; i++) {
					var mod = i % 5;
					value = hasMap[featureList[i]];
					trit = (value===false)?0:(value===true?1:2/* don't care */);
					b += trit * Math.pow(3, mod);
					if (mod == 4 || i == len-1) {
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