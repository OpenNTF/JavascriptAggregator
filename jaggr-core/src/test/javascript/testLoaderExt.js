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


define(["dojo/_base/lang"], function(lang) {
	function processUrl(url, deps) {
		for (i = 0; i < urlProcessors.length; i++) {
			url = urlProcessors[i](url, deps);
		}
		return url;
	}

	var testDefineIntercept;
	(function() {	// make sure we are accessing global define
		var originalDefine = this.define;
		this.define = function() {
			(testDefineIntercept||originalDefine).apply(this, arguments);
		};
	})();
	
	var config = {
		has: function() { return false; },
		cache: {}
	};
	config.has.cache = {};
	
	describe("test getLocalesUrlProcessor", function() {
		beforeEach(function() {
			require.combo.serverExpandLayers = 0;
			dojo.locale = "en_US";
		});
		it("should add locale to url", function() {
			expect(processUrl("/server.com?count=1", [{name:"app/nls/foo", prefix:""}])).toContain("&locs=en_US");
			expect(processUrl("/server.com?count=1", [{name:"/nls/foo", prefix:""}])).toContain("&locs=en_US");
			dojo.config.extraLocale = "es";
			expect(processUrl("/server.com?count=1", [{name:"app/nls/foo", prefix:""}])).toContain("&locs=en_US,es");
			require(lang.mixin(require.combo, {plugins:{"combo/i18n":1}}));
			expect(processUrl("/server.com?count=1", [{name:"app/foo", prefix:"combo/i18n"}])).toContain("&locs=en_US,es");
			require.combo.serverExpandLayers = 1;
			expect(processUrl("/server.com?count=1", [{name:"app/foo", prefix:""}])).toContain("&locs=en_US,es");
		});
		it("should not add locale to url", function() {
			expect(processUrl("/server.com?count=1", [{name:"app/nlsfoo", prefix:""}])).not.toContain("&locs=");
			expect(processUrl("/server.com?count=1", [{name:"nls/foo", prefix:""}])).not.toContain("&locs=");
			expect(processUrl("/server.com?count=1", [{name:"appnls/foo", prefix:""}])).not.toContain("&locs=");
			expect(processUrl("/server.com?count=1", [{name:"app/nls", prefix:""}])).not.toContain("&locs=");
			dojo.locale = "";
			expect(processUrl("/server.com?count=1", [{name:"app/nls/foo", prefix:""}])).not.toContain("&locs=");
			require.combo.plugins = {"combo/i18n":1};
			// if url already has locs arg, then don't mess with it
			expect(processUrl("/server.com?count=1&locs=foo", [{name:"app/nls/foo", prefix:"combo/i18n"}])).not.toContain("en_US");
			expect(processUrl("/server.com?locs=foo&count=1", [{name:"app/nls/foo", prefix:"combo/i18n"}])).not.toContain("en_US");
		});
	});
	
	describe("test beginDefs/endDefs", function() {
		// Tests that module defines are processed in request order
		
		var originalServerExpandedLayers;
		var originalComboDisabled;
		beforeEach(function() {
			originalServerExpandedLayers = require.combo.serverExpandedLayers;
			originalComboDisabled = require.combo.disabled;
			require.combo.serverExpandLayers = true;
			require.combo.disabled = false;
			require(require.combo);
			require.combo.resetExcludeList();
		});
		
		afterEach(function() {
			testDefineIntercept = null;
			require.combo.serverExpandLayers = originalServerExpandedLayers;
			require.combo.disabled = originalComboDisabled;
			require(require.combo);
		});
		
		it("should not intercept define calls 1", function() {
			// single require/load.  Not define intercepts 
			var loadMids;
			require.combo.add("", "foo", "/foo.js", config);
			require.combo.add("", "bar", "/bar.js", config);
			require.combo.done(function(mids, url) {
				loadMids = mids;
			}, config);
			expect(loadMids).toEqual(['foo', 'bar']);
			require.combo.beginDefs(loadMids);
			var definedMids = [];
			testDefineIntercept = function(mid) {
				definedMids.push(mid);
			};
			define("foo");
			define("bar");
			require.combo.endDefs();
			expect(definedMids).toEqual(loadMids);
		});
		it("should not intercept define calls 2", function() {
			// module loads occur in require order
			// i.e. require1 -> require2
			var loadMids1, loadMids2;
			require.combo.add("", "foo", "/foo.js", config);
			require.combo.add("", "bar", "/bar.js", config);
			require.combo.done(function(mids, url) {
				loadMids1 = mids;
			}, config);
			require.combo.add("", "fee", "/fee.js", config);
			require.combo.add("", "fie", "/fie.js", config);
			require.combo.done(function(mids, url) {
				loadMids2 = mids;
			}, config);
			expect(loadMids1).toEqual(['foo', 'bar']);
			expect(loadMids2).toEqual(['fee', 'fie']);
			require.combo.beginDefs(loadMids1);
			var definedMids1 = [], definedMids2 = [];
			testDefineIntercept = function(mid) {
				definedMids1.push(mid);
			};
			define("foo");
			define("bar");
			require.combo.endDefs();
			expect(definedMids1).toEqual(loadMids1);
			
			require.combo.beginDefs(loadMids2);
			testDefineIntercept = function(mid) {
				definedMids2.push(mid);
			};
			define("fee");
			define("fie");
			require.combo.endDefs();
			expect(definedMids2).toEqual(loadMids2);
			
		});
		it("should intercept define calls", function() {
			// module loads do not occur in require order
			// i.e. require2 -> require1
			// defines are intercepted and played back in require order
			var loadMids1, loadMids2;
			require.combo.add("", "foo", "/foo.js", config);
			require.combo.add("", "bar", "/bar.js", config);
			require.combo.done(function(mids, url) {
				loadMids1 = mids;
			}, config);
			require.combo.add("", "fee", "/fee.js", config);
			require.combo.add("", "fie", "/fie.js", config);
			require.combo.done(function(mids, url) {
				loadMids2 = mids;
			}, config);
			expect(loadMids1).toEqual(['foo', 'bar']);
			expect(loadMids2).toEqual(['fee', 'fie']);
			require.combo.beginDefs(loadMids2);
			var definedMids = [];
			testDefineIntercept = function(mid) {
				definedMids.push(mid);
			};
			define("fee");
			define("fie");
			require.combo.endDefs();
			expect(definedMids).toEqual([]);
			
			require.combo.beginDefs(loadMids1);
			testDefineIntercept = function(mid) {
				definedMids.push(mid);
			};
			define("foo");
			define("bar");
			expect(definedMids).toEqual([]);
			require.combo.endDefs();
			expect(loadMids1).toEqual(["foo", "bar", "fee", "fie"]);
			expect(definedMids).toEqual(loadMids1);
			
		});

		it("should intercept some define calls but not others", function() {
			// module loads partially occur in require order
			// i.e. require3 -> require1 -> require2
			// require1 modules are defined when require1 load occurs, but 
			// require2 and require3 modules are defined together
			// when require2 load occurs
			var loadMids1, loadMids2, loadMids3;
			require.combo.add("", "foo", "/foo.js", config);
			require.combo.add("", "bar", "/bar.js", config);
			require.combo.done(function(mids, url) {
				loadMids1 = mids;
			}, config);
			require.combo.add("", "fee", "/fee.js", config);
			require.combo.add("", "fie", "/fie.js", config);
			require.combo.done(function(mids, url) {
				loadMids2 = mids;
			}, config);
			require.combo.add("", "foe", "/foe.js", config);
			require.combo.add("", "fum", "/fum.js", config);
			require.combo.done(function(mids, url) {
				loadMids3 = mids;
			}, config);
			expect(loadMids1).toEqual(['foo', 'bar']);
			expect(loadMids2).toEqual(['fee', 'fie']);
			expect(loadMids3).toEqual(['foe', 'fum']);
			require.combo.beginDefs(loadMids3);
			var definedMids = [];
			testDefineIntercept = function(mid) {
				definedMids.push(mid);
			};
			define("foe");
			define("fum");
			require.combo.endDefs();
			expect(definedMids).toEqual([]);
			
			require.combo.beginDefs(loadMids1);
			define("foo");
			define("bar");
			require.combo.endDefs();
			expect(definedMids).toEqual(["foo", "bar"]);
			expect(loadMids1).toEqual(definedMids);
			definedMids = [];

			require.combo.beginDefs(loadMids2);
			define("fee");
			define("fie");
			require.combo.endDefs();
			expect(definedMids).toEqual(["fee", "fie", "foe", "fum"]);
			expect(loadMids2).toEqual(definedMids);

		});
	});
});