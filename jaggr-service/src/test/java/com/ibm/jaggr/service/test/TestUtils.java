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

import com.ibm.jaggr.service.IAggregator;
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
import com.ibm.jaggr.service.transport.IHttpTransport;

public class TestUtils {
	public static String a = "define([\"./b\"], function(b) {\nalert(\"hello from a.js\");\nreturn null;\n});";
	public static String b = "define([\"./c\"], function(a) {\nalert(\"hello from b.js\");\nreturn null;\n});";
	public static String c = "define([\"./a\", \"./b\", \"./noexist\"], function(a, b, d) {\nalert(\"hello from c.js\");\nreturn null;\n});";
	public static String foo = "define([\"p1/a\", \"p2/p1/b\", \"p2/p1/p1/c\", \"p2/noexist\"], function(a, b, c, noexist) {\n"
			+ "	if (has(\"conditionTrue\")) { \n"
			+ "		require([\"p2/a\"], function(a) {\n"
			+ "			alert(\"condition_True\");\n"
			+ "		});\n"
			+ "	}\n"
			+ "	if (has(\"conditionFalse\")) {\n"
			+ "		alert(\"condition_False\");\n"
			+ "	}\n"
			+ "	return null;\n"
			+ "});";
	public static String err = "/* Comment */define([\"p1/a\", \"p1/b\",], function(){}};";
	static String hello = "Hello world text";

	static public final ModuleDeps emptyDepMap = new ModuleDeps();

	static  public Map<String, ModuleDeps> createTestDepMap() {
		ModuleDeps temp = new ModuleDeps();
		Map<String, ModuleDeps> depMap = new HashMap<String, ModuleDeps>();
		temp.add("p2/b", new ModuleDepInfo(null, null, ""));
		temp.add("p2/c", new ModuleDepInfo(null, null, ""));
		depMap.put("p2/a",  temp);
		temp = new ModuleDeps();
		temp.add("p1/b", new ModuleDepInfo(null, null, ""));
		temp.add("p1/c", new ModuleDepInfo(null, null, ""));
		temp.add("p1/a", new ModuleDepInfo(null, null, ""));
		temp.add("p1/noexist", new ModuleDepInfo(null, null, ""));
		depMap.put("p1/a", temp);
		temp = new ModuleDeps();
		temp.add("p2/p1/p1/a", new ModuleDepInfo(null, null, ""));
		temp.add("p2/p1/p1/b", new ModuleDepInfo(null, null, ""));
		temp.add("p2/p1/p1/noexist", new ModuleDepInfo(null, null, ""));
		temp.add("p2/p1/p1/c", new ModuleDepInfo(null, null, ""));
		depMap.put("p2/p1/p1/c", temp);

		return depMap;
	}
	static public void deleteRecursively(File file) throws InterruptedException {
		boolean deleted = false;
		int retryCount = 5;
		while (!deleted && retryCount-- > 0) {
			if (file.isDirectory()) {
				for (File f : file.listFiles()) {
					deleteRecursively(f);
				}
			}
			deleted = file.delete();
			if (deleted) {
				break;
			}
			Thread.sleep(1000);
		}
		if (!deleted) {
			System.out.println("Failed to delete " + file.getPath());
		}
		assertTrue(deleted);
	}

	static public File createTestFile(File dir, String name, String content)
			throws IOException {
		if (!dir.exists())
			dir.mkdirs();
		String filename = name;
		if (!filename.contains("."))
			filename += ".js";
		File f = new File(dir, filename);
		Writer ow = new FileWriter(f);
		ow.write(content);
		ow.close();
		return f;
	}

	static public void createTestFiles(File tmpdir) throws IOException {
		File p1 = new File(tmpdir, "p1");
		File p2 = new File(tmpdir, "p2");
		File p2p1 = new File(p2, "p1");
		File p2p1p1 = new File(p2p1, "p1");

		createTestFile(p1, "a", a);
		createTestFile(p1, "b", b);
		createTestFile(p1, "c", c);
		createTestFile(p1, "p1", foo);
		createTestFile(p2, "a", a);
		createTestFile(p2, "b", b);
		createTestFile(p2p1, "a", a);
		createTestFile(p2p1, "p1", b);
		createTestFile(p2p1p1, "a", a);
		createTestFile(p2p1p1, "b", b);
		createTestFile(p2p1p1, "c", c);
		createTestFile(p2p1p1, "foo", foo);
		createTestFile(p1, "hello.txt", hello);
	}
	
