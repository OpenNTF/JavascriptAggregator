/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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

package com.ibm.jaggr.core.impl.layer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigScopeModifier;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.impl.AggregatorLayerListener;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.impl.module.NotFoundModule;
import com.ibm.jaggr.core.impl.transport.AbstractHttpTransport;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.test.MockRequestedModuleNames;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.Features;

import com.google.common.io.Files;
import com.google.common.net.HttpHeaders;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.zip.Deflater;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

@RunWith(PowerMockRunner.class)
@PrepareForTest(LayerImpl.class)
public class LayerTest extends EasyMock {

	static int id = 0;
	static File tmpdir = null;
	static Pattern layerPattern = Pattern.compile("[/\\\\]layer\\.[0-9]*\\.cache$");
	static FileFilter layerFilter = new FileFilter() {
		@Override public boolean accept(File pathname) {
			return  layerPattern.matcher(pathname.getPath()).find();
		}
	};

	IAggregator mockAggregator;
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	Map<String, String[]> requestParameters = new HashMap<String, String[]>();
	Map<String, String> requestHeaders = new HashMap<String, String>();
	Map<String, String> responseAttributes = new HashMap<String, String>();
	HttpServletRequest mockRequest;
	HttpServletResponse mockResponse = TestUtils.createMockResponse(responseAttributes);
	IDependencies mockDependencies = createNiceMock(IDependencies.class);
	Map<String, String[]> testDepMap;
	IPlatformServices mockPlatformServices;


	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = Files.createTempDir();
		TestUtils.createTestFiles(tmpdir);
		LayerImpl.LAYERBUILD_REMOVE_DELAY_SECONDS = 0;
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	static long lastModified = 0;

	@Before
	public void setup() throws Exception {
		//mockBundleContext = null;
		mockPlatformServices = null;
		testDepMap = TestUtils.createTestDepMap();
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParameters, null, requestHeaders);
		expect(mockAggregator.getDependencies()).andAnswer(new IAnswer<IDependencies>() {
			public IDependencies answer() throws Throwable {
				return mockDependencies;
			}
		}).anyTimes();

		expect(mockDependencies.getLastModified()).andReturn(0L).anyTimes();
		expect(mockDependencies.getDelcaredDependencies(isA(String.class))).andAnswer(new IAnswer<List<String>>() {
			@Override
			public List<String> answer() throws Throwable {
				String name = (String)getCurrentArguments()[0];
				String[] result = testDepMap.get(name);
				return result != null ? Arrays.asList(result) : null;
			}
		}).anyTimes();

