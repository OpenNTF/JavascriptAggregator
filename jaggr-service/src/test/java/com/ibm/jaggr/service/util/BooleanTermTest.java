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
package com.ibm.jaggr.service.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.BooleanVar;
import com.ibm.jaggr.core.util.Features;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class BooleanTermTest {

	@Test
	public void testEquals() {
		assertTrue(BooleanTerm.TRUE.equals(BooleanTerm.TRUE));
		assertTrue(BooleanTerm.FALSE.equals(BooleanTerm.FALSE));
		assertFalse(BooleanTerm.TRUE.equals(BooleanTerm.FALSE));
		assertFalse(BooleanTerm.FALSE.equals(BooleanTerm.TRUE));
		assertFalse(BooleanTerm.TRUE.equals(new BooleanTerm("A")));
		assertFalse(new BooleanTerm("A").equals(BooleanTerm.TRUE));
		assertFalse(BooleanTerm.FALSE.equals(new BooleanTerm("A")));
		assertFalse(new BooleanTerm("A").equals(BooleanTerm.FALSE));
		assertTrue(new BooleanTerm("A*!B").equals(new BooleanTerm("!B*A")));
		assertFalse(new BooleanTerm("A*!B").equals(new BooleanTerm("B*A")));	
	}
	
	@Test
	public void testHashCode() {
		assertEquals(Boolean.FALSE.hashCode(), BooleanTerm.FALSE.hashCode());
		assertEquals(Boolean.TRUE.hashCode(), BooleanTerm.TRUE.hashCode());
		assertEquals(new BooleanTerm("A*!B").hashCode(), new BooleanTerm("!B*A").hashCode());
		assertNotSame(new BooleanTerm("A*!B").hashCode(), new BooleanTerm("B*A").hashCode());
	}

	@Test
	public void testIsTrue() {
		assertTrue(BooleanTerm.TRUE.isTrue());
		assertFalse(BooleanTerm.FALSE.isTrue());
		assertFalse(new BooleanTerm("A").isTrue());
	}

	@Test
	public void testIsFalse() {
		assertFalse(BooleanTerm.TRUE.isFalse());
		assertTrue(BooleanTerm.FALSE.isFalse());
		assertFalse(new BooleanTerm("A").isFalse());
	}
	
	@Test(expected=NullPointerException.class)
	public void testNullVarConstruction() {
		new BooleanTerm((BooleanVar)null);
	}
	
	@Test(expected=NullPointerException.class)
	public void testNullSetConstruction() {
		new BooleanTerm((Set<BooleanVar>)null);
	}

	@Test(expected=UnsupportedOperationException.class)
	public void testEmptySetConstruction() {
		new BooleanTerm(new HashSet<BooleanVar>());
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testAddFail() {
		BooleanTerm term = new BooleanTerm("A");
		term.add(new BooleanVar("B", false));
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testClearFail() {
		BooleanTerm term = new BooleanTerm("A");
		term.clear();
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testRemoveFail() {
		Set<BooleanVar> vars = new HashSet<BooleanVar>();
		vars.add(new BooleanVar("A", true));
		BooleanTerm term = new BooleanTerm(vars);
		term.remove(new BooleanVar("A", true));
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testRemoveAllFail() {
		Set<BooleanVar> vars = new HashSet<BooleanVar>();
		vars.add(new BooleanVar("A", true));
		vars.add(new BooleanVar("B", false));
		BooleanTerm term = new BooleanTerm(vars);
		term.removeAll(vars);
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testAddAllFail() {
		Set<BooleanVar> vars = new HashSet<BooleanVar>();
		vars.add(new BooleanVar("A", true));
		vars.add(new BooleanVar("B", false));
		BooleanTerm term = new BooleanTerm("C");
		term.addAll(vars);
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testRetainAllFail() {
		Set<BooleanVar> vars = new HashSet<BooleanVar>();
		vars.add(new BooleanVar("A", true));
		vars.add(new BooleanVar("B", false));
		BooleanTerm term = new BooleanTerm("C");
		term.retainAll(vars);
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testAddToTrueFail() {
		BooleanTerm.TRUE.add(new BooleanVar("A", true));
	}
	
	@Test(expected=UnsupportedOperationException.class)
	public void testAddToFalseFail() {
		BooleanTerm.FALSE.add(new BooleanVar("A", true));
	}
	
	@Test
	public void testToString() {
		assertEquals("[TRUE]", BooleanTerm.TRUE.toString());
		assertEquals("[FALSE]", BooleanTerm.FALSE.toString());
		assertEquals("A", new BooleanTerm("A").toString());
		assertEquals("(A*B)", new BooleanTerm("A*B").toString());
		assertEquals("(!A*B)", new BooleanTerm("B*!A").toString());
	}
	
	@Test
	public void testResolveWith() {
		Features features = new Features();
		features.put("A", true);
		features.put("B", true);
		assertEquals(BooleanTerm.TRUE, new BooleanTerm("A*B").resolveWith(features));
		assertEquals(BooleanTerm.FALSE, new BooleanTerm("A*!B").resolveWith(features));
		assertEquals(new BooleanTerm("C"), new BooleanTerm("A*B*C").resolveWith(features));
		assertEquals(BooleanTerm.FALSE, new BooleanTerm("A*!B*C").resolveWith(features));
		assertEquals(new BooleanTerm("A*B"), new BooleanTerm("A*B").resolveWith(new Features()));
		assertEquals(BooleanTerm.TRUE, BooleanTerm.TRUE.resolveWith(features));
		assertEquals(BooleanTerm.TRUE, BooleanTerm.TRUE.resolveWith(features));
		assertEquals(BooleanTerm.FALSE, BooleanTerm.FALSE.resolveWith(features));
	}
	
	@Test
	public void testAndWith() {
		assertEquals(BooleanTerm.TRUE, BooleanTerm.TRUE.andWith(BooleanTerm.TRUE));
		assertEquals(BooleanTerm.FALSE, BooleanTerm.TRUE.andWith(BooleanTerm.FALSE));
		assertEquals(new BooleanTerm("A*B"), BooleanTerm.TRUE.andWith(new BooleanTerm("A*B")));
		assertEquals(BooleanTerm.FALSE, BooleanTerm.FALSE.andWith(BooleanTerm.TRUE));
		assertEquals(BooleanTerm.FALSE, BooleanTerm.FALSE.andWith(BooleanTerm.FALSE));
		assertEquals(BooleanTerm.FALSE, BooleanTerm.FALSE.andWith(new BooleanTerm("A*B")));
		assertEquals(new BooleanTerm("A*B"), new BooleanTerm("A*B").andWith(BooleanTerm.TRUE));
		assertEquals(BooleanTerm.FALSE, new BooleanTerm("A*B").andWith(BooleanTerm.FALSE));
		assertEquals(new BooleanTerm("A*B"), new BooleanTerm("A*B").andWith(new BooleanTerm("A")));
		assertEquals(new BooleanTerm("A*B"), new BooleanTerm("A").andWith(new BooleanTerm("A*B")));
		assertEquals(new BooleanTerm("A*B"), new BooleanTerm("A*B").andWith(new BooleanTerm("A*B")));
		assertEquals(new BooleanTerm("A*AB*B*BA"), new BooleanTerm("A*B").andWith(new BooleanTerm("AB*BA")));
		assertEquals(BooleanTerm.FALSE, new BooleanTerm("A*B").andWith(new BooleanTerm("!A")));
		assertEquals(BooleanTerm.FALSE, new BooleanTerm("!A").andWith(new BooleanTerm("A*B")));
		assertEquals(BooleanTerm.FALSE, new BooleanTerm("A*B").andWith(new BooleanTerm("!A*!B")));
	}
}
