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

/*
 * Note: __original_text_plugin and __aggregator_text_plugin are aliased in the 
 * loader config by the http transport to the actual names so that the strings 
 * can be static in this file.
 */
define(["dojo/_base/lang", "__original_text_plugin"], function(lang, original_text_plugin) {
	return lang.mixin({}, original_text_plugin, {
		load:function(id, require, load){
			if (/^(\/)|([^:\/]+:[\/]{2})/.test(id)) {
				original_text_plugin.load(id, require, load);
			} else {
				var url = require.toUrl(id),
				    requireCacheUrl = "url:" + url;
				if (requireCacheUrl in require.cache) {
					load(require.cache[requireCacheUrl]);
				} else {
					require(["__aggregator_text_plugin!"+id], function(text) {
						load(text);
					});
				}
			}			
		}
	});
});