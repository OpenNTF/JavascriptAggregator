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

package com.ibm.jaggr.service.impl.deps;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.impl.module.ModuleImpl;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.JavaScriptModuleBuilder;
import com.ibm.jaggr.service.impl.modulebuilder.text.TextModuleBuilder;
import com.ibm.jaggr.service.impl.resource.FileResource;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.test.TestUtils;

public class DependenciesTest extends EasyMock {

	File tmpdir = null;
	TestUtils.Ref<IConfig> configRef = new TestUtils.Ref<IConfig>(null);
	IAggregator mockAggregator;
	
	
	@BeforeClass
	public static void setupClass() throws Exception {
	}
	
	@Before 
	public void setup() {
		tmpdir = Files.createTempDir();
		mockAggregator = createMockAggregator();
		
	}
	@After
	public void tearDown() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}
	
	private TestDependenciesWrapper createDependencies(File tmpdir, boolean clean, boolean validateDeps) throws Exception {

		TestUtils.createTestFiles(tmpdir);
		String rawConfig = "{paths:{'p1':'p1','p2':'p2','p2/p1':'p2/p1'}}";
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), rawConfig));
		return new TestDependenciesWrapper(tmpdir, mockAggregator, clean, validateDeps);
	}
	
	/**
	 * Test method for {@link com.ibm.jaggr.service.impl.deps.DepTree#Dependencies(java.util.Collection, java.io.File, boolean, boolean)}.
	 * @throws ClassNotFoundException 
	 */
	@Test
	public void testDependencies() throws Exception {
		TestDependenciesWrapper deps = createDependencies(tmpdir, false, false);
		ConcurrentMap<URI, DepTreeNode> depMap = deps.getDepMap();
		assertEquals(2, depMap.size());
		URI p1Path = tmpdir.toURI().resolve("p1");
		URI p2Path = tmpdir.toURI().resolve("p2");
		DepTreeNode p1Node = depMap.get(p1Path);
		DepTreeNode p2Node = depMap.get(p2Path);
		
		assertEquals(4, p1Node.getChildren().size());
		assertEquals(3, p2Node.getChildren().size());
		assertEquals(0, p1Node.getChild("a").getChildren().size());
		assertEquals(0, p1Node.getChild("b").getChildren().size());
		assertEquals(0, p1Node.getChild("c").getChildren().size());
		assertEquals(0, p1Node.getChild("p1").getChildren().size());
		assertEquals(0, p2Node.getChild("a").getChildren().size());
		assertEquals(0, p2Node.getChild("b").getChildren().size());
		assertEquals(2, p2Node.getChild("p1").getChildren().size());
		assertEquals(0, p2Node.getChild("p1").getChild("a").getChildren().size());
		assertEquals(4, p2Node.getChild("p1").getChild("p1").getChildren().size());
		assertEquals(0, p2Node.getChild("p1").getChild("p1").getChild("foo").getChildren().size());

		assertEquals("[./b]",
				Arrays.asList(p1Node.getChild("a").getDepArray()).toString());
		assertEquals("[./c]", 
				Arrays.asList(p1Node.getChild("b").getDepArray()).toString());
		assertEquals("[./a, ./b, ./noexist]", 
				Arrays.asList(p1Node.getChild("c").getDepArray()).toString());
		assertEquals("[p1/a, p2/p1/b, p2/p1/p1/c, p2/noexist]", 
				Arrays.asList(p1Node.getChild("p1").getDepArray()).toString());
		assertEquals("[./b]", 
				Arrays.asList(p2Node.getChild("a").getDepArray()).toString());
		assertEquals("[./c]", 
				Arrays.asList(p2Node.getChild("b").getDepArray()).toString());
		assertEquals("[./b]", 
				Arrays.asList(p2Node.getChild("p1").getChild("a").getDepArray()).toString());
		assertEquals("[./c]", 
				Arrays.asList(p2Node.getChild("p1").getChild("p1").getDepArray()).toString());
		assertEquals("[p1/a, p2/p1/b, p2/p1/p1/c, p2/noexist]", 
				Arrays.asList(p2Node.getChild("p1").getChild("p1").getChild("foo").getDepArray()).toString());
		
		DepTreeNode p1_a_node = p1Node.getChild("a");
		long yesterday = new Date().getTime()- 24*60*60*1000;
		p1_a_node.setDependencies(new String[]{"p2/p1/b", "p2/p1/p1/c", "p2/noexist"}, yesterday, yesterday);
		
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.impl.deps.DepTree#mapDependencies(com.ibm.jaggr.service.modules.AMDConfig)}.
	 * @throws ClassNotFoundException 
	 * @throws CloneNotSupportedException 
	 */
	@Test
	public void testGetDependencyMap() throws Exception {
		TestDependenciesWrapper deps = createDependencies(tmpdir, false, false);
		ConcurrentMap<URI, DepTreeNode> depMap = deps.getDepMap();
		
		URI p1Path = tmpdir.toURI().resolve("p1");
		URI p2Path = tmpdir.toURI().resolve("p2");
		URI p3Path = tmpdir.toURI().resolve("p3");
		DepTreeNode p1Node = depMap.get(p1Path);
		DepTreeNode p2Node = depMap.get(p2Path);
		
		long yesterday = new Date().getTime() - 24*60*60*1000;
		p1Node.getChild("a").setDependencies(new String[]{"foo/bar"}, yesterday, yesterday);
		// Leave deps the same to test that lastModifiedDep doesn't change
		p1Node.getChild("b").setDependencies(p1Node.getChild("b").getDepArray(), yesterday, yesterday);
		DepTreeNode p3Node = new DepTreeNode("p3");
		p3Node.add(new DepTreeNode("a"));
		// create a node that is not on the file system
		p3Node.getChild("a").setDependencies(new String[]{"./b"}, yesterday, yesterday);
		depMap.put(p3Path, p3Node);
		long p2_p1_a_lastMod = new File(tmpdir, "p2/p1/a.js").lastModified();
		// change deps but leave timestamps the same so that the deps won't be updated by validation
		// but will be updated by clean.
		p2Node.getChild("p1").getChild("a").setDependencies(new String[]{"xxx"}, p2_p1_a_lastMod, p2_p1_a_lastMod);

		File cacheFile = new File(tmpdir, "deps/depmap.cache");
		String configJson = "{paths: {p1Alias:'p1', p2Alias:'p2'}}"; 
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		deps = new TestDependenciesWrapper(depMap, configRef.get());
		ObjectOutputStream os;
		os = new ObjectOutputStream(new FileOutputStream(cacheFile));
		os.writeObject(deps);
		os.close();

		deps = new TestDependenciesWrapper(tmpdir, mockAggregator, false, false);

		Map<String, URI> map = new HashMap<String, URI>();
		map.put("p1Alias", p1Path);
		map.put("p2Alias", p2Path);
		map.put("p3Alias", p3Path);
		configJson = "{paths: {p1Alias:'p1', p2Alias:'p2', p3Alias:'p3'}}"; 
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson));
		DepTreeRoot root = new DepTreeRoot(configRef.get());
		deps.mapDependencies(root, null, map, configRef.get());
		
		assertEquals("", root.getName());
		assertEquals(3, root.getChildren().size());
		assertEquals("[foo/bar]",
				Arrays.asList(root.getChild("p1Alias").getChild("a").getDepArray()).toString());
		assertEquals(yesterday, root.getChild("p1Alias").getChild("a").lastModified());
		assertEquals(yesterday, root.getChild("p1Alias").getChild("a").lastModifiedDep());
		assertEquals(yesterday, root.getChild("p1Alias").getChild("b").lastModified());
		assertEquals(yesterday, root.getChild("p1Alias").getChild("b").lastModifiedDep());
		assertEquals("[./b]",
				Arrays.asList(root.getChild("p3Alias").getChild("a").getDepArray()).toString());
		assertEquals("[xxx]",
				Arrays.asList(root.getChild("p2Alias").getChild("p1").getChild("a").getDepArray()).toString());
		assertEquals(p2_p1_a_lastMod, root.getChild("p2Alias").getChild("p1").getChild("a").lastModified());
		assertEquals(p2_p1_a_lastMod, root.getChild("p2Alias").getChild("p1").getChild("a").lastModifiedDep());
		
		// Test validation
		deps = new TestDependenciesWrapper(tmpdir, mockAggregator, false, true);
		root = new DepTreeRoot(configRef.get());
		deps.mapDependencies(root, null, map, configRef.get());
		assertEquals("", root.getName());
		assertEquals(3, root.getChildren().size());
		assertEquals(0, root.getChild("p3Alias").getChildren().size());
		assertEquals("[./b]",
				Arrays.asList(root.getChild("p1Alias").getChild("a").getDepArray()).toString());
		assertEquals(new File(tmpdir, "p1/a.js").lastModified(), root.getChild("p1Alias").getChild("a").lastModified());
		assertEquals(new File(tmpdir, "p1/a.js").lastModified(), root.getChild("p1Alias").getChild("a").lastModifiedDep());
		assertEquals(new File(tmpdir, "p1/b.js").lastModified(), root.getChild("p1Alias").getChild("b").lastModified());
		assertEquals(yesterday, root.getChild("p1Alias").getChild("b").lastModifiedDep());
		assertEquals("[xxx]",
				Arrays.asList(root.getChild("p2Alias").getChild("p1").getChild("a").getDepArray()).toString());
		assertEquals(p2_p1_a_lastMod, root.getChild("p2Alias").getChild("p1").getChild("a").lastModified());
		assertEquals(p2_p1_a_lastMod, root.getChild("p2Alias").getChild("p1").getChild("a").lastModifiedDep());
		
		// Ensure clean argument works.  Node /p2Alias/p1/a won't get updated by validatation because
		// the timestamps are identical to the file timestamps, but it should be updated by specifying
		// the clean flag.
		deps = new TestDependenciesWrapper(tmpdir, mockAggregator, true, false);
		deps.mapDependencies(root, null, map, configRef.get());
		assertEquals("[./b]",
				Arrays.asList(root.getChild("p2Alias").getChild("p1").getChild("a").getDepArray()).toString());
		assertEquals(p2_p1_a_lastMod, root.getChild("p2Alias").getChild("p1").getChild("a").lastModified());
		assertEquals(p2_p1_a_lastMod, root.getChild("p2Alias").getChild("p1").getChild("a").lastModifiedDep());
	}
	
	private IAggregator createMockAggregator() {
		IAggregator mockAggregator = createNiceMock(IAggregator.class);
		expect(mockAggregator.getWorkingDirectory()).andReturn(tmpdir).anyTimes();
		expect(mockAggregator.getConfig()).andAnswer(new IAnswer<IConfig>() {
			public IConfig answer() throws Throwable {
				return configRef.get();
			}
		}).anyTimes();
		expect(mockAggregator.getName()).andReturn("test").anyTimes();
		expect(mockAggregator.newResource((URI)anyObject())).andAnswer(new IAnswer<IResource>() {
			public IResource answer() throws Throwable {
				return new FileResource((URI)getCurrentArguments()[0]);
			}
		}).anyTimes();
		expect(mockAggregator.newModule((String)anyObject(), (URI)anyObject())).andAnswer(new IAnswer<IModule>() {
			public IModule answer() throws Throwable {
				String mid = (String)getCurrentArguments()[0];
				URI uri = (URI)getCurrentArguments()[1];
				return new ModuleImpl(mid, uri);
			}
		}).anyTimes();
		expect(mockAggregator.getModuleBuilder((String)anyObject(), (IResource)anyObject())).andAnswer(new IAnswer<IModuleBuilder>() {
			public IModuleBuilder answer() throws Throwable {
				String mid = (String)getCurrentArguments()[0];
				return mid.contains(".") ? new TextModuleBuilder() : new JavaScriptModuleBuilder();
			}
		}).anyTimes();
		
		replay(mockAggregator);
		return mockAggregator;
	}
	
	private static class TestDependenciesWrapper extends DepTree {
		private static final long serialVersionUID = 7700824773233302591L;

		public TestDependenciesWrapper(File cacheDir, IAggregator aggregator,
				boolean clean, boolean validateDeps) throws Exception {
			super(aggregator.getConfig().getPathURIs().values(), aggregator, clean, validateDeps);
		}
		
		/** For unit testing 
		 * @throws CloneNotSupportedException */
		public TestDependenciesWrapper(ConcurrentMap<URI, DepTreeNode> depMap, IConfig config) throws IOException {
			this.depMap = depMap;
			this.rawConfig = config.getRawConfig();
		}
		
		public ConcurrentMap<URI, DepTreeNode> getDepMap() {
			return depMap;
		}
		
	}
}
