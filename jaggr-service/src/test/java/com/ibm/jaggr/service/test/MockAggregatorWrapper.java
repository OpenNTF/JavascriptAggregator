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

import org.apache.wink.json4j.JSONException;
import org.easymock.EasyMock;
import org.osgi.framework.BundleContext;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IAggregatorExtension;
import com.ibm.jaggr.service.InitParams;
import com.ibm.jaggr.service.InitParams.InitParam;
import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.executors.IExecutors;
import com.ibm.jaggr.service.layer.ILayerCache;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.module.IModuleCache;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.test.TestUtils.Ref;
import com.ibm.jaggr.service.transport.IHttpTransport;

/**
 * Wrapper class for mock aggregator to make it easy to override
 * methods for test.
 */
public class MockAggregatorWrapper implements IAggregator {
	
	IAggregator mock;
	
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
			List<InitParam> initParams) throws IOException, JSONException {
		mock = TestUtils.createMockAggregator(configRef, workingDirectory, initParams);
		EasyMock.replay(mock);
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

}
