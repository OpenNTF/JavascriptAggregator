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

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.AggregatorLayerListener;
import com.ibm.jaggr.core.impl.PlatformAggregatorProvider;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.impl.layer.BaseLayerTest;
import com.ibm.jaggr.core.impl.layer.LayerImpl;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.service.PlatformServicesImpl;
import com.ibm.jaggr.service.test.ITestAggregator;
import com.ibm.jaggr.service.test.TestUtils;

@RunWith(PowerMockRunner.class)
@PrepareForTest(LayerImpl.class)
public class LayerTest extends BaseLayerTest {

	BundleContext mockBundleContext;
	
	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		mockBundleContext = null;
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParameters, null, requestHeaders);
		expect(mockAggregator.getDependencies()).andAnswer(new IAnswer<IDependencies>() {
			public IDependencies answer() throws Throwable {
				return mockDependencies;
			}
		}).anyTimes();
		
		expect(((ITestAggregator) mockAggregator).getBundleContext()).andAnswer(new IAnswer<BundleContext>() {
			@Override
			public BundleContext answer() throws Throwable {
				return mockBundleContext;
			}
		}).anyTimes();
		
		expect(mockDependencies.getDelcaredDependencies(eq("p1/p1"))).andReturn(Arrays.asList(new String[]{"p1/a", "p2/p1/b", "p2/p1/p1/c", "p2/noexist"})).anyTimes();
		expect(mockDependencies.getDelcaredDependencies(eq("p1/a"))).andReturn(Arrays.asList(new String[]{"p1/b"})).anyTimes();
		expect(mockDependencies.getExpandedDependencies((String)EasyMock.anyObject(), (Features)EasyMock.anyObject(), (Set<String>)EasyMock.anyObject(), EasyMock.anyBoolean(), EasyMock.anyBoolean())).andAnswer(new IAnswer<ModuleDeps>() {
			public ModuleDeps answer() throws Throwable {
				String name = (String)EasyMock.getCurrentArguments()[0];
				Features features = (Features)EasyMock.getCurrentArguments()[1];
				Set<String> dependentFeatures = (Set<String>)EasyMock.getCurrentArguments()[2];
				ModuleDeps result = testDepMap.get(name);
				if (result == null) {
					result = TestUtils.emptyDepMap;
				}
				// resolve aliases
				ModuleDeps temp = new ModuleDeps();
				IConfig config = mockAggregator.getConfig();
				for (Map.Entry<String, ModuleDepInfo> entry : result.entrySet()) {
					String depName = entry.getKey();
					String resolved = config.resolve(depName, features, dependentFeatures, null, true);
					temp.add(resolved != null ? resolved : depName, entry.getValue());
				}
				return temp;
			}
		}).anyTimes();
		
		URI p1Path = new File(tmpdir, "p1").toURI();
		URI p2Path = new File(tmpdir, "p2").toURI();
		final Map<String, URI> map = new HashMap<String, URI>();
		map.put("p1", p1Path);
		map.put("p2", p2Path);

		replay(mockAggregator, mockRequest, mockResponse, mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}, packages:[{name:'foo', location:'foo'}]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));

/*
 	    Enable this code to display FINEST level logging for these tests in the
 	    Eclipse console.
 	    
	    //get the top Logger:
	    Logger topLogger = java.util.logging.Logger.getLogger("");

	    // Handler for console (reuse it if it already exists)
	    Handler consoleHandler = null;
	    //see if there is already a console handler
	    for (Handler handler : topLogger.getHandlers()) {
	        if (handler instanceof ConsoleHandler) {
	            //found the console handler
	            consoleHandler = handler;
	            break;
	        }
	    }
	    if (consoleHandler == null) {
	        //there was no console handler found, create a new one
	        consoleHandler = new ConsoleHandler();
	        topLogger.addHandler(consoleHandler);
	    }
	    //set the console handler to fine:
	    consoleHandler.setLevel(java.util.logging.Level.FINEST);	
	    LayerImpl.log.setLevel(Level.FINEST);
*/	    
	}
	@After
	public void tearDown() throws Exception {
		mockAggregator.getCacheManager().clearCache();
		for (File file : mockAggregator.getCacheManager().getCacheDir().listFiles()) {
			if (file.getName().startsWith("layer.")) {
				file.delete();
			}
		}
	}

	/**
	 * Test method for {@link com.ibm.jaggr.core.impl.layer.LayerImpl#getInputStream(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.ibm.jaggr.service.config.IConfig, long)}.
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetInputStream() throws Exception {		
		final AggregatorLayerListener layerListener = new AggregatorLayerListener(mockAggregator);
		mockBundleContext = createNiceMock(BundleContext.class);
		ServiceReference mockServiceReference = createMock(ServiceReference.class);
		final ServiceReference[] serviceReferences = new ServiceReference[]{mockServiceReference};
		expect(mockBundleContext.getServiceReferences(ILayerListener.class.getName(), "(name=test)")).andAnswer(new IAnswer<ServiceReference[]>() {
			@Override public ServiceReference[] answer() throws Throwable {
				return serviceReferences;
			}
		}).anyTimes();
		expect(mockBundleContext.getService(mockServiceReference)).andReturn(layerListener).anyTimes();
		replay(mockBundleContext, mockServiceReference);
		
		PlatformServicesImpl osgiPlatformAggregator = new PlatformServicesImpl(mockBundleContext);		
		PlatformAggregatorProvider.setPlatformAggregator(osgiPlatformAggregator);		
		super.testGetInputStream();		
	}
	
	/**
	 * Test method for {@link com.ibm.jaggr.core.impl.layer.LayerImpl#toString()}.
	 */
	@Test
	public void testToString() throws Exception {
		PlatformServicesImpl osgiPlatformAggregator = new PlatformServicesImpl(null);			
		PlatformAggregatorProvider.setPlatformAggregator(osgiPlatformAggregator);		
		super.testToString();
	}	
	
	@Test
	public void gzipTests() throws Exception {		
		PlatformServicesImpl osgiPlatformAggregator = new PlatformServicesImpl(null);		
		PlatformAggregatorProvider.setPlatformAggregator(osgiPlatformAggregator);
		super.gzipTests();		
	}	
}
