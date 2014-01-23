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

import com.ibm.jaggr.core.util.BooleanFormula;
import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.Features;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import junit.framework.Assert;


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

		Assert.assertTrue(new BooleanFormula(true).simplify().isTrue());
		Assert.assertFalse(new BooleanFormula(true).simplify().isFalse());
		Assert.assertTrue(new BooleanFormula(false).simplify().isFalse());
		Assert.assertFalse(new BooleanFormula(false).simplify().isTrue());
	}

	@Test
	public void testAdd() {
		BooleanFormula expression = new BooleanFormula("(A*B)");
		Assert.assertTrue(expression.add(new BooleanTerm("(A*C)")));
		Assert.assertEquals(expression, new BooleanFormula("(A*B)+(A*C)"));
		Assert.assertFalse(expression.add(new BooleanTerm("(A*B)")));
		Assert.assertFalse(expression.add(new BooleanTerm("(A*B*C)")));
		Assert.assertEquals(expression, new BooleanFormula("(A*B)+(A*C)"));
		Assert.assertTrue(expression.add(new BooleanTerm("C")));
		Assert.assertEquals(expression, new BooleanFormula("(A*B)+C"));
		Assert.assertTrue(expression.add(new BooleanTerm("A")));
		Assert.assertEquals(expression, new BooleanFormula("A+C"));
		Assert.assertFalse(expression.add(new BooleanTerm("(A*D)")));
		Assert.assertFalse(expression.add(new BooleanTerm("(C*D)")));
		Assert.assertEquals(expression, new BooleanFormula("A+C"));
		Assert.assertTrue(expression.add(new BooleanTerm("(B*D)")));
		Assert.assertEquals(expression, new BooleanFormula("A+C+(B*D)"));
		expression = new BooleanFormula(false);
		Assert.assertTrue(expression.add(new BooleanTerm("A*B")));
		Assert.assertEquals(new BooleanFormula("A*B"), expression);
	}


	@Test
	public void testResolveWith() {
		BooleanFormula expression =
				new BooleanFormula("(!A*!B*!C)+(!A*B)+(A*B*!C)+(A*C)");
		Features features = new Features();
		features.put("A", false);
		Assert.assertEquals(new BooleanFormula("B+!C"), new BooleanFormula(expression).resolveWith(features).simplify());

		features.put("B", false);
		Assert.assertEquals(new BooleanFormula("!C"), new BooleanFormula(expression).resolveWith(features).simplify());

		features.put("C", false);
		Assert.assertEquals(new BooleanFormula(true), new BooleanFormula(expression).resolveWith(features).simplify());

		features = new Features();
		features.put("C", true);
		Assert.assertEquals(new BooleanFormula("A+B"), new BooleanFormula(expression).resolveWith(features).simplify());
		features.put("A", false);
		Assert.assertEquals(new BooleanFormula("B"), new BooleanFormula(expression).resolveWith(features).simplify());
		features.put("B", false);
		Assert.assertEquals(new BooleanFormula(false), new BooleanFormula(expression).resolveWith(features).simplify());

		expression = new BooleanFormula("(!A*!B*!C)+(!A*!B*C)+(!A*B*D)+(A*!B*!D)+(A*B*!C)+(A*B*C*D)");
		features = new Features();
		features.put("A", true);
		Assert.assertEquals(new BooleanFormula("(!B*!D)+(!C*!D)+(B*D)"), new BooleanFormula(expression).resolveWith(features).simplify());
		features.put("D", false);
		Assert.assertEquals(new BooleanFormula("!B+!C"), new BooleanFormula(expression).resolveWith(features).simplify());
		features.put("B", false);
		Assert.assertEquals(new BooleanFormula(true), new BooleanFormula(expression).resolveWith(features).simplify());

		expression = new BooleanFormula(true);
		Assert.assertEquals(new BooleanFormula(true), new BooleanFormula(expression).resolveWith(features));

		expression = new BooleanFormula(false);
		Assert.assertEquals(new BooleanFormula(false), new BooleanFormula(expression).resolveWith(features));
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

	@Test
	public void testAndWith() throws Exception {
		BooleanFormula expression1 =
				new BooleanFormula("(A*B)+(C*D)");
		BooleanFormula expression2 =
				new BooleanFormula("!Z");
		Assert.assertEquals(new BooleanFormula("(A*B*!Z)+(C*D*!Z)"),expression1.andWith(expression2));
		// Test associativity
		expression1 = new BooleanFormula("(A*B)+(C*D)");
		Assert.assertEquals(new BooleanFormula("(A*B*!Z)+(C*D*!Z)"),expression2.andWith(expression1));

		// Test with a result term that is false
		expression1 = new BooleanFormula("(A*B)+(C*D)");
		expression2 = new BooleanFormula("!A");
		Assert.assertEquals(new BooleanFormula("(!A*C*D)"),expression1.andWith(expression2));
		expression1 = new BooleanFormula("(A*B)+(C*D)");
		Assert.assertEquals(new BooleanFormula("(!A*C*D)"),expression2.andWith(expression1));

		// Test compound inputs
		expression1 = new BooleanFormula("(A*B)+(C*D)");
		expression2 = new BooleanFormula("(A*X)+(C*Y)");
		Assert.assertEquals(new BooleanFormula("(A*B*X)+(A*C*D*X)+(A*B*C*Y)+(C*D*Y)"),expression1.andWith(expression2));
		expression1 = new BooleanFormula("(A*B)+(C*D)");
		Assert.assertEquals(new BooleanFormula("(A*B*X)+(A*C*D*X)+(A*B*C*Y)+(C*D*Y)"),expression2.andWith(expression1));

		// Test with a false result
		expression1 = new BooleanFormula("(A*B)+(A*C)");
		expression2 = new BooleanFormula("!A");
		Assert.assertEquals(new BooleanFormula(false),expression1.andWith(expression2));
		expression1 = new BooleanFormula("(A*B)+(A*C)");
		Assert.assertEquals(new BooleanFormula(false),expression2.andWith(expression1));

		// Test with unchanged result
		expression1 = new BooleanFormula("(A*B)+(A*C)");
		expression2 = new BooleanFormula("A");
		Assert.assertEquals(new BooleanFormula("(A*B)+(A*C)"),expression1.andWith(expression2));
		expression1 = new BooleanFormula("(A*B)+(A*C)");
		Assert.assertEquals(new BooleanFormula("(A*B)+(A*C)"),expression2.andWith(expression1));

		// one input true
		expression1 = new BooleanFormula("(A*B)+(A*C)");
		expression2 = new BooleanFormula(true);
		Assert.assertEquals(new BooleanFormula("(A*B)+(A*C)"),expression1.andWith(expression2));
		Assert.assertEquals(new BooleanFormula("(A*B)+(A*C)"),expression2.andWith(expression1));

		// one input false
		expression1 = new BooleanFormula("(A*B)+(A*C)");
		expression2 = new BooleanFormula(false);
		Assert.assertEquals(new BooleanFormula(false),expression1.andWith(expression2));
		expression1 = new BooleanFormula("(A*B)+(A*C)");
		Assert.assertEquals(new BooleanFormula(false),expression2.andWith(expression1));

		// both inputs true
		expression1 = new BooleanFormula(true);
		expression2 = new BooleanFormula(true);
		Assert.assertEquals(new BooleanFormula(true), expression1.andWith(expression2));

		// both inputs false
		expression1 = new BooleanFormula(false);
		expression2 = new BooleanFormula(false);
		Assert.assertEquals(new BooleanFormula(false), expression1.andWith(expression2));

		// one true, one false
		expression1 = new BooleanFormula(true);
		expression2 = new BooleanFormula(false);
		Assert.assertEquals(new BooleanFormula(false), expression1.andWith(expression2));
		expression1 = new BooleanFormula(true);
		Assert.assertEquals(new BooleanFormula(false), expression2.andWith(expression1));

	}

}
