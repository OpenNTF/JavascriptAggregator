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

package com.ibm.jaggr.service.impl.modulebuilder.javascript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.io.Files;
import com.ibm.jaggr.core.DependencyVerificationException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.layer.ILayerListener.EventType;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.OptimizationLevel;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.RequestUtil;
import com.ibm.jaggr.core.util.TypeUtil;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.module.ModuleImpl;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;

@RunWith(PowerMockRunner.class)
@PrepareForTest( JavaScriptModuleBuilder.class )
public class JavaScriptModuleBuilderTest extends EasyMock {
	
	File tmpdir = null;
	IAggregator mockAggregator;
	HttpServletRequest mockRequest;
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	IDependencies mockDependencies = createMock(IDependencies.class);
	Map<String, List<String>> dependentFeaturesMap = new HashMap<String, List<String>>();
	
	
	
	@BeforeClass
	public static void setupBeforeClass() {
	}

	@Before
	public void setup() throws Exception {
		final Map<String, String[]> testDepMap = TestUtils.createTestDepMap();
		tmpdir = Files.createTempDir();
		expect(mockDependencies.getDelcaredDependencies(isA(String.class))).andAnswer(new IAnswer<List<String>>() {
			@Override
			public List<String> answer() throws Throwable {
				String name = (String)getCurrentArguments()[0];
				String[] result = testDepMap.get(name);
				return result != null ? Arrays.asList(result) : null;
			}
		}).anyTimes();
		expect(mockDependencies.getDependentFeatures(isA(String.class))).andAnswer(new IAnswer<List<String>>() {
			@Override
			public List<String> answer() throws Throwable {
				String name = (String)getCurrentArguments()[0];
				return dependentFeaturesMap.get(name);
			}
			
		}).anyTimes();
		expect(mockDependencies.getLastModified()).andReturn(0L).anyTimes();
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		replay(mockAggregator);
		replay(mockRequest);
		replay(mockDependencies);
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}"));
	}
	
	@After
	public void tearDown() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}
	/**
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#getBuild(javax.servlet.http.HttpServletRequest)}.
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * @throws ClassNotFoundException 
	 */
	@Test
	public void testGet() throws Exception {
		TestUtils.createTestFiles(tmpdir);
		
		JsModuleTester p1 = new JsModuleTester("p1/p1", new File(tmpdir, "/p1/p1.js").toURI());
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.SIMPLE);
		Future<ModuleBuildReader> future = p1.getBuild(mockRequest);
		Reader reader = future.get();
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		String compiled = writer.toString();
		System.out.println(compiled);

		// Assert that the two conditionals were identified
		List<ICacheKeyGenerator> gens = future.get().getCacheKeyGenerators();
		String s = KeyGenUtil.toString(gens);
		System.out.println(s);
		assertEquals("expn;js:(has:[conditionFalse, conditionTrue])",s);

		// Make sure the content of both has has features is present
		assertTrue(compiled.contains("has(\"conditionTrue\")"));
		assertTrue(compiled.contains("has(\"conditionFalse\")"));
		assertTrue(compiled.contains("condition_True"));
		assertTrue(compiled.contains("condition_False"));

		// getCachedFileName will wait for the cached file to be persisted asynchronously.
		JavaScriptModuleBuilder builder = new JavaScriptModuleBuilder();
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators(mockAggregator);
		String cacheFileName = p1.getCachedFileName(KeyGenUtil.generateKey(mockRequest, keyGens));
		assertNotNull(cacheFileName);

		// serialize the modules to disk then read it back
		File serializedModule = new File(tmpdir, "a.cached");
		ObjectOutputStream os;
		os = new ObjectOutputStream(new FileOutputStream(serializedModule));
		os.writeObject(p1);
		os.close();
		ObjectInputStream is = new ObjectInputStream(new FileInputStream(serializedModule));
		p1 = (JsModuleTester) is.readObject();
		is.close();
		// get the module reader again using flag to make sure it comes from cache
		reader = p1.get(mockRequest, true).get();
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		assertEquals(compiled,  writer.toString());
		
		// delete the cached module file and make sure get from cache fails
		new File(mockAggregator.getCacheManager().getCacheDir(), cacheFileName).delete();
		try {
			p1.get(mockRequest, true);
			fail("Exception not thrown");
		} catch (IOException e) {}

		// compile again, this time specifying a has condition and require list expansion
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		
		Features features = new Features();
		features.put("conditionTrue", true);
		features.put("conditionFalse", false);
		requestAttributes.put(IHttpTransport.FEATUREMAP_REQATTRNAME, features);
		
		future = p1.getBuild(mockRequest);
		reader = future.get();
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		System.out.println(compiled);
		gens = future.get().getCacheKeyGenerators();
		String cacheFile1 = p1.getCachedFileName(KeyGenUtil.generateKey(mockRequest, gens));
		assertTrue(new File(mockAggregator.getCacheManager().getCacheDir(), cacheFile1).exists());
		
		System.out.println(compiled);
		// validate that require list was expanded and has blocks were removed
		Matcher m = Pattern.compile("require\\(\\[\\\"([^\"]*)\\\",\\\"([^\"]*)\\\",\\\"([^\"]*)\\\"\\]").matcher(compiled);
		Assert.assertTrue(compiled, m.find());
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"p2/a", "p2/b", "p2/c"})),  
				new HashSet<String>(Arrays.asList(new String[]{m.group(1), m.group(2), m.group(3)})));
		assertTrue(compiled.contains("condition_True"));
		assertFalse(compiled.contains("condition_False"));
		assertFalse(compiled.contains("has("));
		
		features.put("condition_Foo", true);
		// Make sure that adding a has condition doesn't change the cache key
		// (i.e. the feature list is filtered by the has conditionals that are
		// actually specified in the source).
		assertEquals(cacheFile1, p1.getCachedFileName(KeyGenUtil.generateKey(mockRequest, gens)));
		
		// remove the conditionFalse feature and verify that the has conditional
		// is in the output
		features.remove("conditionFalse");
		reader = p1.getBuild(mockRequest).get();
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		System.out.println(compiled);
	
		assertTrue(compiled.contains("condition_True"));
		assertTrue(compiled.contains("condition_False"));
		assertFalse(compiled.contains("has(\"conditionTrue\")"));
		assertTrue(compiled.contains("has(\"conditionFalse\")"));
		
		Collection<String> cacheKeys = new TreeSet<String>(p1.getKeys());
		System.out.println(cacheKeys);
		assertEquals("[expn:0;js:S:0:0:1;has{}, expn:0;js:S:1:0:1;has{!conditionFalse,conditionTrue}, expn:0;js:S:1:0:1;has{conditionTrue}]",
				cacheKeys.toString());
		
		// Now set the coerceUndefinedToFalse option and make sure that the
		// conditionFalse block is again removed
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), "{coerceUndefinedToFalse:true}"));
		// Setting properties clears normally clears the cache
		p1.clearCached(mockAggregator.getCacheManager());
		
		reader = p1.getBuild(mockRequest).get();
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		System.out.println(compiled);
		
		assertFalse(compiled.contains("has(\"conditionFalse\")"));
		assertFalse(compiled.contains("conditionFalse"));
		
		cacheKeys = new TreeSet<String>(p1.getKeys());
		System.out.println(cacheKeys);
		assertEquals("[expn:0;js:S:1:0:1;has{!conditionFalse,conditionTrue}]",
				cacheKeys.toString());
		
		Thread.sleep(1500L);   // Wait long enough for systems with coarse grained last-mod
		                       // times to notice the file has been updated
		// Now touch the source file and assert that the cached results are cleared
		new File(tmpdir, "p1/p1.js").setLastModified(new Date().getTime());
		requestAttributes.remove(IHttpTransport.FEATUREMAP_REQATTRNAME);
		p1.getBuild(mockRequest).get().close();
		cacheKeys = p1.getKeys();
		System.out.println(cacheKeys);
		assertEquals("[expn:0;js:S:1:0:1;has{!conditionFalse,!conditionTrue}]", cacheKeys.toString());
		
		// Test error handling.  In production mode, a js syntax error should throw an excepton
		TestUtils.createTestFile(new File(tmpdir, "p1"), "err", TestUtils.err);
		p1 = new JsModuleTester("p1/err", new File(tmpdir, "p1/err.js").toURI());
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.SIMPLE);
		future = p1.getBuild(mockRequest);
		boolean exceptionCaught = false;
		try {
			reader = future.get();
		} catch (ExecutionException e) {
			exceptionCaught = true;
			Assert.assertTrue(e.getCause().getMessage().startsWith("Error compiling module:"));
		}
		Assert.assertTrue(exceptionCaught);
		// In development mode, a js syntax error should return build containing a console.error()
		//  call followed by the un-optimized module content.
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		future = p1.getBuild(mockRequest);
		reader = future.get();
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		System.out.println(compiled);
		Assert.assertTrue(Pattern.compile("\\sconsole\\.error\\(\\\"Error compiling module:[^\"]*\\\"\\);\\s*\\/\\* Comment \\*\\/").matcher(compiled).find());
		Assert.assertTrue(compiled.endsWith(TestUtils.err));
		
	}

	@Test
	public void testDependencyVerificationException() throws Exception
	{
		TestUtils.createTestFiles(tmpdir);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		mockAggregator.getOptions().setOption(IOptions.VERIFY_DEPS, true);
		dependentFeaturesMap.put("p1/p1", Arrays.asList(new String[]{}));
		JsModuleTester p1 = new JsModuleTester("p1/p1", new File(tmpdir, "/p1/p1.js").toURI());
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.SIMPLE);
		Future<ModuleBuildReader> future = p1.getBuild(mockRequest);
		try {
			future.get();
			fail();
		} catch (java.util.concurrent.ExecutionException ex) {
			Assert.assertEquals(DependencyVerificationException.class, ex.getCause().getClass());
		}
		
		dependentFeaturesMap.put("p1/p1", Arrays.asList(new String[]{"conditionTrue", "conditionFalse"}));
		future = p1.getBuild(mockRequest);
		future.get();
	}
	
	/**
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#getModuleName()}.
	 * @throws URISyntaxException 
	 */
	@Test
	public void testGetName() throws URISyntaxException {
		ModuleImpl module = new ModuleImpl("foo", new File("foo.js").toURI());
		assertEquals("foo", module.getModuleId());
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#clear()}.
	 * @throws IOException 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 */
	@Test
	public void testClear() throws Exception {
		TestUtils.createTestFiles(tmpdir);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		JsModuleTester p1 = new JsModuleTester("p1/p1", new File(tmpdir, "p1/p1.js").toURI());
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.SIMPLE);
		
		p1.getBuild(mockRequest).get().close();

		requestAttributes.clear();
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.WHITESPACE);
		p1.getBuild(mockRequest).get().close();
		
		assertEquals(2, p1.getKeys().size());
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#clear(com.ibm.jaggr.service.cache.ICacheManager, int)}.
	 * @throws IOException 
	 * @throws InterruptedException 
	 * @throws ExecutionException 
	 */
	@Test
	public void testClearCached() throws Exception {
		TestUtils.createTestFiles(tmpdir);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		JsModuleTester p1 = new JsModuleTester("p1/p1", new File(tmpdir, "p1/p1.js").toURI());
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.SIMPLE);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		Future<ModuleBuildReader> future = p1.getBuild(mockRequest);
		future.get().close();
		
		requestAttributes.clear();
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.WHITESPACE);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		p1.getBuild(mockRequest).get().close();
		future.get().close();
		
		Collection<String> keys = p1.getKeys();
		assertEquals(2, keys.size());
		System.out.println(keys);
		Collection<File> files = new HashSet<File>(2);
		for (String key : p1.getKeys()) {
			File file = new File(mockAggregator.getCacheManager().getCacheDir(), p1.getCachedFileName(key));
			assertTrue(file.exists());
			files.add(file);
		}
		p1.clearCached(mockAggregator.getCacheManager());
		// Verify cache files get deleted asynchronously
		for (File file : files) {
			for (int i = 0; i < 5; i++) {
				if (file.exists()) {
					Thread.sleep(500L);
				}
			}
			assertFalse(file.exists());
		}
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#toString()}.
	 * @throws Exception 
	 */
	@Test
	public void testToString() throws Exception {
		TestUtils.createTestFiles(tmpdir);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		JsModuleTester p1 = new JsModuleTester("p1/p1", new File(tmpdir, "p1/p1.js").toURI());
		String s = p1.toString();
		System.out.println(s);
		assertTrue(Pattern.compile("IModule: p1/p1\\n").matcher(s).find());
		assertTrue(Pattern.compile("Source: .*p1\\.js").matcher(s).find());
		String lastModified = new Date(new File(tmpdir, "p1/p1.js").lastModified()).toString();
		
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.SIMPLE);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		p1.getBuild(mockRequest).get().close();
		// wait for cache file to get written
		String key = p1.getKeys().iterator().next();
		while (p1.getCachedFileName(key) == null) {
			Thread.sleep(100L);
		}
		s = p1.toString();
		System.out.println(s);
		assertTrue(Pattern.compile("IModule: p1/p1\\n").matcher(s).find());
		assertTrue(Pattern.compile("Source: .*p1.js").matcher(s).find());
		assertTrue(Pattern.compile("Modified: " + lastModified).matcher(s).find());
		assertTrue(Pattern.compile("\\texpn:0;js:S:0:0:1;has\\{\\} : .*p1\\..*\\.cache").matcher(s).find());
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#s_generateCacheKey(com.google.javascript.jscomp.CompilationLevel, java.util.Map, java.util.Set, javax.servlet.http.HttpServletRequest)}.
	 * @throws IOException 
	 */
	@Test
	public void testGenerateCacheKey() throws IOException {
		TestUtils.createTestFiles(tmpdir);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		TestJavaScriptModuleBuilder builder = new TestJavaScriptModuleBuilder();
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators(mockAggregator);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		assertEquals("expn:0;js:S:0:0:1;has{}", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.SIMPLE);
		assertEquals("expn:0;js:S:0:0:1;has{}", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.NONE);
		assertEquals("expn:0;js:N:0:0:1", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.WHITESPACE);
		assertEquals("expn:0;js:W:0:0:1;has{}", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		assertEquals("expn:0;js:W:1:0:1;has{}", KeyGenUtil.generateKey(mockRequest, keyGens));
		Features features = new Features();
		features.put("foo", true);
		features.put("bar", false);
		requestAttributes.put(IHttpTransport.FEATUREMAP_REQATTRNAME, features);
		assertEquals("expn:0;js:W:1:0:1;has{!bar,foo}", KeyGenUtil.generateKey(mockRequest, keyGens));
		Set<String> hasConditionals = new HashSet<String>();
		keyGens = builder.getCacheKeyGenerators(hasConditionals);
		assertEquals("expn:0;js:W:1:0:1;has{}", KeyGenUtil.generateKey(mockRequest, keyGens));
		hasConditionals.add("foo");
		keyGens = builder.getCacheKeyGenerators(hasConditionals);
		assertEquals("expn:0;js:W:1:0:1;has{foo}", KeyGenUtil.generateKey(mockRequest, keyGens));
		hasConditionals.add("bar");
		keyGens = builder.getCacheKeyGenerators(hasConditionals);
		assertEquals("expn:0;js:W:1:0:1;has{!bar,foo}", KeyGenUtil.generateKey(mockRequest, keyGens));
		hasConditionals.add("undefined");
		keyGens = builder.getCacheKeyGenerators(hasConditionals);
		assertEquals("expn:0;js:W:1:0:1;has{!bar,foo}", KeyGenUtil.generateKey(mockRequest, keyGens));
	}


	/**
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#getCacheKey(com.google.javascript.jscomp.CompilationLevel, java.util.Map, java.util.Set, javax.servlet.http.HttpServletRequest)}.
	 */
	@Test
	public void testGetCacheKey() {
		// JavaScriptModuleBuilder.getCacheKey() trivially calls JavaScriptModuleBuilder.s_generateCacheKey()
		// so no need to test it separately
	}

	/**
	 * Test method for {@link com.ibm.servlets.amd.aggregator.modulebuilder.impl.javascript.JavaScriptModuleBuilder#isHasFiltering(javax.servlet.http.HttpServletRequest)}.
	 * @throws IOException 
	 */
	@Test
	public void testIsHasFiltering() throws Exception {
		assertTrue(RequestUtil.isHasFiltering(mockRequest));
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		assertTrue(RequestUtil.isHasFiltering(mockRequest));
		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASFILTERING, true);
		assertFalse(RequestUtil.isHasFiltering(mockRequest));
	}

	/**
	 * Test method for {@link com.ibm.servlets.amd.aggregator.modulebuilder.impl.javascript.JavaScriptModuleBuilder#isExplodeRequires(javax.servlet.http.HttpServletRequest)}.
	 * @throws IOException 
	 */
	@Test
	public void testIsExplodeRequires() throws Exception {
		assertFalse(RequestUtil.isExplodeRequires(mockRequest));
		assertFalse(RequestUtil.isExplodeRequires(mockRequest));
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		assertTrue(RequestUtil.isExplodeRequires(mockRequest));
		requestAttributes.remove(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME);
		assertFalse(RequestUtil.isExplodeRequires(mockRequest));
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		assertTrue(RequestUtil.isExplodeRequires(mockRequest));
		mockAggregator.getOptions().setOption(IOptions.DISABLE_REQUIRELISTEXPANSION, true);
		assertFalse(RequestUtil.isExplodeRequires(mockRequest));
	}
	
	@Test
	public void testLayerBeginEndNotifier_disableExportModuleNames() throws Exception {
		List<IModule> modules = new ArrayList<IModule>();
		Set<String> dependentFeatures = new HashSet<String>();
		JavaScriptModuleBuilder builder = new JavaScriptModuleBuilder();
		mockRequest.setAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		String result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);
		Assert.assertTrue(TypeUtil.asBoolean(mockRequest.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME)));
		
		// Test that EXPORTMODULENAMES is disabled when compliler optimiation is turned off
		mockRequest.setAttribute(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.NONE);
		mockRequest.setAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		
		// First, try with debug mode disabled (has no effect)
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);
		Assert.assertTrue(TypeUtil.asBoolean(mockRequest.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME)));
		
		// Now, enable development mode.  Should cause export names to be disabled
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		mockRequest.setAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);
		Assert.assertFalse(TypeUtil.asBoolean(mockRequest.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME)));
		
		// Should also work with debug mode
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, false);
		mockAggregator.getOptions().setOption(IOptions.DEBUG_MODE, true);
		mockRequest.setAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);
		Assert.assertFalse(TypeUtil.asBoolean(mockRequest.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME)));

		// whitespace optimization should not disable module name exporting
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		mockRequest.setAttribute(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.WHITESPACE);
		mockRequest.setAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);
		Assert.assertTrue(TypeUtil.asBoolean(mockRequest.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME)));
	}
	
	@Test
	public void testLayerBeginEndNotifier_exportModuleNames() throws Exception {
		List<IModule> modules = new ArrayList<IModule>();
		Set<String> dependentFeatures = new HashSet<String>();
		JavaScriptModuleBuilder builder = new JavaScriptModuleBuilder();
		String result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);
		Assert.assertNull(mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES));
		
		Features features = new Features();
		mockRequest.setAttribute(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, true);
		mockRequest.setAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME, features);

		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), "{deps:['cfgfoo']}"));
		final DependencyList mockConfigDeps = createMock(DependencyList.class);
		final DependencyList mockLayerDeps = createMock(DependencyList.class);
		
		ModuleDeps configExplicitDeps = new ModuleDeps();
		ModuleDeps configExpandedDeps = new ModuleDeps();
		configExplicitDeps.add("cfgfoo", new ModuleDepInfo());
		configExpandedDeps.add("cfgfoodep", new ModuleDepInfo());
		
		expect(mockConfigDeps.getExplicitDeps()).andReturn(configExplicitDeps).anyTimes();
		expect(mockConfigDeps.getExpandedDeps()).andReturn(configExpandedDeps).anyTimes();
		expect(mockConfigDeps.getDependentFeatures()).andReturn(new HashSet<String>(Arrays.asList(new String[]{"feature1"}))).anyTimes();

		ModuleDeps layerExplicitDeps = new ModuleDeps();
		ModuleDeps layerExpandedDeps = new ModuleDeps();
		layerExplicitDeps.add("foo", new ModuleDepInfo());
		layerExplicitDeps.add("bar", new ModuleDepInfo());
		layerExpandedDeps.add("foodep", new ModuleDepInfo());
		layerExpandedDeps.add("bardep", new ModuleDepInfo());
		expect(mockLayerDeps.getExplicitDeps()).andReturn(layerExplicitDeps).anyTimes();
		expect(mockLayerDeps.getExpandedDeps()).andReturn(layerExpandedDeps).anyTimes();
		expect(mockLayerDeps.getDependentFeatures()).andReturn(new HashSet<String>(Arrays.asList(new String[]{"feature2"}))).anyTimes();
		
		ModuleDeps expectedDeps = new ModuleDeps();
		expectedDeps.addAll(configExplicitDeps);
		expectedDeps.addAll(configExpandedDeps);
		expectedDeps.addAll(layerExplicitDeps);
		expectedDeps.addAll(layerExpandedDeps);

		PowerMock.expectNew(DependencyList.class, isA(List.class), isA(IAggregator.class), eq(features), anyBoolean(), eq(false))
		                .andAnswer(new IAnswer<DependencyList>() {
							@Override public DependencyList answer() throws Throwable {
								@SuppressWarnings("unchecked")
								List<String> modules = (List<String>)getCurrentArguments()[0];
								if (Arrays.asList(new String[]{"cfgfoo"}).equals(modules)) {
									return mockConfigDeps;
								} else if (Arrays.asList(new String[]{"foo", "bar"}).equals(modules)) {
									return mockLayerDeps;
								}
								Assert.fail("Unexpected argument");
								return null;
							}
		                }).anyTimes();
		PowerMock.replay(mockConfigDeps, mockLayerDeps, DependencyList.class);

		modules = Arrays.asList(new IModule[]{
				new ModuleImpl("foo", new URI("file://foo.js")),
				new ModuleImpl("bar", new URI("file://bar.js"))
		});
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);
		ModuleDeps resultDeps = (ModuleDeps)mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		Assert.assertEquals(expectedDeps, resultDeps);
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature1", "feature2"})), dependentFeatures);
	}
	
	private static final String loggingOutput = "console.log(\"%cEnclosing dependencies for require list expansion (these modules will be omitted from subsequent expanded require lists):\", \"color:blue;background-color:yellow\");console.log(\"%cExpanded dependencies for config deps:\", \"color:blue\");console.log(\"%c	cfgfoo (cfgfoo detail)\\r\\n	cfgfoodep (cfgfoodep detail)\\r\\n\", \"font-size:x-small\");console.log(\"%cExpanded dependencies for layer deps:\", \"color:blue\");console.log(\"%c	foo (foo detail)\\r\\n	bar (bar detail)\\r\\n	foodep (foodep detail)\\r\\n	bardep (bardep detail)\\r\\n\", \"font-size:x-small\");";
	@Test
	public void testLayerBeginEndNotifier_exportModuleNamesWithDetails() throws Exception {
		List<IModule> modules = new ArrayList<IModule>();
		Set<String> dependentFeatures = new HashSet<String>();
		JavaScriptModuleBuilder builder = new JavaScriptModuleBuilder();
		String result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);
		Assert.assertNull(mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES));
		
		Features features = new Features();
		mockRequest.setAttribute(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, true);
		mockRequest.setAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME, features);
		mockRequest.setAttribute(IHttpTransport.EXPANDREQLOGGING_REQATTRNAME, true);

		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), "{deps:['cfgfoo']}"));
		final DependencyList mockConfigDeps = createMock(DependencyList.class);
		final DependencyList mockLayerDeps = createMock(DependencyList.class);
		
		ModuleDeps configExplicitDeps = new ModuleDeps();
		ModuleDeps configExpandedDeps = new ModuleDeps();
		configExplicitDeps.add("cfgfoo", new ModuleDepInfo(null, null, "cfgfoo detail"));
		configExpandedDeps.add("cfgfoodep", new ModuleDepInfo(null, null, "cfgfoodep detail"));
		
		expect(mockConfigDeps.getExplicitDeps()).andReturn(configExplicitDeps).anyTimes();
		expect(mockConfigDeps.getExpandedDeps()).andReturn(configExpandedDeps).anyTimes();
		expect(mockConfigDeps.getDependentFeatures()).andReturn(new HashSet<String>(Arrays.asList(new String[]{"feature1"}))).anyTimes();

		ModuleDeps layerExplicitDeps = new ModuleDeps();
		ModuleDeps layerExpandedDeps = new ModuleDeps();
		layerExplicitDeps.add("foo", new ModuleDepInfo(null, null, "foo detail"));
		layerExplicitDeps.add("bar", new ModuleDepInfo(null, null, "bar detail"));
		layerExpandedDeps.add("foodep", new ModuleDepInfo(null, null, "foodep detail"));
		layerExpandedDeps.add("bardep", new ModuleDepInfo(null, null, "bardep detail"));
		expect(mockLayerDeps.getExplicitDeps()).andReturn(layerExplicitDeps).anyTimes();
		expect(mockLayerDeps.getExpandedDeps()).andReturn(layerExpandedDeps).anyTimes();
		expect(mockLayerDeps.getDependentFeatures()).andReturn(new HashSet<String>(Arrays.asList(new String[]{"feature2"}))).anyTimes();
		
		ModuleDeps expectedDeps = new ModuleDeps();
		expectedDeps.addAll(configExplicitDeps);
		expectedDeps.addAll(configExpandedDeps);
		expectedDeps.addAll(layerExplicitDeps);
		expectedDeps.addAll(layerExpandedDeps);

		PowerMock.expectNew(DependencyList.class, isA(List.class), isA(IAggregator.class), eq(features), anyBoolean(), anyBoolean())
		                .andAnswer(new IAnswer<DependencyList>() {
							@Override public DependencyList answer() throws Throwable {
								@SuppressWarnings("unchecked")
								List<String> modules = (List<String>)getCurrentArguments()[0];
								if (Arrays.asList(new String[]{"cfgfoo"}).equals(modules)) {
									return mockConfigDeps;
								} else if (Arrays.asList(new String[]{"foo", "bar"}).equals(modules)) {
									return mockLayerDeps;
								}
								Assert.fail("Unexpected argument");
								return null;
							}
		                }).anyTimes();
		PowerMock.replay(mockConfigDeps, mockLayerDeps, DependencyList.class);

		modules = Arrays.asList(new IModule[]{
				new ModuleImpl("foo", new URI("file://foo.js")),
				new ModuleImpl("bar", new URI("file://bar.js"))
		});
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertNull(result);	// no output if debug mode is not enabled
		ModuleDeps resultDeps = (ModuleDeps)mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		Assert.assertEquals(expectedDeps, resultDeps);
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature1", "feature2"})), dependentFeatures);

		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		mockRequest.removeAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		dependentFeatures.clear();
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		System.out.println(result);
		Assert.assertEquals(loggingOutput, result);		// This is pretty brittle
		resultDeps = (ModuleDeps)mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		Assert.assertEquals(expectedDeps, resultDeps);
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature1", "feature2"})), dependentFeatures);
	
	}
	/**
	 * Tester class that extends JavaScriptModuleBuilder to expose protected methods for testing
	 */
	private static class JsModuleTester extends ModuleImpl {
		private static final long serialVersionUID = 1L;
		
		public Future<ModuleBuildReader> get(final HttpServletRequest request, boolean fromCacheOnly) throws IOException {
			return super.getBuild(request, fromCacheOnly);
		}

		protected JsModuleTester(ModuleImpl module) {
			super(module);
		}
		
		public JsModuleTester(String mid, URI uri) {
			super(mid, uri);
		}
		
		public String getCachedFileName(String key) throws InterruptedException {
			return super.getCachedFileName(key);
		}
		
		public Collection<String> getKeys() {
			return super.getKeys();
		}
		
		protected Object writeReplace() throws ObjectStreamException {
			return new SerializationProxy(this);
		}

		private void readObject(ObjectInputStream stream) throws InvalidObjectException {
		    throw new InvalidObjectException("Proxy required");
		}

		static class SerializationProxy extends ModuleImpl.SerializationProxy implements Serializable {

			private static final long serialVersionUID = 1618641512031181089L;

			SerializationProxy(ModuleImpl module) {
				super(module);
		    }

		    protected Object readResolve() {
		    	return new JsModuleTester((ModuleImpl)super.readResolve());
		    }
		}
	}
	
	public class TestJavaScriptModuleBuilder extends JavaScriptModuleBuilder {
		@Override
		public List<ICacheKeyGenerator> getCacheKeyGenerators(
				Set<String> dependentFeatures) {
			return super.getCacheKeyGenerators(dependentFeatures);
		}
	}
}
