/*
 * (C) Copyright 2014, IBM Corporation
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

package com.ibm.jaggr.core.impl.modulebuilder.less;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.resource.StringResource;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.Features;

import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Scriptable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class LessModuleBuilderTest extends EasyMock {
	static File tmpdir;
	static File testdir;
	public static final String LESS_STATIC_IMPORT = "@import \"colors.less\";\n\nbody{\n  " +
            "background: @mainColor;\n}";

	public static final String LESS_VAR_IMPORT1 = "@mixin: 'colors';\n@import \"@{mixin}.less\";\n\nbody{\n  " +
            "background: @mainColor;\n}";

	public static final String LESS_VAR_IMPORT2 = "@import \"@{mixin}.less\";\n\nbody{\n  " +
            "background: @mainColor;\n}";

	public static final String LESS_GLOBAL_VAR = "body{@{bidiLeft}:10px;}";

	Map<String, String[]> requestParams = new HashMap<String, String[]>();
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	Scriptable configScript;
	IAggregator mockAggregator;
	Ref<IConfig> configRef;
	HttpServletRequest mockRequest;
	long seq = 1;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = new File(System.getProperty("java.io.tmpdir"));
		testdir = new File(tmpdir, "LessModuleBuilderTest");
		testdir.mkdir();
		// create file to import
		CopyUtil.copy("@mainColor: #FF0000;", new FileWriter(new File(testdir,
				"colors.less")));
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TestUtils.deleteRecursively(testdir);
	}

	@Before
	public void setUp() throws Exception {
		configRef = new Ref<IConfig>(null);
		mockAggregator = TestUtils.createMockAggregator(configRef, testdir);
		mockRequest = TestUtils.createMockRequest(mockAggregator,
				requestAttributes, requestParams, null, null);
		IPlatformServices mockPlatformServices = EasyMock.createNiceMock(IPlatformServices.class);
		EasyMock.expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();
		IServiceRegistration mockRegistration = EasyMock.createNiceMock(IServiceRegistration.class);
		EasyMock.expect(mockPlatformServices.registerService(EasyMock.isA(String.class), EasyMock.anyObject(), EasyMock.isA(Dictionary.class))).andReturn(mockRegistration).anyTimes();
		replay(mockRequest, mockAggregator, mockPlatformServices, mockRegistration);
	}

	@Test
	public void testLessCompilationWithImport() throws Exception {
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		LessModuleBuilder builder = new LessModuleBuilder(mockAggregator);
		builder.configLoaded(cfg, seq++);
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators
				(mockAggregator);
		URI resUri = new File(testdir, "test.less").toURI();
		ModuleBuild mb = builder.build("test.less", new StringResource(LESS_STATIC_IMPORT, resUri), mockRequest, keyGens);
		String output = (String)mb.getBuildOutput();
		Assert.assertEquals("define('body{background:#ff0000}');", output);
		Assert.assertEquals("txt;css", KeyGenUtil.toString(mb.getCacheKeyGenerators()));
	}

	@Test
	public void testLessCompilationWithVarImport() throws Exception {
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		LessModuleBuilder builder = new LessModuleBuilder(mockAggregator);
		builder.configLoaded(cfg, seq++);
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators
				(mockAggregator);
		URI resUri = new File(testdir, "test.less").toURI();
		ModuleBuild mb = builder.build("test.less", new StringResource(LESS_VAR_IMPORT1, resUri), mockRequest, keyGens);
		String output = (String)mb.getBuildOutput();
		Assert.assertEquals("define('body{background:#ff0000}');", output);
		Assert.assertEquals("txt;css", KeyGenUtil.toString(mb.getCacheKeyGenerators()));
	}

	@Test
	public void testLessCompilationWithGlobalVar() throws Exception {
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{lessGlobals:{bidiLeft:'left'}}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		LessModuleBuilder builder = new LessModuleBuilder(mockAggregator);
		builder.configLoaded(cfg, seq++);
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators
				(mockAggregator);
		ModuleBuild mb = builder.build("res.less", new StringResource(LESS_GLOBAL_VAR, URI.create("res.less")), mockRequest, keyGens);
		String output = (String)mb.getBuildOutput();
		Assert.assertEquals("define('body{left:10px}');", output);
		Assert.assertEquals("txt;css", KeyGenUtil.toString(mb.getCacheKeyGenerators()));
	}

	@Test
	public void testLessCompilationWithFeatureDependentGlobalVar() throws Exception {
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{lessGlobals:function(){ return{bidiLeft:has('RtlLanguage')?'right':'left'};}}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		Features features = new Features();
		mockRequest.setAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME, features);
		LessModuleBuilder builder = new LessModuleBuilder(mockAggregator);
		builder.configLoaded(cfg, seq++);
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators
				(mockAggregator);
		ModuleBuild mb = builder.build("res.less", new StringResource(LESS_GLOBAL_VAR, URI.create("res.less")), mockRequest, keyGens);
		String output = (String)mb.getBuildOutput();
		Assert.assertEquals("define('body{left:10px}');", output);
		Assert.assertEquals("txt;css;has:[RtlLanguage]", KeyGenUtil.toString(mb.getCacheKeyGenerators()));
		Assert.assertEquals("txt:0:0;css:0:0:0;has{}", KeyGenUtil.generateKey(mockRequest, mb.getCacheKeyGenerators()));

		features.put("RtlLanguage", true);
		mb = builder.build("res.less", new StringResource(LESS_GLOBAL_VAR, URI.create("res.less")), mockRequest, keyGens);
		output = (String)mb.getBuildOutput();
		Assert.assertEquals("define('body{right:10px}');", output);
		Assert.assertEquals("txt;css;has:[RtlLanguage]", KeyGenUtil.toString(mb.getCacheKeyGenerators()));
		Assert.assertEquals("txt:0:0;css:0:0:0;has{RtlLanguage}", KeyGenUtil.generateKey(mockRequest, mb.getCacheKeyGenerators()));

		features.put("RtlLanguage", false);
		mb = builder.build("res.less", new StringResource(LESS_GLOBAL_VAR, URI.create("res.less")), mockRequest, keyGens);
		output = (String)mb.getBuildOutput();
		Assert.assertEquals("define('body{left:10px}');", output);
		Assert.assertEquals("txt;css;has:[RtlLanguage]", KeyGenUtil.toString(mb.getCacheKeyGenerators()));
		Assert.assertEquals("txt:0:0;css:0:0:0;has{!RtlLanguage}", KeyGenUtil.generateKey(mockRequest, mb.getCacheKeyGenerators()));
	}

	@Test
	public void testLessCompilationWithGlobalVarImport() throws Exception {
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{lessGlobals:{mixin:'colors'}}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		LessModuleBuilder builder = new LessModuleBuilder(mockAggregator);
		builder.configLoaded(cfg, seq++);
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators
				(mockAggregator);
		URI resUri = new File(testdir, "test.less").toURI();
		ModuleBuild mb = builder.build("test.less", new StringResource(LESS_VAR_IMPORT2, resUri), mockRequest, keyGens);
		String output = (String)mb.getBuildOutput();
		Assert.assertEquals("define('body{background:#ff0000}');", output);
		Assert.assertEquals("txt;css", KeyGenUtil.toString(mb.getCacheKeyGenerators()));
	}

	@Test
	public void testLessCompilationWithAMDImport() throws Exception {
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{lessGlobals:{mixin:'\"pkg/colors\"'},packages:[{name:'pkg', location:'" + testdir.toURI().toString() + "'}],cssEnableAMDIncludePaths:true}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		LessModuleBuilder builder = new LessModuleBuilder(mockAggregator);
		builder.configLoaded(cfg, seq++);
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators
				(mockAggregator);
		URI resUri = new File(testdir, "test.less").toURI();
		ModuleBuild mb = builder.build("test.less", new StringResource(LESS_VAR_IMPORT2, resUri), mockRequest, keyGens);
		String output = (String)mb.getBuildOutput();
		Assert.assertEquals("define('body{background:#ff0000}');", output);
		Assert.assertEquals("txt;css", KeyGenUtil.toString(mb.getCacheKeyGenerators()));
	}

	@Test
	public void testLessCompilationWithNotFoundImport() throws Exception {
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{lessGlobals:{mixin:'foo'}}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		LessModuleBuilder builder = new LessModuleBuilder(mockAggregator);
		builder.configLoaded(cfg, seq++);
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators
				(mockAggregator);
		URI resUri = new File(testdir, "test.less").toURI();
		String exceptionMessage = null;
		try {
			builder.build("test.less", new StringResource(LESS_VAR_IMPORT2, resUri), mockRequest, keyGens);
		} catch (IOException e) {
			exceptionMessage = e.getMessage();
		}
		Assert.assertNotNull(exceptionMessage);
		Assert.assertTrue(exceptionMessage.contains("foo.less"));
	}
}
