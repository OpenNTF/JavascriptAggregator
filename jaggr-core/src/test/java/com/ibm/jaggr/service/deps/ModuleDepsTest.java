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

package com.ibm.jaggr.service.deps;

import java.util.Arrays;
import java.util.HashSet;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.jaggr.service.deps.ModuleDepInfo;
import com.ibm.jaggr.service.deps.ModuleDeps;
import com.ibm.jaggr.service.util.BooleanTerm;

public class ModuleDepsTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAdd() {
		ModuleDeps deps = new ModuleDeps();
		Assert.assertTrue(deps.add("module1", new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("B"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		Assert.assertEquals(2, deps.size());
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"has!A?"})), 
				deps.get("module1").getHasPluginPrefixes()
		);
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"has!B?", "has!C?"})), 
				deps.get("module2").getHasPluginPrefixes()
		);
		// Ensure adding an entry already in the deps list doesn't change the list
		Assert.assertFalse(deps.add("module1", deps.get("module1")));
		
	}
	@Test
	public void testAddAll() {
		ModuleDeps deps = new ModuleDeps();
		Assert.assertTrue(deps.add("module1", new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("B"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		
		ModuleDeps deps2 = new ModuleDeps();
		Assert.assertTrue(deps2.addAll(deps));
		Assert.assertEquals(deps2, deps);
	}

	@Test
	public void testContainsDep() {
		ModuleDeps deps = new ModuleDeps();
		Assert.assertTrue(deps.add("module1", new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("B"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		Assert.assertTrue(deps.add("module3", new ModuleDepInfo(null, null, null)));
		Assert.assertTrue(deps.add("module4", new ModuleDepInfo("has", BooleanTerm.emptyTerm, null)));
		Assert.assertTrue(deps.containsDep("module1", new BooleanTerm("A")));
		Assert.assertTrue(deps.containsDep("module2", new BooleanTerm("B")));
		Assert.assertTrue(deps.containsDep("module2", new BooleanTerm("C")));
		Assert.assertTrue(deps.containsDep("module3", null));
		Assert.assertTrue(deps.containsDep("module4", BooleanTerm.emptyTerm));
		
		Assert.assertTrue(deps.add("module1", new ModuleDepInfo("has", new BooleanTerm("!A"), null)));
		deps.simplify();
		Assert.assertTrue(deps.containsDep("module1", null));
	}

	@Test
	public void testSubtract() {
		ModuleDeps deps = new ModuleDeps();
		Assert.assertTrue(deps.add("module1", new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("B"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		Assert.assertTrue(deps.add("module3", new ModuleDepInfo(null, null, null)));
		Assert.assertTrue(deps.add("module4", new ModuleDepInfo("has", BooleanTerm.emptyTerm, null)));
		Assert.assertTrue(deps.subtract("module2", new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"has!B?"})), 
				deps.get("module2").getHasPluginPrefixes()
		);
		Assert.assertTrue(deps.subtract("module2", new ModuleDepInfo("has", new BooleanTerm("B"), null)));
		Assert.assertEquals(0, deps.get("module2").getHasPluginPrefixes().size());
		Assert.assertFalse(deps.subtract("module_missing", new ModuleDepInfo(null, null, null)));
		Assert.assertFalse(deps.subtract("module1", new ModuleDepInfo("has", new BooleanTerm("a"), null)));
		
	}

	@Test
	public void testSubtractAll() {
		ModuleDeps deps = new ModuleDeps();
		Assert.assertTrue(deps.add("module1", new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("B"), null)));
		Assert.assertTrue(deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		Assert.assertTrue(deps.add("module3", new ModuleDepInfo(null, null, null)));
		Assert.assertTrue(deps.add("module4", new ModuleDepInfo("has", BooleanTerm.emptyTerm, null)));
		
		ModuleDeps deps2 = new ModuleDeps();
		Assert.assertTrue(deps2.add("module2", new ModuleDepInfo("has", new BooleanTerm("B"), null)));
		Assert.assertTrue(deps2.add("module2", new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		Assert.assertTrue(deps2.add("module3", new ModuleDepInfo(null, null, null)));
		Assert.assertTrue(deps.subtractAll(deps2));
		Assert.assertEquals(0, deps.get("module2").getHasPluginPrefixes().size());
		Assert.assertEquals(0, deps.get("module3").getHasPluginPrefixes().size());
	}

	@Test
	public void testGetModuleIds() {
		ModuleDeps deps = new ModuleDeps();
		deps.add("module1", new ModuleDepInfo("has", new BooleanTerm("A"), null));
		deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("B"), null));
		deps.add("module2", new ModuleDepInfo("has", new BooleanTerm("C"), null));
		deps.add("module3", new ModuleDepInfo(null, null, null));
		deps.add("module4", new ModuleDepInfo("has", BooleanTerm.emptyTerm, null));
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{
						"has!A?module1", "has!B?module2", "has!C?module2", "module3"
				})),
				deps.getModuleIds()
		);
	}

}
