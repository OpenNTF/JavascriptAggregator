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

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.wink.json4j.JSONObject;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.test.BaseTestUtils;
import com.ibm.jaggr.core.util.Features;

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
	 * Test method for {@link com.ibm.jaggr.core.impl.layer.LayerImpl#getHasReaturesFromRequest(javax.servlet.http.HttpServletRequest)}.
	 * @throws ServletException 
	 */
	@Test
	public void testGetFeaturesFromRequest() throws Exception {
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		Map<String, String[]> requestParameters = new HashMap<String, String[]>();
		Cookie[] cookies = new Cookie[1];
		HttpServletRequest request = BaseTestUtils.createMockRequest(null, requestAttributes, requestParameters, cookies, null);
		EasyMock.replay(request);
		assertNull(AbstractHttpTransport.getHasConditionsFromRequest(request));
		
		String hasConditions = "foo;!bar";
		requestParameters.put("has", new String[]{hasConditions});
		Features features = AbstractHttpTransport.getFeaturesFromRequest(request);
		assertEquals(2, features.featureNames().size());
		Assert.assertTrue(features.featureNames().contains("foo") && features.featureNames().contains("bar"));
		Assert.assertTrue(features.isFeature("foo"));
		Assert.assertFalse(features.isFeature("bar"));
		
		// Now try specifying the has conditions in the cookie
		requestParameters.clear();
		requestParameters.put("hashash", new String[]{"xxxx"}); // value not checked by server
		cookies[0] = new Cookie("has", hasConditions);
		features = AbstractHttpTransport.getFeaturesFromRequest(request);
		assertEquals(2, features.featureNames().size());
		Assert.assertTrue(features.featureNames().contains("foo") && features.featureNames().contains("bar"));
		Assert.assertTrue(features.isFeature("foo"));
		Assert.assertFalse(features.isFeature("bar"));
		
		// Make sure we handle null cookie values without throwing
		requestParameters.put("hashash", new String[]{"xxxx"}); // value not checked by server
		cookies[0] = new Cookie("has", null);
		features = AbstractHttpTransport.getFeaturesFromRequest(request);
		assertEquals(0, features.featureNames().size());

		// Try missing cookie
		cookies[0] = new Cookie("foo", "bar");
		features = AbstractHttpTransport.getFeaturesFromRequest(request);
		assertEquals(0, features.featureNames().size());
	}
	
	@Test
	public void testUnfoldModules() throws Exception {
		AbstractHttpTransport transport = new AbstractHttpTransport() {
			@Override protected URI getComboUri() { return null; }
			@Override public String getLayerContribution(HttpServletRequest request, LayerContributionType type, Object arg) { return null; }
			@Override public boolean isServerExpandable(HttpServletRequest request, String mid) { return false; }
			@Override public List<ICacheKeyGenerator> getCacheKeyGenerators() { return null; }
		};
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
	public void decodeMopdules() throws Exception {
		AbstractHttpTransport transport = new AbstractHttpTransport() {
			@Override protected URI getComboUri() { return null; }
			@Override public String getLayerContribution(HttpServletRequest request, LayerContributionType type, Object arg) { return null; }
			@Override public boolean isServerExpandable(HttpServletRequest request, String mid) { return false; }
			@Override public List<ICacheKeyGenerator> getCacheKeyGenerators() { return null; }
		};
		
		JSONObject decoded = transport.decodeModules("(foo!(bar!0*baz!(<|xxx>!2*yyy!1))*dir!3)");
		Assert.assertEquals(new JSONObject("{foo:{bar:'0',baz:{'(!xxx)':'2',yyy:'1'}},dir:'3'}"), decoded);
	
		decoded = transport.decodeModules("("+AbstractHttpTransport.PLUGIN_PREFIXES_PROP_NAME+"!(combo/text!0*abc!1)*foo!(bar!0*baz!(xxx.txt!1-0*yyy.txt!2-1)))");
		Assert.assertEquals(new JSONObject("{'"+AbstractHttpTransport.PLUGIN_PREFIXES_PROP_NAME+"':{'combo/text':'0', abc:'1'},foo:{bar:'0', baz:{xxx.txt:'1-0', yyy.txt:'2-1'}}}"), decoded);
	
	}
}