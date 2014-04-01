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
package com.ibm.jaggr.core.impl.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.RequestedModuleNames;
import com.ibm.jaggr.core.util.TypeUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.wink.json4j.JSONObject;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

public class AbstractHttpTransportTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSetRequestedModuleNamesWithEncodedModules() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParams = new HashMap<String, String[]>();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		EasyMock.replay(mockAggregator, request);
		AbstractHttpTransport transport = new TestHttpTransport();
		transport.setRequestedModuleNames(request);
		RequestedModuleNames requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertEquals("", requestedNames.toString());

		// Test with encoded modules param
		String encModules = "(foo!(bar!0*baz!(xxx!2*yyy!1))*dir!3)";
		requestParams.put("modules", new String[]{encModules});
		requestParams.put("count", new String[]{"4"});
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		List<String> expected = Arrays.asList(new String[]{"foo/bar", "foo/baz/yyy", "foo/baz/xxx", "dir"});
		assertEquals(expected, requestedNames.getModules());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertEquals(encModules, requestedNames.toString());

		// Test with invalid count param
		requestParams.put("count", new String[]{"5"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		requestParams.put("count", new String[]{"3"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// Test with non-numeric count param
		requestParams.put("count", new String[]{"abc"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// test with count out of range
		requestParams.put("count", new String[]{"10000"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
			assertEquals(ex.getMessage(), "count:10000");
		}
		requestParams.put("count", new String[]{"0"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
			assertEquals(ex.getMessage(), "count:0");
		}

		// test with invalid encoding indices
		requestParams.put("modules", new String[]{"(foo!(bar!0*baz!(xxx!2*yyy!1))*dir!2)"});
		requestParams.put("count", new String[]{"4"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// test with unused encoding index
		requestParams.put("modules", new String[]{"(foo!(bar!0*baz!(xxx!2*yyy!1))*dir!4)"});
		requestParams.put("count", new String[]{"5"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// test with illegal encoding syntax
		requestParams.put("modules", new String[]{"(foo!(bar0*baz!(xxx!2*yyy!1))*dir!4)"});
		requestParams.put("count", new String[]{"5"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// test invalid count param when retrieving from cache
		requestParams.put("modules", new String[]{encModules});
		requestParams.put("count", new String[]{"4"});
		transport.setRequestedModuleNames(request);
		requestParams.put("count", new String[]{"5"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}

	}

	@Test
	public void testSetRequestedModuleNames() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParams = new HashMap<String, String[]>();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		EasyMock.replay(mockAggregator, request);
		AbstractHttpTransport transport = new TestHttpTransport();

		// test with scripts only
		requestParams.put(AbstractHttpTransport.SCRIPTS_REQPARAM, new String[]{"script/a, script/b"});
		transport.setRequestedModuleNames(request);
		RequestedModuleNames requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"script/a", "script/b"}), requestedNames.getScripts());
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertEquals("scripts:[script/a, script/b]", requestedNames.toString());

		// test with deps only
		requestParams.remove(AbstractHttpTransport.SCRIPTS_REQPARAM);
		requestParams.put(AbstractHttpTransport.DEPS_REQPARAM, new String[]{"dep/a,dep/b"});
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"dep/a", "dep/b"}), requestedNames.getDeps());
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertEquals("deps:[dep/a, dep/b]", requestedNames.toString());

		// test with preloads only
		requestParams.remove(AbstractHttpTransport.DEPS_REQPARAM);
		requestParams.put(AbstractHttpTransport.PRELOADS_REQPARAM, new String[]{"preload/a,preload/b"});
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"preload/a", "preload/b"}), requestedNames.getPreloads());
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertEquals("preloads:[preload/a, preload/b]", requestedNames.toString());

		// test with all three
		requestParams.put(AbstractHttpTransport.DEPS_REQPARAM, new String[]{"dep/a,dep/b"});
		requestParams.put(AbstractHttpTransport.SCRIPTS_REQPARAM, new String[]{"script/a, script/b"});
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"script/a", "script/b"}), requestedNames.getScripts());
		assertEquals(Arrays.asList(new String[]{"dep/a", "dep/b"}), requestedNames.getDeps());
		assertEquals(Arrays.asList(new String[]{"preload/a", "preload/b"}), requestedNames.getPreloads());
		assertEquals("scripts:[script/a, script/b];deps:[dep/a, dep/b];preloads:[preload/a, preload/b]", requestedNames.toString());

	}

	@SuppressWarnings("deprecation")
	@Test
	public void testSetRequestedModuleNamesExceptions() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParams = new HashMap<String, String[]>();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		EasyMock.replay(mockAggregator, request);
		AbstractHttpTransport transport = new TestHttpTransport();

		// test exceptions with scripts param
		requestParams.put(AbstractHttpTransport.SCRIPTS_REQPARAM, new String[]{"script/a"});
		requestParams.put(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM, new String[]{"module/a"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {}
		requestParams.remove(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM);
		requestParams.put(AbstractHttpTransport.REQUIRED_REQPARAM, new String[]{"required/a"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {}

		// test exceptions with deps param
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestParams.clear();
		requestParams.put(AbstractHttpTransport.DEPS_REQPARAM, new String[]{"deps/a"});
		requestParams.put(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM, new String[]{"module/a"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {}
		requestParams.remove(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM);
		requestParams.put(AbstractHttpTransport.REQUIRED_REQPARAM, new String[]{"required/a"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {}

		// test exceptions with preloads param
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestParams.clear();
		requestParams.put(AbstractHttpTransport.PRELOADS_REQPARAM, new String[]{"preloads/a"});
		requestParams.put(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM, new String[]{"module/a"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {}
		requestParams.remove(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM);
		requestParams.put(AbstractHttpTransport.REQUIRED_REQPARAM, new String[]{"required/a"});
		try {
			transport.setRequestedModuleNames(request);
			fail("Expected exception");
		} catch (BadRequestException ex) {}

	}

	@SuppressWarnings("deprecation")
	@Test
	public void testSetRequestedModuleNamesWithDeprecatedParams() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParams = new HashMap<String, String[]>();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		EasyMock.replay(mockAggregator, request);
		AbstractHttpTransport transport = new TestHttpTransport();

		// check the warn deprecated flag when using 'modules' (dev/debug only)
		requestParams.put(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM, new String[]{"foo/a,bar/b"});
		transport.setRequestedModuleNames(request);
		RequestedModuleNames requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getScripts());
		assertFalse(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG)));
		// now enable debug mode
		request.removeAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		request.removeAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, true);
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getScripts());
		assertTrue(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG)));
		// make sure it works for development mode as well
		request.removeAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		request.removeAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, false);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getScripts());
		assertTrue(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG)));

		// check the warn deprecated flag when using 'require' (dev/debug only)
		requestParams.clear();
		requestAttributes.clear();
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, false);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestParams.put(AbstractHttpTransport.REQUIRED_REQPARAM, new String[]{"foo/a,bar/b"});
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getDeps());
		assertFalse(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG)));
		// now enable debug mode
		request.removeAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		request.removeAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, true);
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getDeps());
		assertTrue(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG)));
		// make sure it works for development mode as well
		request.removeAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		request.removeAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, false);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		transport.setRequestedModuleNames(request);
		requestedNames = (RequestedModuleNames)request.getAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getDeps());
		assertTrue(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG)));


	}

	@Test
	public void testGetFeaturesFromRequest() throws Exception {
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParameters = new HashMap<String, String[]>();
		AbstractHttpTransport transport = new TestHttpTransport();
		Cookie[] cookies = new Cookie[1];
		HttpServletRequest request = TestUtils.createMockRequest(null, requestAttributes, requestParameters, cookies, null);
		EasyMock.replay(request);
		assertNull(transport.getHasConditionsFromRequest(request));

		String hasConditions = "foo;!bar";
		requestParameters.put("has", new String[]{hasConditions});
		Features features = transport.getFeaturesFromRequest(request);
		assertEquals(2, features.featureNames().size());
		Assert.assertTrue(features.featureNames().contains("foo") && features.featureNames().contains("bar"));
		Assert.assertTrue(features.isFeature("foo"));
		Assert.assertFalse(features.isFeature("bar"));

		// Now try specifying the has conditions in the cookie
		requestParameters.clear();
		requestParameters.put("hashash", new String[]{"xxxx"}); // value not checked by server
		cookies[0] = new Cookie("has", hasConditions);
		features = transport.getFeaturesFromRequest(request);
		assertEquals(2, features.featureNames().size());
		Assert.assertTrue(features.featureNames().contains("foo") && features.featureNames().contains("bar"));
		Assert.assertTrue(features.isFeature("foo"));
		Assert.assertFalse(features.isFeature("bar"));

		// Make sure we handle null cookie values without throwing
		requestParameters.put("hashash", new String[]{"xxxx"}); // value not checked by server
		cookies[0] = new Cookie("has", null);
		features = transport.getFeaturesFromRequest(request);
		assertEquals(0, features.featureNames().size());

		// Try missing cookie
		cookies[0] = new Cookie("foo", "bar");
		features = transport.getFeaturesFromRequest(request);
		assertEquals(0, features.featureNames().size());
	}

	@Test
	public void testUnfoldModules() throws Exception {
		AbstractHttpTransport transport = new TestHttpTransport();
		// basic folded paths  with no plugin prefixes
		JSONObject obj = new JSONObject("{foo:{bar:'0', baz:{xxx:'2', yyy:'1'}}, dir:'3'}");
		String[] paths = new String[4];
		transport.unfoldModules(obj, paths);
		Assert.assertArrayEquals(new String[] {"foo/bar", "foo/baz/yyy", "foo/baz/xxx", "dir" }, paths);

		// folded paths with plugin prefixes
		obj = new JSONObject("{'"+AbstractHttpTransport.PLUGIN_PREFIXES_PROP_NAME+"':{'combo/text':'0', abc:'1'},foo:{bar:'0', baz:{xxx.txt:'1-0', yyy.txt:'2-1'}}}");
		paths = new String[3];
		transport.unfoldModules(obj, paths);
		Assert.assertArrayEquals(new String[] {"foo/bar",  "combo/text!foo/baz/xxx.txt", "abc!foo/baz/yyy.txt"}, paths);

		// make sure legacy format for specifying plugin prefixes works
		obj = new JSONObject("{foo:{bar:'0', baz:{xxx.txt:'1-combo/text', yyy.txt:'2-abc'}}}");
		paths = new String[3];
		transport.unfoldModules(obj, paths);
		Assert.assertArrayEquals(new String[] {"foo/bar",  "combo/text!foo/baz/xxx.txt", "abc!foo/baz/yyy.txt"}, paths);
	}

	@Test
	public void testDecodeMopdules() throws Exception {
		AbstractHttpTransport transport = new TestHttpTransport();
		JSONObject decoded = transport.decodeModules("(foo!(bar!0*baz!(<|xxx>!2*yyy!1))*dir!3)");
		Assert.assertEquals(new JSONObject("{foo:{bar:'0',baz:{'(!xxx)':'2',yyy:'1'}},dir:'3'}"), decoded);

		decoded = transport.decodeModules("("+AbstractHttpTransport.PLUGIN_PREFIXES_PROP_NAME+"!(combo/text!0*abc!1)*foo!(bar!0*baz!(xxx.txt!1-0*yyy.txt!2-1)))");
		Assert.assertEquals(new JSONObject("{'"+AbstractHttpTransport.PLUGIN_PREFIXES_PROP_NAME+"':{'combo/text':'0', abc:'1'},foo:{bar:'0', baz:{xxx.txt:'1-0', yyy.txt:'2-1'}}}"), decoded);

	}

	@Test
	public void testGetHasConditionsEncodedFromRequest() throws Exception{
		CountDownLatch latch = new CountDownLatch(0);
		AbstractHttpTransport transport = new TestHttpTransport(latch, featureList, 0L);
		Map<String, String[]> requestParams = new HashMap<String, String[]>();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(null, new HashMap<String, Object>(), requestParams, new Cookie[0], new HashMap<String, String>());
		EasyMock.replay(mockRequest);
		Features features = new Features();
		features.put("0", true);
		features.put("2", true);
		features.put("10", false);
		features.put("17", false);
		features.put("21", true);
		features.put("50", true);
		features.put("99", true);
		requestParams.put(AbstractHttpTransport.ENCODED_FEATURE_MAP_REQPARAM, new String[]{encode(features)});
		Features result = transport.getFeaturesFromRequestEncoded(mockRequest);
		System.out.println(result);
		Assert.assertEquals(features, result);

		features = new Features();
		requestParams.put(AbstractHttpTransport.ENCODED_FEATURE_MAP_REQPARAM, new String[]{encode(features)});
		result = transport.getFeaturesFromRequestEncoded(mockRequest);
		Assert.assertEquals(features, result);

		features = new Features();
		for (String name : featureList) {features.put(name, true); }
		requestParams.put(AbstractHttpTransport.ENCODED_FEATURE_MAP_REQPARAM, new String[]{encode(features)});
		result = transport.getFeaturesFromRequestEncoded(mockRequest);
		System.out.println(result);
		Assert.assertEquals(features, result);
	}

	@Test
	public void testFeatureListResourceFactory_newResource() throws IOException {
		URI uri = URI.create("namedbundleresource://com.ibm.jaggr-service/combo/featureList.js");
		StringBuffer expected = new StringBuffer(AbstractHttpTransport.FEATURE_LIST_PRELUDE + "[");
		for (int i = 0; i < featureList.size(); i++) {
			expected.append(i == 0 ? "" : ",").append("\"").append(featureList.get(i)).append("\"");
		}
		expected.append("]" + AbstractHttpTransport.FEATURE_LIST_PROLOGUE);

		final IAggregator mockAggregator = EasyMock.createMock(IAggregator.class);
		IDependencies mockDependencies = EasyMock.createMock(IDependencies.class);
		EasyMock.expect(mockDependencies.getLastModified()).andReturn(10L).anyTimes();
		EasyMock.expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();

		EasyMock.replay(mockAggregator, mockDependencies);
		AbstractHttpTransport transport = new TestHttpTransport(new CountDownLatch(0), featureList, 10L) {
			@Override
			protected IAggregator getAggregator() { return mockAggregator; }
		};
		AbstractHttpTransport.FeatureListResourceFactory  factory = transport.newFeatureListResourceFactory(uri);
		IResource resourceOut = factory.newResource(uri);
		Assert.assertEquals(10L, resourceOut.lastModified());
		StringWriter writer = new StringWriter();
		CopyUtil.copy(resourceOut.getReader(), writer);
		Assert.assertEquals(expected.toString(), writer.toString());

		resourceOut = factory.newResource(URI.create("namedbundleresource://com.ibm.jaggr-service/combo/foo.js"));
		writer = new StringWriter();
		try {
			CopyUtil.copy(resourceOut.getReader(), writer);
			Assert.fail();
		} catch (IOException e) {
		}
	}

	@Test
	public void testDecodeModuleIds() throws Exception {
		final List<String> idList = new ArrayList<String>();
		TestHttpTransport transport = new TestHttpTransport() {
			@Override public List<String> getModuleIdList() { return idList; }
		};
		idList.add(null);
		idList.add("module1");
		idList.add("module2");
		idList.add("foo");
		idList.add("bar");
		idList.add("plugin");

		int ids[] = new int [] {
			// specifies the following {,,,"module1",,"module2","plugin!foo",,,"bar"}
			// first segment
			3,		// start at third slot in module list
			1,		// one module id to follow
			// start of module id
			1,		// "module1"
			// new segment
			5,		// fifth slot in module list
			2,		// two module ids to follow
			2,	// module2
			// second module id in segment specifies "plugin!foo"
			0,
			5,	// "plugin"
			3,	// "foo"
			// third segment
			10,		// slot 10 in modulelist
			1,		// one module id to follow
			4		// "bar"
		};

		byte[] bytes = new byte[24];
		for (int i = 0; i < ids.length; i++) {
			bytes[i*2] = (byte)(ids[i] >> 8);
			bytes[i*2+1] = (byte)(ids[i] & 0xFF);
		}
		String encoded = Base64.encodeBase64URLSafeString(bytes);
		String resultArray[] = new String[11];
		transport.decodeModuleIds(encoded, resultArray);
		Assert.assertArrayEquals(new String[]{null,null,null,"module1",null,"module2","plugin!foo",null,null,null,"bar"},  resultArray);
	}

	@Test
	public void testclientRegisterSyntheticModules() {
		final Map<String, Integer> moduleIdMap = new HashMap<String, Integer>();
		final Collection<String> syntheticModuleNames = new ArrayList<String>();
		moduleIdMap.put("combo/text", 100);
		moduleIdMap.put("fooplugin", 200);
		TestHttpTransport transport = new TestHttpTransport() {
			@Override public Map<String, Integer> getModuleIdMap() { return moduleIdMap; }
			@Override public String getModuleIdRegFunctionName() { return "reg"; }
			@Override public Collection<String> getSyntheticModuleNames() { return syntheticModuleNames; }
		};
		String result = transport.clientRegisterSyntheticModules();
		Assert.assertEquals("", result);

		syntheticModuleNames.add("combo/text");
		result = transport.clientRegisterSyntheticModules();
		Assert.assertEquals("reg([[[\"combo/text\"]],[[100]]]);", result);

		syntheticModuleNames.add("noid");
		result = transport.clientRegisterSyntheticModules();
		Assert.assertEquals("reg([[[\"combo/text\"]],[[100]]]);", result);

		syntheticModuleNames.add("fooplugin");
		result = transport.clientRegisterSyntheticModules();
		Assert.assertEquals("reg([[[\"combo/text\",\"fooplugin\"]],[[100,200]]]);", result);
	}

	@Test
	public void testGenerateModuleIdMap() throws Exception {
		final String[] moduleIdRegFunctionName = new String[]{"reg"};
		final Collection<String> syntheticModuleNames = new ArrayList<String>();
		final Map<String, List<String>> dependencyNames = new HashMap<String, List<String>>();
		TestHttpTransport transport = new TestHttpTransport() {
			@Override public String getModuleIdRegFunctionName() { return moduleIdRegFunctionName[0]; }
			@Override public Collection<String> getSyntheticModuleNames() { return syntheticModuleNames; }
		};
		IDependencies mockDependencies = EasyMock.createMock(IDependencies.class);
		EasyMock.expect(mockDependencies.getDependencyNames()).andAnswer(new IAnswer<Iterable<String>>() {
			@Override
			public Iterable<String> answer() throws Throwable {
				return dependencyNames.keySet();
			}
		}).anyTimes();
		EasyMock.expect(mockDependencies.getDelcaredDependencies(EasyMock.isA(String.class))).andAnswer(new IAnswer<List<String>>() {
			@Override
			public List<String> answer() throws Throwable {
				String mid = (String)EasyMock.getCurrentArguments()[0];
				return dependencyNames.get(mid);
			}
		}).anyTimes();
		IAggregator mockAggregator = EasyMock.createMock(IAggregator.class);
		EasyMock.expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		Whitebox.setInternalState(transport, "aggregator", mockAggregator);
		EasyMock.replay(mockAggregator, mockDependencies);

		// verify an empty id map is created with no dependencies
		transport.generateModuleIdMap();
		Assert.assertEquals(0, transport.getModuleIdMap().size());
		Assert.assertEquals(Arrays.asList(new String[]{""}), transport.getModuleIdList());

		// now add some dependencies
		List<String> declaredDeps = Arrays.asList(new String[]{"foo", "bar"});
		dependencyNames.put("foobar", declaredDeps);
		transport.generateModuleIdMap();
		List<String> idList = transport.getModuleIdList();
		Assert.assertEquals(Arrays.asList(new String[]{"", "bar", "foo", "foobar"}), idList);
		for (int i = 1; i < idList.size(); i++) {
			Assert.assertEquals(i, transport.getModuleIdMap().get(idList.get(i)).intValue());
		}
		Assert.assertEquals(idList.size()-1, transport.getModuleIdMap().size());

		// add some synthetic modules
		syntheticModuleNames.add("combo/text");
		syntheticModuleNames.add("combo/other");
		transport.generateModuleIdMap();
		idList = transport.getModuleIdList();
		Assert.assertEquals(Arrays.asList(new String[]{"", "bar", "combo/other", "combo/text", "foo", "foobar"}), idList);
		for (int i = 1; i < idList.size(); i++) {
			Assert.assertEquals(i, transport.getModuleIdMap().get(idList.get(i)).intValue());
		}
		Assert.assertEquals(idList.size()-1, transport.getModuleIdMap().size());

		// add some more deps
		declaredDeps = Arrays.asList(new String[]{"dep1", "dep2", "dep3"});
		dependencyNames.put("module", declaredDeps);
		transport.generateModuleIdMap();
		System.out.println(transport.getModuleIdList());
		idList = transport.getModuleIdList();
		Assert.assertEquals(Arrays.asList(new String[]{"", "bar", "combo/other", "combo/text", "dep1", "dep2", "dep3", "foo", "foobar", "module"}), idList);
		for (int i = 1; i < idList.size(); i++) {
			Assert.assertEquals(i, transport.getModuleIdMap().get(idList.get(i)).intValue());
		}
		Assert.assertEquals(idList.size()-1, transport.getModuleIdMap().size());

		// Ensure nothing is generated if no reg function name
		moduleIdRegFunctionName[0] = null;
		Whitebox.setInternalState(transport, "moduleIdMap", (Map<String, Integer>)null);
		Whitebox.setInternalState(transport, "moduleIdList", (List<String>)null);
		transport.generateModuleIdMap();
		Assert.assertNull(transport.getModuleIdMap());
		Assert.assertNull(transport.getModuleIdList());
	}

	class TestHttpTransport extends AbstractHttpTransport {
		TestHttpTransport() {}
		TestHttpTransport(CountDownLatch latch, List<String> dependentFeatures, long lastMod) {super(latch, dependentFeatures, lastMod);}
		@Override protected URI getComboUri() { return URI.create("namedbundleresource://bundlename/combo"); }
		@Override public String getLayerContribution(HttpServletRequest request, LayerContributionType type, Object arg) { return null; }
		@Override public boolean isServerExpandable(HttpServletRequest request, String mid) { return false; }
		@Override public List<ICacheKeyGenerator> getCacheKeyGenerators() { return null; }
		@Override protected String getTransportId() { return null; }
		@Override protected String getResourcePathId() { return "combo"; }
		@Override protected String getAggregatorTextPluginName() { return "combo/text"; }
	};

	static List<String> featureList = Arrays.asList(new String[]{
			"0",  "1",  "2",  "3",  "4",  "5",  "6",  "7",  "8",  "9",
			"10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
			"20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
			"30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
			"40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
			"50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
			"60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
			"70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
			"80", "81", "82", "83", "84", "85", "86", "87", "88", "89",
			"90", "91", "92", "93", "94", "95", "96", "97", "98", "99"
	});

	/**
	 * Method to encode a feature string the same way that the JavaScript code
	 * in featureMap.js does it.
	 *
	 * @param features
	 *            the '*' delimited list of features. Null features are
	 *            preceeded by the '!' character.
	 * @return the encoded result
	 */
	String encode(Features features) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int len = featureList.size();
		bos.write(len & 0xFF);
		bos.write((len & 0xFF00) >> 8);
		// Build trit map.  5 trits per byte
		int trite = 0;
		for (int i = 0; i < len; i++) {
			if (i % 5 == 0) {
				trite = 0;
			}
			int trit = 2;	// don't care
			String name = featureList.get(i);
			if (features.contains(name)) {
				trit = features.isFeature(name) ? 1 : 0;
			}
			trite += trit * Math.pow(3, i % 5);
			if (i % 5 == 4 || i == len-1) {
				bos.write((byte)trite);
			}
		}
		String encoded = Base64.encodeBase64String(bos.toByteArray());
		return encoded;
	}

}