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

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

import org.apache.wink.json4j.JSONObject;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.resource.FileResource;
import com.ibm.jaggr.service.modulebuilder.ModuleBuild;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.CopyUtil;

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
	JSONObject configParams = new JSONObject();
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	ConcurrentMap<String, Object> concurrentMap = new ConcurrentHashMap<String, Object>();
	I18nModuleBuilder builder;
	IResource res;
	List<ICacheKeyGenerator> keyGens = null;

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
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		replay(mockRequest);
		replay(mockAggregator);
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
		builder = new I18nModuleBuilder();
		keyGens = builder.getCacheKeyGenerators(mockAggregator);
		s = KeyGenUtil.toString(keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n:null:provisional"));
		keyGens = null;
		output = buildIt();
		
		Assert.assertTrue(builder.handles("nls/strings", new FileResource(new File(nls, "strings.js").toURI())));

		// build the module.  Should not get locale expansion without module name exporting
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
		requestAttributes.put(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en}"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/en/strings\",{locale_label:\"en\"});define(\"nls/strings\",{locale_label:\"root\"});", output);

		// Test with an unavailable locale
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"eb"}));
		
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertFalse(s.contains("i18n"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);
		
		// Now add the locale,  make sure the change isn't detected without development
		// mode being enabled (because we're using the list cached in the key generator).
		File eb = new File(nls, "eb");
		eb.mkdir();
		CopyUtil.copy("define({locale_label:'eb'});", new FileWriter(new File(eb, "strings.js")));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);

		// Now enable development mode and make sure the addition of the new locale is detected
		TestUtils.deleteRecursively(eb);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		keyGens = null;
		output = buildIt();
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
		// Now add the missing locale resources and make sure it's the new resource is
		// returned in the build.
		eb = new File(nls, "eb");
		eb.mkdir();
		CopyUtil.copy("define({locale_label:'eb'});", new FileWriter(new File(eb, "strings.js")));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/eb/strings\",{locale_label:\"eb\"});define(\"nls/strings\",{locale_label:\"root\"});", output);
		
	}
	
	@Test
	public void testLocaleMatching() throws Exception {
		String s, output;
		requestAttributes.put(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		builder = new I18nModuleBuilder();
		
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"en-us-var"}));
		
		output = buildIt();
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en-us-var"));
		System.out.println(output);
		Assert.assertEquals("define(\"nls/en-us-var/strings\",{locale_label:\"en-us-var\"});define(\"nls/strings\",{locale_label:\"root\"});", output);
		
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"en-ca-var"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en-ca}"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/en-ca/strings\",{locale_label:\"en-ca\"});define(\"nls/strings\",{locale_label:\"root\"});", output);
		
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"es-ca-var"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{es}"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/es/strings\",{locale_label:\"es\"});define(\"nls/strings\",{locale_label:\"root\"});", output);

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"ex-ca-var"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertFalse(s.contains("i18n"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"en-ca"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en-ca}"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/en-ca/strings\",{locale_label:\"en-ca\"});define(\"nls/strings\",{locale_label:\"root\"});", output);

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"en-cx"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en}"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/en/strings\",{locale_label:\"en\"});define(\"nls/strings\",{locale_label:\"root\"});", output);

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"en"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{en}"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/en/strings\",{locale_label:\"en\"});define(\"nls/strings\",{locale_label:\"root\"});", output);

		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"ex"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertFalse(s.contains("i18n"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/strings\",{locale_label:\"root\"});", output);

		// Try with multiple locales
		requestAttributes.put(
				IHttpTransport.REQUESTEDLOCALES_REQATTRNAME, 
				Arrays.asList(new String[]{"es", "en-us", "en-ca-var", "foo"}));
		s = KeyGenUtil.generateKey(mockRequest, keyGens);
		System.out.println(s);
		Assert.assertTrue(s.contains("i18n{es,en-us,en-ca}"));
		output = buildIt();
		System.out.println(output);
		Assert.assertEquals("define(\"nls/es/strings\",{locale_label:\"es\"});define(\"nls/en-us/strings\",{locale_label:\"en-us\"});define(\"nls/en-ca/strings\",{locale_label:\"en-ca\"});define(\"nls/strings\",{locale_label:\"root\"});", output);
		
		// Assert that the builder doesn't handle requests for locale specific resources
		Assert.assertFalse(builder.handles("nls/en/strings", new FileResource(new File(en, "strings.js").toURI())));
	}
	
	private String buildIt() throws Exception {
		concurrentMap.clear();
		ModuleBuild build = builder.build("nls/strings", res, mockRequest, keyGens);
		keyGens = build.getCacheKeyGenerators();
		if (build.isError()) {
			throw new Exception("Build error");
		}
		return build.getBuildOutput();
		
	}
}
