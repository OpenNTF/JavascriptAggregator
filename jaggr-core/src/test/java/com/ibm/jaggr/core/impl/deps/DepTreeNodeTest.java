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

package com.ibm.jaggr.core.impl.deps;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.impl.deps.DepTreeNode;
import com.ibm.jaggr.core.test.TestUtils;

import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

public class DepTreeNodeTest extends EasyMock {
	File tmpdir = null;
	TestUtils.Ref<IConfig> configRef = new TestUtils.Ref<IConfig>(null);
	IAggregator mockAggregator;

	@Before
	public void setup() throws Exception {
		tmpdir = Files.createTempDir();
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		EasyMock.replay(mockAggregator);
		configRef.set(new ConfigImpl(
				mockAggregator,
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				"{}"
				));
	}

	@After
	public void tearDown() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	@Test
	public void testDepTreeNode() {
		DepTreeNode node = new DepTreeNode("foo", null);
		assertNotNull(node);
		try {
			node = new DepTreeNode("foo/bar", null);
			fail("Exception not thrown");
		} catch (IllegalArgumentException e) {
		}
		try {
			node = new DepTreeNode(null, null);
			fail("Exception not thrown");
		} catch (NullPointerException e) {
		}
	}

	@Before
	public void testSetup() {

	}

	@Test
	public void testGetName() {
		DepTreeNode node = new DepTreeNode("foo", null);
		assertEquals(node.getName(), "foo");
	}

	@Test
	public void testLastModified() {
		DepTreeNode node = new DepTreeNode("foo", null);
		assertEquals(node.lastModified(), -1);
		node.setDependencies(new String[]{"dep1", "dep2"}, new String[0], new String[0], 1234567890L, 0L);
		assertEquals(node.lastModified(), 1234567890L);
	}

	@Test
	public void testLastModifiedDep() {
		DepTreeNode node = new DepTreeNode("foo", null);
		assertEquals(node.lastModifiedDep(), -1);
		node.setDependencies(new String[]{"dep1", "dep2"}, new String[0], new String[0], 0L, 1234567890L);
		assertEquals(node.lastModifiedDep(), 1234567890L);
	}

	@Test
	public void testLastModifiedDepTree() {
		DepTreeNode a = new DepTreeNode("a", null);
		DepTreeNode aa = new DepTreeNode("aa", null);
		DepTreeNode ab = new DepTreeNode("ab", null);
		a.add(aa);
		a.add(ab);
		a.setDependencies(new String[]{"b", "c"}, new String[0], new String[0], 1234567890L, 1234567890L);
		aa.setDependencies(new String[]{"b", "c"}, new String[0], new String[0], 1234567890L, 1234567890L);
		ab.setDependencies(new String[]{"b", "c"}, new String[0], new String[0], 1234567890L, 2000000000L);
		assertEquals(a.lastModifiedDepTree(), 2000000000L);
	}

	@Test
	public void testGetParent() {
		DepTreeNode parent = new DepTreeNode("parent", null);
		DepTreeNode child = new DepTreeNode("child", null);
		parent.add(child);
		assertEquals(parent, child.getParent());
		assertEquals(null, parent.getParent());
	}

	@Test
	public void testGetRoot() {
		DepTreeNode parent = new DepTreeNode("parent", null);
		DepTreeNode child = new DepTreeNode("child", null);
		DepTreeNode grandchild = new DepTreeNode("grandchild", null);
		parent.add(child);
		child.add(grandchild);
		assertEquals(parent, parent.getRoot());
		assertEquals(parent, child.getRoot());
		assertEquals(parent, grandchild.getRoot());
	}

	@Test
	public void testGetDepArrays() {
		String[] defineDeps = new String[] { "dep1", "dep2" };
		String[] requireDeps = new String[] { "req1", "req2" };
		DepTreeNode node = new DepTreeNode("node", null);
		node.setDependencies(defineDeps, requireDeps, new String[0], 1234567890L, 1234567890L);
		assert(Arrays.equals(node.getDefineDepArray(), defineDeps));
		assert(Arrays.equals(node.getRequireDepArray(), requireDeps));
	}

