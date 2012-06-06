/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
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
				require(["__aggregator_text_plugin!"+id], function(text) {
					load(text);
				});
			}			
		}
	});
});