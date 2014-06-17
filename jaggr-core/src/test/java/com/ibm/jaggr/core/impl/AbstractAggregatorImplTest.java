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
package com.ibm.jaggr.core.impl;

import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.StringResource;
import com.ibm.jaggr.core.test.TestUtils;

import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AbstractAggregatorImplTest {

	@Test
	public void testInitWorkingDirectory() throws Exception {
		File defaultDir = Files.createTempDir();
		File optionsDir = Files.createTempDir();
		try {
			final IOptions mockOptions = EasyMock.createMock(IOptions.class);
			EasyMock.expect(mockOptions.getCacheDirectory()).andReturn(null).times(2);
			TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
					.addMockedMethod("getOptions")
					.addMockedMethod("getName")
					.createMock();
			EasyMock.expect(aggregator.getOptions()).andAnswer(new IAnswer<IOptions>() {
				public IOptions answer() throws Throwable {
					return mockOptions;
				}
			}).times(3);
			EasyMock.expect(aggregator.getName()).andReturn("tester").times(3);
			EasyMock.replay(mockOptions, aggregator);
			File result = aggregator.initWorkingDirectory(defaultDir, null, "69");
			Assert.assertEquals(new File(defaultDir, "tester/69"), result);
			Assert.assertTrue(new File(defaultDir, "tester/69").exists());

			// Change bundle id and make sure new bundle dir is create and old one is deleted
			result = aggregator.initWorkingDirectory(defaultDir, null, "70");
			Assert.assertEquals(new File(defaultDir, "tester/70"), result);
			Assert.assertTrue(new File(defaultDir, "tester/70").exists());
			Assert.assertFalse(new File(defaultDir, "tester/69").exists());

			// Make sure that cache directory specified in options is honored
			EasyMock.verify(mockOptions);
			EasyMock.reset(mockOptions);
			EasyMock.expect(mockOptions.getCacheDirectory()).andReturn(optionsDir.toString()).times(1);
			EasyMock.replay(mockOptions);
			result = aggregator.initWorkingDirectory(defaultDir, null, "70");
			Assert.assertEquals(new File(optionsDir, "tester/70"), result);
			Assert.assertTrue(new File(optionsDir, "tester/70").exists());

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
		Map<String, IResource> map = new HashMap<String, IResource>();
		IResource res = new StringResource("test resource", new URI("/test/resource"));
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
		Map<String, IResource> map = new HashMap<String, IResource>();
		IResource res = new StringResource("test resource", new URI("/test/resource"));
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
		Map<String, IResource> map = aggregator.getPathsAndAliases(initParams);
		Assert.assertTrue(map.containsKey("/aliasPath"));
		Assert.assertNull(map.get("/aliasPath"));
		Assert.assertEquals(URI.create("resBaseName"), map.get("/resAlias").getURI());
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

	@Test (expected=NullPointerException.class)
	public void testGetpathsAndAliases_nullResource() {
		TestAggregatorImpl aggregator = EasyMock.createMockBuilder(TestAggregatorImpl.class)
				.addMockedMethod("newResource").createMock();
		EasyMock.expect(aggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				return null;
			}
		}).anyTimes();
		EasyMock.replay(aggregator);
		InitParams initParams = new InitParams(
				Arrays.asList(new InitParam[] {
					new InitParam(InitParams.RESOURCEID_INITPARAM, "resid", aggregator),
					new InitParam("resid:alias", "aliasPath", aggregator),
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
