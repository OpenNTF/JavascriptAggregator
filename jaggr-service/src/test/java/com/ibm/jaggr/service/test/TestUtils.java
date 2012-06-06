/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.wink.json4j.JSONException;
import org.easymock.EasyMock;
import org.easymock.IAnswer;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.executors.IExecutors;
import com.ibm.jaggr.service.impl.cache.CacheManagerImpl;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.executors.ExecutorsImpl;
import com.ibm.jaggr.service.impl.module.ModuleImpl;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.JavaScriptModuleBuilder;
import com.ibm.jaggr.service.impl.modulebuilder.text.TextModuleBuilder;
import com.ibm.jaggr.service.impl.options.OptionsImpl;
import com.ibm.jaggr.service.impl.resource.FileResource;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.transport.IHttpTransport;

public class TestUtils {
	static public final Map<String, Map<String, String>> testDepMap = new HashMap<String, Map<String, String>>();
	static public final Map<String, String> emptyDepMap = new HashMap<String, String>();
	
	static {
		Map<String,String> temp = new HashMap<String, String>();
		temp.put("p2/b", "");
		temp.put("p2/c", "");
		testDepMap.put("p2/a",  temp);
		temp = new HashMap<String, String>();
		temp.put("p1/b", "");
		temp.put("p1/c", "");
		temp.put("p1/a", "");
		temp.put("p1/noexist", "");
		testDepMap.put("p1/a", temp);
		temp = new HashMap<String, String>();
		temp.put("p2/p1/p1/a", "");
		temp.put("p2/p1/p1/b", "");
		temp.put("p2/p1/p1/noexist", "");
		temp.put("p2/p1/p1/c", "");
		testDepMap.put("p2/p1/p1/c", temp);
	}
	
	
	static String a = "define([\"./b\"], function(b) {\nalert(\"hello from a.js\");\nreturn null;\n});";
	static String b = "define([\"./c\"], function(a) {\nalert(\"hello from b.js\");\nreturn null;\n});";
	static String c = "define([\"./a\", \"./b\", \"./noexist\"], function(a, b, d) {\nalert(\"hello from c.js\");\nreturn null;\n});";
	static String foo = "define([\"p1/a\", \"p2/p1/b\", \"p2/p1/p1/c\", \"p2/noexist\"], function(a, b, c, noexist) {\n"
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
	static String hello = "Hello world text";

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
		return createMockAggregator(null, null);
	}
	

	public static IAggregator createMockAggregator(
			Ref<IConfig> configRef, 
			File workingDirectory) throws IOException, JSONException {

		IAggregator mockAggregator = EasyMock.createNiceMock(IAggregator.class);
		IOptions options = new OptionsImpl(null, "test", false);
		options.setOption(IOptions.DELETE_DELAY, "0");
		boolean createConfig = (configRef == null);
		if (workingDirectory == null) {
			workingDirectory = new File(System.getProperty("java.io.tmpdir"));
		}
		final Ref<ICacheManager> cacheMgrRef = new Ref<ICacheManager>(null);
		final Ref<IHttpTransport> transportRef = new Ref<IHttpTransport>(EasyMock.createNiceMock(IHttpTransport.class));
		final Ref<IExecutors> executorsRef = new Ref<IExecutors>(new ExecutorsImpl(null,
				new SynchronousExecutor(), 
				null, 
				new SynchronousScheduledExecutor(), 
				new SynchronousScheduledExecutor()));
		
		EasyMock.replay(transportRef.get());
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
		EasyMock.replay(mockAggregator);
		cacheMgrRef.set(new CacheManagerImpl(mockAggregator));
		//((IOptionsListener)cacheMgrRef.get()).optionsUpdated(options, 1);
		if (createConfig) {
			configRef.set(new ConfigImpl(mockAggregator, workingDirectory.toURI(), "{}"));
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

		return mockAggregator;
	}
	
	public static HttpServletRequest createMockRequest(IAggregator aggregator) {
		HttpServletRequest mockRequest = EasyMock.createNiceMock(HttpServletRequest.class);
		EasyMock.expect(mockRequest.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME)).andReturn(aggregator).anyTimes();
		return mockRequest;
	}
	
	public static HttpServletRequest createMockRequest(IAggregator aggregator, Map<String, Object> requestAttributes) {
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, aggregator);
		return createMockRequest(aggregator, requestAttributes, null);
	}
	
	public static HttpServletRequest createMockRequest(
			IAggregator aggregator,
			final Map<String, Object> requestAttributes, 
			final Map<String, String> requestParameters) {
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
					return requestParameters.get((String)EasyMock.getCurrentArguments()[0]);
				}
			}).anyTimes();
		}
		return mockRequest;
	}
}
