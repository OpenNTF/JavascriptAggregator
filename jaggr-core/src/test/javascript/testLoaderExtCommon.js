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

describe("Test foldModuleNames", function() {
	it("tests basic path folding", function() {
		var names = ["foo/bar", "foo/baz/yyy", "foo/baz/xxx", "dir"];
		var depmap = {
			"foo/bar": {name:"foo/bar"},
			"foo/baz/yyy": {name:"foo/baz/yyy"},
			"foo/baz/xxx": {name:"foo/baz/xxx"},
			"dir" :{name:"dir"}
		};
		var result = foldModuleNames(names, depmap);
		expect(result).toEqual({foo:{bar:0,baz:{yyy:1,xxx:2}},dir:3});
	});
	
	it("tests path folding with plugin prefixes", function() {
		var names = ["foo/bar",  "foo/baz/yyy.txt", "foo/baz/xxx.txt"];
		var depmap = {
			"foo/bar": {name:"foo/bar"},
			"foo/baz/yyy.txt": {name:"foo/baz/yyy.txt", prefix:"combo/text"},
			"foo/baz/xxx.txt": {name:"foo/baz/xxx.txt", prefix:"abc"}
		};
		var result = foldModuleNames(names, depmap);
		console.log(result);
		var expected = {foo:{bar:0,baz:{'yyy.txt':[1, 0],'xxx.txt':[2, 1]}}};
		expected[pluginPrefixesPropName] = {'combo/text':0, abc:1},
		expect(result).toEqual(expected);
	});
	it("should throw invlalid module name", function() {
		var invalidChars = ['{', '}', ',', ':', '|', '<', '>', '*'];
		for (var i = 0; i < invalidChars.length; i++) {
			var badName = "foo/baz/yyy"+invalidChars[i];
			var names = ["foo/bar", badName, "foo/baz/xxx", "dir"];
			var depmap = {
				"foo/bar": {name:"foo/bar"},
				"foo/baz/xxx": {name:"foo/baz/xxx"},
				"dir" :{name:"dir"}
			};
			depmap[badName] = {name: badName};
			var error = null;
			try {
				var result = foldModuleNames(names, depmap);
			} catch (e) {
				error = e;
			}
			expect(error).not.toBe(null);
			expect(error.message).toEqual("Invalid module name: " + badName);
		}
		
	});
	
	describe("test encodeModules", function() {
		it("tests basic encoding and sorting", function() {
			var result = encodeModules({foo:{bar:0,baz:{yyy:1,xxx:2}},dir:3});
			expect(result).toEqual("(dir!3*foo!(bar!0*baz!(xxx!2*yyy!1)))");
		});
		
		it("tests encoding with module prefixes", function() {
			var obj = {foo:{bar:0,baz:{'yyy.txt':[1, 0],'xxx.txt':[2, 1]}}};
			obj[pluginPrefixesPropName] = {'combo/text':0, abc:1};
			var result = encodeModules(obj);
			expect(result).toEqual("("+pluginPrefixesPropName+"!(abc!1*combo/text!0)*foo!(bar!0*baz!(xxx.txt!2-1*yyy.txt!1-0)))");
		});
		
		it("tests special character handling", function() {
			var result = encodeModules({foo:{'(bar)':0,baz:{yyy:1,xxx:2}},'dir!':3});
			expect(result).toEqual("(dir|!3*foo!(<bar>!0*baz!(xxx!2*yyy!1)))");
		});
	});
});