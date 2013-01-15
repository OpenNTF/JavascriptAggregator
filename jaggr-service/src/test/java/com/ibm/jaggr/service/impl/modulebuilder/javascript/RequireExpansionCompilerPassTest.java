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

package com.ibm.jaggr.service.impl.modulebuilder.javascript;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.Compiler.CodeBuilder;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.util.DependencyList;
import com.ibm.jaggr.service.util.Features;

public class RequireExpansionCompilerPassTest extends EasyMock {
	
	private static Map<String, String[]> declaredDependencies = new HashMap<String, String[]>();
	private static Map<String, String[]> expandedDependencies = new HashMap<String, String[]>();
	static {
		declaredDependencies.put("foo", new String[]{"bar"});
		declaredDependencies.put("dependsOnBar", new String[]{"bar"});
		declaredDependencies.put("dependsOnModule", new String[]{"module"});
		declaredDependencies.put("x/y", new String[]{"x/y/z", "foo"});
		
		expandedDependencies.put("foo", new String[]{"a/b"});
		expandedDependencies.put("a/c", new String[]{"c/d"});
		expandedDependencies.put("has1", new String[]{"dep1"});
		expandedDependencies.put("has2", new String[]{"dep2"});
	}
	
	private IAggregator mockAggregator;
	private IDependencies mockDependencies = createMock(IDependencies.class);
	private String moduleName = "test";
	


	@SuppressWarnings("unchecked")
	@Before
	public void setUp() throws Exception {
		mockAggregator = TestUtils.createMockAggregator();
		expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		expect(mockDependencies.getDelcaredDependencies(
				(String)anyObject())).andAnswer(new IAnswer<List<String>>() {
			public List<String> answer() throws Throwable {
				return getDeclaredDependencies((String)getCurrentArguments()[0]);
			}
		}).anyTimes();
		expect(mockDependencies.getExpandedDependencies(
				(String)anyObject(), 
				(Features)anyObject(), 
				(Set<String>)anyObject(), 
				anyBoolean())).andAnswer(new IAnswer<Map<String, String>>() {
			public Map<String, String> answer() throws Throwable {
				return getExpandedDependencies(getCurrentArguments());
			}
		}).anyTimes();
		replay(mockAggregator);
		replay(mockDependencies);
	}

	@Test
	public void testRequireExpansion() throws Exception {
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				null, null, null, false);

		String code, output;
		
		// Ensure require list is modified
		code = "require([\"foo\"],function(foo){});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));

