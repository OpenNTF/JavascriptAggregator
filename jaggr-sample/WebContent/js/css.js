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
	'dojo/_base/array',
	'dojo/_base/lang',
	'dojo/io-query',
	'dojo/_base/Deferred',
	'dojo/has!dojo-combo-api?:postcss?dojo/promise/all',
	'dojo/has!dojo-combo-api?:postcss?postcss',
	'dojo/text',
], function(require, dwindow, dhtml, domConstruct, has, query, arrays, lang, ioQuery, Deferred, all, postcss){
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
	
		urlPattern = /(^[^:\/]+:\/\/[^\/\?]*)([^\?]*)(\??.*)/,
	
		isLessUrl = function(url) {
			return /\.less(?:$|\?)/i.test(url);
		},
	
		isRelative = function(url) {
			return !/^[^:\/]+:\/\/|^\//.test(url); 
		},    
	
		dequote = function(url) {
			// remove surrounding quotes and normalize slashes
			return url.replace(/^\s\s*|\s*\s$|[\'\"]|\\/g, function(s) {
				return s === '\\' ? '/' : '';
			});
		},
	
		joinParts = function(parts) {
			// joins URL parts into a single string, handling insertion of '/' were needed
			var result = '';
			arrays.forEach(parts, function(part) {
				result = result + 
					(result && result.charAt(result.length-1) !== '/' && part && part.charAt(0) !== '/' ? '/' : '') +
				part;
			});
			return result;
		},
	
	
		normalize = function(url) {
			// Collapse .. and . in url paths
			var match = urlPattern.exec(url) || [url, '', url, ''],
			    host = match[1], path = match[2], queryArgs = match[3];
	
			if (!path || path === '/') return url;
	
			var parts = [];
			arrays.forEach(path.split('/'), function(part, i, ary) {
				if (part === '.') {
					if (i === ary.length - 1) {
						parts.push('');
					}
					return;
				} else if (part == '..') {
					if ((parts.length > 1 || parts.length == 1 && parts[0] !== '') && parts[parts.length-1] !== '..') {
						parts.pop();
					} else {
						parts.push(part);
					}
				} else {
					parts.push(part);
				}
			});
			var result = parts.join('/');
	
			return joinParts([host, result]) + queryArgs;
		},
	
		resolve = function(base, relative) {
			// Based on behavior of the Java URI.resolve() method.
			if (!base || !isRelative(relative)) {
				return normalize(relative);
			}
			if (!relative) {
				return normalize(base);
			}
			var match = urlPattern.exec(base) || [base, '', base, ''],
			host = match[1], path = match[2], queryArgs = match[3];
	
			// remove last path component from base before appending relative
			if (path.indexOf('/') !== -1 && path.charAt(path.length) !== '/') {
				// remove last path component
				path = path.split('/').slice(0, -1).join('/') + '/';
			} 
	
			return normalize(joinParts([host, path, relative]));
		}, 
	
		addArgs = function(url, queryArgs) {
			// Mix in the query args specified by queryArgs to the URL
			if (queryArgs) {
				var queryObj = ioQuery.queryToObject(queryArgs),
				    mixedObj = lang.mixin(queryObj, ioQuery.queryToObject(url.split('?')[1] || ''));
				url = url.split('?').shift() + '?' + ioQuery.objectToQuery(mixedObj);
			}
			return url;
		},
	
		fixUrlsInCssFile = function(/*String*/filePath, /*String*/content, /*boolean*/lessImportsOnly){  
			var queryArgs = filePath.split('?')[1] || '';
	
			var rewriteUrl = function(url) {
				if (lessImportsOnly && url.charAt(0) === '@') {
					return url;
				}
				// only fix relative URLs.
				if (isRelative(url)) {
					url = resolve(filePath, url);
					if (lessImportsOnly && isLessUrl(url)) {
						// LESS compiler fails to locate imports using relative urls when
						// the document base has been modified (e.g. via a <base> tag),
						// so make the url absolute.
						var baseURI = dwindow.doc.baseURI;
						if (!baseURI) {
							// IE doesn't support document.baseURI.  See if there's a base tag
							var baseTags = dwindow.doc.getElementsByTagName("base");
							baseURI = baseTags.length && baseTags[0].href || dwindow.location && dwindow.location.href;
						}
						url = resolve(baseURI, url);
					}
				}
				return addArgs(url, queryArgs);		// add cachebust arg from including file
			};
	
			if (lessImportsOnly) {
				// Only modify urls for less imports.  We need to do it this way because the LESS compiler
				// needs to be able to find the imports, but we don't want to do non-less imports because
				// that would result in those URLs being rewritten a second time when we process the compiled CSS.
				content = content.replace(/@import\s+(url\()?([^\s;]+)(\))?/gi, function(match, prefix, url) {
					url = dequote(url);
					return isLessUrl(url) ? '@import \'' + rewriteUrl(url) + '\'' : match;
				});
			} else {
				content = content.replace(/url\s*\(([^#\n\);]+)\)/gi, function(match, url){
					return 'url(\'' + rewriteUrl(dequote(url)) + '\')';
				});
				// handle @imports that don't use url(...) 
				content = content.replace(/@import\s+(url\()?([^\s;]+)(\))?/gi, function(match, prefix, url) {
					return (prefix == 'url(' ? match : ('@import \'' + rewriteUrl(dequote(url)) + '\''));
				});
			}
			return content;
		},
		
		postcssProcessor,
		
		postcssPromise,
		
		/*
		 * Initialize PostCSS and configured plugins.  Plugins are configured with the postcssPlugins 
		 * property in dojoConfig or the global require object.
		 * 
		 * Plugins are configured using an array of two element arrays as in the following example:
		 * <code><pre>
		 * postcss: {
		 *    plugins: [
		 *       [
		 *          'autoprefixer',  // Name of the plugin resource
		 *                           // Can be an AMD module id or an absolute URI to a server resource
		 *          function(autoprefixer) { 
		 *             return autoprefixer({browsers: '> 1%'}).postcss; // the init function
		 *          }
		 *       ]
		 *    ]
		 * },
		 * </pre></code>
		 */
		postcssInitialize = function() {
			var deferred;
			if (!has('dojo-combo-api') && has('postcss') && postcss) {
				var postcssConfig = window.require.postcss || window.dojoConfig && window.dojoConfig.postcss;
				if (postcssConfig) {
					var pluginsConfig = postcssConfig.plugins;
					if (pluginsConfig) {
						// Load each module using async require.  Each loaded module will get a Promise
						// that will be resolved when that module loads and the plugin object has been
						// initialized.
						var promises = [], plugins = [];
						arrays.forEach(pluginsConfig, function(pluginConfig) {
							var deferred = new Deferred();
							promises.push(deferred.promise);
							require([pluginConfig[0]], function(p) {
								try {
									plugins.push(pluginConfig[1](p));
									deferred.resolve();
								} catch (e) {
									console.error(e);
									deferred.reject(e);
								}
							});
						});
						if (promises.length > 0) {
							// Use dojo/promise/all so we know when all of the plugins have been
							// loaded and initialized.
							deferred = new Deferred();
							postcssPromise = deferred.promise;
							all(promises).then(function() {
								try {
									postcssProcessor = postcss(plugins);
									deferred.resolve();
								} catch (e) {
									console.error(e);
									deferred.reject(e);
								}
							}).otherwise(function(e) {
								// one or more plugins failed to initialize
								console.error(e);
								deferred.reject(e);
							});
						}
					}
				}
			}
				
			if (!postcssPromise) {
				// Not using PostCSS or there were no plugins configured.  Just create a 
				// resolved promise.
				deferred = new Deferred();
				postcssPromise = deferred.promise;
				deferred.resolve();
			}
		};
		
		postcssInitialize();
		
		
	return {
		load: function (/*String*/id, /*Function*/parentRequire, /*Function*/load) {
			if (has('no-css')) {
				return load();
			}
			var url = parentRequire.toUrl(id).replace(/^\s+/g, ''); // Trim possible leading white-space

			// see if a stylesheet element has already been added for this module
			var styles = query("head>style[url='" + url.split('?').shift() + "']"), style;
			if (styles.length === 0) {
				// create a new style element for this module and add it to the DOM
				style = domConstruct.create('style', {}, head);
				style.type = 'text/css';
				dhtml.setAttr(style, 'url', url.split('?').shift());
				dhtml.setAttr(style, 'loading', '');
			} else {
				style = styles[0];
				if (!dhtml.hasAttr(style, 'loading')) {
					load(style);
					return;
				}
			}

			parentRequire(['dojo/text!' + id], function (text) {
				// Check if we need to compile LESS client-side
				if (isLessUrl(id) && !has('dojo-combo-api')) {
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
				var pathParts = url.split('?').shift().split('/');
				require(['lesspp'], function (lesspp) {
					lesspp.render(fixUrlsInCssFile(url, lessText, true), {
						filename: pathParts.pop()
					}).then(function (res) {
						processCss(res.css);
					}, load.error);
				});
			}

			/**
			 * Injects CSS text into the stylesheet element, fixing relative URLs.
			 * @param  {String} cssText The CSS to inject.
			 */
			function processCss(cssText) {
				postcssPromise.always(function() {
					if (cssText && dojo.isString(cssText) && cssText.length) {
						cssText = fixUrlsInCssFile(url, cssText);
						if (postcssProcessor) {
							cssText = postcssProcessor.process(cssText, {safe: true});
						}
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
				});
			}
		},

		// export utility functions for unit tests
		__isLessUrl: isLessUrl,
		__isRelative: isRelative,
		__dequote: dequote,
		__normalize: normalize,
		__resolve: resolve,
		__fixUrlsInCssFile: fixUrlsInCssFile
	};
});
