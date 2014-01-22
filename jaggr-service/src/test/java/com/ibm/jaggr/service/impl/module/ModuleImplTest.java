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

package com.ibm.jaggr.service.impl.module;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.apache.commons.lang.ArrayUtils;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.io.Files;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.JavaScriptBuildRenderer;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.JavaScriptModuleBuilder;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;

@RunWith(PowerMockRunner.class)
@PrepareForTest(JavaScriptModuleBuilder.class)
public class ModuleImplTest {
	
	static File tmpdir = null;
	IAggregator mockAggregator;
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	HttpServletRequest mockRequest;
	HttpServletResponse mockResponse = EasyMock.createNiceMock(HttpServletResponse.class);
	IDependencies mockDependencies = EasyMock.createMock(IDependencies.class);
	static final Map<String, String[]> testDepMap = TestUtils.createTestDepMap();

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

	@Before
	public void setup() throws Exception {
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		EasyMock.expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		EasyMock.expect(mockDependencies.getLastModified()).andReturn(0L).anyTimes();
		EasyMock.expect(mockDependencies.getDelcaredDependencies(EasyMock.isA(String.class))).andAnswer(new IAnswer<List<String>>() {
			@Override
			public List<String> answer() throws Throwable {
				String name = (String)EasyMock.getCurrentArguments()[0];
				String[] result = testDepMap.get(name);
				return result != null ? Arrays.asList(result) : null;
			}
		}).anyTimes();

		URI p1Path = new File(tmpdir, "p1").toURI();
		URI p2Path = new File(tmpdir, "p2").toURI();
		final Map<String, URI> map = new HashMap<String, URI>();
		map.put("p1", p1Path);
		map.put("p2", p2Path);

		EasyMock.replay(mockAggregator);
		EasyMock.replay(mockRequest);
		EasyMock.replay(mockResponse);
		EasyMock.replay(mockDependencies);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
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
	
	/*
	 * Tests to make sure that the cache key generator for the module is properly updated
	 * by requests that modify the dependent feature set (implemented via a path alias
	 * config function).
	 */
	@Test
	public void testModuleImpl() throws Exception {
		testDepMap.put("p2/a", (String[])ArrayUtils.add(testDepMap.get("p2/a"), "p1/aliased/d"));
		String configJson = "{paths:{p1:'p1',p2:'p2'}, aliases:[[/\\/aliased\\//, function(s){if (has('foo')) return '/foo/'; else if (has('bar')) return '/bar/'; has('non'); return '/non/'}]]}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		
		Features features = new Features();
		features.put("foo", true);
		features.put("bar", true);
		requestAttributes.put(IHttpTransport.FEATUREMAP_REQATTRNAME, features);
		
		ModuleImpl module = (ModuleImpl)mockAggregator.newModule("p1/p1", mockAggregator.getConfig().locateModuleResource("p1/p1"));
		Reader reader  = module.getBuild(mockRequest).get();
		System.out.println(module.toString());
		Assert.assertEquals("[expn, js:(has:[conditionFalse, conditionTrue, foo])]", module.getCacheKeyGenerators().toString());
		Assert.assertTrue(module.getKeys().size() == 1 && module.getKeys().containsAll(Arrays.asList(new String[]{"expn:0;js:S:1:0:1;has{foo}"})));
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		String compiled = writer.toString();
		System.out.println(compiled);
		assertTrue(Pattern.compile("require\\(\\[.*?,\\\"p1/foo/d\\\".*?\\],").matcher(compiled).find());
		
		features.put("foo", false);
		reader = module.getBuild(mockRequest).get();
		System.out.println(module.toString());
		Assert.assertEquals("[expn, js:(has:[bar, conditionFalse, conditionTrue, foo])]", module.getCacheKeyGenerators().toString());
		Assert.assertTrue(module.getKeys().size() == 2 && module.getKeys().containsAll(Arrays.asList(
				new String[]{"expn:0;js:S:1:0:1;has{foo}", "expn:0;js:S:1:0:1;has{bar,!foo}"})));
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		assertTrue(Pattern.compile("require\\(\\[.*?,\\\"p1/bar/d\\\".*?\\],").matcher(compiled).find());
		
		features.put("bar", false);
		reader = module.getBuild(mockRequest).get();
		System.out.println(module.toString());
		List<ICacheKeyGenerator> cacheKeyGenerators = module.getCacheKeyGenerators();
		Assert.assertEquals("[expn, js:(has:[bar, conditionFalse, conditionTrue, foo, non])]", module.getCacheKeyGenerators().toString());
		Assert.assertTrue(module.getKeys().size() == 3 && module.getKeys().containsAll(Arrays.asList(
				new String[]{
					"expn:0;js:S:1:0:1;has{foo}", 
					"expn:0;js:S:1:0:1;has{bar,!foo}",
					"expn:0;js:S:1:0:1;has{!bar,!foo}" 
				}
		)));
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		System.out.println(compiled);
		assertTrue(Pattern.compile("require\\(\\[.*?,\\\"p1/non/d\\\".*?\\],").matcher(compiled).find());
		
		features.remove("bar");
		features.put("foo", true);
		reader = module.getBuild(mockRequest).get();
		System.out.println(module.toString());
		Assert.assertTrue(cacheKeyGenerators == module.getCacheKeyGenerators());
		Assert.assertTrue(module.getKeys().size() == 3 && module.getKeys().containsAll(Arrays.asList(
				new String[]{
					"expn:0;js:S:1:0:1;has{foo}", 
					"expn:0;js:S:1:0:1;has{bar,!foo}",
					"expn:0;js:S:1:0:1;has{!bar,!foo}" 
				}
		)));
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		System.out.println(compiled);
		assertTrue(Pattern.compile("require\\(\\[.*?,\\\"p1/foo/d\\\".*?\\],").matcher(compiled).find());

		features.put("bar", true);
		reader = module.getBuild(mockRequest).get();
		System.out.println(module.toString());
		Assert.assertTrue(cacheKeyGenerators == module.getCacheKeyGenerators());
		Assert.assertTrue(module.getKeys().size() == 4 && module.getKeys().containsAll(Arrays.asList(
				new String[]{
					"expn:0;js:S:1:0:1;has{foo}", 
					"expn:0;js:S:1:0:1;has{bar,foo}",
					"expn:0;js:S:1:0:1;has{bar,!foo}",
					"expn:0;js:S:1:0:1;has{!bar,!foo}"
				}
		)));
		writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		compiled = writer.toString();
		assertTrue(Pattern.compile("require\\(\\[.*?,\\\"p1/foo/d\\\".*?\\],").matcher(compiled).find());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testBuildRendererDependentFeatures() throws Exception {
		
		PowerMock.expectNew(JavaScriptBuildRenderer.class, EasyMock.isA(String.class), EasyMock.isA(String.class), EasyMock.isA(List.class), EasyMock.eq(false)).andAnswer(new IAnswer<JavaScriptBuildRenderer>() {
			@SuppressWarnings("serial")
			@Override
			public JavaScriptBuildRenderer answer() throws Throwable {
				String mid = (String)EasyMock.getCurrentArguments()[0];
				String content = (String)EasyMock.getCurrentArguments()[1];
				List<ModuleDeps> depList = (List<ModuleDeps>)EasyMock.getCurrentArguments()[2];
				Boolean isLogging = (Boolean)EasyMock.getCurrentArguments()[3];
				return new JavaScriptBuildRenderer(mid, content, depList, isLogging) {
					@Override
					public String renderBuild(HttpServletRequest request, Set<String> dependentFeatures) {
						dependentFeatures.add("feature1");
						return super.renderBuild(request, dependentFeatures);
					}
				};
			}
			
		}).anyTimes();
		PowerMock.replay(JavaScriptBuildRenderer.class);
		requestAttributes.put(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, Boolean.TRUE);
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}"));
		requestAttributes.put(IHttpTransport.FEATUREMAP_REQATTRNAME, new Features());
		requestAttributes.put(ILayer.DEPENDENT_FEATURES, new HashSet<String>());
		ModuleImpl module = (ModuleImpl)mockAggregator.newModule("p1/p1", mockAggregator.getConfig().locateModuleResource("p1/p1"));
		Reader reader  = module.getBuild(mockRequest).get();
		System.out.println(module.toString());
		StringWriter writer = new StringWriter();
		// make sure dependent features from build renderer aren't added until reader is actually read
		Assert.assertEquals(0, ((Set<String>)mockRequest.getAttribute(ILayer.DEPENDENT_FEATURES)).size());
		CopyUtil.copy(reader, writer);
		// dependent features from build renderer should now be in request
		Assert.assertEquals((Set<String>)new HashSet<String>(Arrays.asList(new String[]{"feature1"})), (Set<String>)mockRequest.getAttribute(ILayer.DEPENDENT_FEATURES));
		
	}
}
