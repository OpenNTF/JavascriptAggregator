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
package com.ibm.jaggr.service.impl;

import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.service.test.TestUtils;

import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.File;

public class AbstractAggregatorImplTest {

	@Test
	public void testInitWorkingDirectory() throws Exception {
		File defaultDir = Files.createTempDir();
		File optionsDir = Files.createTempDir();
		try {
			final BundleContext mockContext = EasyMock.createMock(BundleContext.class);
			Bundle mockBundle = EasyMock.createMock(Bundle.class);
			final IOptions mockOptions = EasyMock.createMock(IOptions.class);
			EasyMock.expect(mockContext.getBundle()).andReturn(mockBundle).times(3);
			EasyMock.expect(mockBundle.getBundleId()).andReturn((long)69).times(1);
			EasyMock.expect(mockOptions.getCacheDirectory()).andReturn(null).times(2);
			AbstractAggregatorImpl aggregator = EasyMock.createMockBuilder(AbstractAggregatorImpl.class)
					.addMockedMethod("getOptions")
					.addMockedMethod("getName")
					.addMockedMethod("getBundleContext")
					.createMock();
			EasyMock.expect(aggregator.getOptions()).andAnswer(new IAnswer<IOptions>() {
				public IOptions answer() throws Throwable {
					return mockOptions;
				}
			}).times(3);
			EasyMock.expect(aggregator.getName()).andReturn("tester").times(3);
			EasyMock.expect(aggregator.getBundleContext()).andAnswer(new IAnswer<BundleContext>() {
				public BundleContext answer() throws Throwable {
					return mockContext;
				}
			}).times(3);
			EasyMock.replay(mockContext, mockBundle, mockOptions, aggregator);
			File result = aggregator.initWorkingDirectory(defaultDir, null);
			Assert.assertEquals(new File(defaultDir, "tester/69"), result);
			Assert.assertTrue(new File(defaultDir, "tester/69").exists());
			EasyMock.verify(mockBundle);

			// Change bundle id and make sure new bundle dir is create and old one is deleted
			EasyMock.reset(mockBundle);
			EasyMock.expect(mockBundle.getBundleId()).andReturn((long)70).times(2);
			EasyMock.replay(mockBundle);
			result = aggregator.initWorkingDirectory(defaultDir, null);
			Assert.assertEquals(new File(defaultDir, "tester/70"), result);
			Assert.assertTrue(new File(defaultDir, "tester/70").exists());
			Assert.assertFalse(new File(defaultDir, "tester/69").exists());

			// Make sure that cache directory specified in options is honored
			EasyMock.verify(mockOptions);
			EasyMock.reset(mockOptions);
			EasyMock.expect(mockOptions.getCacheDirectory()).andReturn(optionsDir.toString()).times(1);
			EasyMock.replay(mockOptions);
			result = aggregator.initWorkingDirectory(defaultDir, null);
			Assert.assertEquals(new File(optionsDir, "tester/70"), result);
			Assert.assertTrue(new File(optionsDir, "tester/70").exists());

			EasyMock.verify(mockContext, mockBundle, mockOptions, aggregator);
		} finally {
			TestUtils.deleteRecursively(defaultDir);
			TestUtils.deleteRecursively(optionsDir);
		}
	}

}
