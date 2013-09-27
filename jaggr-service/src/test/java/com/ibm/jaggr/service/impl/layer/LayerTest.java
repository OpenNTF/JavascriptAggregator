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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.zip.Deflater;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.deps.ModuleDepInfo;
import com.ibm.jaggr.service.deps.ModuleDeps;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.module.NotFoundModule;
import com.ibm.jaggr.service.impl.transport.AbstractHttpTransport;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.module.IModuleCache;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.Features;

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
	IDependencies mockDependencies = createMock(IDependencies.class);
	static Map<String, ModuleDeps> testDepMap;
	

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = Files.createTempDir();
		TestUtils.createTestFiles(tmpdir);
		LayerImpl.LAYERBUILD_REMOVE_DELAY_SECONDS = 0;
		testDepMap = TestUtils.createTestDepMap();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	static long lastModified = 0;

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParameters, null, requestHeaders);
		expect(mockAggregator.getDependencies()).andAnswer(new IAnswer<IDependencies>() {
			public IDependencies answer() throws Throwable {
				return mockDependencies;
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
					String resolved = config.resolve(depName, features, dependentFeatures, null);
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

		replay(mockAggregator);
		replay(mockRequest);
		replay(mockResponse);
		replay(mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		String configJson = "{paths:{p1:'p1',p2:'p2'}}";
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
	 * Test method for {@link com.ibm.jaggr.service.impl.layer.LayerImpl#getInputStream(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.ibm.jaggr.service.config.IConfig, long)}.
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetInputStream() throws Exception {

		// Request a single module
		File cacheDir = mockAggregator.getCacheManager().getCacheDir();
		ConcurrentLinkedHashMap<String, CacheEntry> cacheMap = (ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap();
		long totalSize = 0;
		Collection<String> modules = Arrays.asList(new String[]{"p1/a"}); 
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		List<String> layerCacheInfo = new LinkedList<String>();
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		Writer writer = new StringWriter();
		CopyUtil.copy(in, writer);
		String result = writer.toString();
		System.out.println(result);
		totalSize += result.length();
		assertEquals("[update_keygen, update_key, update_add]", layerCacheInfo.toString());
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));
		Map<String, String> moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertTrue(result.contains("\"hello from a.js\""));
		
		// Request two modules
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		modules = Arrays.asList(new String[]{"p1/b", "p1/a"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		layer = newLayerImpl(modules.toString(), mockAggregator);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_keygen, update_key, update_add]", layerCacheInfo.toString());
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
		modules = Arrays.asList(new String[]{"p1/b","p1/a","p1/hello.txt"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		layer = newLayerImpl(modules.toString(), mockAggregator);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_keygen, update_key, update_add]", layerCacheInfo.toString());
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("add", moduleCacheInfo.get("p1/hello.txt"));
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
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_lastmod, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("p1/hello.txt"));
		assertTrue(result.contains(String.format(LayerBuilder.PREAMBLEFMT, new File(tmpdir, "p1/a.js").toURI())));
		assertTrue(result.contains(String.format(LayerBuilder.PREAMBLEFMT, new File(tmpdir, "p1/b.js").toURI())));
		assertTrue(result.contains(String.format(LayerBuilder.PREAMBLEFMT, new File(tmpdir, "p1/hello.txt").toURI())));
		totalSize += result.length();
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));
		String saveResult = result;
		

		// Requst the same layer again and make sure it comes from the layer cache
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
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
	
		// rename one of the source files and make sure it busts the cache
		new File(tmpdir, "p1/a.js").renameTo(new File(tmpdir, "p1/a.js.save"));
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_lastmod, error_noaction]", layerCacheInfo.toString());
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("remove", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("p1/hello.txt"));
		assertTrue(result.contains(String.format(LayerBuilder.PREAMBLEFMT, new File(tmpdir, "p1/a.js").toURI())));
		assertTrue(result.contains(String.format(LayerBuilder.PREAMBLEFMT, new File(tmpdir, "p1/b.js").toURI())));
		assertTrue(result.contains(String.format(LayerBuilder.PREAMBLEFMT, new File(tmpdir, "p1/hello.txt").toURI())));
		URI uri = new File(tmpdir, "p1/a.js").toURI();
		NotFoundModule nfm = new NotFoundModule("p1/a.js", uri);
		Reader rdr = nfm.getBuild(mockRequest).get();
		writer = new StringWriter();
		CopyUtil.copy(rdr, writer);
		assertTrue(result.contains(writer.toString()));
		assertTrue(result.contains("\"hello from b.js\""));
		assertTrue(result.contains("Hello world text"));

		// now rename it back
		new File(tmpdir, "p1/a.js.save").renameTo(new File(tmpdir, "p1/a.js"));
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("[update_keygen, update_key, update_hit]", layerCacheInfo.toString());
		assertEquals("add", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("p1/hello.txt"));
		assertEquals("weighted size error", totalSize, cacheMap.weightedSize());
		assertEquals("cache file size error", totalSize, TestUtils.getDirListSize(cacheDir, layerFilter));
		assertEquals(saveResult, result);
		
		Thread.sleep(1500L);   // Wait long enough for systems with coarse grain last-mod
		                       // times to recognize that the file has changed.
		// Touch a file and make sure the layer is rebuilt
		new File(tmpdir, "p1/b.js").setLastModified(new Date().getTime());
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("[update_lastmod, update_keygen, update_key, update_add]", layerCacheInfo.toString());
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(IModuleCache.MODULECACHEINFO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("p1/hello.txt"));
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
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUIRED_REQATTRNAME, new HashSet<String>(Arrays.asList(new String[]{"p1/a"})));
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		Pattern p = Pattern.compile(new StringBuffer()
				.append("require\\(\\{cache:\\{")
				.append("\\\"p1/b\\\":function\\(\\)\\{.*?\\},")
				.append("\\\"p1/c\\\":function\\(\\)\\{.*?\\},")
				.append("\\\"p1/a\\\":function\\(\\)\\{.*?\\},")
				.append("\\\"p1/noexist\\\":function\\(\\)\\{.*?Module not found: .*?\\}")
				.append("\\}\\}\\);require\\(\\{cache:\\{\\}\\}\\);require\\(\\[\\\"p1/a\\\"\\]\\);").toString());
		assertTrue(p.matcher(result).find());
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.impl.layer.LayerImpl#getLastModified(javax.servlet.http.HttpServletRequest, com.ibm.jaggr.service.config.IConfig, long)}.
	 */
	@Test
	public void testGetLastModified() throws Exception {
		new File(tmpdir, "p1/a.js").setLastModified(new Date().getTime());
		Long lastMod = Math.max(new File(tmpdir, "p1/a.js").lastModified(), 
				                new File(tmpdir, "p1/b.js").lastModified());
		
		Collection<String> modules = Arrays.asList(new String[]{"p1/b", "p1/a"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		long testLastMod = layer.getLastModified(mockRequest);
		assertEquals("Last-modifieds don't match", lastMod, testLastMod, 0);

		lastMod += 1500;
		new File(tmpdir, "p1/a.js").setLastModified(lastMod);
		lastMod = new File(tmpdir, "p1/a.js").lastModified();
		assertNotSame("Last modifieds shouldn't match", lastMod,  testLastMod);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		testLastMod = layer.getLastModified(mockRequest);
		assertEquals("Last modifieds don't match", lastMod, testLastMod, 0);
		assertNotNull(requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertTrue(testLastMod == (Long)requestAttributes.get(LayerImpl.LAST_MODIFIED_PROPNAME));
		
		// Make sure config last-mod is considered
		lastMod -= 100000;
		new File(tmpdir, "p1/a.js").setLastModified(lastMod);
		new File(tmpdir, "p1/b.js").setLastModified(lastMod);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		testLastMod = layer.getLastModified(mockRequest);
		assertEquals("Last modifieds don't match", testLastMod, configRef.get().lastModified());
	}


	/**
	 * Test method for {@link com.ibm.jaggr.service.impl.layer.LayerImpl#toString()}.
	 */
	@Test
	public void testToString() throws Exception {
		Collection<String> modules = Arrays.asList(new String[]{"p1/b", "p1/a"});
		requestAttributes.put(
				IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		in.close();
		String s = layer.toString();
		System.out.println(s);
		assertTrue(Pattern.compile("\\s[0-9]+-expn:0;has\\{\\};lyr:0:0;js:S:0:0.*layer\\..*\\.cache").matcher(s).find());
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.impl.layer.LayerImpl#getResourceURI(javax.servlet.http.HttpServletRequest, java.lang.String, com.ibm.jaggr.service.config.IConfig)}.
	 */
	@Test
	public void testGetResourceURI() {
		TestLayerImpl impl = new TestLayerImpl(""); 		
		URI uri = new File(tmpdir, "p1/a.js").toURI();
		assertEquals(uri, impl.newModule(mockRequest, "p1/a").getURI());
		assertEquals(uri, impl.newModule(mockRequest, "p1/a.js").getURI());
		assertEquals(uri, impl.newModule(mockRequest, "p1/a/.").getURI());
		assertEquals(uri, impl.newModule(mockRequest, "foo!p1/a/.").getURI());
		uri = new File(tmpdir, "p1/hello.txt").toURI();
		assertEquals(uri, impl.newModule(mockRequest, "p1/hello.txt").getURI());

	}
	
	@Test
	public void featureSetUpdatingTests() throws Exception {
		File cacheDir = mockAggregator.getCacheManager().getCacheDir();
		ConcurrentLinkedHashMap<String, CacheEntry> cacheMap = (ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap();
		long totalSize = 0;
		testDepMap.get("p2/a").add("p1/aliased/d", new ModuleDepInfo(null, null, ""));
		List<String> layerCacheInfo = new LinkedList<String>();
		String configJson = "{paths:{p1:'p1',p2:'p2'}, aliases:[[/\\/aliased\\//, function(s){if (has('foo')) return '/foo/'; else if (has('bar')) return '/bar/'; has('non'); return '/non/'}]]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		
		Collection<String> modules = Arrays.asList(new String[]{"p1/a", "p1/p1"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
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
		testDepMap.get("p2/a").add("p1/aliased/d", new ModuleDepInfo(null, null, ""));
		String configJson = "{paths:{p1:'p1',p2:'p2'}}";
		List<String> layerCacheInfo = new LinkedList<String>();
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		File cacheDir = mockAggregator.getCacheManager().getCacheDir();
		ConcurrentLinkedHashMap<String, CacheEntry> cacheMap = (ConcurrentLinkedHashMap<String, CacheEntry>)((LayerCacheImpl)mockAggregator.getCacheManager().getCache().getLayers()).getLayerBuildMap();
		
		Collection<String> modules = Arrays.asList(new String[]{"p1/a", "p1/p1"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(LayerImpl.LAYERCACHEINFO_PROPNAME, layerCacheInfo);
		LayerImpl layer = newLayerImpl(modules.toString(), mockAggregator);
		
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		byte[] unzipped = bos.toByteArray();
		assertEquals("[update_keygen, update_key, update_add]",layerCacheInfo.toString());
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
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
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
