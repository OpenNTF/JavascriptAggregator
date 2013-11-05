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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.deps.ModuleDepInfo;
import com.ibm.jaggr.service.deps.ModuleDeps;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.test.BaseTestUtils;
import com.ibm.jaggr.service.test.BaseTestUtils.Ref;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.CopyUtil;
import com.ibm.jaggr.service.util.Features;

public class ModuleImplTest {
	
	static File tmpdir = null;
	IAggregator mockAggregator;
	Ref<IConfig> configRef = new Ref<IConfig>(null);
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	HttpServletRequest mockRequest;
	HttpServletResponse mockResponse = EasyMock.createNiceMock(HttpServletResponse.class);
	IDependencies mockDependencies = EasyMock.createMock(IDependencies.class);
	static final Map<String, ModuleDeps> testDepMap = BaseTestUtils.createTestDepMap();

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = Files.createTempDir();
		BaseTestUtils.createTestFiles(tmpdir);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (tmpdir != null) {
			BaseTestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	@SuppressWarnings("unchecked")
	@Before
	public void setup() throws Exception {
		mockAggregator = BaseTestUtils.createMockAggregator(configRef, tmpdir);
		mockRequest = BaseTestUtils.createMockRequest(mockAggregator, requestAttributes);
		EasyMock.expect(mockAggregator.getDependencies()).andAnswer(new IAnswer<IDependencies>() {
			public IDependencies answer() throws Throwable {
				return mockDependencies;
			}
		}).anyTimes();
		
		EasyMock.expect(mockDependencies.getDelcaredDependencies(EasyMock.eq("p1/p1"))).andReturn(Arrays.asList(new String[]{"p1/a", "p2/p1/b", "p2/p1/p1/c", "p2/noexist"})).anyTimes();
		EasyMock.expect(mockDependencies.getExpandedDependencies((String)EasyMock.anyObject(), (Features)EasyMock.anyObject(), (Set<String>)EasyMock.anyObject(), EasyMock.anyBoolean(), EasyMock.anyBoolean())).andAnswer(new IAnswer<ModuleDeps>() {
			public ModuleDeps answer() throws Throwable {
				String name = (String)EasyMock.getCurrentArguments()[0];
				Features features = (Features)EasyMock.getCurrentArguments()[1];
				Set<String> dependentFeatures = (Set<String>)EasyMock.getCurrentArguments()[2];
				ModuleDeps result = testDepMap.get(name);
				if (result == null) {
					result = BaseTestUtils.emptyDepMap;
				}
				// resolve aliases
				ModuleDeps temp = new ModuleDeps();
				IConfig config = mockAggregator.getConfig();
				for (Map.Entry<String, ModuleDepInfo> entry : result.entrySet()) {
					String depName = entry.getKey();
					String resolved = config.resolve(depName, features, dependentFeatures, null, true);
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
		testDepMap.get("p2/a").add("p1/aliased/d", new ModuleDepInfo(null, null, ""));
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
}
