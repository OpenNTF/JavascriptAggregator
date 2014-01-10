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
package com.ibm.jaggr.service.impl.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.wink.json4j.JSONObject;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.Features;

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

	/**
	 * Test method for {@link com.ibm.jaggr.service.impl.layer.LayerImpl#getHasReaturesFromRequest(javax.servlet.http.HttpServletRequest)}.
	 * @throws ServletException 
	 */
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
		String[] paths = transport.unfoldModules(obj, 4);
		Assert.assertArrayEquals(new String[] {"foo/bar", "foo/baz/yyy", "foo/baz/xxx", "dir" }, paths);
		
		// folded paths with plugin prefixes
		obj = new JSONObject("{'"+AbstractHttpTransport.PLUGIN_PREFIXES_PROP_NAME+"':{'combo/text':'0', abc:'1'},foo:{bar:'0', baz:{xxx.txt:'1-0', yyy.txt:'2-1'}}}");
		paths = transport.unfoldModules(obj, 3);
		Assert.assertArrayEquals(new String[] {"foo/bar",  "combo/text!foo/baz/xxx.txt", "abc!foo/baz/yyy.txt"}, paths);
		
		// make sure legacy format for specifying plugin prefixes works
		obj = new JSONObject("{foo:{bar:'0', baz:{xxx.txt:'1-combo/text', yyy.txt:'2-abc'}}}");
		paths = transport.unfoldModules(obj, 3);
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
		AbstractHttpTransport transport = new TestHttpTransport(latch, featureList); 
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
		AbstractHttpTransport transport = new TestHttpTransport(new CountDownLatch(0), featureList) {
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
	
	
	class TestHttpTransport extends AbstractHttpTransport {
		TestHttpTransport() {}
		TestHttpTransport(CountDownLatch latch, List<String> dependentFeatures) {super(latch, dependentFeatures);}
		@Override protected URI getComboUri() { return null; }
		@Override public String getLayerContribution(HttpServletRequest request, LayerContributionType type, Object arg) { return null; }
		@Override public boolean isServerExpandable(HttpServletRequest request, String mid) { return false; }
		@Override public List<ICacheKeyGenerator> getCacheKeyGenerators() { return null; }
		@Override protected String getPluginUniqueId() { return null; }
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
	 * @param featureString
	 *            the '*' delimited list of features. Null features are
	 *            preceeded by the '!' character.
	 * @return
	 */
	String encode(Features features) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		// Build trit map.  5 trits per byte
		int trite = 0;
		for (int i = 0; i < featureList.size(); i++) {
			if (i % 5 == 0) {
				trite = 0;
			}
			int trit = 2;	// don't care
			String name = featureList.get(i);
			if (features.contains(name)) {
				trit = features.isFeature(name) ? 1 : 0;
			}
			trite += trit * Math.pow(3, i % 5);
			if (i % 5 == 4 || i == featureList.size()-1) {
				bos.write((byte)trite);
			}
		}
		String encoded = Base64.encodeBase64String(bos.toByteArray());
		return encoded;
	}

}