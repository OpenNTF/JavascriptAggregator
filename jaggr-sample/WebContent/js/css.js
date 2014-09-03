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
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
define([
	'require',
	'dojo/_base/window',
	'dojo/_base/html',
	'dojo/dom-construct',
	'dojo/has',
	'dojo/query',
	'dojo/text'
], function(req, dwindow, dhtml, domConstruct, has, query){
	/*
	 * module:
	 *    css
	 * summary:
	 *    This plugin handles AMD module requests for CSS files.  Required modules are
	 *    loaded by delegating to the dojo/text plugin and then inserting the CSS into
	 *    a style sheet element that is appended to the HEAD element in the DOM, and 
	 *    the style element is returned as the value of the module.
	 * 
	 *    URLs for url(...) and @import statements in the CSS are fixed up to make them 
	 *    relative to the requested module's path.
	 * 
	 *    This plugin guarantees that style elements will be inserted into the DOM in 
	 *    the same order that the associated CSS modules are requested, except when a
	 *    previously requested module is requested again.  In other words, if 
	 *    stylesA.css is required before stylesB.css, then the styles for stylesA.css
	 *    will be inserted into the DOM ahead of the styles for stylesB.css.  This
	 *    behavior ensures proper cascading of styles based on order of request.
	 */
	var
		head = dwindow.doc.getElementsByTagName('head')[0],
		toAbsMid= has('dojo-loader') ?
				function(id, require){
					var result = require.toAbsMid(id + '/x');
					return result.substring(0, result.length-2);
				} :
				function(id, require){
					return require.toUrl(id);
				},

		fixUrlsInCssFile = function(/*String*/filePath, /*String*/content){
			var rewriteUrl = function(url) {
				// remove surrounding quotes and normalize slashes
				url = url.replace(/[\'\"]/g,'').replace(/\\/g, '/').replace(/^\s\s*/, '').replace(/\s*\s$/, '');
				// only fix relative URLs.
				if (url.charAt(0) != '/' && !/^[a-zA-Z]:\/\//.test(url)) {
					url = filePath.slice(0, filePath.lastIndexOf('/') + 1) + url;
					//Collapse .. and .
					var parts = url.split('/');
					for(var i = parts.length - 1; i > 0; i--){
						if(parts[i] == '.'){
							parts.splice(i, 1);
						}else if(parts[i] == '..'){
							if(i !== 0 && parts[i - 1] !== '..'){
								parts.splice(i - 1, 2);
							}
						}
					}
				 url = require.toUrl(parts.join('/')); // Apply dojo's cache bust (if any)
				}
				return url;
			};
			content = content.replace(/url\s*\(([^#\n\);]+)\)/gi, function(match, url){
				return 'url(\'' + rewriteUrl(url) + '\')';
			});
			content = content.replace(/@import\s+(url\()?([^\s;]+)(\))?/gi, function(match, prefix, url) {
				return (prefix == 'url(' ? match : ('@import \'' + rewriteUrl(url) + '\''));
			});
			return content;
		};

	return {
		load: function (/*String*/id, /*Function*/parentRequire, /*Function*/load) {
			if (has('no-css')) {
				return load();
			}
			var parts = id.split('!'),
					url = parentRequire.toUrl(parts[0]).replace(/^\s+/g, ''), // Trim possible leading white-space
					absMid= toAbsMid(parts[0], parentRequire);

			// see if a stylesheet element has already been added for this module
			var styles = query("head>style[url='" + url + "']"), style;
			if (styles.length === 0) {
				// create a new style element for this module and add it to the DOM
				style = domConstruct.create('style', {}, head);
				style.type = 'text/css';
				dhtml.setAttr(style, 'url', url);
				dhtml.setAttr(style, 'loading', '');
			} else {
				style = styles[0];
				if (!dhtml.hasAttr(style, 'loading')) {
					load(style);
					return;
				}
			}
			
			parentRequire(['dojo/text!' + absMid], function (text) {
				// Check if we need to compile LESS client-side
				if (/\.less(?:$|\?)/.test(absMid) && !has('dojo-combo-api')) {
					processLess(text);
				} else {
					processCss(text);
				}
			});

			/**
			 * Compiles LESS to CSS and passes off the result to the standard `processCss` method.
			 * @param  {String} lessText The LESS text to compile.
			 */
			function processLess(lessText) {
				req(['lesspp'], function (lesspp) {
					var pathParts = url.split('?').shift().split('/');
					var parser = new lesspp.Parser({
						filename: pathParts.pop(),
						paths: [pathParts.join('/')]
					});
					parser.parse(fixUrlsInCssFile(url, lessText), function (err, tree) {
						if (err) {
							console.error('LESS Parser Error!');
							console.error(err);
							return load.error(err);
						}
						processCss(tree.toCSS());
					});
				});
			}

			/**
			 * Injects CSS text into the stylesheet element, fixing relative URLs.
			 * @param  {String} cssText The CSS to inject.
			 */
			function processCss(cssText) {
				if (cssText && dojo.isString(cssText) && cssText.length) {
					cssText = fixUrlsInCssFile(url, cssText);
					if(style.styleSheet){
						style.styleSheet.cssText = cssText;
					}else{
						while (style.firstChild) {
							style.removeChild(style.firstChild);
						}
						style.appendChild(dwindow.doc.createTextNode(cssText));
					}
				}
				dhtml.removeAttr(style, "loading");
				load(style);
			}
		}
	};
});
