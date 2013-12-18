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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;

import javax.servlet.http.HttpServletRequest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import com.ibm.jaggr.core.impl.PlatformAggregatorProvider;
import com.ibm.jaggr.core.impl.layer.BaseLayerBuilderTest;
import com.ibm.jaggr.service.PlatformServicesImpl;
import com.ibm.jaggr.service.test.ITestAggregator;
import com.ibm.jaggr.service.test.TestUtils;


public class LayerBuilderTest extends BaseLayerBuilderTest{
	

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
	public void testNotifyLayerListeners() throws Exception {		
		
		ITestAggregator mockAggregator = TestUtils.createMockAggregator();
		final BundleContext mockBundleContext = createMock(BundleContext.class);
		
		PlatformServicesImpl osgiPlatformAggregator = new PlatformServicesImpl(mockBundleContext);			
		PlatformAggregatorProvider.setPlatformAggregator(osgiPlatformAggregator);		
		
		expect(mockAggregator.getBundleContext()).andReturn(mockBundleContext).anyTimes();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator);
		
		ServiceReference mockServiceRef1 = createMock(ServiceReference.class),
				         mockServiceRef2 = createMock(ServiceReference.class);
		ServiceReference[] serviceReferences = new ServiceReference[]{mockServiceRef1, mockServiceRef2}; 		
		
		super.testNotifyLayerListeners(mockAggregator, mockRequest, mockBundleContext, mockServiceRef1, mockServiceRef2, serviceReferences);
		
		
	}
}
