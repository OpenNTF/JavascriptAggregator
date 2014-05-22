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
        "dojo/has!dojo-combo-api?combo/featureList"     // not a physical module.  Resolved by JAGGR 
        //  to return array of dependent features 
], function(featureList) {

	/**
	 * The functionality provided by this module is not used by the aggregator for normal
	 * operation because request decoding is normally done by the server.  This module is 
	 * provided for unit testing and client side diagnostic purposes.
	 */
	
	return {
		decode: function(encoded, base64decoder) {
			var base64decoded = base64decoder.decode(encoded.replace(/[_-]/g, function(c) {
				return (c=='-')?'+':'/';
			}));
			var len = (base64decoded[0]&0xFF) + ((base64decoded[1]&0xFF) << 8);
			if (len !== featureList.length) {
				throw new Error("Invalid feature list length");
			}
			// Now decode the trit map
			var trits = [];
			for (var i = 2; i < base64decoded.length; i++) {
				var q = base64decoded[i] & 0xFF;
				for (var j = 0; j < 5 && (i-2)*5+j < len; j++) {
					trits.push(q % 3);
					q = Math.floor(q / 3);
				}
			}
			// now reconstruct the features
			var result = {};
			for (i = 0; i < trits.length; i++) {
				if (trits[i] < 2) {
					result[featureList[i]] = (trits[i] ? true : false);
				}
			}
			return result;
		}
	};
});