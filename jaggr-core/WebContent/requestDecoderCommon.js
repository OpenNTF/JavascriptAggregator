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
    "combo/moduleDecoder",
    "combo/featureDecoder"
], function(moduleDecoder, featureDecoder) {

	/**
	 * The functionality provided by this module is not used by the aggregator for normal
	 * operation because request decoding is normally done by the server.  This module is
	 * provided for unit testing and client side diagnostic purposes.
	 */

	return {
		decodeRequest: function(url, base64decoder) {
			var argsArray = url.split("?"),
			    queryArgs = argsArray.length > 1 ? argsArray[1] : argsArray[0],
			    result = {};
			argsArray = queryArgs.split("&");
			var args = {};
			for (var i = 0; i < argsArray.length; i++) {
				var arg = decodeURIComponent(argsArray[i]);
				var parts = arg.split("=");
				if (parts.length === 2) {
					args[parts[0]] = parts[1];
				}
			}
			moduleArray = [];
			if ("count" in args) {
				if ("modules" in args) {
					moduleDecoder.unfoldModuleNames(args.modules, moduleArray);
				}
				if ("moduleIds" in args) {
					moduleDecoder.decodeModuleIdList(args.moduleIds, base64decoder, moduleArray, true);
				}
				// validate module array.  Make sure array size == count arg and make sure there are
				// no holes
				if (moduleArray.length != args.count) {
					throw new Error("Invalid count");
				}
				for (i = 0; i < moduleArray.length; i++) {
					if (!moduleArray[i]) {
						throw new Error("Unassigned index in result array");
					}
				}
				if (moduleArray.length) {
					result.modules = moduleArray;
				}
				moduleArray = [];
				if ("exEnc" in args) {
					moduleDecoder.unfoldModuleNames(args.exEnc, moduleArray);
				}
				if ("exIds" in args) {
					moduleDecoder.decodeModuleIdList(args.exIds, base64decoder, moduleArray, false);
				}
				for (i = 0; i < moduleArray.length; i++) {
					if (!moduleArray[i]) {
						throw new Error("Unassigned index in result array");
					}
				}
				if (moduleArray.length) {
					result.excludes = moduleArray;
				}
			} else {
				if ("scripts" in args) {
					result.scripts = args.scripts.split(/[,\s]/);
				}
				if ("deps" in args) {
					result.deps = args.deps.split(/[,\s]/);
				}
				if ("preloads" in args) {
					result.preloads = args.preloads.split(/[,\s]/);
				}
				if ("excludes" in args) {
					result.excludes = args.excludes.split(/[,\s]/);
				}
			}

			// Decode feature list
			if ("hasEnc" in args) {
				result.features = featureDecoder.decode(args.hasEnc, base64decoder);
			} else if ("has" in args) {
				var features = {},
				    ary = args.has.split(/[;*]/);
				for (i = 0; i < ary.length; i++) {
					var feature = ary[i];
					if (feature.charAt(0) !== '!') {
						features[feature] = true;
					} else {
						features[feature.substring(1)] = false;
					}
				}
				result.features = features;
			}
			return result;
		}
	};
});
