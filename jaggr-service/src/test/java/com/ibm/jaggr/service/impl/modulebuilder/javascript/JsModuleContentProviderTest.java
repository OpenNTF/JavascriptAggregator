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

import com.google.common.io.Files;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.module.ModuleImpl;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.readers.ModuleBuildReader;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.transport.IHttpTransport.OptimizationLevel;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.Features;

public class JsModuleContentProviderTest extends EasyMock {
	
	File tmpdir = null;
	IAggregator mockAggregator;
	HttpServletRequest mockRequest;
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	IDependencies mockDependencies = createMock(IDependencies.class);
	
	
	
	@BeforeClass
	public static void setupBeforeClass() {
	}

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		tmpdir = Files.createTempDir();
		expect(mockDependencies.getDelcaredDependencies(eq("p1/p1"))).andReturn(Arrays.asList(new String[]{"p1/a", "p2/p1/b", "p2/p1/p1/c", "p2/noexist"})).anyTimes();
		expect(mockDependencies.getExpandedDependencies((String)anyObject(), (Features)anyObject(), (Set<String>)anyObject(), anyBoolean())).andAnswer(new IAnswer<Map<String, String>>() {
			public Map<String, String> answer() throws Throwable {
				String name = (String)getCurrentArguments()[0];
				Map<String, String> result = TestUtils.testDepMap.get(name);
				if (result == null) {
					result = TestUtils.emptyDepMap;
				}
				return result;
			}
		}).anyTimes();
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
		
		// validate that require list was expanded and has blocks were removed
		Matcher m = Pattern.compile("require\\(\\[\\\"([^\"]*)\\\",\\\"([^\"]*)\\\",\\\"([^\"]*)\\\"\\]").matcher(compiled);
		Assert.assertTrue(m.find());
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
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#isHasFiltering(javax.servlet.http.HttpServletRequest)}.
	 * @throws IOException 
	 */
	@Test
	public void testIsHasFiltering() throws Exception {
		assertTrue(JavaScriptModuleBuilder.s_isHasFiltering(mockRequest));
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		assertTrue(JavaScriptModuleBuilder.s_isHasFiltering(mockRequest));
		mockAggregator.getOptions().setOption(IOptions.SKIP_HASFILTERING, true);
		assertFalse(JavaScriptModuleBuilder.s_isHasFiltering(mockRequest));
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#isExplodeRequires(javax.servlet.http.HttpServletRequest)}.
	 * @throws IOException 
	 */
	@Test
	public void testIsExplodeRequires() throws Exception {
		assertFalse(JavaScriptModuleBuilder.s_isExplodeRequires(mockRequest));
		assertFalse(JavaScriptModuleBuilder.s_isExplodeRequires(mockRequest));
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		assertTrue(JavaScriptModuleBuilder.s_isExplodeRequires(mockRequest));
		requestAttributes.remove(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME);
		assertFalse(JavaScriptModuleBuilder.s_isExplodeRequires(mockRequest));
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		assertTrue(JavaScriptModuleBuilder.s_isExplodeRequires(mockRequest));
		mockAggregator.getOptions().setOption(IOptions.SKIP_REQUIRELISTEXPANSION, true);
		assertFalse(JavaScriptModuleBuilder.s_isExplodeRequires(mockRequest));
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
