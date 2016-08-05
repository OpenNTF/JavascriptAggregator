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
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
define(['require', 'dojo/has', 'dojo/_base/window', 'js/css', 'dojo/query', "dojo/NodeList-manipulate"], function(require, has, win, css, query) {

	var querySelector = "head>style[url='spec/styles/test.css']";
	describe('CSS plugin tests', function() {

		if (!win.doc.baseURI) {
			win.doc.baseURI = "http://localhost:1234/";
		}
		var basePath = win.doc.baseURI.split('?').shift();

		it('should test normalize utility function', function() {
			expect(css.__normalize('/')).toBe('/');
			expect(css.__normalize('')).toBe('');
			expect(css.__normalize('.')).toBe('');
			expect(css.__normalize('..')).toBe('..');
			expect(css.__normalize('./')).toBe('');
			expect(css.__normalize('../')).toBe('../');
			expect(css.__normalize('/.')).toBe('/');
			expect(css.__normalize('/..')).toBe('/..');
			expect(css.__normalize('/../')).toBe('/../');
			expect(css.__normalize('/./')).toBe('/');
			expect(css.__normalize('./.')).toBe('');
			expect(css.__normalize('../..')).toBe('../..');
			expect(css.__normalize('../../..')).toBe('../../..');
			expect(css.__normalize('/../../..')).toBe('/../../..');
			expect(css.__normalize('/../../../')).toBe('/../../../');
			expect(css.__normalize('/.././../.')).toBe('/../../');
		
			expect(css.__normalize('foo')).toBe('foo');
			expect(css.__normalize('./foo/.')).toBe('foo/');
			expect(css.__normalize('../foo')).toBe('../foo');
			expect(css.__normalize('foo/../../bar')).toBe('../bar');
			expect(css.__normalize('foo/bar/../baz')).toBe('foo/baz');
			expect(css.__normalize('foo/bar/../baz/../../..')).toBe('..');
			expect(css.__normalize('/foo/./bar/../baz')).toBe('/foo/baz');
			expect(css.__normalize('/foo/./bar/../baz/../..')).toBe('');
			expect(css.__normalize('/foo/./bar/../baz/../../..')).toBe('/..');
			expect(css.__normalize('/foo/./bar/../baz/../../?arg=value')).toBe('/?arg=value');
			expect(css.__normalize('./foo/./bar/../baz/../../?arg=value')).toBe('?arg=value');
		
			expect(css.__normalize('http://foo.com/../bar')).toBe('http://foo.com/../bar');
			expect(css.__normalize('http://foo.com')).toBe('http://foo.com');
			expect(css.__normalize('http://foo.com/')).toBe('http://foo.com/');
			expect(css.__normalize('http://foo.com/bar')).toBe('http://foo.com/bar');
			expect(css.__normalize('http://foo.com/./bar')).toBe('http://foo.com/bar');
			expect(css.__normalize('http://foo.com/./bar/')).toBe('http://foo.com/bar/');
			expect(css.__normalize('http://foo.com/./bar')).toBe('http://foo.com/bar');
			expect(css.__normalize('http://foo.com/../bar/')).toBe('http://foo.com/../bar/');
			expect(css.__normalize('http://foo.com/./../bar/')).toBe('http://foo.com/../bar/');
			expect(css.__normalize('http://foo.com/.././bar/')).toBe('http://foo.com/../bar/');
			expect(css.__normalize('http://foo.com/./')).toBe('http://foo.com/');
			expect(css.__normalize('http://foo.com/.')).toBe('http://foo.com/');
			expect(css.__normalize('http://foo.com/..')).toBe('http://foo.com/..');
			expect(css.__normalize('http://foo.com/../')).toBe('http://foo.com/../');
			expect(css.__normalize('http://foo.com:8080/bar/../baz?arg=value')).toBe('http://foo.com:8080/baz?arg=value');
			expect(css.__normalize('http://foo.com/bar/../baz/.?arg=value')).toBe('http://foo.com/baz/?arg=value');
			expect(css.__normalize('file:///foo/bar/../baz.ext')).toBe('file:///foo/baz.ext');
		});

		it('should test resolve utility function', function() {
			expect(css.__resolve("", "")).toBe("");
			expect(css.__resolve("", ".")).toBe("");
			expect(css.__resolve(".", "")).toBe("");
			expect(css.__resolve(".", ".")).toBe("");
			expect(css.__resolve(".", "bar")).toBe("bar");
			expect(css.__resolve("/", "bar")).toBe("/bar");
			expect(css.__resolve("foo", "bar")).toBe("foo/bar");
			expect(css.__resolve("foo", "/bar")).toBe("/bar");
			expect(css.__resolve("/foo", "bar")).toBe("/bar");
			expect(css.__resolve("/foo/", "bar")).toBe("/foo/bar");
			expect(css.__resolve("/foo/", "../bar")).toBe("/bar");
			expect(css.__resolve("foo/?arg1=val1", "../bar?arg2=val2")).toBe("bar?arg2=val2");
			expect(css.__resolve("foo", "../bar?arg=val")).toBe("bar?arg=val");
		
			expect(css.__resolve("http://foo.com", "./bar")).toBe("http://foo.com/bar");
			expect(css.__resolve("http://foo.com/", "./bar")).toBe("http://foo.com/bar");
			expect(css.__resolve("http://foo.com/", "bar")).toBe("http://foo.com/bar");
			expect(css.__resolve("http://foo.com/bar", "../baz")).toBe("http://foo.com/../baz");
			expect(css.__resolve("http://foo.com?arg1=val1", "./bar?arg2=val2")).toBe("http://foo.com/bar?arg2=val2");
		});

		it('should test dequote utility function', function() {
			expect(css.__dequote("'foo'")).toBe("foo");
			expect(css.__dequote("' foo '")).toBe(" foo ");
			expect(css.__dequote(" 'foo' ")).toBe("foo");
			expect(css.__dequote("\"foo\"")).toBe("foo");
			expect(css.__dequote(" \"foo\\bar \"")).toBe("foo/bar ");
		});

		it('should test fixUrlsInCssFile utility function', function() {
			expect(css.__fixUrlsInCssFile("foo/bar/themes/styles.css", "@import 'images/smile.png';"))
				.toBe("@import 'foo/bar/themes/images/smile.png';");
			expect(css.__fixUrlsInCssFile("foo/bar/themes/styles.less", "@import 'mixins/style_mixin.less';"))
				.toBe("@import 'foo/bar/themes/mixins/style_mixin.less';");
			expect(css.__fixUrlsInCssFile("foo/bar/themes/styles.less", "@import 'mixins/style_mixin.less';", true))
				.toBe("@import '" + basePath + "foo/bar/themes/mixins/style_mixin.less';");
			expect(css.__fixUrlsInCssFile("foo/bar/themes/styles.less", "@import 'mixins/style_mixin.css';", true))
				.toBe("@import 'mixins/style_mixin.css';");
			expect(css.__fixUrlsInCssFile("foo/bar/themes/styles.css?arg=val", "@import 'images/smile.png';"))
				.toBe("@import 'foo/bar/themes/images/smile.png?arg=val';");
			expect(css.__fixUrlsInCssFile("foo/bar/themes/styles.css", "@import 'images/smile.png?arg=val';"))
				.toBe("@import 'foo/bar/themes/images/smile.png?arg=val';");
			expect(css.__fixUrlsInCssFile("foo/bar/themes/styles.css?arg=val1", "@import 'images/smile.png?arg=val2';"))
				.toBe("@import 'foo/bar/themes/images/smile.png?arg=val2';");
			expect(css.__fixUrlsInCssFile("foo/bar/themes/styles.css?arg1=val1", "@import 'images/smile.png?arg2=val2';"))
				.toBe("@import 'foo/bar/themes/images/smile.png?arg1=val1&arg2=val2';");
		});
	});
	
	describe("PostCSS extension tests", function() {
		beforeEach(function() {
			has.add('postcss', true, true, true);
			has.add('dojo-combo-api', false, true, true);
			require.undef('js/css');
			dojoConfig.postcss = {
				plugins: [
				    [
				        'colorize',
				        function(colorize) { return colorize('red');}
				    ]
			    ]
			};
		});
		
		afterEach(function() {
			query(querySelector).remove();
			require.undef('js/css');
			has.add('postcss', false, true, true);
			has.add('dojo-combo-api', true, true, true);
			delete dojoConfig.postcssPlugins;
		});
		it("test color change", function() {
			var style;
			runs(function() {
				require(['js/css!./styles/test.css'], function(test) {
					style = test;
				});
			});
			waitsFor(function() {
				return style;
			});
			runs(function() {
				expect(style.innerHTML).toBe('.html {color: red;}');
			});
		});
	});
	
	describe("tests css-inject-api feature", function() {
		var undef, signal;
		beforeEach(function() {
			require.undef("js/css");
			css = undef;
			require.undef("spec/styles/test.css");
		});
		
		afterEach(function() {
			query(querySelector).remove();
			if (signal) {
				signal.remove();
				signal = undef;
			}
		});
		
		it("should not automatically inject the css when inject API is enabled", function() {
			has.add("css-inject-api", true, true, true);
			var requireArgs;
			runs(function() {
				require(['js/css!./styles/test.css'], function(test) {
					requireArgs = arguments;
				});
			});
			waitsFor(function() {
				return requireArgs;
			});
			runs(function() {
				var style = requireArgs[0];
				var url = style.getAttribute("url");
				// make sure the style is not in the DOM
				expect(query(querySelector).length).toBe(0);
				// now inject the style
				require('js/css').inject.apply(this, requireArgs);
				// verify the style was injected
				expect(query(querySelector).length).toBe(1);
				expect(query(querySelector)[0]).toBe(style);
			});
		});
		
		it("should automatically inject the css when inject API is enabled and installAutoInjectHooks has been called with global require", function() {
			has.add("css-inject-api", true, true, true);
			runs(function() {
				window.require(["js/css"], function(plugin) {
					css = plugin;
				});
			});
			waitsFor(function() {
				return css;
			});
			runs(function() {
				signal = css.installAutoInjectHooks();
				var style;
				expect(query(querySelector).length).toBe(0);
				runs(function() {
					window.require(['js/css!spec/styles/test.css'], function(test) {
						style = arguments[0];
					});
				});
				waitsFor(function() {
					return style;
				});
				runs(function() {
					// verify the style was injected
					expect(query(querySelector).length).toBe(1);
					expect(query(querySelector)[0]).toBe(style);
					// verify that require function has property values
					expect(require.toUrl("spec/styles/test.css")).toEqual("spec/styles/test.css");
					expect(define.amd.vendor).toBe("dojotoolkit.org");
				});
			});
		});

		it("should automatically inject the css when inject API is enabled and installAutoInjectHooks has been called with context require", function() {
			has.add("css-inject-api", true, true, true);
			var contextRequire;
			runs(function() {
				window.require(["js/css"], function(plugin) {
					css = plugin;
				});
			});
			waitsFor(function() {
				return css;
			});
			runs(function() {
				signal = css.installAutoInjectHooks();
				var style;
				expect(query(querySelector).length).toBe(0);
				window.require(["require"], function(contextRequire) {
					runs(function() {
						contextRequire(['js/css!spec/styles/test.css'], function() {
							style = arguments[0];
						});
					});
					waitsFor(function() {
						return style;
					}, "timeout waiting for contextRequire");
					runs(function() {
						// verify the style was injected
						expect(query(querySelector).length).toBe(1);
						expect(query(querySelector)[0]).toBe(style);
						// verify that context require function has property values
						expect(contextRequire.toUrl("./styles/test.css")).toEqual("./styles/test.css");
						expect(define.amd.vendor).toBe("dojotoolkit.org");
					});
				});
			});
		});
	});
	
});