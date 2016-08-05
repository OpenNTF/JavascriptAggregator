/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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
    "combo/dojo/featureMap", 
    "dojox/encoding/base64", 
    "combo/featureDecoder"], function(featureMap, base64, featureDecoder) {
	describe("testFeatureEncoding", function() {
		
		it("should encode the feature string properly", function() {
			var features = "has=!0*2*12*!27*!31*50*!63*99*101";
			var result = featureMap.getQueryString(features);
			expect(result.substring(0,7)).toEqual("hasEnc=");
			// Now decode the encoded feature list
			expect(featureDecoder.decode(result.substring(7), base64)).toEqual({"0":false, "2":true, "12":true, "27":false, "31":false, "50":true, "63":false, "99":true});
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
			expect(result.substring(0,7)).toEqual("hasEnc=");
			expect(featureDecoder.decode(result.substring(7), base64)).toEqual({});
		});
		it("should gracefully handle garbage input", function() {
			var input = "fd;lakejrlewmasldm";
			var result = featureMap.getQueryString(input);
			expect(result).toEqual(input);
		});
	});
});