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
define(["combo/dojo/featureMap", "dojox/encoding/base64", "combo/featureList"], function(featureMap, base64, featureList) {
	describe("testFeatureEncoding", function() {
		function decode(encoded) {
			expect(encoded.indexOf("hasEnc=")).toBe(0);
			var base64decoded = base64.decode(encoded.substring(7).replace(/[_-]/g, function(c) {
				return (c=='-')?'+':'/';
			}) + '=');
			var len = base64decoded[0] + (base64decoded[1] << 8);
			expect(len).toBe(featureList.length);
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
			var a = [];
			for (i = 0; i < trits.length; i++) {
				if (trits[i] < 2) {
					a.push((trits[i] ? "" : "!") + featureList[i]);
				}
			}
			return a.join('*');
		}
		
		it("should encode the feature string properly", function() {
			var features = "has=!0*2*12*!27*!31*50*!63*99*101";
			var result = featureMap.getQueryString(features);
			// Now decode the encoded feature list
			expect(decode(result)).toEqual("!0*2*12*!27*!31*50*!63*99");
		});
		it("should handle empty feature list", function() {
			var result = featureMap.getQueryString("has=");
			expect(result).toBe("has=");
		});
		it("should handle null gracefully", function() {
			var result = featureMap.getQueryString(null);
			expect(result).toBeNull();
		});
		it("should encode empty feature set for unknown features", function() {
			var result = featureMap.getQueryString("has=foo*bar*hello");
			expect(decode(result)).toEqual("");
		});
		it("should gracefully handle garbage input", function() {
			var input = "fd;lakejrlewmasldm";
			var result = featureMap.getQueryString(input);
			expect(result).toEqual(input);
		});
	});
});