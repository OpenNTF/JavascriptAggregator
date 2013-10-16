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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.easymock.IAnswer;

//import com.ibm.jaggr.service.ITestAggregator;
//import com.ibm.jaggr.osgi.service.impl.ITestAggregator;
import com.ibm.jaggr.service.InitParams;
import com.ibm.jaggr.service.InitParams.InitParam;
import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.deps.ModuleDepInfo;
import com.ibm.jaggr.service.deps.ModuleDeps;
import com.ibm.jaggr.service.executors.IExecutors;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.executors.ExecutorsImpl;
import com.ibm.jaggr.service.impl.layer.LayerCacheImpl;
import com.ibm.jaggr.service.impl.module.ModuleCacheImpl;
import com.ibm.jaggr.service.impl.module.ModuleImpl;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.JavaScriptModuleBuilder;
import com.ibm.jaggr.service.impl.modulebuilder.text.TextModuleBuilder;
import com.ibm.jaggr.service.impl.options.OptionsImpl;
import com.ibm.jaggr.service.impl.resource.FileResource;
import com.ibm.jaggr.service.impl.transport.DojoHttpTransport;
import com.ibm.jaggr.service.layer.ILayerCache;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.module.IModuleCache;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.test.SynchronousExecutor;
import com.ibm.jaggr.service.test.SynchronousScheduledExecutor;
import com.ibm.jaggr.service.test.TestCacheManager;
import com.ibm.jaggr.service.transport.IHttpTransport;

public class TestUtils extends com.ibm.jaggr.service.test.BaseTestUtils{
	

	public static ITestAggregator createMockAggregator() throws Exception {
		return createMockAggregator(null, null, null, null, null);
	}
	
	public static ITestAggregator createMockAggregator(
			Ref<IConfig> configRef,
			File workingDirectory) throws Exception {
		
		return createMockAggregator(configRef, workingDirectory, null, null, null);
	}

	public static ITestAggregator createMockAggregator(
			Ref<IConfig> configRef,
			File workingDirectory, List<InitParam> initParams) throws Exception {
		
		return createMockAggregator(configRef, workingDirectory, initParams, null, null);
	}

