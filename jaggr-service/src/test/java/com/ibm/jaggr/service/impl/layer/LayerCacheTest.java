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

package com.ibm.jaggr.service.impl.layer;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.google.common.io.Files;
import com.ibm.jaggr.core.impl.PlatformAggregatorProvider;
import com.ibm.jaggr.core.impl.layer.BaseLayerCacheTest;
import com.ibm.jaggr.service.PlatformServicesImpl;
import com.ibm.jaggr.service.test.TestUtils;

public class LayerCacheTest extends BaseLayerCacheTest{	

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = Files.createTempDir();
		TestUtils.createTestFiles(tmpdir);
		PlatformServicesImpl osgiPlatformAggregator = new PlatformServicesImpl(null);			
		PlatformAggregatorProvider.setPlatformAggregator(osgiPlatformAggregator);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	@Test
	public void test() throws Exception {
		super.test();
	}
	
	@Test
	public void testGetMaxCapacity() throws Exception {
		super.testGetMaxCapacity();
	}	
}
