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
import com.ibm.jaggr.core.test.BaseTestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;

/**
 * Wrapper class for mock aggregator to make it easy to override
 * methods for test.
 */
public class MockAggregatorWrapper implements ITestAggregator {
	
	protected ITestAggregator mock;
	
	public MockAggregatorWrapper() throws Exception {
		mock = TestUtils.createMockAggregator();
		EasyMock.replay(mock);
	}
	
	public MockAggregatorWrapper(Ref<IConfig> configRef,
			File workingDirectory) throws Exception {
		mock = TestUtils.createMockAggregator(configRef, workingDirectory);
		EasyMock.replay(mock);
	}
	
	public MockAggregatorWrapper(Ref<IConfig> configRef,
			File workingDirectory,
			List<InitParam> initParams) throws Exception {
		mock = TestUtils.createMockAggregator(configRef, workingDirectory, initParams);
		EasyMock.replay(mock);
	}
	
	public MockAggregatorWrapper(ITestAggregator mock) {
		this.mock = mock;
	}
	
	@Override
	public String getName() {
		return mock.getName();
	}

	@Override
	public IConfig getConfig() {
		return mock.getConfig();
	}

	@Override
	public IOptions getOptions() {
		return mock.getOptions();
	}

	@Override
	public IExecutors getExecutors() {
		return mock.getExecutors();
	}

	@Override
	public ICacheManager getCacheManager() {
		return mock.getCacheManager();
	}

	@Override
	public IDependencies getDependencies() {
		return mock.getDependencies();
	}

	@Override
	public IHttpTransport getTransport() {
		return mock.getTransport();
	}

	@Override
	public BundleContext getBundleContext() {
		System.out.println("entered in bundle context");
		return mock.getBundleContext();
	}

	@Override
	public IResource newResource(URI uri) {
		return mock.newResource(uri);
	}

	@Override
	public IModuleBuilder getModuleBuilder(String mid, IResource res) {
		return mock.getModuleBuilder(mid, res);
	}

	@Override
	public InitParams getInitParams() {
		return mock.getInitParams();
	}

	@Override
	public File getWorkingDirectory() {
		return mock.getWorkingDirectory();
	}

	@Override
	public boolean reloadConfig() throws IOException {
		return mock.reloadConfig();
	}

	@Override
	public HttpServlet asServlet() {
		return mock.asServlet();
	}

	@Override
	public Iterable<IAggregatorExtension> getResourceFactoryExtensions() {
		return mock.getResourceFactoryExtensions();
	}

	@Override
	public Iterable<IAggregatorExtension> getModuleBuilderExtensions() {
		return mock.getModuleBuilderExtensions();
	}

	@Override
	public IAggregatorExtension getHttpTransportExtension() {
		return mock.getHttpTransportExtension();
	}

	@Override
	public IModule newModule(String mid, URI uri) {
		return mock.newModule(mid, uri);
	}

	@Override
	public ILayerCache newLayerCache() {
		return mock.newLayerCache();
	}

	@Override
	public IModuleCache newModuleCache() {
		return mock.newModuleCache();
	}

	@Override
	public String substituteProps(String str) {
		return mock.substituteProps(str);
	}

	@Override
	public String substituteProps(String str,
			SubstitutionTransformer transformer) {
		return mock.substituteProps(str, transformer);
	}

}
