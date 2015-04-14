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
import static org.junit.Assert.assertNull;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.test.MockRequestedModuleNames;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.Features;

import org.apache.commons.codec.binary.Base64;
import org.apache.wink.json4j.JSONArray;
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
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	public void testGetFeaturesFromRequest() throws Exception {
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParameters = new HashMap<String, String[]>();
		Ref<IConfig> configRef = new Ref<IConfig>(null);
		File tmpdir = new File(System.getProperty("java.io.tmpdir"));
		IAggregator mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		EasyMock.replay(mockAggregator);
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), "{has:{def1:1,def2:0}}"));
		AbstractHttpTransport transport = new TestHttpTransport(mockAggregator);
		Cookie[] cookies = new Cookie[1];
		HttpServletRequest request = TestUtils.createMockRequest(null, requestAttributes, requestParameters, cookies, null);
		EasyMock.replay(request);
		assertNull(transport.getHasConditionsFromRequest(request));

		String hasConditions = "foo;!bar";
		requestParameters.put("has", new String[]{hasConditions});
		Features features = transport.getFeaturesFromRequest(request);
		assertEquals(4, features.featureNames().size());
		Assert.assertTrue(features.featureNames().contains("foo") &&
				          features.featureNames().contains("bar") &&
				          features.featureNames().contains("def1") &&
				          features.featureNames().contains("def2"));
		Assert.assertTrue(features.isFeature("foo"));
		Assert.assertFalse(features.isFeature("bar"));
		Assert.assertTrue(features.isFeature("def1"));
		Assert.assertFalse(features.isFeature("def2"));

		// Now try specifying the has conditions in the cookie
		requestParameters.clear();
		requestParameters.put("hashash", new String[]{"xxxx"}); // value not checked by server
		cookies[0] = new Cookie("has", hasConditions);
		features = transport.getFeaturesFromRequest(request);
		assertEquals(4, features.featureNames().size());
		Assert.assertTrue(features.featureNames().contains("foo") &&
				          features.featureNames().contains("bar") &&
				          features.featureNames().contains("def1") &&
				          features.featureNames().contains("def2"));
		Assert.assertTrue(features.isFeature("foo"));
		Assert.assertFalse(features.isFeature("bar"));
		Assert.assertTrue(features.isFeature("def1"));
		Assert.assertFalse(features.isFeature("def2"));

		// Make sure we handle null cookie values without throwing
		requestParameters.put("hashash", new String[]{"xxxx"}); // value not checked by server
		cookies[0] = new Cookie("has", null);
		features = transport.getFeaturesFromRequest(request);
		assertEquals(2, features.featureNames().size());

		// Try missing cookie
		cookies[0] = new Cookie("foo", "bar");
		features = transport.getFeaturesFromRequest(request);
		assertEquals(2, features.featureNames().size());
	}

	// Test that getFeaturesFromRequest calls IConfig.getDefaultFeatures() with the right parameters
	@Test
	public void testGetFeaturesFromRequest_IConfig_defaultFeatures_parameter() throws Exception {
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParameters = new HashMap<String, String[]>();
		Ref<IConfig> configRef = new Ref<IConfig>(null);
		IConfig mockConfig = EasyMock.createNiceMock(IConfig.class);
		configRef.set(mockConfig);
		File tmpdir = new File(System.getProperty("java.io.tmpdir"));
		IAggregator mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		AbstractHttpTransport transport = new TestHttpTransport(mockAggregator);
		EasyMock.replay(mockAggregator);
		final Features defaultFeatures = new Features();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(null, requestAttributes, requestParameters, null, null);
		EasyMock.expect(mockRequest.getRequestURL()).andReturn(new StringBuffer("http://server.com")).anyTimes();
		EasyMock.expect(mockRequest.getQueryString()).andReturn("abc=1").anyTimes();
		EasyMock.expect(mockConfig.getDefaultFeatures("http://server.com?abc=1")).andReturn(defaultFeatures).once();
		EasyMock.replay(mockConfig, mockRequest);
		transport.getFeaturesFromRequest(mockRequest);
		EasyMock.verify(mockRequest, mockConfig);

		// Test with null query arg
		EasyMock.reset(mockRequest, mockConfig);
		mockRequest = TestUtils.createMockRequest(null, requestAttributes, requestParameters, null, null);
		EasyMock.expect(mockRequest.getRequestURL()).andReturn(new StringBuffer("http://server.com")).anyTimes();
		EasyMock.expect(mockConfig.getDefaultFeatures("http://server.com")).andReturn(defaultFeatures).once();
		EasyMock.replay(mockRequest, mockConfig);
		transport.getFeaturesFromRequest(mockRequest);
		EasyMock.verify(mockRequest, mockConfig);
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
		Features result = transport.getFeaturesFromRequestEncoded(mockRequest, new Features());
		System.out.println(result);
		Assert.assertEquals(features, result);

		features = new Features();
		requestParams.put(AbstractHttpTransport.ENCODED_FEATURE_MAP_REQPARAM, new String[]{encode(features)});
		result = transport.getFeaturesFromRequestEncoded(mockRequest, new Features());
		Assert.assertEquals(features, result);

		features = new Features();
		for (String name : featureList) {features.put(name, true); }
		requestParams.put(AbstractHttpTransport.ENCODED_FEATURE_MAP_REQPARAM, new String[]{encode(features)});
		result = transport.getFeaturesFromRequestEncoded(mockRequest, new Features());
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
	public void testclientRegisterSyntheticModules() throws Exception {
		final Map<String, Integer> moduleIdMap = new HashMap<String, Integer>();
		final Collection<String> syntheticModuleNames = new ArrayList<String>();
		moduleIdMap.put("combo/text", 100);
		moduleIdMap.put("fooplugin", 200);
		TestHttpTransport transport = new TestHttpTransport(TestUtils.createMockAggregator()) {
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
	public void testContributeBootLayerDeps() throws Exception {
		final Pattern pat = Pattern.compile("^require\\.combo\\.addBootLayerDeps\\(([^;]*)\\);\\s");
		IAggregator mockAggregator = EasyMock.createMock(IAggregator.class);
		TestHttpTransport transport = new TestHttpTransport(mockAggregator);
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator);
		EasyMock.replay(mockAggregator, mockRequest);
		MockRequestedModuleNames reqNames = new MockRequestedModuleNames();
		mockRequest.setAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, reqNames);
		reqNames.setDeps(Arrays.asList("dep/a", "prefix!dep/b"));
		StringBuffer sb = new StringBuffer();
		transport.contributeBootLayerDeps(sb, mockRequest);
		System.out.println(sb.toString());
		Matcher m = pat.matcher(sb.toString());
		Assert.assertTrue(m.find());
		JSONArray jsonObj = new JSONArray(m.group(1));
		Assert.assertEquals(new JSONArray("[{\"name\":\"dep/a\"},{\"name\":\"dep/b\",\"prefix\":\"prefix\"}]"), jsonObj);

		reqNames = new MockRequestedModuleNames();
		mockRequest.setAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, reqNames);
		reqNames.setPreloads(Arrays.asList("prefix!preload/a", "preload/b"));
		sb = new StringBuffer();
		transport.contributeBootLayerDeps(sb, mockRequest);
		m = pat.matcher(sb.toString());
		Assert.assertTrue(m.find());
		jsonObj = new JSONArray(m.group(1));
		Assert.assertEquals(new JSONArray("[{\"name\":\"preload/a\",\"prefix\":\"prefix\"},{\"name\":\"preload/b\"}]"), jsonObj);

		reqNames = new MockRequestedModuleNames();
		mockRequest.setAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, reqNames);
		reqNames.setPreloads(Arrays.asList("dep/a", "preload/a"));
		sb = new StringBuffer();
		transport.contributeBootLayerDeps(sb, mockRequest);
		m = pat.matcher(sb.toString());
		Assert.assertTrue(m.find());
		jsonObj = new JSONArray(m.group(1));
		Assert.assertEquals(new JSONArray("[{\"name\":\"dep/a\"},{\"name\":\"preload/a\"}]"), jsonObj);


	}

	@Test
	public void testGenerateModuleIdMap() throws Exception {
		final String[] moduleIdRegFunctionName = new String[]{"reg"};
		final Collection<String> syntheticModuleNames = new ArrayList<String>();
		final Set<String> dependencyNames = new HashSet<String>();
		TestHttpTransport transport = new TestHttpTransport(TestUtils.createMockAggregator()) {
			@Override public String getModuleIdRegFunctionName() { return moduleIdRegFunctionName[0]; }
			@Override public Collection<String> getSyntheticModuleNames() { return syntheticModuleNames; }
		};
		IDependencies mockDependencies = EasyMock.createMock(IDependencies.class);
		EasyMock.expect(mockDependencies.getDependencyNames()).andAnswer(new IAnswer<Iterable<String>>() {
			@Override
			public Iterable<String> answer() throws Throwable {
				return dependencyNames;
			}
		}).anyTimes();
		IAggregator mockAggregator = EasyMock.createMock(IAggregator.class);
		EasyMock.expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		Whitebox.setInternalState(transport, "aggregator", mockAggregator);
		EasyMock.replay(mockAggregator, mockDependencies);

		// verify an empty id map is created with no dependencies
		transport.generateModuleIdMap(mockDependencies);
		Assert.assertEquals(0, transport.getModuleIdMap().size());
		Assert.assertEquals(Arrays.asList(new String[]{""}), transport.getModuleIdList());

		// now add some dependencies
		dependencyNames.add("foo");
		dependencyNames.add("bar");
		transport.generateModuleIdMap(mockDependencies);
		List<String> idList = transport.getModuleIdList();
		Assert.assertEquals(Arrays.asList(new String[]{"", "bar", "foo"}), idList);
		for (int i = 1; i < idList.size(); i++) {
			Assert.assertEquals(i, transport.getModuleIdMap().get(idList.get(i)).intValue());
		}
		Assert.assertEquals(idList.size()-1, transport.getModuleIdMap().size());

		// add some synthetic modules
		syntheticModuleNames.add("combo/text");
		syntheticModuleNames.add("combo/other");
		transport.generateModuleIdMap(mockDependencies);
		idList = transport.getModuleIdList();
		Assert.assertEquals(Arrays.asList(new String[]{"", "bar", "combo/other", "combo/text", "foo"}), idList);
		for (int i = 1; i < idList.size(); i++) {
			Assert.assertEquals(i, transport.getModuleIdMap().get(idList.get(i)).intValue());
		}
		Assert.assertEquals(idList.size()-1, transport.getModuleIdMap().size());

		// add some more deps
		dependencyNames.add("module");
		transport.generateModuleIdMap(mockDependencies);
		System.out.println(transport.getModuleIdList());
		idList = transport.getModuleIdList();
		Assert.assertEquals(Arrays.asList(new String[]{"", "bar", "combo/other", "combo/text", "foo", "module"}), idList);
		for (int i = 1; i < idList.size(); i++) {
			Assert.assertEquals(i, transport.getModuleIdMap().get(idList.get(i)).intValue());
		}
		Assert.assertEquals(idList.size()-1, transport.getModuleIdMap().size());

		// Ensure nothing is generated if no reg function name
		moduleIdRegFunctionName[0] = null;
		Whitebox.setInternalState(transport, "moduleIdMap", (Map<String, Integer>)null);
		Whitebox.setInternalState(transport, "moduleIdList", (List<String>)null);
		transport.generateModuleIdMap(mockDependencies);
		Assert.assertNull(transport.getModuleIdMap());
		Assert.assertNull(transport.getModuleIdList());
	}

	class TestHttpTransport extends AbstractHttpTransport {
		private IAggregator aggregator;
		TestHttpTransport(IAggregator aggregator) {this.aggregator = aggregator;}
		TestHttpTransport(CountDownLatch latch, List<String> dependentFeatures, long lastMod) {super(latch, dependentFeatures, lastMod);}
		@Override protected URI getComboUri() { return URI.create("namedbundleresource://bundlename/combo"); }
		@Override public String getLayerContribution(HttpServletRequest request, LayerContributionType type, Object arg) { return null; }
		@Override public boolean isServerExpandable(HttpServletRequest request, String mid) { return false; }
		@Override public List<ICacheKeyGenerator> getCacheKeyGenerators() { return null; }
		@Override protected String getTransportId() { return null; }
		@Override protected String getResourcePathId() { return "combo"; }
		@Override public String getAggregatorTextPluginName() { return "combo/text"; }
		@Override protected IAggregator getAggregator() { return aggregator; }
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