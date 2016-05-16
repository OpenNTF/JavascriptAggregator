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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.util.TypeUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.wink.json4j.JSONObject;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

public class RequestedModuleNamesTest {
	@Test
	public void testRequestedModuleNamesWithEncodedModules() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParams = new HashMap<String, String[]>();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		EasyMock.replay(mockAggregator, request);
		RequestedModuleNames requestedNames = new RequestedModuleNames(request, null, null);
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertEquals("", requestedNames.toString());

		// Test with encoded modules param
		String encModules = "(foo!(bar!0*baz!(xxx!2*yyy!1))*dir!3)";
		requestParams.put("modules", new String[]{encModules});
		requestParams.put("count", new String[]{"4"});
		requestedNames = new RequestedModuleNames(request, null, null);
		List<String> expected = Arrays.asList(new String[]{"foo/bar", "foo/baz/yyy", "foo/baz/xxx", "dir"});
		assertEquals(expected, requestedNames.getModules());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertEquals(encModules+":::", requestedNames.toString());

		// Test with encoded modules and expEx params
		encModules = "(foo!(bar!0*baz!(xxx!2*yyy!1))*dir!3)";
		String expEx = "(foo!(ex!0))";
		requestParams.put("modules", new String[]{encModules});
		requestParams.put("exEnc", new String[]{expEx});
		requestParams.put("count", new String[]{"4"});
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(expected, requestedNames.getModules());
		assertEquals(Arrays.asList("foo/ex"), requestedNames.getExcludesEncoded());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertEquals(encModules+"::"+expEx+":", requestedNames.toString());


