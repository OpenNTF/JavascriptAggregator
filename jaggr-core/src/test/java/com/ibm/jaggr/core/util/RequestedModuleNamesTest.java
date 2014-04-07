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
package com.ibm.jaggr.core.util;

import static org.junit.Assert.assertEquals;

import com.ibm.jaggr.core.test.MockRequestedModuleNames;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RequestedModuleNamesTest {

	@Test
	public void testSetString() {
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		assertEquals("", names.toString());
		names.setString("foobar");
		assertEquals("foobar", names.toString());
		names.setModules(Arrays.asList(new String[]{"foo", "bar"}));
		names.setDeps(Arrays.asList(new String[]{"foodep", "bardep"}));
		names.setPreloads(Arrays.asList(new String[]{"preload"}));
		assertEquals("foobar", names.toString());
	}

	@Test
	public void testToString() {
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		assertEquals("", names.toString());
		names.setModules(Arrays.asList(new String[]{"foo","bar"}));
		assertEquals("[foo, bar]", names.toString());
		names.setDeps(Arrays.asList(new String[]{"foodep", "bardep"}));
		assertEquals("[foo, bar];deps:[foodep, bardep]", names.toString());
		names.setPreloads(Arrays.asList(new String[]{"preload1", "preload2", "preload3"}));
		assertEquals("[foo, bar];deps:[foodep, bardep];preloads:[preload1, preload2, preload3]", names.toString());
		names.setModules(Collections.<String>emptyList());
		assertEquals("deps:[foodep, bardep];preloads:[preload1, preload2, preload3]", names.toString());
		names.setDeps(Collections.<String>emptyList());
		assertEquals("preloads:[preload1, preload2, preload3]", names.toString());
		names.setPreloads(Collections.<String>emptyList());
		assertEquals("", names.toString());
		names.setDeps(Arrays.asList(new String[]{"foodep", "bardep"}));
		assertEquals("deps:[foodep, bardep]", names.toString());
	}

	@Test
	public void testSetModule() {
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		assertEquals(Collections.emptyList(), names.getModules());
		List<String> list = Arrays.asList(new String[]{"a", "b"});
		names.setModules(list);
		assertEquals(list, names.getModules());
		names.setModules(Collections.<String>emptyList());
		assertEquals(Collections.emptyList(), names.getModules());
	}

	@Test
	public void testSetDeps() {
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		assertEquals(Collections.emptyList(), names.getDeps());
		List<String> list = Arrays.asList(new String[]{"a", "b"});
		names.setDeps(list);
		assertEquals(list, names.getDeps());
		names.setDeps(Collections.<String>emptyList());
		assertEquals(Collections.emptyList(), names.getDeps());
	}

	@Test
	public void testSetPreloads() {
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		assertEquals(Collections.emptyList(), names.getPreloads());
		List<String> list = Arrays.asList(new String[]{"a", "b"});
		names.setPreloads(list);
		assertEquals(list, names.getPreloads());
		names.setPreloads(Collections.<String>emptyList());
		assertEquals(Collections.emptyList(), names.getPreloads());
	}

	@Test(expected=NullPointerException.class)
	public void testSetModulesNull() {
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		names.setModules(null);
	}

	@Test(expected=NullPointerException.class)
	public void testSetDepsNull() {
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		names.setModules(null);
	}

	@Test(expected=NullPointerException.class)
	public void testSetPreloadsNull() {
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		names.setModules(null);
	}

}