	@Test
	public void testGetParentPath() {
		DepTreeNode root = new DepTreeNode("root", null);
		DepTreeNode child = new DepTreeNode("child", null);
		DepTreeNode grandchild = new DepTreeNode("grandchild", null);
		root.add(child);
		child.add(grandchild);
		assertNull(root.getParentPath());
		assertEquals("root", child.getParentPath());
		assertEquals("root/child", grandchild.getParentPath());
	}

	@Test
	public void testGetFullPathName() {
		DepTreeNode root = new DepTreeNode("root", null);
		DepTreeNode child = new DepTreeNode("child", null);
		DepTreeNode grandchild = new DepTreeNode("grandchild", null);
		root.add(child);
		child.add(grandchild);
		assertEquals("root", root.getFullPathName());
		assertEquals("root/child", child.getFullPathName());
		assertEquals("root/child/grandchild", grandchild.getFullPathName());
	}

	@Test
	public void testAdd() {
		DepTreeNode parent = new DepTreeNode("parent", null);
		DepTreeNode child = new DepTreeNode("child", null);
		assertEquals(null, child.getParent());
		parent.add(child);
		assertEquals(parent, child.getParent());
		assertEquals(child, parent.getChild("child"));
		try {
			parent.add(new DepTreeNode("", null));
			fail("Exception not thrown");
		} catch (IllegalStateException ex) {}
	}

	@Test
	public void testCreateOrGet() {
		DepTreeNode root = new DepTreeNode("root", null);
		DepTreeNode grandchild = root.createOrGet("child/grandchild", null);
		DepTreeNode child = root.getChild("child");
		assertEquals(child.getParent(), root);
		assertEquals(grandchild.getParent(), child);
		DepTreeNode node = root.createOrGet("child/grandchild", null);
		assertEquals(node, grandchild);
		try {
			root.createOrGet("/badpath", null);
			fail("Exception not thrown");
		} catch (IllegalArgumentException ex) {}
	}

	@Test
	public void testGetDecendent() {
		DepTreeNode root = new DepTreeNode("root", null);
		DepTreeNode grandchild = root.createOrGet("child/grandchild", null);
		assertEquals(grandchild, root.getDescendent("child/grandchild"));
		assertEquals(null, root.getDescendent("noexist"));
		assertEquals(null, root.getDescendent("child/noexist"));
	}

	@Test
	public void testRemove() {
		DepTreeNode root = new DepTreeNode("root", null);
		DepTreeNode child = root.createOrGet("child", null);
		assertEquals(root, child.getParent());
		root.remove(new DepTreeNode("child", null));
		assertEquals(root, child.getParent());
		root.remove(child);
		assertEquals(null, root.getChild("child"));
		assertEquals(null, child.getParent());
		root.add(child);
		assertEquals(root, child.getParent());
	}

	@Test
	public void testGetChildren() {
		DepTreeNode root = new DepTreeNode("root", null);
		DepTreeNode a = root.createOrGet("a", null);
		root.createOrGet("a/a", null);
		root.createOrGet("a/b", null);
		DepTreeNode b = root.createOrGet("b", null);
		DepTreeNode c = root.createOrGet("c", null);
		Map<String, DepTreeNode> map = root.getChildren();
		assertEquals(3, map.size());
		assertEquals(a, map.get("a"));
		assertEquals(b, map.get("b"));
		assertEquals(c, map.get("c"));
		try {
			map.remove("a");
			fail("Exception not thrown");
		} catch (UnsupportedOperationException ex) {}
		root.removeAll();
		map = root.getChildren();
		assertEquals(0, map.size());
	}

	@Test
	public void testGetChild() {
		DepTreeNode root = new DepTreeNode("root", null);
		assertEquals(null, root.getChild("child"));
		DepTreeNode child = root.createOrGet("child", null);
		assertEquals(child, root.getChild("child"));
		root.remove(child);
		assertEquals(null, root.getChild("child"));
	}

