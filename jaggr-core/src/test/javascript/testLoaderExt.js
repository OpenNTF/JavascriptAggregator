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
	describe("test getLocalesUrlProcessor", function() {
		beforeEach(function() {
			dojo.locale = "en_US";
		});
		it("should add locale to url", function() {
			expect(processUrl("/server.com?count=1", [{name:"app/nls/foo", prefix:""}])).toContain("&locs=en_US");
			expect(processUrl("/server.com?count=1", [{name:"/nls/foo", prefix:""}])).toContain("&locs=en_US");
			dojo.config.extraLocale = "es";
			expect(processUrl("/server.com?count=1", [{name:"app/nls/foo", prefix:""}])).toContain("&locs=en_US,es");
			require(lang.mixin(require.combo, {plugins:{"combo/i18n":1}}));
			expect(processUrl("/server.com?count=1", [{name:"app/foo", prefix:"combo/i18n"}])).toContain("&locs=en_US,es");
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
});