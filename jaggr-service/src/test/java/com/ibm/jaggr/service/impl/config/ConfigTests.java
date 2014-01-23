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

package com.ibm.jaggr.service.impl.config;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.PathUtil;
import com.ibm.jaggr.service.test.MockAggregatorWrapper;
import com.ibm.jaggr.service.test.TestUtils;

import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigTests {

	File tmpFile = null;
	URI tmpDir = null;
	IAggregator mockAggregator;

	@Before
	public void setup() throws Exception {
		tmpFile = Files.createTempDir();
		tmpDir = tmpFile.toURI();
		mockAggregator = TestUtils.createMockAggregator();
		EasyMock.replay(mockAggregator);

	}
	@After
	public void tearDown() throws Exception {
		if (tmpFile != null) {
			TestUtils.deleteRecursively(tmpFile);
			tmpFile = null;
		}
	}

	@Test
	public void normalizePaths() throws IOException, URISyntaxException {
		String ref = "aaa/bbb/ccc/ddd";
		String[] paths = new String[]{
				"./yyy",
				".././zzz",
				"../../..//xxx",
				"../../../../yyy",
				"ddd/eee",
				"plugin!aaa/bbb",
				"plugin!./aaa/bbb",
				"plugin!../aaa/bbb",
				"./plugin!aaa/bbb",
				"../plugin!aaa/bbb",
				"../../has!dojo-firebug?../../with_firebug:../../withot_firebug",
				"abc/123/.",
				"dojo/has!vml?dojo/text!./templates/spinner-vml.html:dojo/text!./templates/spinner-canvas.html",
		};
		String[] result = PathUtil.normalizePaths(ref, paths);
		Assert.assertEquals(result[0], "aaa/bbb/ccc/ddd/yyy");
		Assert.assertEquals(result[1], "aaa/bbb/ccc/zzz");
		Assert.assertEquals(result[2], "aaa/xxx");
		Assert.assertEquals(result[3], "yyy");
		Assert.assertEquals(result[4], "ddd/eee");
		Assert.assertEquals(result[5], "plugin!aaa/bbb");
		Assert.assertEquals(result[6], "plugin!aaa/bbb/ccc/ddd/aaa/bbb");
		Assert.assertEquals(result[7], "plugin!aaa/bbb/ccc/aaa/bbb");
		Assert.assertEquals(result[8], "aaa/bbb/ccc/ddd/plugin!aaa/bbb");
		Assert.assertEquals(result[9], "aaa/bbb/ccc/plugin!aaa/bbb");
		Assert.assertEquals(result[10], "aaa/bbb/has!dojo-firebug?aaa/bbb/with_firebug:aaa/bbb/withot_firebug");
		Assert.assertEquals(result[11], "abc/123");
		Assert.assertEquals(result[12], "dojo/has!vml?dojo/text!aaa/bbb/ccc/ddd/templates/spinner-vml.html:dojo/text!aaa/bbb/ccc/ddd/templates/spinner-canvas.html");
		for (int i = 0; i < paths.length; i++) {
			System.out.println(paths[i] + " -> " + result[i]);
		}

		boolean exceptionCaught = false;
		try {
			result = PathUtil.normalizePaths(ref, new String[]{"/../eee"});
		} catch (IllegalArgumentException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);

		exceptionCaught = false;
		try {
			result = PathUtil.normalizePaths(ref, new String[]{"../../../../../eee"});
		} catch (IllegalArgumentException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);

		exceptionCaught = false;
		try {
			result = PathUtil.normalizePaths(ref, new String[]{"plugin!/aaa/bbb"});
		} catch (IllegalArgumentException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);

		exceptionCaught = false;
		try {
			result = PathUtil.normalizePaths(ref, new String[]{"/plugin!aaa/bbb"});
		} catch (IllegalArgumentException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);

		ref = "/aaa/bbb";
		paths = new String[] {
				"ccc",
				"../xxx",
				"../../zzz"
		};
		result = PathUtil.normalizePaths(ref, paths);
		Assert.assertEquals(result[0], "ccc");
		Assert.assertEquals(result[1], "/aaa/xxx");
		Assert.assertEquals(result[2], "/zzz");
		for (int i = 0; i < paths.length; i++) {
			System.out.println(paths[i] + " -> " + result[i]);
		}

		paths = new String[]{paths[0], paths[1], paths[2], "../../../eee"};
		exceptionCaught = false;
		try {
			result = PathUtil.normalizePaths(ref, paths);
		} catch (IllegalArgumentException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);

	}

	@Test
	public void testVariableSubstitution() throws Exception {
		mockAggregator = new MockAggregatorWrapper() {
			@Override public String substituteProps(String str, IAggregator.SubstitutionTransformer transformer) {
				str = str.replace("${REPLACE_THIS}", "file:/c:/substdir/");
				if (transformer != null) {
					str = transformer.transform("REPLACE_THIS", str);
				}
				return str;
			}
		};
		String config = "{paths:{subst:'${REPLACE_THIS}/resources'}}";
		IConfig cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		IConfig.Location loc = cfg.getPaths().get("subst");
		Assert.assertEquals("file:/c:/substdir/resources", loc.getPrimary().toString());
		Assert.assertNull(loc.getOverride());

		config = "{paths:{subst1:'${REPLACE_THIS}/foo', subst2:'${REPLACE_THIS}/bar' }}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getPaths().get("subst1");
		Assert.assertEquals("file:/c:/substdir/foo", loc.getPrimary().toString());
		loc = cfg.getPaths().get("subst2");
		Assert.assertEquals("file:/c:/substdir/bar", loc.getPrimary().toString());
	}

	@Test
	public void testJsVars() throws Exception {
		// Test to make sure the shared scope options and initParams variables are working
		List<InitParams.InitParam> initParams = new LinkedList<InitParams.InitParam>();
		initParams.add(new InitParams.InitParam("param1", "param1Value1"));
		initParams.add(new InitParams.InitParam("param1", "param1Value2"));
		initParams.add(new InitParams.InitParam("param2", "param2Value"));
		mockAggregator = TestUtils.createMockAggregator(null, null, initParams);

		IPlatformServices mockPlatformServices = EasyMock.createMock(IPlatformServices.class);
		final Dictionary<String, String> dict = new Hashtable<String, String>();
		dict.put("foo", "foobar");
		EasyMock.expect(mockPlatformServices.getHeaders()).andAnswer(new IAnswer<Dictionary<String, String>>() {
			public Dictionary<String, String> answer() throws Throwable {
				return dict;
			}
		}).anyTimes();
		EasyMock.replay(mockPlatformServices);
		EasyMock.expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();
		EasyMock.replay(mockAggregator);
		mockAggregator.getOptions().setOption("foo", "bar");
		String config = "{cacheBust:(function(){console.log(options.foo);console.info(initParams.param1[0]);console.warn(initParams.param1[1]);console.error(initParams.param2[0]);return headers.foo;})()}";
		final TestConsoleLogger logger = new TestConsoleLogger();
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config, true) {
			@Override
			protected ConfigImpl.Console newConsole() {
				return logger;
			}
		};
		System.out.println(cfg.toString());
		List<String> logged = logger.getLogged();
		Assert.assertEquals(cfg.getCacheBust(), "foobar");
		Assert.assertEquals("log: bar", logged.get(0));
		Assert.assertEquals("info: param1Value1", logged.get(1));
		Assert.assertEquals("warn: param1Value2", logged.get(2));
		Assert.assertEquals("error: param2Value", logged.get(3));
	}

	@Test
	public void testAliasResolver() throws Exception {
		Features features = new Features();
		Set<String> dependentFeatures = new HashSet<String>();

		// Test simple string matcher resolver
		String config = "{aliases:[['foo/test', 'bar/test']]}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		String result = cfg.resolveAliases("foo/test", features, dependentFeatures, null);
		Assert.assertEquals("bar/test", result);

		// Test regular expression matcher with string replacement
		config = "{aliases:[[/\\/bar\\//, '/foo/']]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAliases("p1/bar/p2/bar/test", features, dependentFeatures, null);
		Assert.assertEquals("p1/foo/p2/foo/test", result);

		// Test regular expression matcher with replacement function conditioned on feature test
		config = "{aliases:[[/\\/foo\\//, function(s){return '/'+has('test')+'/'}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		features.put("test", true);
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/true/p2", result);
		Assert.assertEquals(1, dependentFeatures.size());
		Assert.assertEquals("test", dependentFeatures.iterator().next());

		features.put("test", false);
		dependentFeatures.clear();
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/false/p2", result);
		Assert.assertEquals(1, dependentFeatures.size());
		Assert.assertEquals("test", dependentFeatures.iterator().next());

		features.remove("test");
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/undefined/p2", result);
		Assert.assertEquals(1, dependentFeatures.size());
		Assert.assertEquals("test", dependentFeatures.iterator().next());

		// Test regular expression with string based group replacement
		dependentFeatures.clear();
		config = "{aliases:[[/^(.*)\\/foo\\/(.*)$/, '$2/bar/$1']]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p2/bar/p1", result);
		Assert.assertEquals(0, dependentFeatures.size());

		// Test regular expression with function based group replacement
		config = "{aliases:[[/^(.*)\\/foo\\/(.*)$/, function(a,b,c){return c+'/bar/'+b;}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p2/bar/p1", result);
		Assert.assertEquals(0, dependentFeatures.size());

		// Test regular expression with replacement function conditioned on options value
		IOptions options = mockAggregator.getOptions();
		options.setOption("developmentMode", "false");
		config = "{aliases:[[/^(.*)\\/foo\\/(.*)$/, function(a,b,c){return options.developmentMode == 'true' ? (c+'/bar/'+b) : (b+/bar/+c);}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/bar/p2", result);
		options.setOption("developmentMode", "true");
		cfg.optionsUpdated(options, 2);
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p2/bar/p1", result);

		// Test regular expression flags
		config = "{aliases:[[/^(.*)\\/Foo\\/(.*)$/, function(a,b,c){return b+'/bar/'+c;}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/foo/p2", result);
		config = "{aliases:[[/^(.*)\\/Foo\\/(.*)$/i, function(a,b,c){return b+'/bar/'+c;}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/bar/p2", result);

		// Test that alias resolver function can call another function defined in closure scope.
		config = "(function(){function fn(a,b,c){return b+'/'+a+'/'+c;} return {aliases:[[/^(.*)\\/Foo\\/(.*)$/, function(a,b,c){return fn('bar',b,c);}]]}})();";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAliases("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/foo/p2", result);

	}

	@Test
	public void testGetBase() throws Exception {
		// Test path without override
		String config = "{baseUrl:'.'}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		IConfig.Location loc = cfg.getBase();
		Assert.assertEquals(tmpDir, loc.getPrimary());
		Assert.assertNull(loc.getOverride());

		config = "{baseUrl:'WebContent'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getBase();
		Assert.assertEquals(tmpDir.resolve("WebContent/"), loc.getPrimary());
		Assert.assertNull(loc.getOverride());

		config = "{baseUrl:['namedbundleresource://com.ibm.config.test/resources']}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getBase();
		Assert.assertEquals("namedbundleresource://com.ibm.config.test/resources/", loc.getPrimary().toString());
		Assert.assertNull(loc.getOverride());

		config = "{baseUrl:['WebContent/', 'file:/e:/overrides']}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getBase();
		Assert.assertEquals(tmpDir.resolve("WebContent/"), loc.getPrimary());
		Assert.assertEquals("file:/e:/overrides/", loc.getOverride().toString());

		config = "{baseUrl:['../primary', '.']}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getBase();
		Assert.assertEquals(tmpDir.resolve("../primary/"), loc.getPrimary());
		Assert.assertEquals(tmpDir, loc.getOverride());

		config = "{baseUrl:['WebContent/', '../override/', 'extra']}";
		boolean exceptionCaught = false;
		try {
			cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		} catch (IllegalArgumentException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);
	}

	@Test
	public void testGetPaths() throws Exception {
		// Test path without override
		String config = "{paths:{foo:'fooPath'}}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		IConfig.Location loc = cfg.getPaths().get("foo");
		Assert.assertEquals(tmpDir.resolve("fooPath"), loc.getPrimary());
		Assert.assertNull(loc.getOverride());

		config = "{paths:{abspath:'file:/c:/temp/resources'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getPaths().get("abspath");
		Assert.assertEquals("file:/c:/temp/resources", loc.getPrimary().toString());
		Assert.assertNull(loc.getOverride());

		config = "{paths:{foo:['fooPath', 'barPath']}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getPaths().get("foo");
		Assert.assertEquals(tmpDir.resolve("fooPath"), loc.getPrimary());
		Assert.assertEquals(tmpDir.resolve("barPath"), loc.getOverride());

		config = "{paths:{foo:['fooPath', 'barPath', 'extraPath']}}";
		boolean exceptionThrown = false;
		try {
			cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		} catch (IllegalArgumentException e) {
			exceptionThrown = true;
		}
		Assert.assertTrue(exceptionThrown);

		// test resolving paths against base
		config = "{baseUrl:'file:/c:/primary',paths:{foo:'fooPath'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getPaths().get("foo");
		Assert.assertEquals("file:/c:/primary/fooPath", loc.getPrimary().toString());
		Assert.assertNull(loc.getOverride());


		config = "{baseUrl:'file:/c:/primary',paths:{foo:['fooPath', 'fooPathOverride']}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getPaths().get("foo");
		Assert.assertEquals("file:/c:/primary/fooPath", loc.getPrimary().toString());
		Assert.assertEquals("file:/c:/primary/fooPathOverride", loc.getOverride().toString());

		config = "{baseUrl:['file:/c:/primary', 'file:/c:/override'],paths:{foo:['fooPath'], bar:['barPath', 'barPathOverride']}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		loc = cfg.getPaths().get("foo");
		Assert.assertEquals("file:/c:/primary/fooPath", loc.getPrimary().toString());
		Assert.assertEquals("file:/c:/override/fooPath", loc.getOverride().toString());
		loc = cfg.getPaths().get("bar");
		Assert.assertEquals("file:/c:/primary/barPath", loc.getPrimary().toString());
		Assert.assertEquals("file:/c:/override/barPathOverride", loc.getOverride().toString());
	}

	@Test
	public void testGetPackages() throws Exception {
		// Test path without override
		String config = "{packages:[{name:'foo', location:'fooPkgLoc'}]}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		IConfig.IPackage pkg = cfg.getPackages().get("foo");
		Assert.assertEquals("foo", pkg.getName());
		Assert.assertEquals("foo/main", pkg.getMain());
		IConfig.Location loc = pkg.getLocation();
		Assert.assertEquals(tmpDir.resolve("fooPkgLoc/"), loc.getPrimary());
		Assert.assertNull(loc.getOverride());

		config = "{packages:[{name:'foo', location:'fooPkgLoc', main:'fooMain'}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		pkg = cfg.getPackages().get("foo");
		Assert.assertEquals("fooMain", pkg.getMain());

		config = "{packages:[{name:'foo', location:'fooPkgLoc', main:'./fooMain'}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		pkg = cfg.getPackages().get("foo");
		Assert.assertEquals("foo/fooMain", pkg.getMain());

		config = "{packages:[{name:'foo', location:['fooPkgLoc', 'fooOverride']}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		pkg = cfg.getPackages().get("foo");
		loc = pkg.getLocation();
		Assert.assertEquals(tmpDir.resolve("fooPkgLoc/"), loc.getPrimary());
		Assert.assertEquals(tmpDir.resolve("fooOverride/"), loc.getOverride());

		config = "{packages:[{name:'foo', location:['fooPkgLoc', 'fooOverride', 'extra']}]}";
		boolean exceptionThrown = false;
		try {
			cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		} catch (IllegalArgumentException e) {
			exceptionThrown = true;
		}
		Assert.assertTrue(exceptionThrown);

		// test resolving package locations against base
		config = "{baseUrl:'file:/c:/primary', packages:[{name:'foo', location:['fooPath']}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		pkg = cfg.getPackages().get("foo");
		loc = pkg.getLocation();
		Assert.assertEquals("file:/c:/primary/fooPath/", loc.getPrimary().toString());
		Assert.assertNull(loc.getOverride());

		config = "{baseUrl:'file:/c:/primary', packages:[{name:'foo', location:['fooPath', 'barPath']}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		pkg = cfg.getPackages().get("foo");
		loc = pkg.getLocation();
		Assert.assertEquals("file:/c:/primary/fooPath/", loc.getPrimary().toString());
		Assert.assertEquals("file:/c:/primary/barPath/", loc.getOverride().toString());

		config = "{baseUrl:['file:/c:/primary', 'file:/c:/override'], packages:[{name:'foo', location:['fooPath\']}, {name:'bar', location:['barPath', 'barPathOverride\']}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		pkg = cfg.getPackages().get("foo");
		loc = pkg.getLocation();
		Assert.assertEquals("file:/c:/primary/fooPath/", loc.getPrimary().toString());
		Assert.assertEquals("file:/c:/override/fooPath/", loc.getOverride().toString());
		pkg = cfg.getPackages().get("bar");
		loc = pkg.getLocation();
		Assert.assertEquals("file:/c:/primary/barPath/", loc.getPrimary().toString());
		Assert.assertEquals("file:/c:/override/barPathOverride/", loc.getOverride().toString());
	}

	@Test
	public void testLocateModuleResource() throws Exception {

		// Test path without override
		String config = "{paths:{foo:'fooPath'}}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		URI uri = cfg.locateModuleResource("fooPath/foo");
		Assert.assertEquals(tmpDir.resolve("fooPath/foo.js"), uri);


		config = "{baseUrl:['.', '" + tmpDir.resolve("override") + "']}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		uri = cfg.locateModuleResource("fooPath/foo");
		Assert.assertEquals(tmpDir.resolve("fooPath/foo.js"), uri);
		TestUtils.createTestFile(new File(tmpFile, "override/fooPath"), "foo.js", "/**/");
		try {
			uri = cfg.locateModuleResource("fooPath/foo");
			Assert.assertEquals(tmpDir.resolve("override/fooPath/foo.js"), uri);
			config = "{paths:{'foo':['fooPath/foo', 'override/fooPath/foo']}}";

			cfg = new ConfigImpl(mockAggregator, tmpDir, config);
			uri = cfg.locateModuleResource("foo");
			Assert.assertEquals(tmpDir.resolve("override/fooPath/foo.js"), uri);
		} finally {
			TestUtils.deleteRecursively(tmpFile);
		}
		uri = cfg.locateModuleResource("foo");
		Assert.assertEquals(tmpDir.resolve("fooPath/foo.js"), uri);

		// test that path definitions override package locations
		config = "{packages:[{name:'foo', location:'fooPath1'}], paths:{foo:'fooPath2'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		uri = cfg.locateModuleResource("foo/bar");
		Assert.assertEquals(tmpDir.resolve("fooPath2/bar.js"), uri);
		uri = cfg.locateModuleResource("foo");
		Assert.assertEquals(tmpDir.resolve("fooPath2.js"), uri);

		config = "{packages:[{name:'foo', location:'fooPath'}], paths:{bar:'barPath'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		uri = cfg.locateModuleResource("foo/bar");
		Assert.assertEquals(tmpDir.resolve("fooPath/bar.js"), uri);
		uri = cfg.locateModuleResource("foo");
		Assert.assertEquals(tmpDir.resolve("fooPath/main.js"), uri);
		uri = cfg.locateModuleResource("bar");
		Assert.assertEquals(tmpDir.resolve("barPath.js"), uri);
		uri = cfg.locateModuleResource("bar/bar");
		Assert.assertEquals(tmpDir.resolve("barPath/bar.js"), uri);

		config = "{packages:[{name:'foo', location:['fooPath', 'fooPathOverride']}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		uri = cfg.locateModuleResource("foo/bar");
		Assert.assertEquals(tmpDir.resolve("fooPath/bar.js"), uri);
		uri = cfg.locateModuleResource("foo");
		Assert.assertEquals(tmpDir.resolve("fooPath/main.js"), uri);
		TestUtils.createTestFile(new File(tmpFile, "fooPathOverride"), "bar.js", "/**/");
		TestUtils.createTestFile(new File(tmpFile, "fooPathOverride"), "main.js", "/**/");
		uri = cfg.locateModuleResource("foo/bar");
		Assert.assertEquals(tmpDir.resolve("fooPathOverride/bar.js"), uri);
		uri = cfg.locateModuleResource("foo");
		Assert.assertEquals(tmpDir.resolve("fooPathOverride/main.js"), uri);

		config = "{baseUrl:['.', 'overrides'], packages:[{name:'foo', location:['fooPath', 'fooPathOverride']}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		TestUtils.createTestFile(new File(tmpFile, "overrides/fooPathOverride"), "bar.js", "/**/");
		TestUtils.createTestFile(new File(tmpFile, "overrides/fooPathOverride"), "main.js", "/**/");
		uri = cfg.locateModuleResource("foo/bar");
		Assert.assertEquals(tmpDir.resolve("overrides/fooPathOverride/bar.js"), uri);
		uri = cfg.locateModuleResource("foo");
		Assert.assertEquals(tmpDir.resolve("overrides/fooPathOverride/main.js"), uri);
	}

	@Test
	public void testIsDepsIncludeBaseUrl() throws Exception {
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertFalse(cfg.isDepsIncludeBaseUrl());

		config = "{depsIncludeBaseUrl:true}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertTrue(cfg.isDepsIncludeBaseUrl());

		config = "{depsIncludeBaseUrl:'true'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertTrue(cfg.isDepsIncludeBaseUrl());

		config = "{depsIncludeBaseUrl:false}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertFalse(cfg.isDepsIncludeBaseUrl());

		config = "{depsIncludeBaseUrl:'foo'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertFalse(cfg.isDepsIncludeBaseUrl());
	}

	@Test
	public void testIsCoerceUndefinedToFalse() throws Exception {
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertFalse(cfg.isCoerceUndefinedToFalse());

		config = "{coerceUndefinedToFalse:true}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertTrue(cfg.isCoerceUndefinedToFalse());

		config = "{coerceUndefinedToFalse:'true'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertTrue(cfg.isCoerceUndefinedToFalse());

		config = "{coerceUndefinedToFalse:'foo'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertFalse(cfg.isCoerceUndefinedToFalse());
	}

	@Test
	public void testGetExpires() throws Exception {
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0, cfg.getExpires());

		config = "{expires:1000}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(1000, cfg.getExpires());

		config = "{expires:'1000'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(1000, cfg.getExpires());

		config = "{expires:'foo'}";
		boolean exceptionCaught = false;
		try {
			cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		} catch (IllegalArgumentException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);
	}

	@Test
	public void testGetDeps() throws Exception {
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		List<String> deps = cfg.getDeps();
		Assert.assertEquals(0, deps.size());

		config = "{deps:['foo', 'bar']}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		deps = cfg.getDeps();
		Assert.assertEquals(2, deps.size());
		Assert.assertEquals("foo", deps.get(0));
		Assert.assertEquals("bar", deps.get(1));

		config = "{deps:{foo:'bar'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0,  cfg.getDeps().size());
	}

	@Test
	public void testGetNotice() throws Exception {
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertNull(cfg.getNotice());

		String copywrite = "/* Copyright IBM Corporation */";
		URI noticeUri = tmpDir.resolve("notice.txt");
		Writer fileWriter = new FileWriter(new File(noticeUri));
		fileWriter.append(copywrite);
		fileWriter.close();

		config = "{notice:'" + noticeUri.toString() + "'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(copywrite, cfg.getNotice());

		// test relative URI
		config = "{notice:'notice.txt'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(copywrite, cfg.getNotice());

		// test file not found exception
		config = "{notice:'foo'}";
		boolean exceptionCaught = false;
		try {
			cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		} catch (IOException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);
	}

	@Test
	public void testGetCacheBust() throws Exception {
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertNull(cfg.getCacheBust());

		config = "{cacheBust:'123'}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals("123", cfg.getCacheBust());
	}

	@Test
	public void testGetPackageLocations() throws Exception {
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0, cfg.getPackageLocations().size());

		config = "{packages:[{name:'foo', location:'fooloc'},{name:'bar', location:['primary', 'override']}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Map<String, IConfig.Location> map = cfg.getPackageLocations();
		Assert.assertEquals(2, cfg.getPackageLocations().size());
		Assert.assertEquals(tmpDir.resolve("fooloc/"), map.get("foo").getPrimary());
		Assert.assertNull(map.get("foo").getOverride());
		Assert.assertEquals(tmpDir.resolve("primary/"), map.get("bar").getPrimary());
		Assert.assertEquals(tmpDir.resolve("override/"), map.get("bar").getOverride());
	}

	@Test
	public void testLastModified() throws Exception {

		String config = "{paths:{foo:'fooloc'}}";
		URI cfgUri = tmpDir.resolve("config.js");
		File cfgFile = new File(cfgUri);
		Writer fileWriter = new FileWriter(new File(cfgUri));
		fileWriter.append(config);
		fileWriter.close();
		long today = cfgFile.lastModified();
		Assert.assertTrue(cfgFile.setLastModified(today - 24 * 60 * 60 * 1000));
		long yesterday = cfgFile.lastModified();

		List<InitParams.InitParam> initParams = new LinkedList<InitParams.InitParam>();
		initParams.add(new InitParams.InitParam(InitParams.CONFIG_INITPARAM, cfgUri.toString()));
		mockAggregator = TestUtils.createMockAggregator(null, null, initParams);
		EasyMock.replay(mockAggregator);
		ConfigImpl cfg = new ConfigImpl(mockAggregator);
		Assert.assertEquals(yesterday, cfg.lastModified());

		Assert.assertTrue(cfgFile.setLastModified(today));
		cfg = new ConfigImpl(mockAggregator);
		Assert.assertEquals(today, cfg.lastModified());
	}

	@Test
	public void testGetConfigUri() throws Exception {
		String config = "{paths:{foo:'fooloc'}}";
		URI cfgUri = tmpDir.resolve("config.js");
		Writer fileWriter = new FileWriter(new File(cfgUri));
		fileWriter.append(config);
		fileWriter.close();

		List<InitParams.InitParam> initParams = new LinkedList<InitParams.InitParam>();
		initParams.add(new InitParams.InitParam(InitParams.CONFIG_INITPARAM, cfgUri.toString()));
		mockAggregator = TestUtils.createMockAggregator(null, null, initParams);
		EasyMock.replay(mockAggregator);
		ConfigImpl cfg = new ConfigImpl(mockAggregator);
		Assert.assertEquals(cfgUri, cfg.getConfigUri());
	}

	// Make sure that if the uri to the config is relative, then getConfigUri returns
	// a namedbundleresource uri
	@Test
	public void testGetConfigUriRelative() throws Exception {
		String config = "{paths:{foo:'fooloc'}}";
		URI cfgUri = tmpDir.resolve("config.js");
		Writer fileWriter = new FileWriter(new File(cfgUri));
		fileWriter.append(config);
		fileWriter.close();

		List<InitParams.InitParam> initParams = new LinkedList<InitParams.InitParam>();
		initParams.add(new InitParams.InitParam(InitParams.CONFIG_INITPARAM, "config.js"));
		mockAggregator = TestUtils.createMockAggregator(null, new File(tmpDir), initParams);

		IPlatformServices mockPlatformServices = EasyMock.createMock(IPlatformServices.class);
		EasyMock.expect(mockPlatformServices.getAppContextURI()).andReturn(new URI("namedbundleresource://org.mock.name/")).anyTimes();;
		EasyMock.replay(mockPlatformServices);
		EasyMock.expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();
		EasyMock.replay(mockAggregator);
		ConfigImpl cfg = new ConfigImpl(mockAggregator, true);
		Assert.assertEquals(new URI("namedbundleresource://org.mock.name/config.js"), cfg.getConfigUri());
	}

	@Test
	public void testGetRawConfig() throws Exception {
		String config = "(function(){ return{ paths: {'foo': 'fooloc'}};})()";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Object rawCfg = cfg.getRawConfig();
		Context.enter();
		String str = Context.toString(rawCfg);
		Context.exit();
		Assert.assertEquals("{paths:{foo:\"fooloc\"}}", str);
	}

	@Test
	public void testToString() throws Exception {
		String config = "(function(){ return{ paths: {'foo': 'fooloc'}};})()";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Object rawCfg = cfg.getRawConfig();
		Context.enter();
		String str = Context.toString(rawCfg);
		Context.exit();
		Assert.assertEquals(str, cfg.toString());

	}

	@Test
	public void testGetTextPluginDelegators() throws Exception {
		String config = "{textPluginDelegators:[\"foo/bar\", \"t2\"]}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/bar", "t2"})), cfg.getTextPluginDelegators());

		config = "{textPluginDelegators:\"t1\"}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0, cfg.getTextPluginDelegators().size());

		config = "{textPluginDelegators:[]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0, cfg.getTextPluginDelegators().size());

		config = "{}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0, cfg.getTextPluginDelegators().size());
	}

	@Test
	public void testGetJsPluginDelegators() throws Exception {
		String config = "{jsPluginDelegators:[\"foo/bar\", \"t2\"]}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/bar", "t2"})), cfg.getJsPluginDelegators());

		config = "{jsPluginDelegators:\"t1\"}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0, cfg.getJsPluginDelegators().size());

		config = "{jsPluginDelegators:[]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0, cfg.getJsPluginDelegators().size());

		config = "{}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals(0, cfg.getJsPluginDelegators().size());
	}

	@Test
	public void testResolve() throws Exception {
		Features features = new Features();
		Set<String> dependentFeatures = new HashSet<String>();
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals("foo", cfg.resolve("foo", features, dependentFeatures, null, false));
		Assert.assertEquals("has!xxx?foo", cfg.resolve("has!xxx?foo", features, dependentFeatures, null, false));
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"xxx"})), dependentFeatures);
		features.put("xxx", true);
		Assert.assertEquals("foo", cfg.resolve("has!xxx?foo", features, dependentFeatures, null, false));
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"xxx"})), dependentFeatures);
		features.put("", false);
		Assert.assertEquals("foo", cfg.resolve("has!xxx?foo", features, dependentFeatures, null, false));
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"xxx"})), dependentFeatures);

		features = new Features();
		dependentFeatures = new HashSet<String>();
		config = "{aliases:[['foo', 'bar']]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals("foo", cfg.resolve("foo", features, dependentFeatures, null, false));
		Assert.assertEquals("bar", cfg.resolve("foo", features, dependentFeatures, null, true));
		Assert.assertEquals(0, dependentFeatures.size());
		Assert.assertEquals("has!xxx?bar:foobar", cfg.resolve("has!xxx?foo:foobar", features, dependentFeatures, null, true));
		Assert.assertEquals(1, dependentFeatures.size());
		Assert.assertEquals("xxx", dependentFeatures.iterator().next());
		features.put("xxx", true);
		Assert.assertEquals("bar", cfg.resolve("has!xxx?foo:foobar", features, dependentFeatures, null, true));
		Assert.assertEquals(1, dependentFeatures.size());
		features.put("xxx", false);
		Assert.assertEquals("foobar", cfg.resolve("has!xxx?foo:foobar", features, dependentFeatures, null, false));

		dependentFeatures.clear();
		config = "{aliases:[['foo', 'has!bar?aaa:bbb']], packages:[{name:'aaa', location:'aaaloc'}, {name:'bbb', location:'bbbloc', main:'mainloc'}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		Assert.assertEquals("has!bar?aaa:bbb", cfg.resolve("foo", features, dependentFeatures, null, true));
		features.put("bar", true);
		Assert.assertEquals("aaa/main", cfg.resolve("foo", features, dependentFeatures, null, true));
		Assert.assertEquals(1, dependentFeatures.size());
		Assert.assertEquals("bar", dependentFeatures.iterator().next());
		features.put("bar", false);
		Assert.assertEquals("mainloc", cfg.resolve("foo", features, dependentFeatures, null, true));
		features.put("xxx", true);
		Assert.assertEquals("mainloc", cfg.resolve("has!xxx?foo", features, dependentFeatures, null, true));

	}

	public static class TestConsoleLogger extends ConfigImpl.Console {
		List<String> logged = new ArrayList<String>();
		List<String> getLogged() {
			return logged;
		}
		@Override
		public void log(String msg) {
			super.log(msg);
			logged.add("log: " + msg);
		}
		@Override
		public void info(String msg) {
			super.info(msg);
			logged.add("info: " + msg);
		}
		@Override
		public void warn(String msg) {
			super.warn(msg);
			logged.add("warn: " + msg);
		}
		@Override
		public void error(String msg) {
			super.error(msg);
			logged.add("error: " + msg);
		}
	}

}
