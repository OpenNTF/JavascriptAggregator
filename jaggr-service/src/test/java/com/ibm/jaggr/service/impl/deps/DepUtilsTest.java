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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class DepUtilsTest {
	/**
	 * Test method for {@link com.ibm.jaggr.service.deps.impl.DepUtils#removeRedundantPaths(java.util.Collection)}.
	 * @throws URISyntaxException 
	 */
	@Test
	public void testRemoveRedundantPaths() throws URISyntaxException {
		URI[] names = new URI[] {
			new URI("/a/a/a"),
			new URI("/a/b"),
			new URI("/a/c"),
			new URI("/a/c/a"),
			new URI("/b/a/a"),
			new URI("/b/a/a/a"),
			new URI("/a/a/b")
		};
		URI[] expected = new URI[] {
			new URI("/a/a/a"),
			new URI("/a/b"),
			new URI("/a/c"),
			new URI("/b/a/a"),
			new URI("/a/a/b")
		};
		Collection<URI> list = new ArrayList<URI>(Arrays.asList(names));
		list = DepUtils.removeRedundantPaths(list);
		assertEquals(5, list.size());
		assertTrue(list.containsAll(Arrays.asList(expected)));
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.deps.impl.DepUtils#getNodeForResource(java.lang.String, java.util.Map)}.
	 * @throws URISyntaxException 
	 */
	@Test
	public void testGetNodeForFilepath() throws URISyntaxException {
		URI[] paths = new URI[] {
			new URI("/a/a/x"),
			new URI("/a/y"),
			new URI("/b/a/z"),
		};
		DepTreeNode x = new DepTreeNode("");
		DepTreeNode y = new DepTreeNode("");
		DepTreeNode z = new DepTreeNode("");
		DepTreeNode t1 = x.createOrGet("1/1");
		DepTreeNode t2 = x.createOrGet("1/2");
		DepTreeNode t3 = z.createOrGet("3");
		Map<URI, DepTreeNode> map = new HashMap<URI, DepTreeNode>();
		map.put(paths[0], x);
		map.put(paths[1], y);
		map.put(paths[2], z);
		
		DepTreeNode node = DepUtils.getNodeForResource(new URI(paths[0].getPath()+"/1"), map);
		assertEquals(node.getName(), "1");
		node = DepUtils.getNodeForResource(new URI(paths[0].getPath()+"/1/1"), map);
		assertEquals(node, t1);
		node = DepUtils.getNodeForResource(new URI(paths[0].getPath()+"/1/2"), map);
		assertEquals(node, t2);
		node = DepUtils.getNodeForResource(new URI(paths[2].getPath()+"/3"), map);
		assertEquals(node, t3);
		node = DepUtils.getNodeForResource(paths[1], map);
		assertEquals(node, y);
		node = DepUtils.getNodeForResource(paths[2], map);
		assertEquals(node, z);
		node = DepUtils.getNodeForResource(new URI(paths[0].getPath()+"/2"), map);
		assertNull(node);
		node = DepUtils.getNodeForResource(new URI("/a/a"), map);
		assertNull(node);
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.deps.impl.DepUtils#parseDependencies(com.google.javascript.rhino.Node)}.
	 */
	@Test
	public void testParseDependencies() {
		String js1 = "/* comment */\ndefine(\"foo\",[\"a\", \"b\", \"c\"], function(a, b, c) {\nalert(\"hellow\");\nreturn null\n});";
		String js2 = "/* comment */\ndefine([\"a\", \"b\", \"c\"], function(a, b, c) {\nalert(\"hellow\");\nreturn null;\n});";
		String js3 = "define([], function() {\nalert(\"hello\");\nreturn null;\n});";
		String js4 = "(function(){\ndefine([\"a\", \"b\", \"c\"], function(a, b, c) {\nalert(\"hellow\");\nreturn null;\n});})()";
		String js5 = "define(deps, function() {\nalert(\"hello\");\nreturn null;\n});";
		String js6 = "function foo() {\nalert(\"hello\");\nreturn null;\n};";
		String js7 = "define(['dep1', 'dojo/has!hasTest1?hasTest2?dep1:dep2'], function() { require(['has!hasTest3?:dep3'], function(dep3) { if (has('fooTest')) {return true;} else {return false;} }); if (has('barTest')) return bar;});";
		
		
		Compiler compiler = new Compiler();
		Node node = compiler.parse(JSSourceFile.fromCode("js1", js1));
		Collection<String> deps = DepUtils.parseDependencies(node, new HashSet<String>());
		assertEquals(deps.toString(), "[a, b, c]");
		node = compiler.parse(JSSourceFile.fromCode("js2", js2));
		deps = DepUtils.parseDependencies(node, new HashSet<String>());
		assertEquals(deps.toString(), "[a, b, c]");
		node = compiler.parse(JSSourceFile.fromCode("js3", js3));
		deps = DepUtils.parseDependencies(node, new HashSet<String>());
		assertEquals(deps.size(), 0);
		node = compiler.parse(JSSourceFile.fromCode("js4", js4));
		deps = DepUtils.parseDependencies(node, new HashSet<String>());
		assertEquals(deps.toString(), "[a, b, c]");
		node = compiler.parse(JSSourceFile.fromCode("js5", js5));
		deps = DepUtils.parseDependencies(node, new HashSet<String>());
		assertNull(deps);
		node = compiler.parse(JSSourceFile.fromCode("js6", js6));
		deps = DepUtils.parseDependencies(node, new HashSet<String>());
		assertNull(deps);
		node = compiler.parse(JSSourceFile.fromCode("js6", js7));
		// Test dependent features
		Set<String> dependentFeatures = new HashSet<String>();
		deps = DepUtils.parseDependencies(node, dependentFeatures);
		assertEquals("[dep1, dojo/has!hasTest1?hasTest2?dep1:dep2]", deps.toString());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"hasTest1", "hasTest2", "hasTest3", "fooTest", "barTest"})), dependentFeatures);
	}
}