		// Ensure require list is modified
		code = "require([\"foo\"]);";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));

		// Ensure only array literals are expanded
		code = "require(\"foo\");";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals(code, output);
		
		// Ensure variables are not modified
		code = "require([\"foo\", jsvar])";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",jsvar,\"bar\",\"a/b\"]"));
		
		// test with compound strings
		code = "require([\"foo\" + jsvar])";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\"+jsvar]"));

		code = "require([\"foo\" + \"bar\"])";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\"+\"bar\"]"));
		
		code = "require([\"foo\", \"a\"+\"b\", \"x/y\"])";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"a\"+\"b\",\"x/y\",\"bar\",\"a/b\",\"x/y/z\"]"));

		// Ensure relative paths are resolved based on module name
		moduleName = "a/a";
		code = "require([\"foo\",\"./c\"]);";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"./c\",\"bar\",\"a/b\",\"c/d\"]"));
		moduleName = "test";
		

		// Ensure enclosing dependencies not expanded
		code = "define([\"bar\"],function(bar){require([\"foo\",\"a/b\"])});";
		moduleName = "dependsOnBar";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals(code, output);
		
		// No encloding dependencies
		moduleName = "dependsOnModule";
		code = "define([\"module\"],function(bar){require([\"foo\"]);});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		// Ensure dependencies in config deps are not expanded
		List<String> configDepNames = Arrays.asList(new String[]{"a/b"});
		DependencyList configDepList = new DependencyList(configDepNames, mockAggregator.getConfig(), mockDependencies, new Features(), false);
		
		RequireExpansionCompilerPass configDepPass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				new HashSet<String>(),
				configDepList, null, false);

		output = runPass(configDepPass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\"]"));
		
		
		// Enable development mode and make sure a RuntimeDependencyVerificationException
		// is thrown if the dependency list specified in the code does not match
		// the dependency list returned by IDependencies.getDeclaredDependencies()
		// for the module.
		
		// First, enable development mode
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		mockAggregator.getOptions().setOption(IOptions.VERIFY_DEPS, true);

		// this test should not throw because the code matches the dependencies
		code = "define([\"module\"],function(bar){require([\"foo\"]);});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		// This test should throw because the code doesn't match the dependencies
		boolean exceptionThrown = false;
		code = "define([\"module\",\"another\"],function(bar){require([\"foo\"]);});";
		try {
			output = runPass(pass, code);
			System.out.println(output);
		} catch (RuntimeDependencyVerificationException e) {
			exceptionThrown = true;
		}
		Assert.assertTrue("RuntimeDependencyVerificationException not thrown", exceptionThrown);

		// This test verifies that relative dependency paths are resolved correctly
		// Will throw RuntimeDependencyVerificationException if relative modules
		// specified in define are not normalized and resolved to the absolute paths
		// specified in the dependency map for module x/y.
		moduleName = "x/y";
		code = "define([\"./y/z\",\"../foo\"],function(bar){require([\"foo\"]);});";
		output = runPass(pass, code);
		System.out.println(output);
		
		moduleName = "dependsOnModule";
		code = "define([\"module\",\"another\"],function(bar){require([\"foo\"]);});";
		
		// Assert that both development mode and verify deps need to be enabled
		// for an exception to be thrown
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		mockAggregator.getOptions().setOption(IOptions.VERIFY_DEPS, false);
		output = runPass(pass, code);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, false);
		mockAggregator.getOptions().setOption(IOptions.VERIFY_DEPS, true);
		output = runPass(pass, code);
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, false);
		mockAggregator.getOptions().setOption(IOptions.VERIFY_DEPS, false);
		output = runPass(pass, code);
		
	}
	
	@Test
	public void testRequireDepsExpansion() throws Exception {
		
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				null, null, null, false);

		String code, output;
		
		// Ensure dependency list is modified for the following
		code = "require = { deps:[\"foo\"] };";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "require.deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "this.require.deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "obj.require.deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "this.require = {deps:[\"foo\"]};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "obj.require = {deps:[\"foo\"]};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "var require = {deps:[\"foo\"]};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "obj = {require: {deps: [\"foo\"]}};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "var obj = {require: {deps:[\"foo\"]}};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		code = "call({require: {deps:[\"foo\"]}});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"bar\",\"a/b\"]"));
		
		// Negative tests.  Dependency list should not be modified
		code = "deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\"]"));
		
		code = "this.deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\"]"));
		
		code = "var obj = {deps:[\"foo\"]};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\"]"));
		
		code = "var obj = {top: {deps:[\"foo\"]}};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\"]"));
		
		code = "call({obj: {deps:[\"foo\"]}});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\"]"));

		code = "require.deps=deps;";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals(code, output);
		
		code = "require.deps={\"foo\":0};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals(code, output);
		
		// ensure proper placement of expanded deps
		code = "require.deps=[first,\"foo\",third];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[first,\"foo\",third,\"bar\",\"a/b\"];"));
		
		// make sure that we can use a different config var name
		pass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				new HashSet<String>(),
				null, "djConfig", false);
		
		code = "djConfig.deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("djConfig.deps=[\"foo\",\"bar\",\"a/b\"]"));
	}
	
	@Test
	public void testHasPluginResolution() throws Exception {
		Features features = new Features();
		Set<String> dependentFeatures = new TreeSet<String>();
		features.put("feature1", true);
		features.put("feature2", true);
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				features,
				dependentFeatures,
				null, null, false);

		String code, output;
		code = "require([\"has!feature1?has1\",\"has!feature2?has2\"]);";
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\",\"dep1\",\"dep2\"]);", output);
		
		features.put("feature2", false);
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\",\"dep1\"]);", output);
		
		features.put("feature1", false);
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\"]);", output);
		
		features.remove("feature2");
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\"]);", output);
		
		features.put("feature1", true);
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\",\"dep1\"]);", output);
		
		features.remove("feature1");
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\"]);", output);
		
	}

	@Test
	public void testLogging() throws Exception {
		
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				null, null, null, true);

		String code, output;
		code = "require([\"foo\"],function(){});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue("Expected pattern not found.", Pattern.compile("console\\.log\\(\\\"[^)\"]*Expanding requires list").matcher(output).find());
		Assert.assertTrue("Output does not contain expected value.", output.contains("foo (Declared.)"));
		Assert.assertTrue("Output does not contain expected value.", output.contains("bar (Declared.)"));
		Assert.assertTrue("Output does not contain expected value.", output.contains("a/b (From: foo)"));
		
	}
		
	private String runPass(RequireExpansionCompilerPass pass, String code) {
		Compiler compiler = new Compiler();
		Node root = compiler.parse(JSSourceFile.fromCode(moduleName, code));
		pass.process(null, root);
		CodeBuilder cb = new CodeBuilder();
		compiler.toSource(cb, 0, root);
		return cb.toString();
	}
	
	private List<String> getDeclaredDependencies(String id) throws Throwable {
		List<String> result = new LinkedList<String>();
		String[] deps = declaredDependencies.get(id);
		if (deps != null) {
			result.addAll(Arrays.asList(deps));
		}
		return result;
	}
	
	private Map<String, String> getExpandedDependencies(Object[] args) throws Throwable {
		String id = (String)args[0];
		boolean includeComments = (Boolean)args[3];
		List<String> declaredDeps = mockDependencies.getDelcaredDependencies(id);
		Map<String, String> result = new LinkedHashMap<String, String>();
		for (String declaredDep : declaredDeps) {
			result.put(declaredDep, includeComments ? "Declared." : null);
		}
		String[] expandedDeps = expandedDependencies.get(id);
		if (expandedDeps != null) {
			for (String expandedDep : expandedDeps) {
				result.put(expandedDep, includeComments ? ("From: " + id) : null);
			}
		}
		return result;
	}	
}
