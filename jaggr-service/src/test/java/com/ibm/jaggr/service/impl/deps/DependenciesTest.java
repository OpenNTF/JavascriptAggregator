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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.test.TestUtils;

import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public class DependenciesTest extends EasyMock {

	File tmpdir = null;
	TestUtils.Ref<IConfig> configRef = new TestUtils.Ref<IConfig>(null);
	IAggregator mockAggregator;


	@BeforeClass
	public static void setupClass() throws Exception {
	}

	@Before
	public void setup() throws Exception {
		tmpdir = Files.createTempDir();
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		EasyMock.replay(mockAggregator);

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
	 * Test method for {@link com.ibm.jaggr.service.deps.impl.DepTree#Dependencies(java.util.Collection, java.io.File, boolean, boolean)}.
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
		p1_a_node.setDependencies(new String[]{"p2/p1/b", "p2/p1/p1/c", "p2/noexist"}, null, yesterday, yesterday);

	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.deps.impl.DepTree#mapDependencies(com.ibm.jaggr.service.modules.AMDConfig)}.
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
		p1Node.getChild("a").setDependencies(new String[]{"foo/bar"}, new String[0], yesterday, yesterday);
		// Leave deps the same to test that lastModifiedDep doesn't change
		p1Node.getChild("b").setDependencies(p1Node.getChild("b").getDepArray(), new String[0], yesterday, yesterday);
		DepTreeNode p3Node = new DepTreeNode("p3");
		p3Node.add(new DepTreeNode("a"));
		// create a node that is not on the file system
		p3Node.getChild("a").setDependencies(new String[]{"./b"}, new String[0], yesterday, yesterday);
		depMap.put(p3Path, p3Node);
		long p2_p1_a_lastMod = new File(tmpdir, "p2/p1/a.js").lastModified();
		// change deps but leave timestamps the same so that the deps won't be updated by validation
		// but will be updated by clean.
		p2Node.getChild("p1").getChild("a").setDependencies(new String[]{"xxx"}, new String[0], p2_p1_a_lastMod, p2_p1_a_lastMod);

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
		deps.mapDependencies(root, map);

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
		deps.mapDependencies(root, map);
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
		deps.mapDependencies(root, map);
		assertEquals("[./b]",
				Arrays.asList(root.getChild("p2Alias").getChild("p1").getChild("a").getDepArray()).toString());
		assertEquals(p2_p1_a_lastMod, root.getChild("p2Alias").getChild("p1").getChild("a").lastModified());
		assertEquals(p2_p1_a_lastMod, root.getChild("p2Alias").getChild("p1").getChild("a").lastModifiedDep());
	}

	private static class TestDependenciesWrapper extends DepTree {
		private static final long serialVersionUID = 7700824773233302591L;

		public TestDependenciesWrapper(File cacheDir, IAggregator aggregator,
				boolean clean, boolean validateDeps) throws Exception {
			super(getPathURIs(aggregator.getConfig().getPaths().values()), aggregator, 0, clean, validateDeps);
		}

		/** For unit testing
		 * @throws CloneNotSupportedException */
		public TestDependenciesWrapper(ConcurrentMap<URI, DepTreeNode> depMap, IConfig config) throws IOException {
			this.depMap = depMap;
			this.rawConfig = config.toString();
		}

		public ConcurrentMap<URI, DepTreeNode> getDepMap() {
			return depMap;
		}

		private static Collection<URI> getPathURIs(Collection<IConfig.Location> locations) {
			Collection<URI> uris = new HashSet<URI>();
			for (IConfig.Location location : locations) {
				uris.add(location.getPrimary());
			}
			return uris;
		}

	}
}
