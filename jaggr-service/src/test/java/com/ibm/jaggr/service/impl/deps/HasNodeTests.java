/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.deps;

import java.util.HashSet;

import junit.framework.Assert;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import com.ibm.jaggr.service.util.Features;
import com.ibm.jaggr.service.util.HasNode;

public class HasNodeTests extends EasyMock {
	Capture<String> containsFeature = new Capture<String>();
	Capture<String> isFeature = new Capture<String>();
	Features chooser = createMock(Features.class);
	HashSet<String> depFeatures;
	
	@Before
	public void setUp() {
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andReturn(true).anyTimes();
		replay(chooser);
		depFeatures = new HashSet<String>();
	}
	
	@Test 
	public void noCondition() {
		reset(chooser); // Remove expectations.
		replay(chooser);
		Assert.assertEquals("foo", new HasNode("foo").evaluate(chooser, depFeatures));
		Assert.assertEquals("foo", new HasNode("foo").toString());
	}
	
	@Test 
	public void conditionAndTrue() {
		String str = "foo?bar";
		Assert.assertEquals("toString()", str, new HasNode(str).toString());
		Assert.assertEquals("True feature.", "bar", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("False feature.", "", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("Not contained in feature map.", null, new HasNode(str).evaluate(chooser, depFeatures));
	}
	
	@Test 
	public void conditionAndFalse() {
		String str = "foo?:car";
		Assert.assertEquals("toString()", str, new HasNode(str).toString());
		Assert.assertEquals("True feature.", "", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("False feature.", "car", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("Not contained in feature map.", null, new HasNode(str).evaluate(chooser, depFeatures));
	}
	
	@Test 
	public void conditionAndBoth() {
		String str = "foo?bar:car";
		Assert.assertEquals("toString()", str, new HasNode(str).toString());
		Assert.assertEquals("True feature.", "bar", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("False feature.", "car", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("Not contained in feature map.", null, new HasNode(str).evaluate(chooser, depFeatures));
	}
	
	@Test 
	public void conditionAndCompoundTrue() {
		String str = "foo?foo2?bar:car";
		Assert.assertEquals("toString()", str, new HasNode(str).toString());
		Assert.assertEquals("True feature.", "bar", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("False all features.", "", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andStubAnswer(new IAnswer<Boolean>() {
			public Boolean answer() throws Throwable {
				return !isFeature.getValue().equals("foo2");
			}
		});
		replay(chooser);
		Assert.assertEquals("False 2nd feature.", "car", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("Not contained in feature map.", null, new HasNode(str).evaluate(chooser, depFeatures));
	}
	
	@Test 
	public void conditionAndCompoundFalse() {
		String str = "foo?:foo2?bar:car";
		Assert.assertEquals("toString()", str, new HasNode(str).toString());
		Assert.assertEquals("True feature.", "", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("False all features.", "car", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andStubAnswer(new IAnswer<Boolean>() {
			public Boolean answer() throws Throwable {
				return isFeature.getValue().equals("foo2");
			}
		});
		replay(chooser);
		Assert.assertEquals("False 1st feature.", "bar", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("Not contained in feature map.", null, new HasNode(str).evaluate(chooser, depFeatures));
	}

	@Test 
	public void conditionAndCompoundBoth() {
		String str = "foo?foo2?bar:car:foo3?far:par";
		Assert.assertEquals("toString()", str, new HasNode(str).toString());
		Assert.assertEquals("True feature.", "bar", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("False all features.", "par", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andStubAnswer(new IAnswer<Boolean>() {
			public Boolean answer() throws Throwable {
				return isFeature.getValue().equals("foo");
			}
		});
		replay(chooser);
		Assert.assertEquals("True 1st feature, false 2nd", "car", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(true).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andStubAnswer(new IAnswer<Boolean>() {
			public Boolean answer() throws Throwable {
				return isFeature.getValue().equals("foo3");
			}
		});
		replay(chooser);
		Assert.assertEquals("False 1st feature.", "far", new HasNode(str).evaluate(chooser, depFeatures));
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("Not contained in feature map.", null, new HasNode(str).evaluate(chooser, depFeatures));
	}
	
	@Test 
	public void coerceUndefinedToFalse() {
		String str = "foo?bar:car";
		Assert.assertEquals("toString()", str, new HasNode(str).toString());
		Assert.assertEquals("True feature.", "bar", new HasNode(str).evaluate(chooser, depFeatures, true));
		
		
		reset(chooser); // Remove expectations.
		expect(chooser.contains(capture(containsFeature))).andReturn(false).anyTimes();
		expect(chooser.isFeature(capture(isFeature))).andReturn(false).anyTimes();
		replay(chooser);
		Assert.assertEquals("False feature.", "car", new HasNode(str).evaluate(chooser, depFeatures, true));
	}
}
