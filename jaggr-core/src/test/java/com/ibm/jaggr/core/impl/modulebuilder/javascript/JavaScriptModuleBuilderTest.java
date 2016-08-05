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

package com.ibm.jaggr.core.impl.modulebuilder.javascript;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ibm.jaggr.core.DependencyVerificationException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.impl.module.ModuleImpl;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.JavaScriptModuleBuilder.CacheKeyGenerator;
import com.ibm.jaggr.core.impl.transport.AbstractHttpTransport;
import com.ibm.jaggr.core.layer.ILayerListener.EventType;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.modulebuilder.SourceMap;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.test.MockRequestedModuleNames;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.OptimizationLevel;
import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.ConcurrentListBuilder;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.RequestUtil;
import com.ibm.jaggr.core.util.TypeUtil;

import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONObject;
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
import java.util.Collections;
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

@RunWith(PowerMockRunner.class)
@PrepareForTest( JavaScriptModuleBuilder.class )
public class JavaScriptModuleBuilderTest extends EasyMock {

	File tmpdir = null;
	IAggregator mockAggregator;
	HttpServletRequest mockRequest;
	IHttpTransport mockTransport;
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	IDependencies mockDependencies = createMock(IDependencies.class);
	Map<String, List<String>> dependentFeaturesMap = new HashMap<String, List<String>>();
	@SuppressWarnings("unchecked")
	final Map<String, Integer>[] moduleIdMap = (Map<String, Integer>[])new Map[]{null};



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
		mockTransport = EasyMock.createMock(IHttpTransport.class);
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir, null, null, mockTransport);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		expect(mockTransport.getModuleIdMap()).andAnswer(new IAnswer<Map<String, Integer>>() {
			@Override
			public Map<String, Integer> answer() throws Throwable {
				return moduleIdMap[0];
			}
		}).anyTimes();
		expect(mockTransport.getModuleIdRegFunctionName()).andReturn("require.combo.reg").anyTimes();
		replay(mockAggregator);
		replay(mockRequest);
		replay(mockDependencies);
		replay(mockTransport);
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
	 * Test method for {@link JavaScriptModuleBuilder#build(String, com.ibm.jaggr.core.resource.IResource, HttpServletRequest, List)}.
	 * @throws Exception
	 * @throws ExecutionException
	 * @throws InterruptedException
	 * @throws ClassNotFoundException
	 */
	@Test
	public void testBuild() throws Exception {
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
		ConcurrentListBuilder<String[]> expDeps = new ConcurrentListBuilder<String[]>();
		requestAttributes.put(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS, expDeps);

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
		Matcher m = Pattern.compile("require([\"p2/a\"].concat(" +
				JavaScriptModuleBuilder.EXPDEPS_VARNAME +
				"[0][000]),function(", Pattern.LITERAL).matcher(compiled);
		Assert.assertTrue(compiled, m.find());
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"p2/b", "p2/c"})),
				new HashSet<String>(Arrays.asList(expDeps.toList().get(0))));
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

		// Test source maps
		mockAggregator.getOptions().setOption(IOptions.SOURCE_MAPS, true);
		p1.clearCached(mockAggregator.getCacheManager());
		reader = p1.getBuild(mockRequest).get();
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		System.out.println(compiled);
		SourceMap sourceMap = ((ModuleBuildReader)reader).getSourceMap();
		Assert.assertEquals("p1/p1", sourceMap.name);
		Assert.assertEquals(FileUtils.readFileToString(new File(tmpdir, "/p1/p1.js")), sourceMap.source);
		System.out.println(sourceMap.map);
		JSONObject sm = new JSONObject(sourceMap.map);
		Assert.assertEquals(sm.get("version"), 3);
		Assert.assertEquals(sm.get("file"), "p1/p1");
		Assert.assertEquals(sm.get("lineCount"), 1);
		Assert.assertEquals(Arrays.asList(((JSONArray)sm.get("sources")).toArray()),
				Arrays.asList("p1/p1"));
		Assert.assertEquals(Arrays.asList(((JSONArray)sm.get("names")).toArray()),
				Arrays.asList("define", "require", "alert", "has"));
		String mappings = (String)sm.getString("mappings");
		Assert.assertNotNull(mappings);
		Assert.assertTrue(mappings.length() > 0);

		mockAggregator.getOptions().setOption(IOptions.SOURCE_MAPS, false);
		p1.clearCached(mockAggregator.getCacheManager());
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

		Assert.assertNull(((ModuleBuildReader)reader).getSourceMap());
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
		mockAggregator.getOptions().setOption("developmentMode", "true");
		p1.getBuild(mockRequest).get().close();
		cacheKeys = p1.getKeys();
		System.out.println(cacheKeys);
		assertEquals("[expn:0;js:S:1:0:1;has{!conditionFalse,!conditionTrue}]", cacheKeys.toString());

		// Test error handling.  In production mode, a js syntax error should throw an excepton
		TestUtils.createTestFile(new File(tmpdir, "p1"), "err", TestUtils.err);
		p1 = new JsModuleTester("p1/err", new File(tmpdir, "p1/err.js").toURI());
		requestAttributes.put(IHttpTransport.OPTIMIZATIONLEVEL_REQATTRNAME, OptimizationLevel.SIMPLE);
		mockAggregator.getOptions().setOption("developmentMode", "false");
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
		Assert.assertEquals(compiled, TestUtils.err);
		Assert.assertTrue(future.get().isError());
		Assert.assertNotNull(future.get().getErrorMessage());

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

	/* This should really be moved to ModuleImplTest */
	@Test
	public void testGetModuleId() throws URISyntaxException {
		ModuleImpl module = new ModuleImpl("foo", new File("foo.js").toURI());
		assertEquals("foo", module.getModuleId());
	}

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
		keyGens = builder.getCacheKeyGenerators(hasConditionals, true);
		assertEquals("expn:0;js:W:1:0:1;has{}", KeyGenUtil.generateKey(mockRequest, keyGens));
		hasConditionals.add("foo");
		keyGens = builder.getCacheKeyGenerators(hasConditionals, true);
		assertEquals("expn:0;js:W:1:0:1;has{foo}", KeyGenUtil.generateKey(mockRequest, keyGens));
		hasConditionals.add("bar");
		keyGens = builder.getCacheKeyGenerators(hasConditionals, false);
		assertEquals("expn:0;js:W:0:0:1;has{!bar,foo}", KeyGenUtil.generateKey(mockRequest, keyGens));
		hasConditionals.add("undefined");
		keyGens = builder.getCacheKeyGenerators(hasConditionals, false);
		assertEquals("expn:0;js:W:0:0:1;has{!bar,foo}", KeyGenUtil.generateKey(mockRequest, keyGens));
	}


	@Test
	public void testGetCacheKey() {
		// JavaScriptModuleBuilder.getCacheKey() trivially calls JavaScriptModuleBuilder.s_generateCacheKey()
		// so no need to test it separately
	}

	@Test
	public void testIsHasFiltering() throws Exception {
		assertTrue(RequestUtil.isHasFiltering(mockRequest));
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		assertTrue(RequestUtil.isHasFiltering(mockRequest));
		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASFILTERING, true);
		assertFalse(RequestUtil.isHasFiltering(mockRequest));
	}

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
		Assert.assertEquals("", result);
		Assert.assertTrue(TypeUtil.asBoolean(mockRequest.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME)));
	}

	@Test
	public void testLayerBeginEndNotifier_exportModuleNames() throws Exception {
		List<IModule> modules = new ArrayList<IModule>();
		Set<String> dependentFeatures = new HashSet<String>();
		JavaScriptModuleBuilder builder = new JavaScriptModuleBuilder();
		String result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertEquals("", result);
		Assert.assertNull(mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES));

		Features features = new Features();
		mockRequest.setAttribute(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, true);
		mockRequest.setAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME, features);

		MockRequestedModuleNames reqnames = new MockRequestedModuleNames();
		reqnames.setExcludes(Arrays.asList(new String[]{"exclude"}));
		mockRequest.setAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, reqnames);

		final DependencyList mockConfigDeps = createMock(DependencyList.class);
		final DependencyList mockLayerDeps = createMock(DependencyList.class);

		ModuleDeps configExplicitDeps = new ModuleDeps();
		ModuleDeps configExpandedDeps = new ModuleDeps();
		configExplicitDeps.add("exclude", new ModuleDepInfo());
		configExpandedDeps.add("excludedep", new ModuleDepInfo());

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

		PowerMock.expectNew(DependencyList.class, isA(String.class), isA(List.class), isA(IAggregator.class), eq(features), anyBoolean(), eq(false))
		.andAnswer(new IAnswer<DependencyList>() {
			@Override public DependencyList answer() throws Throwable {
				@SuppressWarnings("unchecked")
				List<String> modules = (List<String>)getCurrentArguments()[1];
				if (Arrays.asList(new String[]{"exclude"}).equals(modules)) {
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
		Assert.assertEquals("", result);
		ModuleDeps resultDeps = (ModuleDeps)mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		Assert.assertEquals(expectedDeps, resultDeps);
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature1", "feature2"})), dependentFeatures);

		// Add a conditioned module dependency and make sure that it doesn't get added to the result set
		layerExpandedDeps.add("conditioneddep", new ModuleDepInfo("dojo/has", new BooleanTerm("condition"), null));
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertEquals("", result);
		resultDeps = (ModuleDeps)mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		Assert.assertEquals(expectedDeps, resultDeps);

		// Add the complementary condition, causing the combined condition to evaluate to true.
		// The resulting resolved dependency should now show up in the result set.
		layerExpandedDeps.add("conditioneddep", new ModuleDepInfo("dojo/has", new BooleanTerm("!condition"), null));
		expectedDeps.add("conditioneddep", new ModuleDepInfo());
		result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertEquals("", result);
		resultDeps = (ModuleDeps)mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES);
		Assert.assertEquals(expectedDeps, resultDeps);
	}

	private static final String loggingOutput = "console.log(\"%cEnclosing dependencies for require list expansion (these modules will be omitted from subsequent expanded require lists):\", \"color:blue;background-color:yellow\");console.log(\"%cExpanded dependencies for config deps:\", \"color:blue\");console.log(\"%c	exclude (exclude detail)\\r\\n	excludedep (excludedep detail)\\r\\n\", \"font-size:x-small\");console.log(\"%cExpanded dependencies for layer deps:\", \"color:blue\");console.log(\"%c	foo (foo detail)\\r\\n	bar (bar detail)\\r\\n	foodep (foodep detail)\\r\\n	bardep (bardep detail)\\r\\n\", \"font-size:x-small\");";
	@Test
	public void testLayerBeginEndNotifier_exportModuleNamesWithDetails() throws Exception {
		List<IModule> modules = new ArrayList<IModule>();
		Set<String> dependentFeatures = new HashSet<String>();
		JavaScriptModuleBuilder builder = new JavaScriptModuleBuilder();
		String result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertEquals("", result);
		Assert.assertNull(mockRequest.getAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES));

		Features features = new Features();
		mockRequest.setAttribute(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, true);
		mockRequest.setAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME, features);
		mockRequest.setAttribute(IHttpTransport.DEPENDENCYEXPANSIONLOGGING_REQATTRNAME, true);

		MockRequestedModuleNames reqnames = new MockRequestedModuleNames();
		reqnames.setExcludes(Arrays.asList(new String[]{"exclude"}));
		mockRequest.setAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, reqnames);

		final DependencyList mockConfigDeps = createMock(DependencyList.class);
		final DependencyList mockLayerDeps = createMock(DependencyList.class);

		ModuleDeps configExplicitDeps = new ModuleDeps();
		ModuleDeps configExpandedDeps = new ModuleDeps();
		configExplicitDeps.add("exclude", new ModuleDepInfo(null, null, "exclude detail"));
		configExpandedDeps.add("excludedep", new ModuleDepInfo(null, null, "excludedep detail"));

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

		PowerMock.expectNew(DependencyList.class, isA(String.class), isA(List.class), isA(IAggregator.class), eq(features), anyBoolean(), anyBoolean())
		.andAnswer(new IAnswer<DependencyList>() {
			@Override public DependencyList answer() throws Throwable {
				@SuppressWarnings("unchecked")
				List<String> modules = (List<String>)getCurrentArguments()[1];
				if (Arrays.asList(new String[]{"exclude"}).equals(modules)) {
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
		Assert.assertEquals("", result);	// no output if debug mode is not enabled
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

	@Test
	public void testLayerBeginEndNotifier_moduleIdEncoding() {
		List<IModule> modules = new ArrayList<IModule>();
		Set<String> dependentFeatures = new HashSet<String>();
		JavaScriptModuleBuilder builder = new JavaScriptModuleBuilder() {
			@Override protected String moduleNameIdEncodingBeginLayer(HttpServletRequest request) {
				return "[moduleNameIdEncodingBeginLayer]";
			}
			@Override protected String moduleNameIdEncodingBeginAMD(HttpServletRequest request) {
				return "[moduleNameIdEncodingBeginAMD]";
			}
			@Override protected String moduleNameIdEncodingEndLayer(HttpServletRequest request, List<IModule> modules) {
				return "[moduleNameIdEncodingEndLayer]";
			}
		};
		String result = builder.layerBeginEndNotifier(EventType.BEGIN_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertEquals("[moduleNameIdEncodingBeginLayer]", result);
		result = builder.layerBeginEndNotifier(EventType.BEGIN_AMD, mockRequest, modules, dependentFeatures);
		Assert.assertEquals("[moduleNameIdEncodingBeginAMD]", result);
		result = builder.layerBeginEndNotifier(EventType.END_LAYER, mockRequest, modules, dependentFeatures);
		Assert.assertEquals("[moduleNameIdEncodingEndLayer]", result);
	}

	@Test
	public void testModuleNameEncodingIdBeginLayer() throws Exception {
		TestJavaScriptModuleBuilder builder = new TestJavaScriptModuleBuilder();
		Assert.assertEquals("", builder.moduleNameIdEncodingBeginLayer(mockRequest));
		Assert.assertNull(mockRequest.getAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS));

		moduleIdMap[0] = new HashMap<String, Integer>();
		String result = builder.moduleNameIdEncodingBeginLayer(mockRequest);
		Assert.assertEquals("",  result);

		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, true);
		result = builder.moduleNameIdEncodingBeginAMD(mockRequest);
		Assert.assertEquals("(function(){var " + JavaScriptModuleBuilder.EXPDEPS_VARNAME + ";", result);
		Assert.assertNull(mockRequest.getAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS));

		requestAttributes.put(IHttpTransport.SERVEREXPANDLAYERS_REQATTRNAME, true);
		requestAttributes.remove(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS);
		result = builder.moduleNameIdEncodingBeginLayer(mockRequest);
		Assert.assertEquals("", result);
		Assert.assertNull(mockRequest.getAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS));
		requestAttributes.remove(IHttpTransport.SERVEREXPANDLAYERS_REQATTRNAME);

		mockRequest.removeAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS);
		IAggregator aggr = (IAggregator)mockRequest.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		options.setOption(IOptions.DISABLE_MODULENAMEIDENCODING, true);
		Assert.assertEquals("", builder.moduleNameIdEncodingBeginLayer(mockRequest));
		Assert.assertNull(mockRequest.getAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS));
	}

	@Test
	public void testModuleNameEncodingIdEndLayer() throws Exception {
		List<IModule> modules = new ArrayList<IModule>();
		moduleIdMap[0] = new HashMap<String, Integer>();

		TestJavaScriptModuleBuilder builder = new TestJavaScriptModuleBuilder() {
			@Override
			protected String generateModuleIdReg(List<String[]> expDeps, Map<String, Integer> idMap) {
				StringBuffer sb = new StringBuffer("[");
				for (String[] names : expDeps) {
					sb.append(Arrays.asList(names).toString());
				}
				sb.append("]");
				return sb.toString();
			}
		};
		Assert.assertEquals("})();", builder.moduleNameIdEncodingEndLayer(mockRequest, modules));

		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, true);

		ConcurrentListBuilder<String[]> expDeps = new ConcurrentListBuilder<String[]>();
		mockRequest.setAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS, expDeps);
		MockRequestedModuleNames reqNames = new MockRequestedModuleNames();

		// empty deps list
		String result = builder.moduleNameIdEncodingEndLayer(mockRequest, modules);
		Assert.assertEquals("})();", result);
		System.out.println(result);

		// with some values
		expDeps.add(new String[]{"dep1"});
		result = builder.moduleNameIdEncodingEndLayer(mockRequest, modules);
		System.out.println(result);
		Assert.assertEquals(JavaScriptModuleBuilder.EXPDEPS_VARNAME +
				"=[[dep1]];require.combo.reg(" +
				JavaScriptModuleBuilder.EXPDEPS_VARNAME +
				");})();", result);

		expDeps = new ConcurrentListBuilder<String[]>();
		mockRequest.setAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS, expDeps);
		expDeps.add(new String[]{"dep1", "dep2"});
		expDeps.add(new String[]{"foodep"});
		result = builder.moduleNameIdEncodingEndLayer(mockRequest, modules);
		System.out.println(result);
		Assert.assertEquals(JavaScriptModuleBuilder.EXPDEPS_VARNAME +
				"=[[dep1, dep2][foodep]];require.combo.reg(" +
				JavaScriptModuleBuilder.EXPDEPS_VARNAME +
				");})();", result);

		// Add deps, preloads and modules
		mockRequest.setAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, reqNames);
		reqNames.setDeps(Arrays.asList("bootLayerDep"));
		reqNames.setPreloads(Arrays.asList("bootLayerPreload"));
		reqNames.setModules(Arrays.asList("module1", "module2"));
		result = builder.moduleNameIdEncodingEndLayer(mockRequest, modules);
		System.out.println(result);
		Assert.assertEquals(JavaScriptModuleBuilder.EXPDEPS_VARNAME +
				"=[[dep1, dep2][foodep][bootLayerDep][bootLayerPreload]];require.combo.reg(" +
				JavaScriptModuleBuilder.EXPDEPS_VARNAME +
				");})();", result);

		// Make sure requested modules are registered if server expanding layers
		expDeps = new ConcurrentListBuilder<String[]>();
		mockRequest.setAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS, expDeps);
		mockRequest.setAttribute(IHttpTransport.SERVEREXPANDLAYERS_REQATTRNAME, true);
		result = builder.moduleNameIdEncodingEndLayer(mockRequest, modules);
		System.out.println(result);
		Assert.assertEquals("require.combo.reg([[bootLayerDep][bootLayerPreload][module1, module2]]);})();", result);

		// Make sure nothing is added if we are only requesting script modules
		mockRequest.removeAttribute(JavaScriptModuleBuilder.MODULE_EXPANDED_DEPS);
		reqNames = new MockRequestedModuleNames();
		reqNames.setScripts(Arrays.asList("script1"));
		mockRequest.setAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, reqNames);
		result = builder.moduleNameIdEncodingEndLayer(mockRequest, modules);
		Assert.assertEquals("", result);

	}

	@Test
	public void testGenerateModuleIdReg() throws Exception {
		Map<String, Integer> moduleIdMap = new HashMap<String, Integer>();
		moduleIdMap.put("dep1", 100);
		moduleIdMap.put("dep2", 102);
		moduleIdMap.put("foodep", 500);
		moduleIdMap.put("feature/dep1", 511);
		moduleIdMap.put("feature/dep2", 512);
		moduleIdMap.put("feature1/dep", 513);

		TestJavaScriptModuleBuilder builder = new TestJavaScriptModuleBuilder();

		List<String[]> expDeps = new ArrayList<String[]>();

		// empty deps list
		String result = builder.generateModuleIdReg(expDeps, moduleIdMap);
		Assert.assertEquals("[[],[]]", result);
		System.out.println(result);

		// with some values
		expDeps.add(new String[]{"dep1"});
		result = builder.generateModuleIdReg(expDeps, moduleIdMap);
		System.out.println(result);
		Assert.assertEquals("[[[\"dep1\"]],[[100]]]", result);

		expDeps.clear();
		expDeps.add(new String[]{"dep1", "dep2"});
		expDeps.add(new String[]{"foodep"});
		result = builder.generateModuleIdReg(expDeps, moduleIdMap);
		System.out.println(result);
		Assert.assertEquals("[[[\"dep1\",\"dep2\"],[\"foodep\"]],[[100,102],[500]]]", result);

		// including a dep which has no mapping
		expDeps.clear();
		expDeps.add(new String[]{"dep1", "dep2"});
		expDeps.add(new String[]{"foodep"});
		expDeps.add(new String[]{"nodep"});
		result = builder.generateModuleIdReg(expDeps, moduleIdMap);
		System.out.println(result);
		Assert.assertEquals("[[[\"dep1\",\"dep2\"],[\"foodep\"],[\"nodep\"]],[[100,102],[500],[0]]]", result);

		expDeps.add(new String[]{"dojo/has!feature?feature/dep1"});
		result = builder.generateModuleIdReg(expDeps, moduleIdMap);
		System.out.println(result);
		Assert.assertEquals("[[[\"dep1\",\"dep2\"],[\"foodep\"],[\"nodep\"],[\"dojo/has!feature?feature/dep1\"]],[[100,102],[500],[0],[511]]]", result);

		// Test compound module ids with has! plugin
		expDeps.clear();
		expDeps.add(new String[]{"dep1", "dojo/has!feature?feature/dep1"});
		result = builder.generateModuleIdReg(expDeps, moduleIdMap);
		System.out.println(result);
		Assert.assertEquals("[[[\"dep1\",\"dojo/has!feature?feature/dep1\"]],[[100,511]]]", result);

		expDeps.clear();
		expDeps.add(new String[]{"dep1", "dojo/has!feature?feature/dep1:feature/dep2"});
		result = builder.generateModuleIdReg(expDeps, moduleIdMap);
		System.out.println(result);
		Assert.assertEquals("[[[\"dep1\",\"dojo/has!feature?feature/dep1:feature/dep2\"]],[[100,[511,512]]]]", result);

		expDeps.clear();
		expDeps.add(new String[]{"dep1", "dojo/has!feature1?feature1/dep:feature?feature/dep1:feature/dep2", "dep2"});
		result = builder.generateModuleIdReg(expDeps, moduleIdMap);
		System.out.println(result);
		Assert.assertEquals("[[[\"dep1\",\"dojo/has!feature1?feature1/dep:feature?feature/dep1:feature/dep2\",\"dep2\"]],[[100,[513,511,512],102]]]", result);

	}

	@Test
	public void testCacheKeyGeneratorEquals() {
		Set<String> features = new HashSet<String>();
		features.add("feature1");
		features.add("feature2");
		CacheKeyGenerator keyGen = new CacheKeyGenerator(features, false, false);
		Set<String> otherFeatures = new HashSet<String>(features);
		Assert.assertEquals(keyGen, new CacheKeyGenerator(otherFeatures, false, false));
		Assert.assertFalse(keyGen.equals(new CacheKeyGenerator(otherFeatures, true, false)));
		Assert.assertFalse(keyGen.equals(new CacheKeyGenerator(otherFeatures, false, true)));
		Assert.assertFalse(keyGen.equals(new CacheKeyGenerator(otherFeatures, true, true)));
		otherFeatures.remove("feature2");
		Assert.assertFalse(keyGen.equals(new CacheKeyGenerator(otherFeatures, false, false)));
	}

	@Test
	public void testCacheKeyGeneratorGenerateKey() {
		// Make sure that key specifies expandRequires only if the option is specified
		// in the request and the module contains expandable requires
		Set<String> features = Collections.emptySet();
		CacheKeyGenerator keyGen = new CacheKeyGenerator(features, false /*hasExpandableRequires*/, false);
		Assert.assertEquals("js:S:0:0:1;has{}", keyGen.generateKey(mockRequest));
		mockRequest.setAttribute(AbstractHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, true);
		Assert.assertEquals("js:S:0:0:1;has{}", keyGen.generateKey(mockRequest));
		keyGen = new CacheKeyGenerator(features, true /*hasExpandableRequires*/, false);
		Assert.assertEquals("js:S:1:0:1;has{}", keyGen.generateKey(mockRequest));
		mockRequest.removeAttribute(AbstractHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME);
		Assert.assertEquals("js:S:0:0:1;has{}", keyGen.generateKey(mockRequest));
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
				Set<String> dependentFeatures, boolean hasExpandableRequires) {
			return super.getCacheKeyGenerators(dependentFeatures, hasExpandableRequires);
		}
		@Override
		public String moduleNameIdEncodingBeginLayer(HttpServletRequest request) {
			return super.moduleNameIdEncodingBeginLayer(request);
		}
		@Override
		public String moduleNameIdEncodingEndLayer(HttpServletRequest request, List<IModule> modules) {
			return super.moduleNameIdEncodingEndLayer(request, modules);
		}
		@Override
		protected String generateModuleIdReg(List<String[]> expDeps, Map<String, Integer> idMap) {
			return super.generateModuleIdReg(expDeps, idMap);
		}
	}
}