		URI p1Path = new File(tmpdir, "p1").toURI();
		URI p2Path = new File(tmpdir, "p2").toURI();
		final Map<String, URI> map = new HashMap<String, URI>();
		map.put("p1", p1Path);
		map.put("p2", p2Path);

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
	 * Test method for {@link com.ibm.jaggr.core.impl.layer.LayerImpl#getInputStream(HttpServletRequest, HttpServletResponse)}.
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetInputStream() throws Exception {

		final AggregatorLayerListener layerListener = new AggregatorLayerListener(mockAggregator);
		mockPlatformServices = createMock(IPlatformServices.class);
		IServiceReference mockServiceReference = createMock(IServiceReference.class);
		final IServiceReference[] serviceReferences = new IServiceReference[]{mockServiceReference};
		expect(mockPlatformServices.getServiceReferences(IConfigScopeModifier.class.getName(), "(name=test)")).andReturn(new IServiceReference[]{}).anyTimes();
		expect(mockPlatformServices.getServiceReferences(ILayerListener.class.getName(), "(name=test)")).andAnswer(new IAnswer<IServiceReference[]>() {
			@Override public IServiceReference[] answer() throws Throwable {
				return serviceReferences;
			}
		}).anyTimes();
		expect(mockPlatformServices.getServiceReferences(IOptionsListener.class.getName(), "(name=test)")).andAnswer(new IAnswer<IServiceReference[]>() {
			@Override public IServiceReference[] answer() throws Throwable {
				return null;
			}
		}).anyTimes();
		expect(mockPlatformServices.getService(mockServiceReference)).andReturn(layerListener).anyTimes();
		expect(mockPlatformServices.ungetService(mockServiceReference)).andReturn(true).anyTimes();
		expect(mockPlatformServices.getHeaders()).andReturn(null).anyTimes();
		expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();

		replay(mockAggregator, mockRequest, mockResponse, mockDependencies,mockPlatformServices, mockServiceReference);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}, packages:[{name:'foo', location:'foo'}]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson, true));

		// Request a single module
		File cacheDir = mockAggregator.getCacheManager().getCacheDir();
		ConcurrentLinkedHashMap<String, CacheEntry> cacheMap = (ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap();
		long totalSize = 0;
		MockRequestedModuleNames modules = new MockRequestedModuleNames();
		modules.setModules(Arrays.asList(new String[]{"p1/a"}));
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		List<String> layerCacheInfo = new LinkedList<String>();
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		Writer writer = new StringWriter();
		CopyUtil.copy(in, writer);
		String result = writer.toString();
		System.out.println(result);
		totalSize += result.length();
		assertEquals("[update_lastmod1, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));
		Map<String, String> moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertTrue(result.contains("\"hello from a.js\""));

		// Request two modules
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		modules.setModules(Arrays.asList(new String[]{"p1/b", "p1/a"}));
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		layer = newLayerImpl(modules.toString(), mockAggregator);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_lastmod1, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("add", moduleCacheInfo.get("p1/b"));
		assertTrue(result.contains("\"hello from a.js\""));
		assertTrue(result.contains("\"hello from b.js\""));
		totalSize += result.length();
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

		// Add a text resource
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		modules.setModules(Arrays.asList(new String[]{"p1/b","p1/a","combo/text!p1/hello.txt"}));
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		layer = newLayerImpl(modules.toString(), mockAggregator);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_lastmod1, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("add", moduleCacheInfo.get("combo/text!p1/hello.txt"));
		assertTrue(result.contains("\"hello from a.js\""));
		assertTrue(result.contains("\"hello from b.js\""));
		assertTrue(result.contains("Hello world text"));
		totalSize += result.length();
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

		// Test filename prologue option
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, "true");
		layer = newLayerImpl(modules.toString(), mockAggregator);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_lastmod1, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("combo/text!p1/hello.txt"));
		assertTrue(result.contains(String.format(AggregatorLayerListener.PREAMBLEFMT, new File(tmpdir, "p1/a.js").toURI())));
		assertTrue(result.contains(String.format(AggregatorLayerListener.PREAMBLEFMT, new File(tmpdir, "p1/b.js").toURI())));
		assertTrue(result.contains(String.format(AggregatorLayerListener.PREAMBLEFMT, new File(tmpdir, "p1/hello.txt").toURI())));
		totalSize += result.length();
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));
		String saveResult = result;


		// Requst the same layer again and make sure it comes from the layer cache
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[hit_1]", layerCacheInfo.toString());
		assertNull(requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME));
		assertEquals(saveResult, result);
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));


		Thread.sleep(1500L);   // Wait long enough for systems with coarse grain last-mod
		// times to recognize that the file has changed.
		// Touch a file and make sure the layer is rebuilt
		new File(tmpdir, "p1/b.js").setLastModified(new Date().getTime());
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_lastmod2, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("combo/text!p1/hello.txt"));
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));
		assertEquals(saveResult, result);

		// Delete a layer cache entry from disk and make sure it's rebuilt
		String key = "" + layer.getId() + "-" + (String)requestAttributes.get(LayerImpl.LAYERBUILDCACHEKEY_PROPNAME);
		CacheEntry cacheEntry = ((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap().get(key);
		File file = new File(mockAggregator.getCacheManager().getCacheDir(), cacheEntry.getFilename());
		assertTrue("Error deleting file", file.delete());
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[hit_1, update_weights_2]", layerCacheInfo.toString());
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));
		assertEquals(saveResult, result);
		// make sure a new file was written out
		file = new File(mockAggregator.getCacheManager().getCacheDir(), cacheEntry.getFilename());
		assertTrue("missing cache file", file.exists());

		// Test required request parameter
		layer = newLayerImpl(modules.toString(), mockAggregator);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		modules.setModules(Collections.<String>emptyList());
		modules.setDeps(Arrays.asList(new String[]{"p1/a"}));
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		Pattern p = Pattern.compile(new StringBuffer()
		.append("require\\(\\{cache:\\{")
		.append("\\\"p1/a\\\":function\\(\\)\\{.*?\\},")
		.append("\\\"p1/b\\\":function\\(\\)\\{.*?\\},")
		.append("\\\"p1/c\\\":function\\(\\)\\{.*?\\}")
		.append("\\}\\}\\);require\\(\\{cache:\\{\\}\\}\\);require\\(\\[\\\"p1/a\\\"\\]\\);")
		.append("\\s*console.warn\\(\\\"Module not found:[^\\\"]*/p1/noexist.js\\\"\\);").toString());
		assertTrue(p.matcher(result).find());

		// Ensure that package name in require list get's translated to package main module
		layer = newLayerImpl(modules.toString(), mockAggregator);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		modules.setDeps(Arrays.asList(new String[]{"foo"}));
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		p = Pattern.compile(new StringBuffer()
		.append("require\\(\\{cache:\\{")
		.append("\\\"foo/main\\\":function\\(\\)\\{.*?Module not found: .*?\\}")
		.append("\\}\\}\\);require\\(\\{cache:\\{\\}\\}\\);require\\(\\[\\\"foo/main\\\"\\]\\);")
		.append("\\s*console.error\\(\\\"Module not found:[^\\\"]*/foo/main.js\\\"\\);").toString());
		assertTrue(p.matcher(result).find());

	}

	/**
	 * Test method for {@link com.ibm.jaggr.core.impl.layer.LayerImpl#getLastModified(HttpServletRequest)}.
	 * @throws Exception
	 */
	@Test
	public void testGetLastModified() throws Exception {

		replay(mockAggregator, mockRequest, mockResponse, mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}, packages:[{name:'foo', location:'foo'}]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));

		new File(tmpdir, "p1/a.js").setLastModified(new Date().getTime());
		Long lastMod = Math.max(new File(tmpdir, "p1/a.js").lastModified(),
				new File(tmpdir, "p1/b.js").lastModified());

		MockRequestedModuleNames modules = new MockRequestedModuleNames();
		modules.setModules(Arrays.asList(new String[]{"p1/b", "p1/a"}));
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		long testLastMod = layer.getLastModified(mockRequest);
		assertEquals("Last-modifieds don't match", lastMod, testLastMod, 0);

		lastMod += 1500;
		new File(tmpdir, "p1/a.js").setLastModified(lastMod);
		lastMod = new File(tmpdir, "p1/a.js").lastModified();
		assertNotSame("Last modifieds shouldn't match", lastMod,  testLastMod);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		testLastMod = layer.getLastModified(mockRequest);
		assertEquals("Last modifieds don't match", lastMod, testLastMod, 0);
		assertNotNull(requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertTrue(testLastMod == (Long)requestAttributes.get(LayerImpl.LAST_MODIFIED_PROPNAME));

		// enable development mode so last-modified will be re-calculated
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		// Make sure config last-mod is considered
		lastMod -= 100000;
		new File(tmpdir, "p1/a.js").setLastModified(lastMod);
		new File(tmpdir, "p1/b.js").setLastModified(lastMod);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		testLastMod = layer.getLastModified(mockRequest);
		assertEquals("Last modifieds don't match", testLastMod, configRef.get().lastModified());
	}


	/**
	 * Test method for {@link com.ibm.jaggr.core.impl.layer.LayerImpl#toString()}.
	 * @throws Exception
	 */
	@Test
	public void testToString() throws Exception {
		replay(mockAggregator, mockRequest, mockResponse, mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}, packages:[{name:'foo', location:'foo'}]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));

		MockRequestedModuleNames modules = new MockRequestedModuleNames();
		modules.setModules(Arrays.asList(new String[]{"p1/b", "p1/a"}));
		requestAttributes.put(
				IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		in.close();
		String s = layer.toString();
		System.out.println(s);
		assertTrue(Pattern.compile("\\s[0-9]+-expn:0;has\\{\\};sexp:0;sm:0;lyr:0:0:0:0;js:S:0:0.*layer\\..*\\.cache").matcher(s).find());
	}

	/**
	 * Test method for {@link com.ibm.jaggr.core.impl.layer.LayerImpl#newModule(HttpServletRequest, String)}.
	 * @throws IOException
	 */
	@Test
	public void testGetResourceURI() throws IOException {
		replay(mockAggregator, mockRequest, mockResponse, mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}, packages:[{name:'foo', location:'foo'}],jsPluginDelegators:['foo']}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));

		TestLayerImpl impl = new TestLayerImpl("");
		URI uri = new File(tmpdir, "p1/a.js").toURI();
		assertEquals(uri, impl.newModule(mockRequest, "p1/a").getURI());
		assertEquals(uri, impl.newModule(mockRequest, "p1/a.js").getURI());
		assertEquals(uri, impl.newModule(mockRequest, "p1/a/.").getURI());
		assertEquals(uri, impl.newModule(mockRequest, "foo!p1/a/.").getURI());
		uri = new File(tmpdir, "p1/hello.txt").toURI();
		assertEquals(uri, impl.newModule(mockRequest, "combo/text!p1/hello.txt").getURI());

	}

	@Test
	public void featureSetUpdatingTests() throws Exception {
		replay(mockAggregator, mockRequest, mockResponse, mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}, packages:[{name:'foo', location:'foo'}]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		File cacheDir = mockAggregator.getCacheManager().getCacheDir();
		ConcurrentLinkedHashMap<String, CacheEntry> cacheMap = (ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap();
		long totalSize = 0;
		testDepMap.put("p1/a", (String[])ArrayUtils.add(testDepMap.get("p2/a"), "p1/aliased/d"));
		List<String> layerCacheInfo = new LinkedList<String>();
		configJson = "{paths:{p1:'p1',p2:'p2'}, aliases:[[/\\/aliased\\//, function(s){if (has('foo')) return '/foo/'; else if (has('bar')) return '/bar/'; has('non'); return '/non/'}]]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));

		MockRequestedModuleNames modules = new MockRequestedModuleNames();
		modules.setModules(Arrays.asList(new String[]{"p1/a", "p1/p1"}));
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, IHttpTransport.OptimizationLevel.NONE);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);

		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);

		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		Writer writer = new StringWriter();
		CopyUtil.copy(in, writer);
		String result = writer.toString();
		totalSize += result.length();
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

		Map<String, ICacheKeyGenerator> keyGen = layer.getCacheKeyGenerators();
		System.out.println(keyGen.values());
		assertTrue(keyGen.values().toString().contains("js:(has:[conditionFalse, conditionTrue])"));

		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		Features features = new Features();
		features.put("foo", true);
		requestAttributes.put(IHttpTransport.FEATUREMAP_REQATTRNAME, features);

		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		totalSize += result.length();
		keyGen = layer.getCacheKeyGenerators();
		System.out.println(keyGen.values());
		assertEquals("[added, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		assertTrue(keyGen.values().toString().contains("js:(has:[conditionFalse, conditionTrue, foo])"));
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

		features.put("foo", false);
		features.put("bar", true);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		totalSize += result.length();
		keyGen = layer.getCacheKeyGenerators();
		System.out.println(keyGen.values());
		assertEquals("[added, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		assertTrue(keyGen.values().toString().contains("js:(has:[bar, conditionFalse, conditionTrue, foo])"));
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

		features.put("foo", true);
		features.put("bar", false);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		totalSize += result.length();
		assertTrue(keyGen == layer.getCacheKeyGenerators());
		assertEquals("[added, update_weights_2]", layerCacheInfo.toString());
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

		features.put("foo", false);
		features.put("bar", false);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		totalSize += result.length();
		assertEquals("[added, update_keygen, update_key, update_weights_2]", layerCacheInfo.toString());
		keyGen = layer.getCacheKeyGenerators();
		System.out.println(keyGen.values());
		assertTrue(keyGen.values().toString().contains("js:(has:[bar, conditionFalse, conditionTrue, foo, non])"));
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

		features.put("foo", true);
		features.put("bar", true);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		totalSize += result.length();
		assertEquals("[added, update_weights_2]", layerCacheInfo.toString());
		assertTrue(keyGen == layer.getCacheKeyGenerators());
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

		features.remove("bar");
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		assertEquals("[hit_1]", layerCacheInfo.toString());
		assertTrue(keyGen == layer.getCacheKeyGenerators());
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));

	}

	@Test
	public void gzipTests() throws Exception {
		replay(mockAggregator, mockRequest, mockResponse, mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}, packages:[{name:'foo', location:'foo'}]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		configJson = "{paths:{p1:'p1',p2:'p2'}}";
		List<String> layerCacheInfo = new LinkedList<String>();
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		File cacheDir = mockAggregator.getCacheManager().getCacheDir();
		ConcurrentLinkedHashMap<String, CacheEntry> cacheMap = (ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap();

		MockRequestedModuleNames modules = new MockRequestedModuleNames();
		modules.setModules(Arrays.asList(new String[]{"p1/a", "p1/p1"}));
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);

		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		byte[] unzipped = bos.toByteArray();
		assertEquals("[update_lastmod1, update_keygen, update_key, update_add]",layerCacheInfo.toString());
		assertEquals(unzipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		assertEquals("weighted size error", unzipped.length, cacheMap.weightedSize());
		assertEquals("cache file size error", unzipped.length, TestUtils.getDirListSize(cacheDir, layerFilter));

		bos = new ByteArrayOutputStream();
		VariableGZIPOutputStream compress = new VariableGZIPOutputStream(bos, 10240);  // is 10k too big?
		compress.setLevel(Deflater.BEST_COMPRESSION);
		Writer writer = new OutputStreamWriter(compress, "UTF-8"); //$NON-NLS-1$
		CopyUtil.copy(new ByteArrayInputStream(unzipped), writer);
		byte[] zipped = bos.toByteArray();

		requestHeaders.put("Accept-Encoding", "gzip");
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		assertArrayEquals(zipped, bos.toByteArray());
		assertEquals(zipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure that the response was generated by zipping the cached unzipped  response
		assertEquals("[added, zip_unzipped, update_weights_1]",layerCacheInfo.toString());
		assertEquals("weighted size error", zipped.length + unzipped.length, cacheMap.weightedSize());
		assertEquals("cache file size error", zipped.length + unzipped.length, TestUtils.getDirListSize(cacheDir, layerFilter));

		requestHeaders.remove("Accept-Encoding");
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		assertArrayEquals(unzipped, bos.toByteArray());
		assertEquals(unzipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response came from cache
		assertEquals("[hit_1]",layerCacheInfo.toString());
		assertEquals("weighted size error", zipped.length + unzipped.length, cacheMap.weightedSize());
		assertEquals("cache file size error", zipped.length + unzipped.length, TestUtils.getDirListSize(cacheDir, layerFilter));

		requestHeaders.put("Accept-Encoding", "gzip");
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		assertArrayEquals(zipped, bos.toByteArray());
		assertEquals(zipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response came from cache
		assertEquals("[hit_1]",layerCacheInfo.toString());
		assertEquals("weighted size error", zipped.length + unzipped.length, cacheMap.weightedSize());
		assertEquals("cache file size error", zipped.length + unzipped.length, TestUtils.getDirListSize(cacheDir, layerFilter));

		mockAggregator.getCacheManager().clearCache();
		cacheMap = (ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap();
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		layer = newLayerImpl(modules.toString(), mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, modules);
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		assertArrayEquals(zipped, bos.toByteArray());
		assertEquals(zipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		assertEquals("[zip, update_keygen, update_key, update_add]",layerCacheInfo.toString());
		assertEquals("weighted size error", zipped.length, cacheMap.weightedSize());
		assertEquals("cache file size error", zipped.length, TestUtils.getDirListSize(cacheDir, layerFilter));

		requestHeaders.remove("Accept-Encoding");
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		assertArrayEquals(unzipped, bos.toByteArray());
		assertEquals(unzipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response was generated by unzipping the cached zipped response
		assertEquals("[added, unzip_zipped, update_weights_1]",layerCacheInfo.toString());
		assertEquals("weighted size error", zipped.length + unzipped.length, cacheMap.weightedSize());
		assertEquals("cache file size error", zipped.length + unzipped.length, TestUtils.getDirListSize(cacheDir, layerFilter));

		requestHeaders.put("Accept-Encoding", "gzip");
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		assertArrayEquals(zipped, bos.toByteArray());
		assertEquals(zipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response came from cache
		assertEquals("[hit_1]",layerCacheInfo.toString());
		assertEquals("weighted size error", zipped.length + unzipped.length, cacheMap.weightedSize());
		assertEquals("cache file size error", zipped.length + unzipped.length, TestUtils.getDirListSize(cacheDir, layerFilter));

		requestHeaders.remove("Accept-Encoding");
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		assertArrayEquals(unzipped, bos.toByteArray());
		assertEquals(unzipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response came from cache
		assertEquals("[hit_1]",layerCacheInfo.toString());
		assertEquals("weighted size error", zipped.length + unzipped.length, cacheMap.weightedSize());
		assertEquals("cache file size error", zipped.length + unzipped.length, TestUtils.getDirListSize(cacheDir, layerFilter));
	}

	@Test
	public void testCacheKeyGenerator() throws Exception {
		@SuppressWarnings("serial")
		LayerImpl impl = new LayerImpl("", 0) {
			// Increase visibility of methods we need to call
			@Override
			public 	void addCacheKeyGenerators(
					Map<String, ICacheKeyGenerator> cacheKeyGenerators,
					Iterable<ICacheKeyGenerator> gens) {
				super.addCacheKeyGenerators(cacheKeyGenerators, gens);
			}
			@Override
			public String generateCacheKey(
					HttpServletRequest request,
					Map<String, ICacheKeyGenerator> cacheKeyGenerators)
							throws IOException {
				return super.generateCacheKey(request, cacheKeyGenerators);
			}
		};
		Map<String, ICacheKeyGenerator> keyGens = new HashMap<String, ICacheKeyGenerator>();
		impl.addCacheKeyGenerators(keyGens, LayerImpl.s_layerCacheKeyGenerators);
		replay(mockAggregator, mockRequest);
		Assert.assertEquals("sexp:0;sm:0;lyr:0:0:0:0", impl.generateCacheKey(mockRequest, keyGens));
		requestHeaders.put("Accept-Encoding", "gzip");
		Assert.assertEquals("sexp:0;sm:0;lyr:1:0:0:0", impl.generateCacheKey(mockRequest, keyGens));
		mockRequest.setAttribute(IHttpTransport.SERVEREXPANDLAYERS_REQATTRNAME, true);
		Assert.assertEquals("sexp:1;sm:0;lyr:1:0:0:0", impl.generateCacheKey(mockRequest, keyGens));
		mockRequest.setAttribute(IHttpTransport.SHOWFILENAMES_REQATTRNAME, true);
		Assert.assertEquals("sexp:1;sm:0;lyr:1:1:0:0", impl.generateCacheKey(mockRequest, keyGens));
		mockRequest.setAttribute(IHttpTransport.INCLUDEREQUIREDEPS_REQATTRNAME, true);
		Assert.assertEquals("sexp:1;sm:0;lyr:1:1:1:0", impl.generateCacheKey(mockRequest, keyGens));
		mockRequest.setAttribute(IHttpTransport.INCLUDEUNDEFINEDFEATUREDEPS_REQATTRNAME, true);
		Assert.assertEquals("sexp:1;sm:0;lyr:1:1:1:1", impl.generateCacheKey(mockRequest, keyGens));
		mockRequest.setAttribute(IHttpTransport.GENERATESOURCEMAPS_REQATTRNAME, true);
		mockAggregator.getOptions().setOption(IOptions.SOURCE_MAPS, true);
		Assert.assertEquals("sexp:1;sm:1;lyr:1:1:1:1", impl.generateCacheKey(mockRequest, keyGens));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSetResponse() throws Exception {
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator);
		HttpServletResponse mockResponse = EasyMock.createMock(HttpServletResponse.class);
		CacheEntry mockCacheEntry = EasyMock.createMock(CacheEntry.class);
		final String sourceMapContent = "Source Map";
		final InputStream mockInputStream = EasyMock.createMock(InputStream.class);
		final byte[] sourceMapBytes = sourceMapContent.getBytes();
		final MutableObject<String> contentType = new MutableObject<String>();
		final MutableInt contentLength = new MutableInt();
		final Map<String, String[]> responseHeaders = new HashMap<String, String[]>();

		mockResponse.setContentType((String)EasyMock.anyObject());
		EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override
			public Void answer() throws Throwable {
				contentType.setValue((String)EasyMock.getCurrentArguments()[0]);
				return null;
			}
		}).anyTimes();
		mockResponse.addHeader(EasyMock.isA(String.class), EasyMock.isA(String.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override
			public Void answer() throws Throwable {
				String headerName = EasyMock.getCurrentArguments()[0].toString();
				String[] value = responseHeaders.get(headerName);
				if (value == null) {
					value = new String[]{};
				}
				List<String> valueList = new ArrayList<String>(Arrays.asList(value));
				valueList.add(EasyMock.getCurrentArguments()[1].toString());
				responseHeaders.put(headerName, valueList.toArray(new String[valueList.size()]));
				return null;
			}
		}).anyTimes();

		mockResponse.setContentLength(EasyMock.anyInt());
		EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override
			public Void answer() throws Throwable {
				contentLength.setValue((Integer)EasyMock.getCurrentArguments()[0]);
				return null;
			}
		}).anyTimes();

		EasyMock.expect(mockCacheEntry.getSize()).andReturn(100).anyTimes();
		EasyMock.expect(mockCacheEntry.getInputStream(EasyMock.isA(HttpServletRequest.class), (MutableObject<byte[]>)EasyMock.anyObject())).andAnswer(new IAnswer<InputStream>() {
			@Override
			public InputStream answer() throws Throwable {
				MutableObject<byte[]> sm = (MutableObject<byte[]>)EasyMock.getCurrentArguments()[1];
				if (sm != null) {
					sm.setValue(sourceMapBytes);
				}
				return mockInputStream;
			}
		}).anyTimes();
		EasyMock.replay(mockAggregator, mockResponse, mockRequest, mockCacheEntry);
		MockRequestedModuleNames modules = new MockRequestedModuleNames();
		modules.setModules(Arrays.asList(new String[]{"p1/a"}));
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		InputStream in = layer.setResponse(mockRequest, mockResponse, mockCacheEntry);
		// Validate results for non-source map request
		Assert.assertEquals(in, mockInputStream);
		Assert.assertEquals(100, contentLength.intValue());
		Assert.assertEquals("application/javascript; charset=utf-8", contentType.getValue());
		Assert.assertEquals(HttpHeaders.ACCEPT_ENCODING, responseHeaders.get(HttpHeaders.VARY)[0]);

		mockRequest = TestUtils.createMockRequest(mockAggregator);
		responseHeaders.clear();
		EasyMock.expect(mockRequest.getPathInfo()).andReturn(ILayer.SOURCEMAP_RESOURCE_PATH).anyTimes();
		EasyMock.replay(mockRequest);
		mockRequest.setAttribute(IHttpTransport.GENERATESOURCEMAPS_REQATTRNAME, true);
		mockAggregator.getOptions().setOption(IOptions.SOURCE_MAPS, true);
		in = layer.setResponse(mockRequest, mockResponse, mockCacheEntry);
		// Validate results for source map request
		Assert.assertEquals(sourceMapContent, IOUtils.toString(in));
		Assert.assertEquals(sourceMapContent.length(), contentLength.intValue());
		Assert.assertEquals("application/json; charset=utf-8", contentType.getValue());
		Assert.assertEquals(HttpHeaders.ACCEPT_ENCODING, responseHeaders.get(HttpHeaders.VARY)[0]);

		EasyMock.reset(mockCacheEntry);
		EasyMock.expect(mockCacheEntry.getInputStream(EasyMock.isA(HttpServletRequest.class), (MutableObject<byte[]>)EasyMock.anyObject())).andThrow(new IOException()).once();
		EasyMock.replay(mockCacheEntry);
		boolean exceptionThrown = false;
		try {
			layer.setResponse(mockRequest, mockResponse, mockCacheEntry);
		} catch (IOException e) {
			exceptionThrown = true;
		}
		Assert.assertTrue(exceptionThrown);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testTrySetResponse() throws Exception {
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator);
		HttpServletResponse mockResponse = EasyMock.createMock(HttpServletResponse.class);
		CacheEntry mockCacheEntry = EasyMock.createMock(CacheEntry.class);
		EasyMock.expect(mockCacheEntry.tryGetInputStream(EasyMock.isA(HttpServletRequest.class), (MutableObject<byte[]>)EasyMock.anyObject())).andReturn(null).once();
		EasyMock.replay(mockAggregator, mockRequest, mockResponse, mockCacheEntry);
		MockRequestedModuleNames modules = new MockRequestedModuleNames();
		modules.setModules(Arrays.asList(new String[]{"p1/a"}));
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		InputStream in = layer.trySetResponse(mockRequest, mockResponse, mockCacheEntry);
		Assert.assertNull(in);
		EasyMock.verify(mockCacheEntry);
	}

	@SuppressWarnings("serial")
	class TestLayerImpl extends LayerImpl {
		TestLayerImpl(String layerKey) {
			super(layerKey, id++);
			setLayerBuildsAccessor(new LayerBuildsAccessor(
					id,
					(ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap(),
					mockAggregator.getCacheManager(),
					new ReentrantReadWriteLock(), null, null));
		}
		@Override
		public IModule newModule(HttpServletRequest request, String mid) {
			return super.newModule(request, mid);
		}
		@Override
		public InputStream getInputStream(HttpServletRequest request, HttpServletResponse response) throws IOException {
			request.removeAttribute(AbstractHttpTransport.LAYERCONTRIBUTIONSTATE_REQATTRNAME);
			return super.getInputStream(request, response);
		}
	};

	static private LayerImpl newLayerImpl(String layerKey, IAggregator aggregator) {
		@SuppressWarnings("serial")
		LayerImpl result = new LayerImpl(layerKey, ++id) {
			@Override
			public InputStream getInputStream(HttpServletRequest request, HttpServletResponse response) throws IOException {
				request.removeAttribute(AbstractHttpTransport.LAYERCONTRIBUTIONSTATE_REQATTRNAME);
				return super.getInputStream(request, response);
			}
		};
		result.setLayerBuildsAccessor(new LayerBuildsAccessor(
				id,
				(ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)aggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap(),
				aggregator.getCacheManager(),
				new ReentrantReadWriteLock(),
				null, null));
		result.setReportCacheInfo(true);
		return result;
	}

}