	static public long getDirListSize(File directory, FileFilter filter) {
		File[] files = directory.listFiles(filter);
		long result = 0;
		for (File file : files) {
			result += file.length();
		}
		return result;
	}

	static public class Ref<T> {
		private T referrant;
		public Ref(T referrant) {
			this.referrant = referrant;
		}
		public T get() {
			return referrant;
		}
		public void set(T referrant) {
			this.referrant = referrant;
		}
	}

	public static IAggregator createMockAggregator() throws Exception {
		return createMockAggregator(null, null, null, null, null);
	}
	
	public static IAggregator createMockAggregator(
			Ref<IConfig> configRef,
			File workingDirectory) throws Exception {
		
		return createMockAggregator(configRef, workingDirectory, null, null, null);
	}

	public static IAggregator createMockAggregator(
			Ref<IConfig> configRef,
			File workingDirectory, List<InitParam> initParams) throws Exception {
		
		return createMockAggregator(configRef, workingDirectory, initParams, null, null);
	}

	public static IAggregator createMockAggregator(
			IHttpTransport transport) throws Exception {
		
		return createMockAggregator(null, null, null, null, transport);
	}
	public static IAggregator createMockAggregator(
			Ref<IConfig> configRef,
			File workingDirectory,
			List<InitParam> initParams,
			Class<?> aggregatorProxyClass,
			IHttpTransport transport) throws Exception {

		final IAggregator mockAggregator = EasyMock.createNiceMock(IAggregator.class);
		IOptions options = new OptionsImpl(null, "test", false);
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
			// ConfigImpl constructor calls IAggregator.newResource()
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
		EasyMock.expect(mockAggregator.substituteProps((String)EasyMock.anyObject(), (IAggregator.SubstitutionTransformer)EasyMock.anyObject())).andAnswer(new IAnswer<String>() {
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
		IAggregator mockAggregatorProxy = mockAggregator;
		if (aggregatorProxyClass != null) {
			mockAggregatorProxy = (IAggregator)aggregatorProxyClass.getConstructor(new Class[]{IAggregator.class}).newInstance(mockAggregator);
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
		EasyMock.expect(mockAggregator.substituteProps((String)EasyMock.anyObject(), (IAggregator.SubstitutionTransformer)EasyMock.anyObject())).andAnswer(new IAnswer<String>() {
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
	
	public static HttpServletRequest createMockRequest(IAggregator aggregator) {
		HttpServletRequest mockRequest = EasyMock.createNiceMock(HttpServletRequest.class);
		EasyMock.expect(mockRequest.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME)).andReturn(aggregator).anyTimes();
		return mockRequest;
	}
	
	public static HttpServletRequest createMockRequest(IAggregator aggregator, Map<String, Object> requestAttributes) {
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, aggregator);
		return createMockRequest(aggregator, requestAttributes, null, null, null);
	}
	
	public static HttpServletRequest createMockRequest(
			IAggregator aggregator,
			final Map<String, Object> requestAttributes, 
			final Map<String, String[]> requestParameters,
			final Cookie[] cookies,
			final Map<String, String> headers) {
		HttpServletRequest mockRequest = EasyMock.createNiceMock(HttpServletRequest.class);
		if (requestAttributes != null) {
			requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, aggregator);
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
			EasyMock.expect(mockRequest.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME)).andReturn(aggregator).anyTimes();
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
	
	public static HttpServletResponse createMockResponse(final Map<String, String> responseAttributes) {
		HttpServletResponse mockResponse = EasyMock.createNiceMock(HttpServletResponse.class);
		mockResponse.setContentLength(EasyMock.anyInt());
		EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
			public Object answer() throws Throwable {
				if (responseAttributes != null) {
					responseAttributes.put("Content-Length", ((Integer)EasyMock.getCurrentArguments()[0]).toString());
				}
				return null;
			}
		}).anyTimes();
		return mockResponse;
	}
}