		// Test with invalid count param
		requestParams.put("count", new String[]{"5"});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		requestParams.put("count", new String[]{"3"});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// Test with non-numeric count param
		requestParams.put("count", new String[]{"abc"});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// test with count out of range
		requestParams.put("count", new String[]{new Integer(AbstractHttpTransport.REQUESTED_MODULES_MAX_COUNT + 1).toString()});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
			assertEquals(ex.getMessage(), "count:" + (AbstractHttpTransport.REQUESTED_MODULES_MAX_COUNT+1));
		}
		requestParams.put("count", new String[]{"0"});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
			assertEquals(ex.getMessage(), "count:0");
		}

		// test with invalid encoding indices
		requestParams.put("modules", new String[]{"(foo!(bar!0*baz!(xxx!2*yyy!1))*dir!2)"});
		requestParams.put("count", new String[]{"4"});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// test with unused encoding index
		requestParams.put("modules", new String[]{"(foo!(bar!0*baz!(xxx!2*yyy!1))*dir!4)"});
		requestParams.put("count", new String[]{"5"});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// test with illegal encoding syntax
		requestParams.put("modules", new String[]{"(foo!(bar0*baz!(xxx!2*yyy!1))*dir!4)"});
		requestParams.put("count", new String[]{"5"});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}
		// test invalid count param when retrieving from cache
		requestParams.put("modules", new String[]{encModules});
		requestParams.put("count", new String[]{"4"});
		new RequestedModuleNames(request, null, null);
		requestParams.put("count", new String[]{"5"});
		try {
			requestedNames = new RequestedModuleNames(request, null, null);
			requestedNames.getModules();
			fail("Expected exception");
		} catch (BadRequestException ex) {
		}

	}

	@Test
	public void testRequestedModuleNames() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParams = new HashMap<String, String[]>();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		EasyMock.replay(mockAggregator, request);

		// test with scripts only
		requestParams.put(AbstractHttpTransport.SCRIPTS_REQPARAM, new String[]{"script/a, script/b"});
		RequestedModuleNames requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"script/a", "script/b"}), requestedNames.getScripts());
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertTrue(requestedNames.getExcludes().isEmpty());
		assertEquals("scripts:[script/a, script/b]", requestedNames.toString());

		// test with deps only
		requestParams.remove(AbstractHttpTransport.SCRIPTS_REQPARAM);
		requestParams.put(AbstractHttpTransport.DEPS_REQPARAM, new String[]{"dep/a,dep/b"});
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"dep/a", "dep/b"}), requestedNames.getDeps());
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertTrue(requestedNames.getExcludes().isEmpty());
		assertEquals("deps:[dep/a, dep/b]", requestedNames.toString());

		// test with preloads only
		requestParams.remove(AbstractHttpTransport.DEPS_REQPARAM);
		requestParams.put(AbstractHttpTransport.PRELOADS_REQPARAM, new String[]{"preload/a,preload/b"});
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"preload/a", "preload/b"}), requestedNames.getPreloads());
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getExcludes().isEmpty());
		assertEquals("preloads:[preload/a, preload/b]", requestedNames.toString());

		// test with excludes only
		requestParams.remove(AbstractHttpTransport.PRELOADS_REQPARAM);
		requestParams.put(AbstractHttpTransport.EXCLUDES_REQPARAM, new String[]{"exclude/a,exclude/b"});
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"exclude/a", "exclude/b"}), requestedNames.getExcludes());
		assertTrue(requestedNames.getModules().isEmpty());
		assertTrue(requestedNames.getDeps().isEmpty());
		assertTrue(requestedNames.getPreloads().isEmpty());
		assertEquals("excludes:[exclude/a, exclude/b]", requestedNames.toString());

		// test with all four
		requestParams.put(AbstractHttpTransport.DEPS_REQPARAM, new String[]{"dep/a,dep/b"});
		requestParams.put(AbstractHttpTransport.SCRIPTS_REQPARAM, new String[]{"script/a, script/b"});
		requestParams.put(AbstractHttpTransport.PRELOADS_REQPARAM, new String[]{"preload/a,preload/b"});
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"script/a", "script/b"}), requestedNames.getScripts());
		assertEquals(Arrays.asList(new String[]{"dep/a", "dep/b"}), requestedNames.getDeps());
		assertEquals(Arrays.asList(new String[]{"preload/a", "preload/b"}), requestedNames.getPreloads());
		assertEquals(Arrays.asList(new String[]{"exclude/a", "exclude/b"}), requestedNames.getExcludes());
		assertEquals("scripts:[script/a, script/b]; deps:[dep/a, dep/b]; preloads:[preload/a, preload/b]; excludes:[exclude/a, exclude/b]", requestedNames.toString());

	}

	@SuppressWarnings("deprecation")
	@Test
	public void testRequestedModuleNamesExceptions() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParams = new HashMap<String, String[]>();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		EasyMock.replay(mockAggregator, request);

		// test exceptions with scripts param
		requestParams.put(AbstractHttpTransport.SCRIPTS_REQPARAM, new String[]{"script/a"});
		requestParams.put(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM, new String[]{"module/a"});
		try {
			new RequestedModuleNames(request, null, null);
			fail("Expected exception");
		} catch (BadRequestException ex) {}
		requestParams.remove(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM);
		requestParams.put(AbstractHttpTransport.REQUIRED_REQPARAM, new String[]{"required/a"});
		try {
			new RequestedModuleNames(request, null, null);
			fail("Expected exception");
		} catch (BadRequestException ex) {}

		// test exceptions with deps param
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestParams.clear();
		requestParams.put(AbstractHttpTransport.DEPS_REQPARAM, new String[]{"deps/a"});
		requestParams.put(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM, new String[]{"module/a"});
		try {
			new RequestedModuleNames(request, null, null);
			fail("Expected exception");
		} catch (BadRequestException ex) {}
		requestParams.remove(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM);
		requestParams.put(AbstractHttpTransport.REQUIRED_REQPARAM, new String[]{"required/a"});
		try {
			new RequestedModuleNames(request, null, null);
			fail("Expected exception");
		} catch (BadRequestException ex) {}

		// test exceptions with preloads param
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestParams.clear();
		requestParams.put(AbstractHttpTransport.PRELOADS_REQPARAM, new String[]{"preloads/a"});
		requestParams.put(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM, new String[]{"module/a"});
		try {
			new RequestedModuleNames(request, null, null);
			fail("Expected exception");
		} catch (BadRequestException ex) {}
		requestParams.remove(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM);
		requestParams.put(AbstractHttpTransport.REQUIRED_REQPARAM, new String[]{"required/a"});
		try {
			new RequestedModuleNames(request, null, null);
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

		// check the warn deprecated flag when using 'modules' (dev/debug only)
		requestParams.put(AbstractHttpTransport.REQUESTEDMODULES_REQPARAM, new String[]{"foo/a,bar/b"});
		RequestedModuleNames requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getScripts());
		assertEquals(Collections.emptyList(), requestedNames.getModules());
		assertFalse(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG)));
		// now enable debug mode
		request.removeAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		request.removeAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, true);
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getScripts());
		assertTrue(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG)));
		// make sure it works for development mode as well
		request.removeAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		request.removeAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, false);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getScripts());
		assertTrue(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_MODULES_QUERYARG)));

		// check the warn deprecated flag when using 'require' (dev/debug only)
		requestParams.clear();
		requestAttributes.clear();
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, false);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestParams.put(AbstractHttpTransport.REQUIRED_REQPARAM, new String[]{"foo/a,bar/b"});
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getDeps());
		assertFalse(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG)));
		// now enable debug mode
		request.removeAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		request.removeAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, true);
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getDeps());
		assertTrue(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG)));
		// make sure it works for development mode as well
		request.removeAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		request.removeAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, false);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		requestedNames = new RequestedModuleNames(request, null, null);
		assertEquals(Arrays.asList(new String[]{"foo/a", "bar/b"}), requestedNames.getDeps());
		assertTrue(TypeUtil.asBoolean(request.getAttribute(AbstractHttpTransport.WARN_DEPRECATED_USE_OF_REQUIRED_QUERYARG)));
	}

	@Test
	public void testUnfoldModules() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator);
		EasyMock.replay(request, mockAggregator);
		RequestedModuleNames requestedNames = new RequestedModuleNames(request, null, null);
		// basic folded paths  with no plugin prefixes
		JSONObject obj = new JSONObject("{foo:{bar:'0', baz:{xxx:'2', yyy:'1'}}, dir:'3'}");
		Map<Integer, String> paths = new TreeMap<Integer, String>();
		requestedNames.unfoldModules(obj, paths);
		Assert.assertArrayEquals(new String[] {"foo/bar", "foo/baz/yyy", "foo/baz/xxx", "dir" }, paths.values().toArray());

		// folded paths with plugin prefixes
		obj = new JSONObject("{'"+RequestedModuleNames.PLUGIN_PREFIXES_PROP_NAME+"':{'combo/text':'0', abc:'1'},foo:{bar:'0', baz:{xxx.txt:'1-0', yyy.txt:'2-1'}}}");
		paths = new TreeMap<Integer, String>();
		requestedNames.unfoldModules(obj, paths);
		Assert.assertArrayEquals(new String[] {"foo/bar",  "combo/text!foo/baz/xxx.txt", "abc!foo/baz/yyy.txt"}, paths.values().toArray());

		// make sure legacy format for specifying plugin prefixes works
		obj = new JSONObject("{foo:{bar:'0', baz:{xxx.txt:'1-combo/text', yyy.txt:'2-abc'}}}");
		paths = new TreeMap<Integer, String>();
		requestedNames.unfoldModules(obj, paths);
		Assert.assertArrayEquals(new String[] {"foo/bar",  "combo/text!foo/baz/xxx.txt", "abc!foo/baz/yyy.txt"}, paths.values().toArray());
	}

	@Test
	public void testDecodeMopdules() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		HttpServletRequest request = TestUtils.createMockRequest(mockAggregator);
		EasyMock.replay(request, mockAggregator);
		RequestedModuleNames requestedNames = new RequestedModuleNames(request, null, null);
		JSONObject decoded = requestedNames.decodeModules("(foo!(bar!0*baz!(<|xxx>!2*yyy!1))*dir!3)");
		Assert.assertEquals(new JSONObject("{foo:{bar:'0',baz:{'(!xxx)':'2',yyy:'1'}},dir:'3'}"), decoded);

		decoded = requestedNames.decodeModules("("+RequestedModuleNames.PLUGIN_PREFIXES_PROP_NAME+"!(combo/text!0*abc!1)*foo!(bar!0*baz!(xxx.txt!1-0*yyy.txt!2-1)))");
		Assert.assertEquals(new JSONObject("{'"+RequestedModuleNames.PLUGIN_PREFIXES_PROP_NAME+"':{'combo/text':'0', abc:'1'},foo:{bar:'0', baz:{xxx.txt:'1-0', yyy.txt:'2-1'}}}"), decoded);

	}

	@Test
	public void testDecodeModuleIds() throws Exception {
		final String[] idList = new String[0x10005];
		idList[1] = "module1";
		idList[2] = "module2";
		idList[3] = "foo";
		idList[4] = "bar";
		idList[5] = "plugin";
		idList[0x10003] = "bigId";

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

		byte hash[] = new byte[]{1, 2, 3};

		// test with 16-bit encoding
		byte[] bytes = new byte[ids.length*2];
		for (int i = 0; i < ids.length; i++) {
			bytes[i*2] = (byte)(ids[i] >> 8);
			bytes[i*2+1] = (byte)(ids[i] & 0xFF);
		}
		byte[] encoded = ArrayUtils.addAll(ArrayUtils.addAll(hash, new byte[]{0}), bytes);
		System.out.println(encoded);
		Map<Integer, String> resultArray = new TreeMap<Integer, String>();

		Ref<IConfig> configRef = new Ref<IConfig>(null);
		File tmpDir = new File(System.getProperty("user.dir"));
		IAggregator mockAggregator = TestUtils.createMockAggregator(configRef, tmpDir);
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParameters = new HashMap<String, String[]>();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParameters, null, null);
		EasyMock.replay(mockAggregator, mockRequest);
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{}"));
		requestParameters.put(AbstractHttpTransport.REQUESTEDMODULEIDS_REQPARAM, new String[]{Base64.encodeBase64URLSafeString(encoded)});
		requestParameters.put(AbstractHttpTransport.REQUESTEDMODULESCOUNT_REQPARAM, new String[]{"4"});
		RequestedModuleNames requestedModules = new RequestedModuleNames(mockRequest, Arrays.asList(idList), hash);
		requestedModules.decodeModuleIds(encoded, resultArray, true);
		Assert.assertEquals(4, resultArray.size());
		Assert.assertEquals("module1", resultArray.get(3));
		Assert.assertEquals("module2", resultArray.get(5));
		Assert.assertEquals("plugin!foo", resultArray.get(6));
		Assert.assertEquals("bar", resultArray.get(10));

		// test again with 32-bit encoding
		ids = ArrayUtils.addAll(ids, new int[]{12, 1, 0x10003});  // slot 12, 1 module, "bigId"
		bytes = new byte[ids.length*4];
		for (int i = 0; i < ids.length; i++) {
			bytes[i*4] = (byte)(ids[i] >> 24);
			bytes[i*4+1] = (byte)((ids[i] >> 16) & 0xFF);
			bytes[i*4+2] = (byte)((ids[i] >> 8) & 0xFF);
			bytes[i*4+3] = (byte)(ids[i] & 0xFF);
		}
		encoded = ArrayUtils.addAll(ArrayUtils.addAll(hash, new byte[]{1}), bytes);
		System.out.println(encoded);
		requestParameters.put(AbstractHttpTransport.REQUESTEDMODULEIDS_REQPARAM, new String[]{Base64.encodeBase64URLSafeString(encoded)});
		requestParameters.put(AbstractHttpTransport.REQUESTEDMODULESCOUNT_REQPARAM, new String[]{"5"});
		requestedModules = new RequestedModuleNames(mockRequest, Arrays.asList(idList), hash);
		resultArray = new TreeMap<Integer, String>();
		requestedModules.decodeModuleIds(encoded, resultArray, true);
		Assert.assertEquals(5, resultArray.size());
		Assert.assertEquals("module1", resultArray.get(3));
		Assert.assertEquals("module2", resultArray.get(5));
		Assert.assertEquals("plugin!foo", resultArray.get(6));
		Assert.assertEquals("bar", resultArray.get(10));
		Assert.assertEquals("bigId", resultArray.get(12));

		// Make sure exception is thrown if hash is not correct
		try {
			encoded = ArrayUtils.addAll(new byte[]{3, 2, 1}, bytes);
			requestParameters.put(AbstractHttpTransport.REQUESTEDMODULEIDS_REQPARAM, new String[]{Base64.encodeBase64URLSafeString(encoded)});
			requestedModules = new RequestedModuleNames(mockRequest, Arrays.asList(idList), hash);
			requestedModules.decodeModuleIds(encoded, new TreeMap<Integer, String>(), true);
			fail("Expected exception");
		} catch (BadRequestException ex) {

		}

		// Make sure that request object specifies error module if configured instead of throwing exception
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{"+RequestedModuleNames.CONFIGPROP_IDLISTHASHERRMODULE+":'errModule'}"));
		requestedModules = new RequestedModuleNames(mockRequest, Arrays.asList(idList), hash);
		Assert.assertEquals("scripts:[errModule]", requestedModules.toString());

	}

	@Test
	public void testVersionErrorHandling() throws Exception {
		Ref<IConfig> configRef = new Ref<IConfig>(null);
		File tmpDir = new File(System.getProperty("user.dir"));
		IAggregator mockAggregator = TestUtils.createMockAggregator(configRef, tmpDir);
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParameters = new HashMap<String, String[]>();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParameters, null, null);
		EasyMock.replay(mockAggregator, mockRequest);
		String encModules = "(foo!(bar!0*baz!(xxx!2*yyy!1))*dir!3)";
		List<String> expected = Arrays.asList(new String[]{"foo/bar", "foo/baz/yyy", "foo/baz/xxx", "dir"});
		requestParameters.put("modules", new String[]{encModules});
		requestParameters.put("count", new String[]{"4"});
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{cacheBust:'12345',cacheBustErrorModule:'cbError'}"));
		requestAttributes.put(AbstractHttpTransport.CACHEBUST_REQATTRNAME, "54321");
		// verify error module as script is only module
		RequestedModuleNames requestedNames = new RequestedModuleNames(mockRequest, null, null);
		assertEquals(Arrays.asList("cbError"), requestedNames.getScripts());
		assertTrue(requestedNames.getModules().isEmpty());

		requestAttributes.put(AbstractHttpTransport.CACHEBUST_REQATTRNAME, "12345");
		// cache busts match.  Requested modules are there and no error module
		requestedNames = new RequestedModuleNames(mockRequest, null, null);
		assertEquals(expected, requestedNames.getModules());
		assertTrue(requestedNames.getScripts().isEmpty());

		requestAttributes.put(AbstractHttpTransport.CACHEBUST_REQATTRNAME, "54321");
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{cacheBust:'12345'}"));
		requestAttributes.put(AbstractHttpTransport.CACHEBUST_REQATTRNAME, "12345");
		// cache busts don't match, but no handler provided.  Requested modules are there and no error module
		requestedNames = new RequestedModuleNames(mockRequest, null, null);
		assertEquals(expected, requestedNames.getModules());
		assertTrue(requestedNames.getScripts().isEmpty());

		// Test id list hash validation
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{idListHashErrorModule:'idError'}"));
		byte[] hash1 = new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15},
		       hash2 = new byte[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,0};
		String encodedHash = Base64.encodeBase64URLSafeString(ArrayUtils.addAll(hash1, new byte[]{0} /*size byte*/));
		requestParameters.put(AbstractHttpTransport.REQUESTEDMODULEIDS_REQPARAM, new String[]{encodedHash});
		// id list hash values match.  Requested modules are there and no error module
		requestedNames = new RequestedModuleNames(mockRequest, Collections.<String>emptyList(), hash1);
		assertEquals(expected, requestedNames.getModules());
		assertTrue(requestedNames.getScripts().isEmpty());

		// id list hash values don't match.  Requested module is error module
		requestedNames = new RequestedModuleNames(mockRequest, Collections.<String>emptyList(), hash2);
		assertEquals(Arrays.asList("idError"), requestedNames.getScripts());
		assertTrue(requestedNames.getModules().isEmpty());

		// id list hash values don't match and no error handler provided.  Exception thrown.
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{}"));
		boolean exceptionThrown = false;
		try {
			requestedNames = new RequestedModuleNames(mockRequest, Collections.<String>emptyList(), hash2);
		} catch (BadRequestException ex) {
			exceptionThrown = true;
		}
		assertTrue(exceptionThrown);
	}

}