	public static ITestAggregator createMockAggregator(
			IHttpTransport transport) throws Exception {
		
		return createMockAggregator(null, null, null, null, transport);
	}
	public static ITestAggregator createMockAggregator(
			Ref<IConfig> configRef,
			File workingDirectory,
			List<InitParam> initParams,
			Class<?> aggregatorProxyClass,
			IHttpTransport transport) throws Exception {

		final ITestAggregator mockAggregator = EasyMock.createNiceMock(ITestAggregator.class);
		IOptions options = new OptionsImpl("test", false);
		options.setOption(IOptions.DELETE_DELAY, "0");
		if (initParams == null) {
			initParams = new LinkedList<InitParam>();
		}
		final InitParams aggInitParams = new InitParams(initParams);
		boolean createConfig = (configRef == null);
		if (workingDirectory == null) {
			workingDirectory = new File(System.getProperty("java.io.tmpdir"));
		}
		final Ref<ICacheManager> cacheMgrRef = new Ref<ICacheManager>(null);
		final Ref<IHttpTransport> transportRef = new Ref<IHttpTransport>(transport == null ? new DojoHttpTransport() : transport);
		System.out.println("transportRef" + transportRef);
		final Ref<IExecutors> executorsRef = new Ref<IExecutors>(new ExecutorsImpl(null,
				new SynchronousExecutor(), 
				null, 
				new SynchronousScheduledExecutor(), 
				new SynchronousScheduledExecutor()));
		
		EasyMock.expect(mockAggregator.getWorkingDirectory()).andReturn(workingDirectory).anyTimes();
		EasyMock.expect(mockAggregator.getName()).andReturn("test").anyTimes();
		EasyMock.expect(mockAggregator.getOptions()).andReturn(options).anyTimes();
		EasyMock.expect(mockAggregator.getExecutors()).andAnswer(new IAnswer<IExecutors>() {
			public IExecutors answer() throws Throwable {
				return executorsRef.get();
			}
		}).anyTimes();
		if (createConfig) {
			configRef = new Ref<IConfig>(null);
			// ConfigImpl constructor calls ITestAggregator.newResource()
			EasyMock.expect(mockAggregator.newResource((URI)EasyMock.anyObject())).andAnswer(new IAnswer<IResource>() {
				public IResource answer() throws Throwable {
					return new FileResource((URI)EasyMock.getCurrentArguments()[0]);
				}
			}).anyTimes();
		}
		EasyMock.expect(mockAggregator.substituteProps((String)EasyMock.anyObject())).andAnswer(new IAnswer<String>() {
			public String answer() throws Throwable {
				return (String)EasyMock.getCurrentArguments()[0];
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.substituteProps((String)EasyMock.anyObject(), (ITestAggregator.SubstitutionTransformer)EasyMock.anyObject())).andAnswer(new IAnswer<String>() {
			public String answer() throws Throwable {
				return (String)EasyMock.getCurrentArguments()[0];
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.newLayerCache()).andAnswer(new IAnswer<ILayerCache>() {
			public ILayerCache answer() throws Throwable {
				return new LayerCacheImpl(mockAggregator);
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.newModuleCache()).andAnswer(new IAnswer<IModuleCache>() {
			public IModuleCache answer() throws Throwable {
				return new ModuleCacheImpl();
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.getInitParams()).andAnswer(new IAnswer<InitParams>() {
			public InitParams answer() throws Throwable {
				return aggInitParams;
			}
		}).anyTimes();
		EasyMock.replay(mockAggregator);
		ITestAggregator mockAggregatorProxy = mockAggregator;
		if (aggregatorProxyClass != null) {
			mockAggregatorProxy = (ITestAggregator)aggregatorProxyClass.getConstructor(new Class[]{ITestAggregator.class}).newInstance(mockAggregator);
		}
		TestCacheManager cacheMgr = new TestCacheManager(mockAggregatorProxy, 1);
		cacheMgrRef.set(cacheMgr);
		//((IOptionsListener)cacheMgrRef.get()).optionsUpdated(options, 1);
		if (createConfig) {
			configRef.set(new ConfigImpl(mockAggregatorProxy, workingDirectory.toURI(), "{}"));
		}
		EasyMock.reset(mockAggregator);
		EasyMock.expect(mockAggregator.getWorkingDirectory()).andReturn(workingDirectory).anyTimes();
		EasyMock.expect(mockAggregator.getOptions()).andReturn(options).anyTimes();
		EasyMock.expect(mockAggregator.getName()).andReturn("test").anyTimes();
		EasyMock.expect(mockAggregator.getTransport()).andAnswer(new IAnswer<IHttpTransport>() {
			public IHttpTransport answer() throws Throwable {
				return transportRef.get();
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.newResource((URI)EasyMock.anyObject())).andAnswer(new IAnswer<IResource>() {
			public IResource answer() throws Throwable {
				return new FileResource((URI)EasyMock.getCurrentArguments()[0]);
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.getModuleBuilder((String)EasyMock.anyObject(), (IResource)EasyMock.anyObject())).andAnswer(new IAnswer<IModuleBuilder>() {
			public IModuleBuilder answer() throws Throwable {
				String mid = (String)EasyMock.getCurrentArguments()[0];
				return mid.contains(".") ? new TextModuleBuilder() : new JavaScriptModuleBuilder();
			}
		}).anyTimes();
		final Ref<IConfig> cfgRef = configRef;
		EasyMock.expect(mockAggregator.getConfig()).andAnswer(new IAnswer<IConfig>() {
			public IConfig answer() throws Throwable {
				return cfgRef.get();
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.getCacheManager()).andAnswer(new IAnswer<ICacheManager>() {
			public ICacheManager answer() throws Throwable {
				return cacheMgrRef.get();
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.newModule((String)EasyMock.anyObject(), (URI)EasyMock.anyObject())).andAnswer(new IAnswer<IModule>() {
			public IModule answer() throws Throwable {
				String mid = (String)EasyMock.getCurrentArguments()[0];
				URI uri = (URI)EasyMock.getCurrentArguments()[1];
				return new ModuleImpl(mid, uri);
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.getExecutors()).andAnswer(new IAnswer<IExecutors>() {
			public IExecutors answer() throws Throwable {
				return executorsRef.get();
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.newLayerCache()).andAnswer(new IAnswer<ILayerCache>() {
			public ILayerCache answer() throws Throwable {
				return new LayerCacheImpl(mockAggregator);
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.newModuleCache()).andAnswer(new IAnswer<IModuleCache>() {
			public IModuleCache answer() throws Throwable {
				return new ModuleCacheImpl();
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.substituteProps((String)EasyMock.anyObject())).andAnswer(new IAnswer<String>() {
			public String answer() throws Throwable {
				return (String)EasyMock.getCurrentArguments()[0];
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.substituteProps((String)EasyMock.anyObject(), (ITestAggregator.SubstitutionTransformer)EasyMock.anyObject())).andAnswer(new IAnswer<String>() {
			public String answer() throws Throwable {
				return (String)EasyMock.getCurrentArguments()[0];
			}
		}).anyTimes();
		EasyMock.expect(mockAggregator.getInitParams()).andAnswer(new IAnswer<InitParams>() {
			public InitParams answer() throws Throwable {
				return aggInitParams;
			}
		}).anyTimes();
		return mockAggregator;
	}
	
	public static HttpServletRequest createMockRequest(ITestAggregator aggregator) {
		HttpServletRequest mockRequest = EasyMock.createNiceMock(HttpServletRequest.class);
		EasyMock.expect(mockRequest.getAttribute(ITestAggregator.AGGREGATOR_REQATTRNAME)).andReturn(aggregator).anyTimes();
		return mockRequest;
	}
	
	public static HttpServletRequest createMockRequest(ITestAggregator aggregator, Map<String, Object> requestAttributes) {
		requestAttributes.put(ITestAggregator.AGGREGATOR_REQATTRNAME, aggregator);
		return createMockRequest(aggregator, requestAttributes, null, null, null);
	}
	
	public static HttpServletRequest createMockRequest(
			ITestAggregator aggregator,
			final Map<String, Object> requestAttributes, 
			final Map<String, String[]> requestParameters,
			final Cookie[] cookies,
			final Map<String, String> headers) {
		HttpServletRequest mockRequest = EasyMock.createNiceMock(HttpServletRequest.class);
		if (requestAttributes != null) {
			requestAttributes.put(ITestAggregator.AGGREGATOR_REQATTRNAME, aggregator);
			EasyMock.expect(mockRequest.getAttribute((String)EasyMock.anyObject())).andAnswer(new IAnswer<Object>() {
				public Object answer() throws Throwable {
					return requestAttributes.get((String)EasyMock.getCurrentArguments()[0]);
				}
			}).anyTimes();
			mockRequest.setAttribute((String)EasyMock.anyObject(), EasyMock.anyObject());
			EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
				public Object answer() throws Throwable {
					String name = (String)EasyMock.getCurrentArguments()[0];
					Object value = EasyMock.getCurrentArguments()[1];
					requestAttributes.put(name, value);
					return null;
				}
			}).anyTimes();
			mockRequest.removeAttribute((String)EasyMock.anyObject());
			EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
				public Object answer() throws Throwable {
					String name = (String)EasyMock.getCurrentArguments()[0];
					requestAttributes.remove(name);
					return null;
				}
			}).anyTimes();
		} else {
			EasyMock.expect(mockRequest.getAttribute(ITestAggregator.AGGREGATOR_REQATTRNAME)).andReturn(aggregator).anyTimes();
		}
		if (requestParameters != null) {
			EasyMock.expect(mockRequest.getParameter((String)EasyMock.anyObject())).andAnswer(new IAnswer<String>() {
				public String answer() throws Throwable {
					String [] ary = requestParameters.get((String)EasyMock.getCurrentArguments()[0]);
					return ary != null && ary.length > 0 ? ary[0] : null;
				}
			}).anyTimes();
		}
		if (cookies != null) {
			EasyMock.expect(mockRequest.getCookies()).andAnswer(new IAnswer<Cookie[]>() {
				public Cookie[] answer() throws Throwable {
					return cookies;
				}
			}).anyTimes();
		}
		if (headers != null) {
			EasyMock.expect(mockRequest.getHeader((String)EasyMock.anyObject())).andAnswer(new IAnswer<String>() {
				public String answer() throws Throwable {
					return headers.get((String)EasyMock.getCurrentArguments()[0]);
				}
			}).anyTimes();
		}
		return mockRequest;
	}
}
