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
import java.util.Collection;
import java.util.HashSet;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.jaggr.service.deps.ModuleDepInfo;
import com.ibm.jaggr.service.util.BooleanTerm;


public class ModuleDepInfoTest {

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
	public void testModuleDepInfo() {
		ModuleDepInfo depInfo = null;
		boolean exceptionCaught = false;
		try {
			depInfo = new ModuleDepInfo(null, new BooleanTerm("A"), null);
		} catch (NullPointerException ex) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);

		depInfo = new ModuleDepInfo(null, null, null);
		Assert.assertNull(depInfo.getComment());

		depInfo = new ModuleDepInfo(null, null, "Comment 1");
		Assert.assertEquals("Comment 1", depInfo.getComment());
	}
	
	@Test
	public void testGetHasPluginPrefixes() {
		ModuleDepInfo depInfo = new ModuleDepInfo("has", new BooleanTerm("B"), null);
		depInfo.add(new ModuleDepInfo("has", new BooleanTerm("A*C"), null));
		
		depInfo.add(new ModuleDepInfo("has", new BooleanTerm("(!A*!C)"), null));
		Collection<String> plugins = depInfo.getHasPluginPrefixes();
		System.out.println(plugins);
		Assert.assertEquals(
			new HashSet<String>(Arrays.asList(new String[]{"has!B?", "has!A?C?", "has!A?:C?:"})), 
			plugins); 
		
		depInfo = new ModuleDepInfo("has", new BooleanTerm("!A*A"), null);
		depInfo.add(new ModuleDepInfo("has", new BooleanTerm("B*!B"), null));
		Assert.assertEquals(0, depInfo.getHasPluginPrefixes().size());
		depInfo = new ModuleDepInfo("has", new BooleanTerm("!A"), null);
		depInfo.add(new ModuleDepInfo("has", new BooleanTerm("A"), null));
		System.out.println(depInfo.getHasPluginPrefixes());
		Assert.assertNull(depInfo.getHasPluginPrefixes());
	}
	
	@Test
	public void testContainsTerm() {
		ModuleDepInfo depInfo =
				new ModuleDepInfo(null, null, null);
		Assert.assertTrue(depInfo.containsTerm(BooleanTerm.FALSE));
		Assert.assertTrue(depInfo.containsTerm(BooleanTerm.TRUE));
		Assert.assertTrue(depInfo.containsTerm(new BooleanTerm("A")));
		
		depInfo = new ModuleDepInfo("has", BooleanTerm.TRUE, null);
		Assert.assertTrue(depInfo.containsTerm(BooleanTerm.TRUE));
		Assert.assertTrue(depInfo.containsTerm(BooleanTerm.FALSE));
		Assert.assertTrue(depInfo.containsTerm(new BooleanTerm("A")));
		
		depInfo = new ModuleDepInfo("has", BooleanTerm.FALSE, null);
		Assert.assertFalse(depInfo.containsTerm(BooleanTerm.TRUE));
		Assert.assertTrue(depInfo.containsTerm(BooleanTerm.FALSE));
		Assert.assertFalse(depInfo.containsTerm(new BooleanTerm("A")));
		
		depInfo = new ModuleDepInfo("has", new BooleanTerm("A*B"), null);
		Assert.assertTrue(depInfo.containsTerm(BooleanTerm.FALSE));
		Assert.assertFalse(depInfo.containsTerm(BooleanTerm.TRUE));
		Assert.assertFalse(depInfo.containsTerm(new BooleanTerm("A")));
		Assert.assertTrue(depInfo.containsTerm(new BooleanTerm("B*A")));
		
		depInfo = new ModuleDepInfo("has", new BooleanTerm("A"), null);
		Assert.assertTrue(depInfo.containsTerm(new BooleanTerm("A*B")));
	}

	@Test
	public void testAdd() {
		ModuleDepInfo depInfo =
				new ModuleDepInfo(null, null, null);
		Assert.assertFalse(depInfo.add(new ModuleDepInfo(null, null, null)));
		Assert.assertFalse(depInfo.add(new ModuleDepInfo("has", BooleanTerm.TRUE, null)));
		Assert.assertFalse(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		
		depInfo = new ModuleDepInfo("has", BooleanTerm.TRUE, null);
		Assert.assertFalse(depInfo.add(new ModuleDepInfo("has", BooleanTerm.TRUE, null)));
		Assert.assertFalse(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		
		depInfo = new ModuleDepInfo("has", new BooleanTerm("A"), null);
		Assert.assertTrue(depInfo.add(new ModuleDepInfo()));
		Assert.assertEquals(new ModuleDepInfo(), depInfo);
		
		depInfo = new ModuleDepInfo("has", BooleanTerm.TRUE, null);
		Assert.assertFalse(depInfo.add(new ModuleDepInfo()));
		Assert.assertEquals(new ModuleDepInfo(), depInfo);
		
		// Test isPluginNameDeclared constructor option
		depInfo = new ModuleDepInfo("has1", new BooleanTerm("A*B"), null);
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has2", new BooleanTerm("B*C"), null, true)));
		Collection<String> prefixes = depInfo.getHasPluginPrefixes();
		for (String prefix : prefixes) {
			Assert.assertTrue(prefix.startsWith("has2!"));
		}
		depInfo.add(new ModuleDepInfo("has3", new BooleanTerm("C"), null));
		for (String prefix : prefixes) {
			Assert.assertTrue(prefix.startsWith("has2!"));
		}
		depInfo = new ModuleDepInfo("has1", new BooleanTerm("A*B"), null, true);
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has2", new BooleanTerm("B*C"), null)));
		prefixes = depInfo.getHasPluginPrefixes();
		for (String prefix : prefixes) {
			Assert.assertTrue(prefix.startsWith("has1!"));
		}
		
		// test comments
		depInfo = new ModuleDepInfo(null, null, null);
		Assert.assertFalse(depInfo.add(new ModuleDepInfo(null, null, null)));
		Assert.assertNull(depInfo.getComment());
		Assert.assertFalse(depInfo.add(new ModuleDepInfo(null, null, "Comment")));
		Assert.assertNull(depInfo.getComment());
		Assert.assertFalse(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("A"), "Comment 2")));
		Assert.assertNull(depInfo.getComment());
		
		depInfo = new ModuleDepInfo("has", new BooleanTerm("A*B"), "Comment 2");
		Assert.assertEquals("Comment 2", depInfo.getComment());
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("B*C"), "Comment 2.1")));
		Assert.assertEquals("Comment 2", depInfo.getComment());
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("C"), "Comment 1")));
		Assert.assertEquals("Comment 1", depInfo.getComment());
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("D"), "Comment 1.1")));
		Assert.assertEquals("Comment 1", depInfo.getComment());
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"has!A?B?", "has!C?", "has!D?"})),
				depInfo.getHasPluginPrefixes()
		);
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", null, "Comment")));
		Assert.assertEquals("Comment", depInfo.getComment());
		Assert.assertNull(depInfo.getHasPluginPrefixes());
	}
	
	@Test
	public void testSubtract() {
		ModuleDepInfo depInfo = new ModuleDepInfo("has", new BooleanTerm("A*B"), null);
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("B*C"), null)));
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("C*D"), null)));
		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo("has", new BooleanTerm("B*C"), null)));
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"has!A?B?", "has!C?D?"})),
				depInfo.getHasPluginPrefixes()
		);
		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		Assert.assertEquals(
				new HashSet<String>(Arrays.asList(new String[]{"has!C?D?"})),
				depInfo.getHasPluginPrefixes()
		);

		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo("has", new BooleanTerm("C*D"), null)));
		Assert.assertEquals(0, depInfo.getHasPluginPrefixes().size());

		depInfo = new ModuleDepInfo("has", new BooleanTerm("B*C"), null);
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("C*D"), null)));
		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		Assert.assertEquals(0, depInfo.getHasPluginPrefixes().size());

		depInfo = new ModuleDepInfo("has", null, null);
		Assert.assertFalse(depInfo.subtract(new ModuleDepInfo("has", new BooleanTerm("B*C"), null)));
		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo(null, null, null)));
		Assert.assertEquals(0, depInfo.getHasPluginPrefixes().size());
		
		depInfo = new ModuleDepInfo("has", new BooleanTerm("A*B"), null);
		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo(null, null, null)));
		Assert.assertEquals(0, depInfo.getHasPluginPrefixes().size());
		
		depInfo = new ModuleDepInfo("has", new BooleanTerm("A"), "Comment A");
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("B"), "Comment B")));
		Assert.assertTrue(depInfo.add(new ModuleDepInfo("has", new BooleanTerm("C"), "Comment C")));
		Assert.assertEquals("Comment A", depInfo.getComment());
		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo("has", new BooleanTerm("A"), null)));
		Assert.assertEquals("Comment A", depInfo.getComment());
		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo("has", new BooleanTerm("C"), null)));
		Assert.assertEquals("Comment A", depInfo.getComment());
		Assert.assertTrue(depInfo.subtract(new ModuleDepInfo("has", new BooleanTerm("B"), null)));
		Assert.assertNull(depInfo.getComment());
	}
	
}