	@Test
	public void testPrune() {
		DepTreeNode root = new DepTreeNode("root", null);
		root.createOrGet("a", null);
		root.createOrGet("b", null);
		root.createOrGet("c", null);
		root.createOrGet("d", null);
		root.createOrGet("a/a", null);
		root.createOrGet("a/b", null);
		root.createOrGet("a/b/a", null);
		root.createOrGet("a/b/b", URI.create("a/b/b"));
		root.getDescendent("a/b").setDependencies(new String[]{"dep"}, new String[0], new String[0], 0L, 0L);
		root.getDescendent("c").setDependencies(new String[]{"dep"}, new String[0], new String[0], 0L, 0L);
		root.getDescendent("a/b/a").setDependencies(new String[]{"dep"}, new String[0], new String[0], 0L, 0L);
		root.prune();
		assertNotNull(root.getChild("a"));
		assertNotNull(root.getChild("c"));
		assertNull(root.getChild("b"));
		assertNull(root.getChild("d"));
		assertNotNull(root.getDescendent("a/b"));
		assertEquals(null, root.getDescendent("a/a"));
		assertNotNull(root.getDescendent("a/b/a"));
		assertNotNull(root.getDescendent("a/b/b"));
	}

	@Test
	public void testSetDependencies() {
		DepTreeNode root = new DepTreeNode("root", null);
		String[] defineDeps = new String[]{"dep1", "dep2"};
		String[] requireDeps = new String[]{"req1", "req2"};
		root.setDependencies(defineDeps, requireDeps, new String[0], 0L, 1L);
		assert(Arrays.equals(root.getDefineDepArray(), defineDeps));
		assert(Arrays.equals(root.getRequireDepArray(), requireDeps));
		assertEquals(root.lastModified(), 0L);
		assertEquals(root.lastModifiedDep(), 1L);
	}

	@Test
	public void testToString() {
		DepTreeNode root = new DepTreeNode("root", null);
		root.setDependencies(new String[] {"a", "b"}, new String[0], new String[0], 0L, 1L);
		root.createOrGet("child", null);
		String str = root.toString();
		assertEquals(str, "root = define:[a, b], require:[]\n");
	}

	@Test
	public void testToStringTree() {
		DepTreeNode root = new DepTreeNode("root", null);
		root.setDependencies(new String[] {"a", "b"}, new String[0], new String[0], 0L, 1L);
		DepTreeNode child = root.createOrGet("child", null);
		child.setDependencies(new String[] {"b", "c"}, new String[0], new String[0], 0L, 1L);
		String str = root.toStringTree();
		assertEquals("root = define:[a, b], require:[]\nroot/child = define:[b, c], require:[]\n", str);
	}

	@Test
	public void testClone() {
		DepTreeNode root = new DepTreeNode("root", null);
		DepTreeNode child = root.createOrGet("child", null);
		root.setDependencies(new String[]{"a", "b"}, new String[]{"c", "d"}, new String[0], 0L, 1L);
		child.setDependencies(new String[]{"e", "f"}, new String[]{"g", "h"}, new String[0], 0L, 1L);
		DepTreeNode clone = null;
		try {
			clone = (DepTreeNode)root.clone();
		} catch (CloneNotSupportedException e) {
			fail(e.getMessage());
		}
		assertArrayEquals(root.getDefineDepArray(), clone.getDefineDepArray());
		assertNotSame(root.getDefineDepArray(), clone.getDefineDepArray());
		assertArrayEquals(root.getRequireDepArray(), clone.getRequireDepArray());
		assertNotSame(root.getRequireDepArray(), clone.getRequireDepArray());
		assertEquals(root.lastModified(), clone.lastModified());
		assertEquals(root.lastModifiedDep(), clone.lastModifiedDep());
		DepTreeNode clonedChild = clone.getChild("child");
		assertArrayEquals(child.getDefineDepArray(), clonedChild.getDefineDepArray());
		assertNotSame(child.getDefineDepArray(), clonedChild.getDefineDepArray());
		assertArrayEquals(child.getRequireDepArray(), clonedChild.getRequireDepArray());
		assertNotSame(child.getRequireDepArray(), clonedChild.getRequireDepArray());
		assertEquals(child.lastModified(), clonedChild.lastModified());
		assertEquals(child.lastModifiedDep(), clonedChild.lastModifiedDep());
	}

	@Test
	public void testResolveDependencyRefs() {
		// This method can only tested in conjunction with getExpandedDependencies,
		// so that test case is used to test both methods.
	}
}
