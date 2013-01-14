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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.zip.Deflater;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.module.NotFoundModule;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.Features;

public class LayerTest extends EasyMock {

	static File tmpdir = null;
	IAggregator mockAggregator;
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	Map<String, String[]> requestParameters = new HashMap<String, String[]>();
	Map<String, String> requestHeaders = new HashMap<String, String>();
	Map<String, String> responseAttributes = new HashMap<String, String>();
	HttpServletRequest mockRequest;
	HttpServletResponse mockResponse = TestUtils.createMockResponse(responseAttributes);
	IDependencies mockDependencies = createMock(IDependencies.class);
	static final Map<String, Map<String, String>> testDepMap;
	
	static {
		testDepMap = new HashMap<String, Map<String, String>>();
		for (Map.Entry<String, Map<String, String>> entry : TestUtils.testDepMap.entrySet()) {
			testDepMap.put(entry.getKey(), new HashMap<String, String>(entry.getValue()));
		}
	}


	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = Files.createTempDir();
		TestUtils.createTestFiles(tmpdir);
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
		expect(mockDependencies.getExpandedDependencies((String)EasyMock.anyObject(), (Features)EasyMock.anyObject(), (Set<String>)EasyMock.anyObject(), EasyMock.anyBoolean())).andAnswer(new IAnswer<Map<String, String>>() {
			public Map<String, String> answer() throws Throwable {
				String name = (String)EasyMock.getCurrentArguments()[0];
				Features features = (Features)EasyMock.getCurrentArguments()[1];
				Set<String> dependentFeatures = (Set<String>)EasyMock.getCurrentArguments()[2];
				Map<String, String> result = testDepMap.get(name);
				if (result == null) {
					result = TestUtils.emptyDepMap;
				}
				// resolve aliases
				Map<String, String> temp = new HashMap<String, String>();
				IConfig config = mockAggregator.getConfig();
				for (Map.Entry<String, String> entry : result.entrySet()) {
					String depName = entry.getKey();
					String resolved = config.resolve(depName, features, dependentFeatures, null);
					temp.put(resolved != null ? resolved : depName, entry.getValue());
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
	 * Test method for {@link com.ibm.servlets.amd.aggregator.layer.impl.LayerImpl#getInputStream(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, com.ibm.servlets.amd.aggregator.config.IConfig, long)}.
	 * @throws Exception 
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testGetInputStream() throws Exception {

		// Request a single module
		Collection<String> modules = Arrays.asList(new String[]{"p1/a"}); 
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(mockAggregator);
		layer.setReportCacheInfo(true);
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		Writer writer = new StringWriter();
		CopyUtil.copy(in, writer);
		String result = writer.toString();
		System.out.println(result);
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		Map<String, String> moduleCacheInfo = (Map<String, String>)requestAttributes.get(LayerImpl.MODULECACHEIFNO_PROPNAME);
		assertEquals("add", moduleCacheInfo.get("p1/a"));
		assertTrue(result.contains("\"hello from a.js\""));
		
		// Request two modules
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		modules = Arrays.asList(new String[]{"p1/b", "p1/a"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		layer = newLayerImpl(mockAggregator);
		layer.setReportCacheInfo(true);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(LayerImpl.MODULECACHEIFNO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("add", moduleCacheInfo.get("p1/b"));
		assertTrue(result.contains("\"hello from a.js\""));
		assertTrue(result.contains("\"hello from b.js\""));
		
		// Add a text resource
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		modules = Arrays.asList(new String[]{"p1/b","p1/a","p1/hello.txt"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		layer = newLayerImpl(mockAggregator);
		layer.setReportCacheInfo(true);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(LayerImpl.MODULECACHEIFNO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("add", moduleCacheInfo.get("p1/hello.txt"));
		assertTrue(result.contains("\"hello from a.js\""));
		assertTrue(result.contains("\"hello from b.js\""));
		assertTrue(result.contains("Hello world text"));
		
		// Test filename prologue option
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, "true");
		layer = newLayerImpl(mockAggregator);
		layer.setReportCacheInfo(true);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(LayerImpl.MODULECACHEIFNO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("p1/hello.txt"));
		assertTrue(result.contains(String.format(LayerImpl.PREAMBLEFMT, new File(tmpdir, "p1/a.js").toURI())));
		assertTrue(result.contains(String.format(LayerImpl.PREAMBLEFMT, new File(tmpdir, "p1/b.js").toURI())));
		assertTrue(result.contains(String.format(LayerImpl.PREAMBLEFMT, new File(tmpdir, "p1/hello.txt").toURI())));
		String saveResult = result;
		

		// Requst the same layer again and make sure it comes from the layer cache
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("hit_1", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		assertNull(requestAttributes.get(LayerImpl.MODULECACHEIFNO_PROPNAME));
		assertEquals(saveResult, result);
	
		// rename one of the source files and make sure it busts the cache
		new File(tmpdir, "p1/a.js").renameTo(new File(tmpdir, "p1/a.js.save"));
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(LayerImpl.MODULECACHEIFNO_PROPNAME);
		assertEquals("remove", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("p1/hello.txt"));
		assertTrue(result.contains(String.format(LayerImpl.PREAMBLEFMT, new File(tmpdir, "p1/a.js").toURI())));
		assertTrue(result.contains(String.format(LayerImpl.PREAMBLEFMT, new File(tmpdir, "p1/b.js").toURI())));
		assertTrue(result.contains(String.format(LayerImpl.PREAMBLEFMT, new File(tmpdir, "p1/hello.txt").toURI())));
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
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(LayerImpl.MODULECACHEIFNO_PROPNAME);
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		assertEquals("add", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("p1/hello.txt"));
		assertEquals(saveResult, result);
		
		// Touch a file and make sure the layer is rebuilt
		new File(tmpdir, "p1/b.js").setLastModified(new Date().getTime());
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		in = layer.getInputStream(mockRequest, mockResponse);
		writer = new StringWriter();
		CopyUtil.copy(in, writer);
		result = writer.toString();
		System.out.println(result);
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		moduleCacheInfo = (Map<String, String>)requestAttributes.get(LayerImpl.MODULECACHEIFNO_PROPNAME);
		assertEquals("hit", moduleCacheInfo.get("p1/a"));
		assertEquals("hit", moduleCacheInfo.get("p1/b"));
		assertEquals("hit", moduleCacheInfo.get("p1/hello.txt"));
		assertEquals(saveResult, result);
		
	}

	/**
	 * Test method for {@link com.ibm.servlets.amd.aggregator.layer.impl.LayerImpl#getLastModified(javax.servlet.http.HttpServletRequest, com.ibm.servlets.amd.aggregator.config.IConfig, long)}.
	 */
	@Test
	public void testGetLastModified() throws Exception {
		Long lastMod = Math.max(new File(tmpdir, "p1/a.js").lastModified(), 
				                new File(tmpdir, "p1/b.js").lastModified());
		
		Collection<String> modules = Arrays.asList(new String[]{"p1/b", "p1/a"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(mockAggregator);
		long testLastMod = layer.getLastModified(mockRequest);
		assertTrue(lastMod == testLastMod);
		lastMod = new Date().getTime();
		new File(tmpdir, "p1/a.js").setLastModified(lastMod);
		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		testLastMod = layer.getLastModified(mockRequest);
		assertTrue(lastMod == testLastMod);
		assertNotNull(requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertTrue(testLastMod == (Long)requestAttributes.get(LayerImpl.LAST_MODIFIED_PROPNAME));
	}


	/**
	 * Test method for {@link com.ibm.servlets.amd.aggregator.layer.impl.LayerImpl#toString()}.
	 */
	@Test
	public void testToString() throws Exception {
		requestAttributes.put(
				IHttpTransport.REQUESTEDMODULES_REQATTRNAME,
				Arrays.asList(new String[]{"p1/b", "p1/a"}));
		LayerImpl layer = newLayerImpl(mockAggregator);
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		in.close();
		String s = layer.toString();
		System.out.println(s);
		assertTrue(Pattern.compile("\\s[0-9]+-expn:0;has\\{\\};lyr:0:0;js:S:0:0.*layer\\..*\\.cache").matcher(s).find());
	}

	/**
	 * Test method for {@link com.ibm.servlets.amd.aggregator.layer.impl.LayerImpl#getResourceURI(javax.servlet.http.HttpServletRequest, java.lang.String, com.ibm.servlets.amd.aggregator.config.IConfig)}.
	 */
	@Test
	public void testGetResourceURI() {
		TestLayerImpl impl = new TestLayerImpl(); 		
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
		testDepMap.get("p2/a").put("p1/aliased/d", "");
		String configJson = "{paths:{p1:'p1',p2:'p2'}, aliases:[[/\\/aliased\\//, function(s){if (has('foo')) return '/foo/'; else if (has('bar')) return '/bar/'; has('non'); return '/non/'}]]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		
		Collection<String> modules = Arrays.asList(new String[]{"p1/a", "p1/p1"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, IHttpTransport.OptimizationLevel.NONE);
		LayerImpl layer = newLayerImpl(mockAggregator);
		layer.setReportCacheInfo(true);
		
		layer.getInputStream(mockRequest, mockResponse).close();
		Map<String, ICacheKeyGenerator> keyGen = layer.getCacheKeyGenerators();
		System.out.println(keyGen.values());
		Assert.assertTrue(keyGen.values().toString().contains("js:(has:[conditionFalse, conditionTrue])"));
		
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		Features features = new Features();
		features.put("foo", true);
		requestAttributes.put(IHttpTransport.FEATUREMAP_REQATTRNAME, features);
		
		layer.getInputStream(mockRequest, mockResponse).close();
		keyGen = layer.getCacheKeyGenerators();
		System.out.println(keyGen.values());
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		Assert.assertTrue(keyGen.values().toString().contains("js:(has:[conditionFalse, conditionTrue, foo])"));
		
		features.put("foo", false);
		features.put("bar", true);
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		layer.getInputStream(mockRequest, mockResponse).close();
		keyGen = layer.getCacheKeyGenerators();
		System.out.println(keyGen.values());
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		Assert.assertTrue(keyGen.values().toString().contains("js:(has:[bar, conditionFalse, conditionTrue, foo])"));
		
		features.put("foo", true);
		features.put("bar", false);
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		layer.getInputStream(mockRequest, mockResponse).close();
		Assert.assertTrue(keyGen == layer.getCacheKeyGenerators());
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		
		features.put("foo", false);
		features.put("bar", false);
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		layer.getInputStream(mockRequest, mockResponse).close();
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		keyGen = layer.getCacheKeyGenerators();
		System.out.println(keyGen.values());
		Assert.assertTrue(keyGen.values().toString().contains("js:(has:[bar, conditionFalse, conditionTrue, foo, non])"));

		features.put("foo", true);
		features.put("bar", true);
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		layer.getInputStream(mockRequest, mockResponse).close();
		assertEquals("add", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		Assert.assertTrue(keyGen == layer.getCacheKeyGenerators());
		
		features.remove("bar");
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		layer.getInputStream(mockRequest, mockResponse).close();
		assertEquals("hit_1", requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		Assert.assertTrue(keyGen == layer.getCacheKeyGenerators());

	}
	
	@Test
	public void gzipTests() throws Exception {
		testDepMap.get("p2/a").put("p1/aliased/d", "");
		String configJson = "{paths:{p1:'p1',p2:'p2'}}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		
		Collection<String> modules = Arrays.asList(new String[]{"p1/a", "p1/p1"});
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		LayerImpl layer = newLayerImpl(mockAggregator);
		layer.setReportCacheInfo(true);
		
		InputStream in = layer.getInputStream(mockRequest, mockResponse);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		byte[] unzipped = bos.toByteArray();
		Assert.assertEquals("add",requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		Assert.assertEquals(unzipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));

		bos = new ByteArrayOutputStream();
		VariableGZIPOutputStream compress = new VariableGZIPOutputStream(bos, 10240);  // is 10k too big?
        compress.setLevel(Deflater.BEST_COMPRESSION);
        Writer writer = new OutputStreamWriter(compress, "UTF-8"); //$NON-NLS-1$
        CopyUtil.copy(new ByteArrayInputStream(unzipped), writer);
        byte[] zipped = bos.toByteArray();
		
        requestHeaders.put("Accept-Encoding", "gzip");
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		Assert.assertArrayEquals(zipped, bos.toByteArray());
		Assert.assertEquals(zipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure that the response was generated by zipping the cached unzipped  response
		Assert.assertEquals("hit_4",requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		
        requestHeaders.remove("Accept-Encoding");
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		Assert.assertArrayEquals(unzipped, bos.toByteArray());
		Assert.assertEquals(unzipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response came from cache
		Assert.assertEquals("hit_1",requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		
        requestHeaders.put("Accept-Encoding", "gzip");
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		Assert.assertArrayEquals(zipped, bos.toByteArray());
		Assert.assertEquals(zipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response came from cache
		Assert.assertEquals("hit_1",requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		
		layer = newLayerImpl(mockAggregator);
		layer.setReportCacheInfo(true);
		requestAttributes.put(IHttpTransport.REQUESTEDMODULES_REQATTRNAME, modules);
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		Assert.assertArrayEquals(zipped, bos.toByteArray());
		Assert.assertEquals(zipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		Assert.assertEquals("add",requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		
		requestHeaders.remove("Accept-Encoding");
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		Assert.assertArrayEquals(unzipped, bos.toByteArray());
		Assert.assertEquals(unzipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response was generated by unzipping the cached zipped response
		Assert.assertEquals("hit_5",requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		
		requestHeaders.put("Accept-Encoding", "gzip");
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		Assert.assertArrayEquals(zipped, bos.toByteArray());
		Assert.assertEquals(zipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response came from cache
		Assert.assertEquals("hit_1",requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
		
		requestHeaders.remove("Accept-Encoding");
		requestAttributes.remove(LayerImpl.LAYERCACHEINFO_PROPNAME);
		in = layer.getInputStream(mockRequest, mockResponse);
		bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		Assert.assertArrayEquals(unzipped, bos.toByteArray());
		Assert.assertEquals(unzipped.length, Integer.parseInt(responseAttributes.get("Content-Length")));
		// ensure response came from cache
		Assert.assertEquals("hit_1",requestAttributes.get(LayerImpl.LAYERCACHEINFO_PROPNAME));
	}

	@SuppressWarnings("serial")
	class TestLayerImpl extends LayerImpl { 
		TestLayerImpl() {
			super("", 1);
			setLayerBuildsAccessor(new LayerBuildsAccessor(
					1,
					new ConcurrentHashMap<String, CacheEntry>(), 
					mockAggregator.getCacheManager(), 
					new ReentrantReadWriteLock(), null, null));
		}
		public IModule newModule(HttpServletRequest request, String mid) {
			return super.newModule(request, mid);
		}
	};
	
	static private LayerImpl newLayerImpl(IAggregator aggregator) {
		LayerImpl result = new LayerImpl("", 1);
		result.setLayerBuildsAccessor(new LayerBuildsAccessor(
				1, 
				new ConcurrentHashMap<String, CacheEntry>(), 
				aggregator.getCacheManager(), 
				new ReentrantReadWriteLock(), 
				null, null));
		return result;
	}

}
