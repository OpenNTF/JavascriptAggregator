/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import junit.framework.Assert;

public class HasNodeTest {

	Features features;
	Set<String> discovered;

	@Before
	public void setup() throws Exception {
		features = new Features();
		discovered = new HashSet<String>();
	}

	@Test
	public void testResolve1() {
		HasNode node = new HasNode("feature?foo:bar").resolve(features, discovered, false);
		Assert.assertEquals("feature?foo:bar", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve2() {
		features.put("feature", true);
		HasNode node = new HasNode("feature?foo:bar").resolve(features, discovered, false);
		Assert.assertEquals("foo", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve3() {
		features.put("feature", false);
		HasNode node = new HasNode("feature?foo:bar").resolve(features, discovered, false);
		Assert.assertEquals("bar", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve4() {
		HasNode node = new HasNode("feature?foo:bar").resolve(features, discovered, true);
		Assert.assertEquals("bar", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve5() {
		HasNode node = new HasNode("feature?foo").resolve(features, discovered, false);
		Assert.assertEquals("feature?foo", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve6() {
		features.put("feature", true);
		HasNode node = new HasNode("feature?foo").resolve(features, discovered, false);
		Assert.assertEquals("foo", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve7() {
		features.put("feature", false);
		HasNode node = new HasNode("feature?foo").resolve(features, discovered, false);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve8() {
		HasNode node = new HasNode("feature?foo").resolve(features, discovered, true);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve9() {
		HasNode node = new HasNode("feature?:bar").resolve(features, discovered, false);
		Assert.assertEquals("feature?:bar", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve10() {
		features.put("feature", true);
		HasNode node = new HasNode("feature?:bar").resolve(features, discovered, false);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve11() {
		features.put("feature", false);
		HasNode node = new HasNode("feature?:bar").resolve(features, discovered, false);
		Assert.assertEquals("bar", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	@Test
	public void testResolve12() {
		HasNode node = new HasNode("feature?:bar").resolve(features, discovered, true);
		Assert.assertEquals("bar", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature"})), discovered);
	}

	// Test partial resolution of compound expressions
	@Test
	public void testResolve13() {
		HasNode node = new HasNode("featureA?featureB?B:notB:featureC?C:notC").resolve(features, discovered, false);
		Assert.assertEquals("featureA?featureB?B:notB:featureC?C:notC", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve14() {
		features.put("featureA", true);
		HasNode node = new HasNode("featureA?featureB?B:notB:featureC?C:notC").resolve(features, discovered, false);
		Assert.assertEquals("featureB?B:notB", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB"})), discovered);
	}

	@Test
	public void testResolve15() {
		features.put("featureA", true);
		features.put("featureC", true);
		HasNode node = new HasNode("featureA?featureB?B:notB:featureC?C:notC").resolve(features, discovered, false);
		Assert.assertEquals("featureB?B:notB", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB"})), discovered);
	}

	@Test
	public void testResolve16() {
		features.put("featureB", true);
		HasNode node = new HasNode("featureA?featureB?B:notB:featureC?C:notC").resolve(features, discovered, false);
		Assert.assertEquals("featureA?B:featureC?C:notC", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve17() {
		features.put("featureB", true);
		HasNode node = new HasNode("featureA?featureB?B:notB:featureC?C:notC").resolve(features, discovered, false);
		Assert.assertEquals("featureA?B:featureC?C:notC", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve18() {
		features.put("featureB", true);
		features.put("featureC", false);
		HasNode node = new HasNode("featureA?featureB?B:notB:featureC?C:notC").resolve(features, discovered, false);
		Assert.assertEquals("featureA?B:notC", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve19() {
		HasNode node = new HasNode("featureA?:featureB?B").resolve(features, discovered, true);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB"})), discovered);
	}

	@Test
	public void testResolve20() {
		features.put("featureB", true);
		HasNode node = new HasNode("featureA?featureB?featureC?foo").resolve(features, discovered, false);
		Assert.assertEquals("featureA?featureC?foo", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve21() {
		features.put("featureA", false);
		HasNode node = new HasNode("featureA?featureB?featureC?foo").resolve(features, discovered, false);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA"})), discovered);
	}

	@Test
	public void testResolve22() {
		features.put("featureB", false);
		HasNode node = new HasNode("featureA?featureB?featureC?foo").resolve(features, discovered, false);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB"})), discovered);
	}

	@Test
	public void testResolve23() {
		features.put("featureC", false);
		HasNode node = new HasNode("featureA?featureB?featureC?foo").resolve(features, discovered, false);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve24() {
		features.put("featureA", true);
		features.put("featureB", true);
		features.put("featureC", true);
		HasNode node = new HasNode("featureA?featureB?featureC?foo").resolve(features, discovered, false);
		Assert.assertEquals("foo", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve25() {
		features.put("featureA", true);
		HasNode node = new HasNode("featureA?:featureB?:featureC?:foo").resolve(features, discovered, false);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA"})), discovered);
	}

	@Test
	public void testResolve26() {
		features.put("featureB", true);
		HasNode node = new HasNode("featureA?:featureB?:featureC?:foo").resolve(features, discovered, false);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB"})), discovered);
	}

	@Test
	public void testResolve27() {
		features.put("featureC", true);
		HasNode node = new HasNode("featureA?:featureB?:featureC?:foo").resolve(features, discovered, false);
		Assert.assertEquals("", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve28() {
		features.put("featureA", false);
		features.put("featureB", false);
		features.put("featureC", false);
		HasNode node = new HasNode("featureA?:featureB?:featureC?:foo").resolve(features, discovered, false);
		Assert.assertEquals("foo", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}

	@Test
	public void testResolve29() {
		HasNode node = new HasNode("featureA?:featureB?:featureC?:foo").resolve(features, discovered, true);
		Assert.assertEquals("foo", node.toString());
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"featureA", "featureB", "featureC"})), discovered);
	}




}
