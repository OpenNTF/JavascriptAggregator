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

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.jaggr.service.util.BooleanTerm;


public class BooleanFormulaTest {

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
	public void testSimplify() {
		// (!A!B!C)+(!AB)+(AB!C)+(AC) == (B)+(AC)+(!A!C)
		BooleanFormula expression = 
				new BooleanFormula("(!A*!B*!C)+(!A*B)+(A*B*!C)+(A*C)");
		BooleanFormula expected =
				new BooleanFormula("B+(A*C)+(!A*!C)");
		
		System.out.println(expression);
		System.out.println(expression.simplify());
		Assert.assertEquals(expected, expression.simplify());
		
		// (!A*!B*!C)+(!A*!B*C)+(!A*B*D)+(A*!B*!D)+(A*B*!C)+(A*B*C*D)
		//  == (!B*!D)+(B*D)+(!A*!B)+(A*!C*!D)

		expression = new BooleanFormula("(!A*!B*!C)+(!A*!B*C)+(!A*B*D)+(A*!B*!D)+(A*B*!C)+(A*B*C*D)");
		expected = new BooleanFormula("(!B*!D)+(B*D)+(!A*!B)+(A*!C*!D)");

		System.out.println(expression);
		System.out.println(expression.simplify());
		Assert.assertEquals(expected, expression.simplify());
		Assert.assertFalse(expression.isTrue());
		Assert.assertFalse(expression.isFalse());

		// Make sure we can add the terms one at a time and get the same results
		expression = new BooleanFormula("(!A*!B*!C)").simplify();
		expression.add(new BooleanTerm("(!A*B*D)"));
		expression.add(new BooleanTerm("(!A*!B*C)"));
		expression.add(new BooleanTerm("(A*B*C*D)"));
		expression.add(new BooleanTerm("(A*B*!C)"));
		expression.add(new BooleanTerm("(A*!B*!D)"));
		Assert.assertEquals(expected.simplify(), expression.simplify());
		Assert.assertFalse(expression.isTrue());
		Assert.assertFalse(expression.isFalse());

		// Validate when one of the terms cancels out
		expression = new BooleanFormula("(!A*A)+(B*C)");
		expected = new BooleanFormula("(B*C)");
		
		System.out.println(expression);
		System.out.println(expression.simplify());
		Assert.assertEquals(expected, expression.simplify());
		Assert.assertFalse(expression.isTrue());
		Assert.assertFalse(expression.isFalse());
		
		// Validate when all of the terms cancel out
		expression = new BooleanFormula("(!A*A)+(B*!B)");
		
		System.out.println(expression);
		
		Assert.assertFalse(expression.isTrue());
		Assert.assertFalse(expression.isFalse());
		expression = expression.simplify();
		System.out.println(expression);
		Assert.assertFalse(expression.isTrue());
		Assert.assertTrue(expression.isFalse());

		// Validate when the expression evaluates to true
		expression = new BooleanFormula("!A+A+B");
		Assert.assertFalse(expression.isTrue());
		expression = expression.simplify();
		System.out.println(expression);
		Assert.assertTrue(expression.isTrue());
		Assert.assertFalse(expression.isFalse());
	}

	@Test
	public void testSubtract() throws Exception {
		BooleanFormula expression = 
				new BooleanFormula("(!A*!B*!C)+(!A*B)+(A*B*!C)+(A*C)");
		BooleanFormula copy = new BooleanFormula(expression);
		copy.removeTerms(new BooleanFormula(true));
		Assert.assertTrue(copy.isFalse());
		
		copy = new BooleanFormula(expression);
		copy.removeTerms(new BooleanFormula(false));
		Assert.assertTrue(copy.simplify().equals(expression.simplify()));
		
		copy = new BooleanFormula(expression);
		copy.removeTerms(new BooleanFormula("(!A*!B*!C)"));
		Assert.assertEquals(new BooleanFormula("(!A*B)+(A*B*!C)+(A*C)"), copy);
		copy.removeTerms(new BooleanFormula("(A*B*!C)+(A*C)+(B*C)"));
		Assert.assertEquals(new BooleanFormula("(!A*B)"), copy);
		copy.removeTerms(new BooleanFormula("(!A*B)"));
		Assert.assertTrue(copy.isFalse());
		
		expression = new BooleanFormula(true);
		expression.removeTerms(new BooleanFormula(true));
		Assert.assertTrue(expression.isFalse());
		
		expression = new BooleanFormula(true);
		expression.removeTerms(new BooleanFormula(false));
		Assert.assertTrue(expression.isTrue());
		
		expression = new BooleanFormula(true);
		expression.removeTerms(new BooleanFormula("A+B"));
		Assert.assertTrue(expression.isTrue());

		expression = new BooleanFormula(false);
		expression.removeTerms(new BooleanFormula(true));
		Assert.assertTrue(expression.isFalse());
		
		expression = new BooleanFormula(false);
		expression.removeTerms(new BooleanFormula(false));
		Assert.assertTrue(expression.isFalse());
		
		expression = new BooleanFormula(false);
		expression.removeTerms(new BooleanFormula("A+B"));
		Assert.assertTrue(expression.isFalse());
	}
	
}
