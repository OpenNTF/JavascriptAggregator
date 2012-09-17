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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.InitParams;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.util.Features;
import com.ibm.jaggr.service.util.PathUtil;

/**
 * These tests require that the "osgi.instance.area" system property be set
 * to point the the osgi workspace directory of the domino server.  You can 
 * set this by specifying the following JVM args:
 * 
 * -Dosgi.instance.area=file:/c:/domino/data/domino/workspace/
 */
public class ConfigTests {
	
//	@Test
//	public void testFindModuleLocation() {
//		fail("Not yet implemented");
//	}

//	@Test
//	public void testGetName() {
//		fail("Not yet implemented");
//	}

//	@Test
//	public void testGet() {
//		fail("Not yet implemented");
//	}

	@Test
	public void testLoad() throws IOException, URISyntaxException {
		/* Note: set the "osgi.instance.area" system property in the run configuration
		 * for this JUnit test
		 */
		if (System.getProperty("osgi.instance.area") == null) {
			System.setProperty("osgi.instance.area", new File(".").toURI().toString());
		}
		// TODO: create a test config  
//    	URI uri = ConfigImpl.class.getResource("../../socmail-config.js").toURI();
//		assertNotNull(ConfigImpl.load("default", uri));
	}

	@Test
	public void normalizePaths() throws IOException, URISyntaxException {
		String ref = "aaa/bbb/ccc/ddd";
		String[] paths = new String[]{
			"./yyy",
			".././zzz",
			"/ooo",
			"../../..//xxx",
			"../../../../yyy",
			"ddd/eee",
			"/../eee",
			"../../../../../eee",
			"plugin!aaa/bbb",
			"plugin!./aaa/bbb",
			"plugin!../aaa/bbb",
			"plugin!/aaa/bbb",
			"./plugin!aaa/bbb",
			"../plugin!aaa/bbb",
			"/plugin!../aaa/bbb",
			"../../has!dojo-firebug?../../with_firebug:../../withot_firebug",
			"abc/123/.",
			"dojo/has!vml?dojo/text!./templates/spinner-vml.html:dojo/text!./templates/spinner-canvas.html",
		};
		String[] result = PathUtil.normalizePaths(ref, paths);
		Assert.assertEquals(result[0], "aaa/bbb/ccc/ddd/yyy");
		Assert.assertEquals(result[1], "aaa/bbb/ccc/zzz");
		Assert.assertEquals(result[2], "/ooo");
		Assert.assertEquals(result[3], "aaa/xxx");
		Assert.assertEquals(result[4], "yyy");
		Assert.assertEquals(result[5], "ddd/eee");
		Assert.assertEquals(result[6], "/../eee");
		Assert.assertEquals(result[7], "../../../../../eee");
		Assert.assertEquals(result[8], "plugin!aaa/bbb");
		Assert.assertEquals(result[9], "plugin!aaa/bbb/ccc/ddd/aaa/bbb");
		Assert.assertEquals(result[10], "plugin!aaa/bbb/ccc/aaa/bbb");
		Assert.assertEquals(result[11], "plugin!/aaa/bbb");
		Assert.assertEquals(result[12], "aaa/bbb/ccc/ddd/plugin!aaa/bbb");
		Assert.assertEquals(result[13], "aaa/bbb/ccc/plugin!aaa/bbb");
		Assert.assertEquals(result[14], "/plugin!aaa/bbb/ccc/aaa/bbb");
		Assert.assertEquals(result[15], "aaa/bbb/has!dojo-firebug?aaa/bbb/with_firebug:aaa/bbb/withot_firebug");
		Assert.assertEquals(result[16], "abc/123");
		Assert.assertEquals(result[17], "dojo/has!vml?dojo/text!aaa/bbb/ccc/ddd/templates/spinner-vml.html:dojo/text!aaa/bbb/ccc/ddd/templates/spinner-canvas.html");
		
		
		for (int i = 0; i < paths.length; i++) {
			System.out.println(paths[i] + " -> " + result[i]);
		}
		ref = "/aaa/bbb";
		paths = new String[] {
				"ccc",
				"../xxx",
				"../../zzz",
				"../../../eee"
		};
		result = PathUtil.normalizePaths(ref, paths);
		Assert.assertEquals(result[0], "ccc");
		Assert.assertEquals(result[1], "/aaa/xxx");
		Assert.assertEquals(result[2], "/zzz");
		Assert.assertEquals(result[3], "../../../eee");
		
		for (int i = 0; i < paths.length; i++) {
			System.out.println(paths[i] + " -> " + result[i]);
		}
	}
	@Test
	public void testJsVars() throws Exception {
		// Test to make sure the shared scope options and initParams variables are working
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		List<InitParams.InitParam> initParams = new LinkedList<InitParams.InitParam>();
		initParams.add(new InitParams.InitParam("param1", "param1Value1"));
		initParams.add(new InitParams.InitParam("param1", "param1Value2"));
		initParams.add(new InitParams.InitParam("param2", "param2Value"));
		EasyMock.expect(mockAggregator.getInitParams()).andReturn(new InitParams(initParams)).anyTimes();
		EasyMock.replay(mockAggregator);
		mockAggregator.getOptions().setOption("foo", "bar");
		String config = "{cacheBust:(function(){console.log(options.foo);console.info(initParams.param1[0]);console.warn(initParams.param1[1]);console.error(initParams.param2[0]);return 'foo';})()}";
		URI tmpDir = new File(System.getProperty("java.io.tmpdir")).toURI();
		final TestConsoleLogger logger = new TestConsoleLogger();
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config) {
			@Override
			protected ConfigImpl.Console newConsole() {
				return logger;
			}
		};
		System.out.println(cfg.getRawConfig());
		List<String> logged = logger.getLogged();
		Assert.assertEquals(cfg.getCacheBust(), "foo");
		Assert.assertEquals("log: bar", logged.get(0));
		Assert.assertEquals("info: param1Value1", logged.get(1));
		Assert.assertEquals("warn: param1Value2", logged.get(2));
		Assert.assertEquals("error: param2Value", logged.get(3));
	}
	
	@Test
	public void testAliasResolver() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		EasyMock.replay(mockAggregator);
		Features features = new Features();
		Set<String> dependentFeatures = new HashSet<String>();
		URI tmpDir = new File(System.getProperty("java.io.tmpdir")).toURI();
		
		// Test simple string matcher resolver
		String config = "{aliases:[['foo/test', 'bar/test']]}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		String result = cfg.resolveAlias("foo/test", features, dependentFeatures, null);
		Assert.assertEquals("bar/test", result);
		
		// Test regular expression matcher with string replacement
		config = "{aliases:[[/\\/bar\\//, '/foo/']]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAlias("p1/bar/p2/bar/test", features, dependentFeatures, null);
		Assert.assertEquals("p1/foo/p2/foo/test", result);
		
		// Test regular expression matcher with replacement function conditioned on feature test
		config = "{aliases:[[/\\/foo\\//, function(s){return '/'+has('test')+'/'}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		features.put("test", true);
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/true/p2", result);
		Assert.assertEquals(1, dependentFeatures.size());
		Assert.assertEquals("test", dependentFeatures.iterator().next());
		
		features.put("test", false);
		dependentFeatures.clear();
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/false/p2", result);
		Assert.assertEquals(1, dependentFeatures.size());
		Assert.assertEquals("test", dependentFeatures.iterator().next());
		
		features.remove("test");
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/undefined/p2", result);
		Assert.assertEquals(1, dependentFeatures.size());
		Assert.assertEquals("test", dependentFeatures.iterator().next());
		
		// Test regular expression with string based group replacement 
		dependentFeatures.clear();
		config = "{aliases:[[/^(.*)\\/foo\\/(.*)$/, '$2/bar/$1']]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p2/bar/p1", result);
		Assert.assertEquals(0, dependentFeatures.size());
		
		// Test regular expression with function based group replacement
		config = "{aliases:[[/^(.*)\\/foo\\/(.*)$/, function(a,b,c){return c+'/bar/'+b;}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p2/bar/p1", result);
		Assert.assertEquals(0, dependentFeatures.size());
		
		// Test regular expression with replacement function conditioned on options value
		IOptions options = mockAggregator.getOptions();
		options.setOption("developmentMode", "false");
		config = "{aliases:[[/^(.*)\\/foo\\/(.*)$/, function(a,b,c){return options.developmentMode == 'true' ? (c+'/bar/'+b) : (b+/bar/+c);}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/bar/p2", result);
		options.setOption("developmentMode", "true");
		cfg.optionsUpdated(options, 2);
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p2/bar/p1", result);
		
		// Test regular expression flags
		config = "{aliases:[[/^(.*)\\/Foo\\/(.*)$/, function(a,b,c){return b+'/bar/'+c;}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/foo/p2", result);
		config = "{aliases:[[/^(.*)\\/Foo\\/(.*)$/i, function(a,b,c){return b+'/bar/'+c;}]]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		result = cfg.resolveAlias("p1/foo/p2", features, dependentFeatures, null);
		Assert.assertEquals("p1/bar/p2", result);
		
		
		
	}
	
	public static class TestConsoleLogger extends ConfigImpl.Console {
		List<String> logged = new ArrayList<String>();
		List<String> getLogged() {
			return logged;
		}
		public void log(String msg) {
			super.log(msg);
			logged.add("log: " + msg);
		}
		public void info(String msg) {
			super.info(msg);
			logged.add("info: " + msg);
		}
		public void warn(String msg) {
			super.warn(msg);
			logged.add("warn: " + msg);
		}
		public void error(String msg) {
			super.error(msg);
			logged.add("error: " + msg);
		}
	}
}
