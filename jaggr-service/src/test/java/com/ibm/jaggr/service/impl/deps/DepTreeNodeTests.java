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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.config.ConfigImpl;

import com.ibm.jaggr.service.test.TestUtils;

import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

public class DepTreeNodeTests extends EasyMock {
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
		DepTreeNode node = new DepTreeNode("foo");
		assertNotNull(node);
		try {
			node = new DepTreeNode("foo/bar");
			fail("Exception not thrown");
		} catch (IllegalArgumentException e) {
		}
		try {
			node = new DepTreeNode(null);
			fail("Exception not thrown");
		} catch (NullPointerException e) {
		}
	}

	@Before
	public void testSetup() {

	}

	@Test
	public void testGetName() {
		DepTreeNode node = new DepTreeNode("foo");
		assertEquals(node.getName(), "foo");
	}

	@Test
	public void testLastModified() {
		DepTreeNode node = new DepTreeNode("foo");
		assertEquals(node.lastModified(), -1);
		node.setDependencies(new String[]{"dep1", "dep2"}, new String[0], 1234567890L, 0L);
		assertEquals(node.lastModified(), 1234567890L);
	}

	@Test
	public void testLastModifiedDep() {
		DepTreeNode node = new DepTreeNode("foo");
		assertEquals(node.lastModifiedDep(), -1);
		node.setDependencies(new String[]{"dep1", "dep2"}, new String[0], 0L, 1234567890L);
		assertEquals(node.lastModifiedDep(), 1234567890L);
	}

	@Test
	public void testLastModifiedDepTree() {
		DepTreeNode a = new DepTreeNode("a");
		DepTreeNode aa = new DepTreeNode("aa");
		DepTreeNode ab = new DepTreeNode("ab");
		a.add(aa);
		a.add(ab);
		a.setDependencies(new String[]{"b", "c"}, new String[0], 1234567890L, 1234567890L);
		aa.setDependencies(new String[]{"b", "c"}, new String[0], 1234567890L, 1234567890L);
		ab.setDependencies(new String[]{"b", "c"}, new String[0], 1234567890L, 2000000000L);
		assertEquals(a.lastModifiedDepTree(), 2000000000L);
	}

	@Test
	public void testGetParent() {
		DepTreeNode parent = new DepTreeNode("parent");
		DepTreeNode child = new DepTreeNode("child");
		parent.add(child);
		assertEquals(parent, child.getParent());
		assertEquals(null, parent.getParent());
	}

	@Test
	public void testGetRoot() {
		DepTreeNode parent = new DepTreeNode("parent");
		DepTreeNode child = new DepTreeNode("child");
		DepTreeNode grandchild = new DepTreeNode("grandchild");
		parent.add(child);
		child.add(grandchild);
		assertEquals(parent, parent.getRoot());
		assertEquals(parent, child.getRoot());
		assertEquals(parent, grandchild.getRoot());
	}

	@Test
	public void testGetDepArray() {
		String[] deps = new String[] { "dep1", "dep2" };
		DepTreeNode node = new DepTreeNode("node");
		node.setDependencies(deps, new String[0], 1234567890L, 1234567890L);
		assert(Arrays.equals(node.getDepArray(), deps));
	}

	@Test
	public void testGetParentPath() {
		DepTreeNode root = new DepTreeNode("root");
		DepTreeNode child = new DepTreeNode("child");
		DepTreeNode grandchild = new DepTreeNode("grandchild");
		root.add(child);
		child.add(grandchild);
		assertNull(root.getParentPath());
		assertEquals("root", child.getParentPath());
		assertEquals("root/child", grandchild.getParentPath());
	}

	@Test
	public void testGetFullPathName() {
		DepTreeNode root = new DepTreeNode("root");
		DepTreeNode child = new DepTreeNode("child");
		DepTreeNode grandchild = new DepTreeNode("grandchild");
		root.add(child);
		child.add(grandchild);
		assertEquals("root", root.getFullPathName());
		assertEquals("root/child", child.getFullPathName());
		assertEquals("root/child/grandchild", grandchild.getFullPathName());
	}

	@Test
	public void testAdd() {
		DepTreeNode parent = new DepTreeNode("parent");
		DepTreeNode child = new DepTreeNode("child");
		assertEquals(null, child.getParent());
		parent.add(child);
		assertEquals(parent, child.getParent());
		assertEquals(child, parent.getChild("child"));
		try {
			parent.add(new DepTreeNode(""));
			fail("Exception not thrown");
		} catch (IllegalStateException ex) {}
	}

	@Test
	public void testCreateOrGet() {
		DepTreeNode root = new DepTreeNode("root");
		DepTreeNode grandchild = root.createOrGet("child/grandchild");
		DepTreeNode child = root.getChild("child");
		assertEquals(child.getParent(), root);
		assertEquals(grandchild.getParent(), child);
		DepTreeNode node = root.createOrGet("child/grandchild");
		assertEquals(node, grandchild);
		try {
			root.createOrGet("/badpath");
			fail("Exception not thrown");
		} catch (IllegalArgumentException ex) {}
	}

	@Test
	public void testGetDecendent() {
		DepTreeNode root = new DepTreeNode("root");
		DepTreeNode grandchild = root.createOrGet("child/grandchild");
		assertEquals(grandchild, root.getDescendent("child/grandchild"));
		assertEquals(null, root.getDescendent("noexist"));
		assertEquals(null, root.getDescendent("child/noexist"));
	}

	@Test
	public void testRemove() {
		DepTreeNode root = new DepTreeNode("root");
		DepTreeNode child = root.createOrGet("child");
		assertEquals(root, child.getParent());
		root.remove(new DepTreeNode("child"));
		assertEquals(root, child.getParent());
		root.remove(child);
		assertEquals(null, root.getChild("child"));
		assertEquals(null, child.getParent());
		root.add(child);
		assertEquals(root, child.getParent());
	}

	@Test
	public void testGetChildren() {
		DepTreeNode root = new DepTreeNode("root");
		DepTreeNode a = root.createOrGet("a");
		root.createOrGet("a/a");
		root.createOrGet("a/b");
		DepTreeNode b = root.createOrGet("b");
		DepTreeNode c = root.createOrGet("c");
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
		DepTreeNode root = new DepTreeNode("root");
		assertEquals(null, root.getChild("child"));
		DepTreeNode child = root.createOrGet("child");
		assertEquals(child, root.getChild("child"));
		root.remove(child);
		assertEquals(null, root.getChild("child"));
	}

	@Test
	public void testPrune() {
		DepTreeNode root = new DepTreeNode("root");
		root.createOrGet("a");
		root.createOrGet("b");
		root.createOrGet("c");
		root.createOrGet("d");
		root.createOrGet("a/a");
		root.createOrGet("a/b");
		root.createOrGet("a/b/a");
		root.getDescendent("a/b").setDependencies(new String[]{"dep"}, new String[0], 0L, 0L);
		root.getDescendent("c").setDependencies(new String[]{"dep"}, new String[0], 0L, 0L);
		root.getDescendent("a/b/a").setDependencies(new String[]{"dep"}, new String[0], 0L, 0L);
		root.prune();
		assertNotNull(root.getChild("a"));
		assertNotNull(root.getChild("c"));
		assertNull(root.getChild("b"));
		assertNull(root.getChild("d"));
		assertNotNull(root.getDescendent("a/b"));
		assertEquals(null, root.getDescendent("a/a"));
		assertNotNull(root.getDescendent("a/b/a"));
	}

	@Test
	public void testSetDependencies() {
		DepTreeNode root = new DepTreeNode("root");
		String[] deps = new String[]{"dep1", "dep2"};
		root.setDependencies(deps, new String[0], 0L, 1L);
		assert(Arrays.equals(root.getDepArray(), deps));
		assertEquals(root.lastModified(), 0L);
		assertEquals(root.lastModifiedDep(), 1L);
	}

	@Test
	public void testToString() {
		DepTreeNode root = new DepTreeNode("root");
		root.setDependencies(new String[] {"a", "b"}, new String[0], 0L, 1L);
		root.createOrGet("child");
		String str = root.toString();
		assertEquals(str, "root = [a, b]\n");
	}

	@Test
	public void testToStringTree() {
		DepTreeNode root = new DepTreeNode("root");
		root.setDependencies(new String[] {"a", "b"}, new String[0], 0L, 1L);
		DepTreeNode child = root.createOrGet("child");
		child.setDependencies(new String[] {"b", "c"}, new String[0], 0L, 1L);
		String str = root.toStringTree();
		assertEquals("root = [a, b]\nroot/child = [b, c]\n", str);
	}

	@Test
	public void testClone() {
		DepTreeNode root = new DepTreeNode("root");
		DepTreeNode child = root.createOrGet("child");
		root.setDependencies(new String[]{"a", "b"}, new String[0], 0L, 1L);
		child.setDependencies(new String[]{"c", "d"}, new String[0], 0L, 1L);
		DepTreeNode clone = null;
		try {
			clone = (DepTreeNode)root.clone();
		} catch (CloneNotSupportedException e) {
			fail(e.getMessage());
		}
		assertArrayEquals(root.getDepArray(), clone.getDepArray());
		assertNotSame(root.getDepArray(), clone.getDepArray());
		assertEquals(root.lastModified(), clone.lastModified());
		assertEquals(root.lastModifiedDep(), clone.lastModifiedDep());
		DepTreeNode clonedChild = clone.getChild("child");
		assertArrayEquals(child.getDepArray(), clonedChild.getDepArray());
		assertNotSame(child.getDepArray(), clonedChild.getDepArray());
		assertEquals(child.lastModified(), clonedChild.lastModified());
		assertEquals(child.lastModifiedDep(), clonedChild.lastModifiedDep());
	}

	@Test
	public void testResolveDependencyRefs() {
		// This method can only tested in conjunction with getExpandedDependencies,
		// so that test case is used to test both methods.
	}
}
