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

package com.ibm.jaggr.service.impl.modulebuilder.i18n;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.resource.FileResource;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;

import org.apache.wink.json4j.JSONObject;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class I18nModuleBuilderTest extends EasyMock {

	static File tmpdir;
	static File testdir;
	static File nls;
	static File en;
	static File en_us;
	static File en_us_var;
	static File en_ca;
	static File en_gb;
	static File es;

	Map<String, String> requestParams = new HashMap<String, String>();
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	HttpServletRequest mockRequest;
	IAggregator mockAggregator;
	IDependencies mockDependencies;
	JSONObject configParams = new JSONObject();
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	ConcurrentMap<String, Object> concurrentMap = new ConcurrentHashMap<String, Object>();
	I18nModuleBuilder builder;
	IResource res;
	List<ICacheKeyGenerator> keyGens = null;

	static String expectedOutput = "define(\"nls/strings\",{locale_label:\"root\"});";

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = new File(System.getProperty("java.io.tmpdir"));
		testdir = new File(tmpdir, "I18nModuleBuilderTest");
		testdir.mkdir();
		nls = new File(testdir, "nls");
		nls.mkdir();
		en = new File(nls, "en");
		en.mkdir();
		es = new File(nls, "es");
		es.mkdir();
		en_us = new File(nls, "en-us");
		en_us.mkdir();
		en_ca = new File(nls, "en-ca");
		en_ca.mkdir();
		en_gb = new File(nls, "en-gb");
		en_gb.mkdir();
		en_us_var = new File(nls, "en-us-var");
		en_us_var.mkdir();
		CopyUtil.copy("define({locale_label:'root'});", new FileWriter(new File(nls, "strings.js")));
		CopyUtil.copy("define({locale_label:'en'});", new FileWriter(new File(en, "strings.js")));
		CopyUtil.copy("define({locale_label:'en-us'});", new FileWriter(new File(en_us, "strings.js")));
		CopyUtil.copy("define({locale_label:'en-ca'});", new FileWriter(new File(en_ca, "strings.js")));
		CopyUtil.copy("define({locale_label:'en-gb'});", new FileWriter(new File(en_gb, "strings.js")));
		CopyUtil.copy("define({locale_label:'en-us-var'});", new FileWriter(new File(en_us_var, "strings.js")));
		CopyUtil.copy("define({locale_label:'es'});", new FileWriter(new File(es, "strings.js")));
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TestUtils.deleteRecursively(testdir);
	}

	@Before
	public void setUp() throws Exception {
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IAggregator.CONCURRENTMAP_REQATTRNAME, concurrentMap);
		mockAggregator = TestUtils.createMockAggregator(configRef, testdir);
		mockDependencies = EasyMock.createNiceMock(IDependencies.class);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		EasyMock.expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		replay(mockRequest);
		replay(mockAggregator);
		replay(mockDependencies);
		res = new FileResource(new File(nls, "strings.js").toURI());
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}"));
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testI18nBuilder() throws Exception {
		String s, output;
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"en"}));
		requestAttributes.put(IHttpTransport.NOADDMODULES_REQATTRNAME, Boolean.TRUE);
		builder = new I18nModuleBuilder();
		keyGens = builder.getCacheKeyGenerators(mockAggregator);
		s = KeyGenUtil.toString(keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n:null:provisional"));
		keyGens = null;
		ModuleBuild build = buildIt();
		output = build.getBuildOutput().toString();

		Assert.assertTrue(builder.handles("nls/strings", new FileResource(new File(nls, "strings.js").toURI())));
		Assert.assertEquals(0, build.getBefore().size());

		// build the module.  Should not get locale expansion with no expansion attribute
		// enabled
		System.out.println(output);
		Assert.assertEquals("define({locale_label:\"root\"});", output);
		s = KeyGenUtil.toString(keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n:[en, en-ca, en-gb, en-us, en-us-var, es]"));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertFalse(s.contains("i18n"));

		// Now enable module name exporting
		requestAttributes.put(IHttpTransport.NOADDMODULES_REQATTRNAME, Boolean.FALSE);
		requestAttributes.put(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en}"));
		build = buildIt();
		output = build.getBuildOutput().toString();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);
		Assert.assertEquals(1, build.getBefore().size());
		Assert.assertEquals("nls/en/strings", build.getBefore().get(0).getModuleId());

		// Test with an unavailable locale
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"eb"}));

		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertFalse(s.contains("i18n"));
		build = buildIt();
		output = build.getBuildOutput().toString();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);
		Assert.assertEquals(0, build.getBefore().size());

		// Now add the locale,  make sure the change isn't detected without development
		// mode being enabled (because we're using the list cached in the key generator).
		File eb = new File(nls, "eb");
		eb.mkdir();
		CopyUtil.copy("define({locale_label:'eb'});", new FileWriter(new File(eb, "strings.js")));
		build = buildIt();
		output = build.getBuildOutput().toString();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);
		Assert.assertEquals(0, build.getBefore().size());

		// Now enable development mode and make sure the addition of the new locale is detected
		TestUtils.deleteRecursively(eb);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		keyGens = null;
		build = buildIt();
		output = build.getBuildOutput().toString();
		s = KeyGenUtil.toString(keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n:null"));
		Assert.assertFalse(s.contains("provisional"));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		// The cache key should specify the requested locale whether or not it exists
		Assert.assertTrue(s.contains("i18n{eb}"));
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);
		Assert.assertEquals(0, build.getBefore().size());
		// Now add the missing locale resources and make sure it's the new resource is
		// returned in the build.
		eb = new File(nls, "eb");
		eb.mkdir();
		File file = new File(eb, "strings.js");
		CopyUtil.copy("define({locale_label:'eb'});", new FileWriter(file));
		build = buildIt();
		output = build.getBuildOutput().toString();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);
		Assert.assertEquals(1, build.getBefore().size());
		Assert.assertEquals("nls/eb/strings", build.getBefore().get(0).getModuleId());
		Assert.assertEquals(file.toURI(), build.getBefore().get(0).getURI());
	}

	@Test
	public void testLocaleMatching() throws Exception {
		String s;
		requestAttributes.put(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		builder = new I18nModuleBuilder();

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"en-us-var"}));

		ModuleBuild build = buildIt();
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en-us-var"));
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(1, build.getBefore().size());
		Assert.assertEquals("nls/en-us-var/strings", build.getBefore().get(0).getModuleId());
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"en-ca-var"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en-ca}"));
		build = buildIt();
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(1, build.getBefore().size());
		Assert.assertEquals("nls/en-ca/strings", build.getBefore().get(0).getModuleId());

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"es-ca-var"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{es}"));
		build = buildIt();
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(1, build.getBefore().size());
		Assert.assertEquals("nls/es/strings", build.getBefore().get(0).getModuleId());

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"ex-ca-var"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertFalse(s.contains("i18n"));
		build = buildIt();
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(0, build.getBefore().size());

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"en-ca"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en-ca}"));
		build = buildIt();
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(1, build.getBefore().size());
		Assert.assertEquals("nls/en-ca/strings", build.getBefore().get(0).getModuleId());

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"en-cx"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en}"));
		build = buildIt();
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(1, build.getBefore().size());
		Assert.assertEquals("nls/en/strings", build.getBefore().get(0).getModuleId());

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"en"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en}"));
		build = buildIt();
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(1, build.getBefore().size());
		Assert.assertEquals("nls/en/strings", build.getBefore().get(0).getModuleId());

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"ex"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertFalse(s.contains("i18n"));
		build = buildIt();
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(0, build.getBefore().size());

		// Try with multiple locales
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME,
				Arrays.asList(new String[]{"es", "en-us", "en-ca-var", "foo"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{es,en-us,en-ca}"));
		build = buildIt();
		Assert.assertEquals(expectedOutput, build.getBuildOutput());
		Assert.assertEquals(3, build.getBefore().size());
		Set<String> mids = new HashSet<String>();
		for (IModule module : build.getBefore()) {
			mids.add(module.getModuleId());
		}
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"nls/es/strings", "nls/en-us/strings", "nls/en-ca/strings"})),
				mids);

		// Assert that the builder doesn't handle requests for locale specific resources
		Assert.assertFalse(builder.handles("nls/en/strings", new FileResource(new File(en, "strings.js").toURI())));
	}

	@Test
	public void testParseAcceptLanguageHeader() {
		HttpServletRequest mockRequest = EasyMock.createMock(HttpServletRequest.class);
		EasyMock.expect(mockRequest.getHeader("Accept-Language")).andReturn("p05;q=0.5,p01;q=0.1,p1").anyTimes();
		EasyMock.replay(mockRequest);
		I18nModuleBuilder builder = new I18nModuleBuilder();
		List<String> result = builder.parseAcceptLanguageHeader(mockRequest);
		Assert.assertEquals(Arrays.asList(new String[]{"p1", "p05", "p01"}), result);
		EasyMock.reset(mockRequest);
		EasyMock.expect(mockRequest.getHeader("Accept-Language")).andReturn("p05;q=0.5,p1;q=1,p01;q=0.1").anyTimes();
		EasyMock.replay(mockRequest);
		result = builder.parseAcceptLanguageHeader(mockRequest);
		Assert.assertEquals(Arrays.asList(new String[]{"p1", "p05", "p01"}), result);
		EasyMock.reset(mockRequest);
		EasyMock.expect(mockRequest.getHeader("Accept-Language")).andReturn("p05;q=0.5,p1;q=1,pX;q=foo,p01;q=0.1").anyTimes();
		EasyMock.replay(mockRequest);
		result = builder.parseAcceptLanguageHeader(mockRequest);
		Assert.assertEquals(Arrays.asList(new String[]{"p1", "p05", "p01"}), result);
		EasyMock.reset(mockRequest);
		EasyMock.expect(mockRequest.getHeader("Accept-Language")).andReturn("p05;q=0.5,p1;q=1,pX;blah,p01;q=0.1").anyTimes();
		EasyMock.replay(mockRequest);
		result = builder.parseAcceptLanguageHeader(mockRequest);
		Assert.assertEquals(Arrays.asList(new String[]{"p1", "p05", "p01"}), result);
	}

	private ModuleBuild buildIt() throws Exception {
		concurrentMap.clear();
		ModuleBuild build = builder.build("nls/strings", res, mockRequest, keyGens);
		keyGens = build.getCacheKeyGenerators();
		if (build.isError()) {
			throw new Exception("Build error");
		}
		return build;

	}
}
