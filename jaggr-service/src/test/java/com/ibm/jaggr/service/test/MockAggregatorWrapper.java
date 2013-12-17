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

package com.ibm.jaggr.service.test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import javax.servlet.http.HttpServlet;

import org.easymock.EasyMock;
import org.osgi.framework.BundleContext;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.layer.ILayerCache;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.test.BaseMockAggregatorWrapper;
import com.ibm.jaggr.core.test.BaseTestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;

/**
 * Wrapper class for mock aggregator to make it easy to override
 * methods for test.
 */
public class MockAggregatorWrapper extends BaseMockAggregatorWrapper implements ITestAggregator {	
	
	public MockAggregatorWrapper() throws Exception {
		super();
	}
	
	public MockAggregatorWrapper(Ref<IConfig> configRef,
			File workingDirectory) throws Exception {
		super(configRef, workingDirectory);		
	}
	
	public MockAggregatorWrapper(Ref<IConfig> configRef,
			File workingDirectory,
			List<InitParam> initParams) throws Exception {
		super(configRef, workingDirectory, initParams);		
	}
	
	public MockAggregatorWrapper(IAggregator mock) {
		super(mock);
	}
	
	@Override
	public BundleContext getBundleContext() {		
		return ((ITestAggregator) mock).getBundleContext();
	}

	
}
