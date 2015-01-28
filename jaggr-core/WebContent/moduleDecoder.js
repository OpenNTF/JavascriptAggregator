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
], function() {
	
	/**
	 * The functionality provided by this module is not used by the aggregator for normal
	 * operation because request decoding is normally done by the server.  This module is 
	 * provided for unit testing and client side diagnostic purposes.
	 */

	var unfoldModulesHelper = function(obj, path, prefixes, resultArray) {
		if (typeof obj === "string"){
			var values = obj.split("-");
			var idx = parseInt(values[0], 10);
			if (resultArray[idx]) {
				throw new Error("Overwrite of module at position " + idx);
			}
			resultArray[idx] = values.length > 1 ?
					((prefixes ?
							prefixes[parseInt(values[1],10)] : values[1])
							+ "!" + path) : //$NON-NLS-1$
								path;
		} else {
			for (var s in obj) {
				unfoldModulesHelper(obj[s], path + "/" + s, prefixes, resultArray);
			}
		}
	};
	
	return {
		decodeModuleIdList: function(encoded, base64decoder, resultArray, hasHash) {
			encoded = encoded.replace(/[-_]/g, function(c) {
				return (c=='-')?'+':'/';
			});
			var decoded = base64decoder.decode(encoded),
			    moduleIdMap = require.combo.getIdMap(),
			    idModuleMap = {},
			    hashLen = moduleIdMap["**idListHash**"].length,
			    hash = hasHash ? decoded.slice(0, hashLen) : 0,
			    use32BitEncoding = hasHash ? decoded[hashLen] : 0,
			    idList = [], length, j, i,
			    elemLen = use32BitEncoding ? 4 : 2;
			
			if (hasHash) {
				if (hash.join(",") != moduleIdMap["**idListHash**"].join(",")) {
					throw new Error("Invalid module id list hash!");
				}
				decoded = decoded.slice(hashLen+1);
			}
			
			for (i = 0; i < decoded.length/elemLen; i++) {
				j = i * elemLen;
				if (use32BitEncoding) {
					idList.push( ((decoded[j]&0xFF) << 24) + ((decoded[j+1]&0xFF) << 16) +
					             ((decoded[j+2]&0xFF) << 8) + (decoded[j+3]&0xFF) );
				} else {
					idList.push( ((decoded[j]&0xFF) << 8) + (decoded[j+1]&0xFF) );
				}
			}
			
			for (var s in moduleIdMap) {
				if (moduleIdMap.hasOwnProperty(s) && s !== "**idListHash**") {
					idModuleMap[moduleIdMap[s]] = s;
				}
			}
			for (i = 0, position = -1, length = 0; i < idList.length;) {
				if (position == -1) {
					// read the position and length values
					position = idList[i++];
					length = idList[i++];
				}
				for (j = 0; j < length; j++) {
					var pluginName = null, moduleName = null, id = idList[i++];
					if (id === 0) {
						// 0 means the next two ints specify plugin and modulename
						id = idList[i++];
						pluginName = idModuleMap[id];
						if (!pluginName) {
							throw new Error("null plugin name.");
						}
						id = idList[i++];
						moduleName = id !== 0 ? idModuleMap[id] : "";
					} else {
						moduleName = idModuleMap[id];
					}
					if (!moduleName) {
						throw new Error("Module for id " + id + " not found!");
					}
					if (resultArray[position+j]) {
						throw new Error("Overwrite of module at position " + (position+j));
					}
					resultArray[position+j] = (pluginName ? (pluginName + "!") : "") + moduleName;
				}
				position = -1;
			}	
		},

		unfoldModuleNames: function(encoded, resultArray) {
			var json = encoded.replace(/([!()|*<>])/g, function($0, $1) {
				switch($1) {
				case "!":
					return ":";
				case "(":
					return "{";
				case ")":
					return "}";
				case "|":
					return "!";
				case "*":
					return ",";
				case "<":
					return "(";
				case ">":
					return ")";
				default:
					throw new Error("Invalid token " + $1 + ": " + $0);
				}
			});
			
			json = json.replace(/([{,:])([^{},:"]+)([},:])/g, '$1"$2"$3');	// matches all keys
			json = json.replace(/([{,:])([^{},:"]+)([},:])/g, '$1"$2"$3'); // matches all values
			var folded = JSON.parse(json);
			var prefixes = [], s;
			if ("/pre/" in folded) {
				var oPrefixes = folded["/pre/"];
				for (s in oPrefixes) {
					prefixes[oPrefixes[s]] = s;
				}
			}
			for (s in folded) {
				if (!/^\/[^\/]+/.test(s)) {		// if not a processing directive (e.g. '/pre/')
					unfoldModulesHelper(folded[s], s, prefixes, resultArray);
				}
			}
		}
	};
});
