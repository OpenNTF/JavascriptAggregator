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
define([ 'dojo/_base/window', 'js/css'], function(win, css) {

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
});