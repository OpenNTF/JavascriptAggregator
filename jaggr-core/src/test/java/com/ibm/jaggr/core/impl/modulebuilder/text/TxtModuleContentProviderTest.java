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

package com.ibm.jaggr.core.impl.modulebuilder.text;

import static org.junit.Assert.assertEquals;

import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.impl.resource.FileResource;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.transport.IHttpTransport;

import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class TxtModuleContentProviderTest extends EasyMock {

	File tmpdir = null;

	@BeforeClass
	public static void setupBeforeClass() {
	}

	@Before
	public void setup() {
	}
	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	@Test
	public void testGetCacheKeyGenerator() throws Exception {
		TextModuleBuilder builder = new TextModuleBuilder();
		List<ICacheKeyGenerator> generators = builder.getCacheKeyGenerators(null);
		HttpServletRequest mockRequest = TestUtils.createMockRequest(TestUtils.createMockAggregator());
		replay(mockRequest);
		String key = KeyGenUtil.generateKey(mockRequest, generators);
		Assert.assertEquals("txt:0", key);
	}

	@Test
	public void testGetContentReader() throws Exception {
		tmpdir = Files.createTempDir();
		File test = new File(tmpdir, "test.txt");
		Writer ow = new FileWriter(test);
		ow.write("'This is a test'. ''  Test's'");
		ow.close();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(TestUtils.createMockAggregator());
		replay(mockRequest);
		TextModuleBuilder builder = new TextModuleBuilder();
		String code = builder.build(
				"test.txt",
				new FileResource(test.toURI()),
				mockRequest, null).getBuildOutput().toString();
		System.out.println(code);
		assertEquals("'\\'This is a test\\'. \\'\\'  Test\\'s\\''", code);
	}
}
