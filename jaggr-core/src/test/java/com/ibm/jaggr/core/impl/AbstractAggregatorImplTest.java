/*
 * (C) Copyright IBM Corp. 2012, 2016
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
package com.ibm.jaggr.core.impl;

import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.impl.resource.NotFoundResource;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.StringResource;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.transport.IHttpTransport;

import com.google.common.io.Files;
import com.google.common.net.HttpHeaders;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableLong;
import org.apache.commons.lang3.mutable.MutableObject;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AbstractAggregatorImplTest {

	@Test
	public void testInitWorkingDirectory() throws Exception {
		File defaultDir = Files.createTempDir();
		File optionsDir = Files.createTempDir();
		try {
			final IOptions mockOptions = EasyMock.createMock(IOptions.class);
			EasyMock.expect(mockOptions.getCacheDirectory()).andReturn(null).anyTimes();
			TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
					.addMockedMethod("getOptions")
					.addMockedMethod("getName")
					.createMock();
			EasyMock.expect(aggregator.getOptions()).andAnswer(new IAnswer<IOptions>() {
				public IOptions answer() throws Throwable {
					return mockOptions;
				}
			}).anyTimes();
			EasyMock.expect(aggregator.getName()).andReturn("tester").anyTimes();
			EasyMock.replay(mockOptions, aggregator);
			File result = aggregator.initWorkingDirectory(defaultDir, null, "69");
			Assert.assertEquals(new File(defaultDir, "69/tester"), result);
			Assert.assertTrue(new File(defaultDir, "69/tester").exists());

			// Change bundle id and make sure new bundle dir is create and old one is deleted
			result = aggregator.initWorkingDirectory(defaultDir, null, "70");
			Assert.assertEquals(new File(defaultDir, "70/tester"), result);
			Assert.assertTrue(new File(defaultDir, "70/tester").exists());
			Assert.assertFalse(new File(defaultDir, "69/tester").exists());

			// make sure that retained versions aren't deleted
			result = aggregator.initWorkingDirectory(defaultDir, null, "71", Arrays.asList(new String[]{"70"}));
			Assert.assertEquals(new File(defaultDir, "71/tester"), result);
			Assert.assertTrue(new File(defaultDir, "71/tester").exists());
			Assert.assertTrue(new File(defaultDir, "70/tester").exists());

			// Make sure that cache directory specified in options is honored
			EasyMock.verify(mockOptions);
			EasyMock.reset(mockOptions);
			EasyMock.expect(mockOptions.getCacheDirectory()).andReturn(optionsDir.toString()).times(1);
			EasyMock.replay(mockOptions);
			result = aggregator.initWorkingDirectory(defaultDir, null, "70");
			Assert.assertEquals(new File(optionsDir, "70/tester"), result);
			Assert.assertTrue(new File(optionsDir, "70/tester").exists());
			EasyMock.verify(mockOptions, aggregator);

		} finally {
			TestUtils.deleteRecursively(defaultDir);
			TestUtils.deleteRecursively(optionsDir);
		}
	}

	/**
	 * Ensure that an exception is thrown when attempting to add alias paths that overlap
	 * (i.e. the path being added is a child or parent of an existing path).
	 *
	 * @throws Exception
	 */
	@Test
	public void testAddAlias_overlappingPathsValidation() throws Exception {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class).createMock();
		Map<String, URI> map = new HashMap<String, URI>();
		URI res = new URI("/test/resource");
		aggregator.addAlias("/test", res, "", map);
		try {
			aggregator.addAlias("/test/sub", res, "", map);
			Assert.fail("Expected exception");
		} catch (IllegalArgumentException e) {}

		map.clear();
		aggregator.addAlias("/test/sub1", res, "", map);
		aggregator.addAlias("/test/sub2", res, "", map);
		try {
			aggregator.addAlias("/test", res, "", map);
			Assert.fail("Expected exception");
		} catch (IllegalArgumentException e) {}
		try {
			aggregator.addAlias("/test/sub1/foo", res, "", map);
			Assert.fail("Expected exception");
		} catch (IllegalArgumentException e) {}
	}

	/**
	 * Validate that alias paths are fixed-up.  Paths added to map should:
	 * <ol>
	 * <li>Start with '/'</li>
	 * <li>End without '/'</li>
	 * <li>Remove duplicate '/'</li>
	 * </ol>
	 * @throws Exception
	 */
	@Test
	public void testAddAlias_pathDelimFixup() throws Exception {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class).createMock();
		Map<String, URI> map = new HashMap<String, URI>();
		URI res = new URI("/test/resource");
		aggregator.addAlias("/test", res, "", map);
		Assert.assertTrue(map.containsKey("/test"));

		map.clear();
		aggregator.addAlias("test", res, "", map);
		Assert.assertTrue(map.containsKey("/test"));

		map.clear();
		aggregator.addAlias("test/", res, "", map);
		Assert.assertTrue(map.containsKey("/test"));

		map.clear();
		aggregator.addAlias("/test/", res, "", map);
		Assert.assertTrue(map.containsKey("/test"));

		map.clear();
		aggregator.addAlias("test/foo//bar/", res, "", map);
		Assert.assertTrue(map.containsKey("/test/foo/bar"));

	}

	@Test
	public void testGetPathsAndAliases() throws Exception {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
				.addMockedMethod("newResource").createMock();
		EasyMock.expect(aggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				URI uri = (URI)EasyMock.getCurrentArguments()[0];
				return new StringResource("Test Resource for " + uri.toString(), uri);
			}
		}).anyTimes();
		EasyMock.replay(aggregator);
		InitParams initParams = new InitParams(
				Arrays.asList(new InitParam[] {
					new InitParam(InitParams.ALIAS_INITPARAM, "aliasPath", aggregator),
					new InitParam(InitParams.RESOURCEID_INITPARAM, "resid", aggregator),
					new InitParam("resid:alias", "resAlias", aggregator),
					new InitParam("resid:base-name", "resBaseName", aggregator)
				})
		);
		Map<String, URI> map = aggregator.getPathsAndAliases(initParams);
		Assert.assertTrue(map.containsKey("/aliasPath"));
		Assert.assertNull(map.get("/aliasPath"));
		Assert.assertEquals(URI.create("resBaseName"), map.get("/resAlias"));
		Assert.assertEquals(2, map.size());
	}

	@Test (expected=IllegalArgumentException.class)
	public void testGetpathsAndAliases_missingResourceAlias() {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
				.addMockedMethod("newResource").createMock();
		EasyMock.expect(aggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				URI uri = (URI)EasyMock.getCurrentArguments()[0];
				return new StringResource("Test Resource for " + uri.toString(), uri);
			}
		}).anyTimes();
		EasyMock.replay(aggregator);
		InitParams initParams = new InitParams(
				Arrays.asList(new InitParam[] {
					new InitParam(InitParams.RESOURCEID_INITPARAM, "resid", aggregator),
					new InitParam("resid:base-name", "resBaseName", aggregator)
				})
		);
		aggregator.getPathsAndAliases(initParams);
		Assert.fail("Expected exception");
	}

	@Test (expected=IllegalArgumentException.class)
	public void testGetpathsAndAliases_duplicateResourceAlias() {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
				.addMockedMethod("newResource").createMock();
		EasyMock.expect(aggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				URI uri = (URI)EasyMock.getCurrentArguments()[0];
				return new StringResource("Test Resource for " + uri.toString(), uri);
			}
		}).anyTimes();
		EasyMock.replay(aggregator);
		InitParams initParams = new InitParams(
				Arrays.asList(new InitParam[] {
					new InitParam(InitParams.RESOURCEID_INITPARAM, "resid", aggregator),
					new InitParam("resid:alias", "resAlias1", aggregator),
					new InitParam("resid:alias", "resAlias2", aggregator),
					new InitParam("resid:base-name", "resBaseName", aggregator),
				})
		);
		aggregator.getPathsAndAliases(initParams);
		Assert.fail("Expected exception");
	}

	@Test (expected=IllegalArgumentException.class)
	public void testGetpathsAndAliases_missingResourceBaseName() {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
				.addMockedMethod("newResource").createMock();
		EasyMock.expect(aggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				URI uri = (URI)EasyMock.getCurrentArguments()[0];
				return new StringResource("Test Resource for " + uri.toString(), uri);
			}
		}).anyTimes();
		EasyMock.replay(aggregator);
		InitParams initParams = new InitParams(
				Arrays.asList(new InitParam[] {
					new InitParam(InitParams.RESOURCEID_INITPARAM, "resid", aggregator),
					new InitParam("resid:alias", "resAlias", aggregator)
				})
		);
		aggregator.getPathsAndAliases(initParams);
		Assert.fail("Expected exception");
	}

	@Test (expected=IllegalArgumentException.class)
	public void testGetpathsAndAliases_duplicateResourceBaseName() {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
				.addMockedMethod("newResource").createMock();
		EasyMock.expect(aggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				URI uri = (URI)EasyMock.getCurrentArguments()[0];
				return new StringResource("Test Resource for " + uri.toString(), uri);
			}
		}).anyTimes();
		EasyMock.replay(aggregator);
		InitParams initParams = new InitParams(
				Arrays.asList(new InitParam[] {
					new InitParam(InitParams.RESOURCEID_INITPARAM, "resid", aggregator),
					new InitParam("resid:alias", "resAlias", aggregator),
					new InitParam("resid:base-name", "resBaseName1", aggregator),
					new InitParam("resid:base-name", "resBaseName2", aggregator)
				})
		);
		aggregator.getPathsAndAliases(initParams);
		Assert.fail("Expected exception");
	}

	@Test (expected=IllegalArgumentException.class)
	public void testGetpathsAndAliases_emptyResourceAlias() {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
				.addMockedMethod("newResource").createMock();
		EasyMock.expect(aggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				URI uri = (URI)EasyMock.getCurrentArguments()[0];
				return new StringResource("Test Resource for " + uri.toString(), uri);
			}
		}).anyTimes();
		EasyMock.replay(aggregator);
		InitParams initParams = new InitParams(
				Arrays.asList(new InitParam[] {
					new InitParam(InitParams.RESOURCEID_INITPARAM, "resid", aggregator),
					new InitParam("resid:alias", "", aggregator),
					new InitParam("resid:base-name", "resBaseName", aggregator),
				})
		);
		aggregator.getPathsAndAliases(initParams);
		Assert.fail("Expected exception");
	}

	@Test (expected=IllegalArgumentException.class)
	public void testGetpathsAndAliases_rootResourceAlias() {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
				.addMockedMethod("newResource").createMock();
		EasyMock.expect(aggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				URI uri = (URI)EasyMock.getCurrentArguments()[0];
				return new StringResource("Test Resource for " + uri.toString(), uri);
			}
		}).anyTimes();
		EasyMock.replay(aggregator);
		InitParams initParams = new InitParams(
				Arrays.asList(new InitParam[] {
					new InitParam(InitParams.RESOURCEID_INITPARAM, "resid", aggregator),
					new InitParam("resid:alias", "/", aggregator),
					new InitParam("resid:base-name", "resBaseName", aggregator),
				})
		);
		aggregator.getPathsAndAliases(initParams);
		Assert.fail("Expected exception");
	}

	@Test
	public void testProcessResourceRequest_invalidAccess() throws Exception {
		final MutableObject<URI> newResource = new MutableObject<URI>();
		@SuppressWarnings("serial")
		AbstractAggregatorImpl testAggregator = new TestAggregatorImpl() {
			@Override
			public IResource newResource(URI uri) {
				newResource.setValue(uri);
				return new NotFoundResource(uri);
			}
		};
		Map<String, String> responseParams = new HashMap<String, String>();
		HttpServletRequest req = TestUtils.createMockRequest(testAggregator);
		HttpServletResponse resp = TestUtils.createMockResponse(responseParams);
		EasyMock.replay(req, resp);

		URI uri = new URI("/folder/");
		testAggregator.processResourceRequest(req, resp, uri, "sub/../../file.res");
		Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, resp.getStatus());

		testAggregator.processResourceRequest(req, resp, uri, "/file.res");
		Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, resp.getStatus());

		uri = new URI("/folder");
		testAggregator.processResourceRequest(req, resp, uri, "/folder.js");
		Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, resp.getStatus());

		newResource.setValue(null);
		testAggregator.processResourceRequest(req, resp, uri, "./file.js");
		Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, resp.getStatus());
		Assert.assertEquals(new URI("/folder/file.js"), newResource.getValue());

		newResource.setValue(null);
		testAggregator.processResourceRequest(req, resp, uri, "file.js");
		Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, resp.getStatus());
		Assert.assertEquals(new URI("/folder/file.js"), newResource.getValue());

		uri = new URI("/");
		newResource.setValue(null);
		testAggregator.processResourceRequest(req, resp, uri, "/file.js");
		Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, resp.getStatus());
		Assert.assertEquals(new URI("/file.js"), newResource.getValue());

		newResource.setValue(null);
		testAggregator.processResourceRequest(req, resp, uri, "./file.js");
		Assert.assertEquals(HttpServletResponse.SC_NOT_FOUND, resp.getStatus());
		Assert.assertEquals(new URI("/file.js"), newResource.getValue());

		testAggregator.processResourceRequest(req, resp, uri, "../file.js");
		Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, resp.getStatus());
	}

	@Test
	public void testSetResourceResponseCacheHeaders() {
		Map<String, String> responseParams = new HashMap<String, String>();
		IResource mockResource = EasyMock.createMock(IResource.class);
		final MutableLong lastModified = new MutableLong();
		EasyMock.expect(mockResource.lastModified()).andAnswer(new IAnswer<Long>() {
			@Override public Long answer() throws Throwable {
				return lastModified.getValue();
			}

		}).anyTimes();
		final IConfig mockConfig = EasyMock.createMock(IConfig.class);
		final MutableInt expires = new MutableInt();
		EasyMock.expect(mockConfig.getExpires()).andAnswer(new IAnswer<Integer>() {
			@Override public Integer answer() throws Throwable {
				return expires.getValue();
			}

		}).anyTimes();
		@SuppressWarnings("serial")
		AbstractAggregatorImpl testAggregator = new TestAggregatorImpl() {
			@Override public IConfig getConfig() { return mockConfig; }
		};
		HttpServletRequest req = TestUtils.createMockRequest(testAggregator);
		HttpServletResponse resp = TestUtils.createMockResponse(responseParams, false);

		// Test with last-modified, expires and cachebust (Cache-Control should include max-age)
		lastModified.setValue(1000);
		expires.setValue(200);
		resp.setDateHeader(HttpHeaders.LAST_MODIFIED, 1000);
		EasyMock.expectLastCall().once();
		resp.addHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=200");
		EasyMock.expectLastCall().once();
		resp.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
		EasyMock.expectLastCall().once();
		EasyMock.replay(req, resp, mockResource, mockConfig);
		req.setAttribute(IHttpTransport.CACHEBUST_REQATTRNAME, "abc");
		testAggregator.setResourceResponseCacheHeaders(req, resp, mockResource, false);
		EasyMock.verify(resp);

		// Test with last-modified, no expires and cachebust (Cache-Control should not include max-age)
		EasyMock.reset(resp);
		expires.setValue(0);
		resp.setDateHeader(HttpHeaders.LAST_MODIFIED, 1000);
		EasyMock.expectLastCall().once();
		resp.addHeader(HttpHeaders.CACHE_CONTROL, "public");
		EasyMock.expectLastCall().once();
		resp.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
		EasyMock.expectLastCall().once();
		EasyMock.replay(resp);
		testAggregator.setResourceResponseCacheHeaders(req, resp, mockResource, false);
		EasyMock.verify(resp);

		// Test with last-modified, expires and no cachebust (Cache-Control should not include max-age)
		EasyMock.reset(resp);
		expires.setValue(200);
		req.removeAttribute(IHttpTransport.CACHEBUST_REQATTRNAME);
		resp.setDateHeader(HttpHeaders.LAST_MODIFIED, 1000);
		EasyMock.expectLastCall().once();
		resp.addHeader(HttpHeaders.CACHE_CONTROL, "public");
		EasyMock.expectLastCall().once();
		resp.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
		EasyMock.expectLastCall().once();
		EasyMock.replay(resp);
		testAggregator.setResourceResponseCacheHeaders(req, resp, mockResource, false);
		EasyMock.verify(resp);

		// Test with isNoCache=true (Cache-Control should specify no-cache, no-store
		EasyMock.reset(resp);
		resp.addHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store");
		EasyMock.expectLastCall().once();
		EasyMock.replay(resp);
		testAggregator.setResourceResponseCacheHeaders(req, resp, mockResource, true);
		EasyMock.verify(resp);
	}


	@SuppressWarnings("serial")
	public class TestAggregatorImpl extends AbstractAggregatorImpl {
		@Override
		public File getWorkingDirectory() {
			return null;
		}
		@Override
		public IOptions getOptions() {
			return null;
		}
		@Override
		public IExecutors getExecutors() {
			return null;
		}
		@Override
		public String substituteProps(String value) {
			return value;
		}
	}

}
