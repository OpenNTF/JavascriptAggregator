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

package com.ibm.jaggr.core.impl.modulebuilder.javascript;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.JavaScriptBuildRenderer;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.RequireExpansionCompilerPass;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.RuntimeDependencyVerificationException;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.util.Features;

import com.google.common.collect.HashMultimap;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.Compiler.CodeBuilder;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CustomPassExecutionTime;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class RequireExpansionCompilerPassTest extends EasyMock {

	private static Map<String, String[]> declaredDependencies = new HashMap<String, String[]>();
	private static String placeHolder0 = String.format(JavaScriptBuildRenderer.REQUIRE_EXPANSION_PLACEHOLDER_FMT, 0);
	private static String placeHolder1 = String.format(JavaScriptBuildRenderer.REQUIRE_EXPANSION_PLACEHOLDER_FMT, 1);

	static {
		declaredDependencies.put("foo", new String[]{"bar"});
		declaredDependencies.put("dependsOnBar", new String[]{"bar"});
		declaredDependencies.put("dependsOnModule", new String[]{"module"});
		declaredDependencies.put("x/y", new String[]{"x/y/z", "foo"});

		declaredDependencies.put("bar", new String[]{"a/b"});
		declaredDependencies.put("a/c", new String[]{"c/d"});
		declaredDependencies.put("has1", new String[]{"dep1"});
		declaredDependencies.put("has2", new String[]{"dep2"});
	}

	private IAggregator mockAggregator;
	private IDependencies mockDependencies = createMock(IDependencies.class);
	private String moduleName = "test";



	@Before
	public void setUp() throws Exception {
		mockAggregator = TestUtils.createMockAggregator();
		expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		expect(mockDependencies.getDelcaredDependencies(
				(String)anyObject())).andAnswer(new IAnswer<List<String>>() {
					public List<String> answer() throws Throwable {
						String name = (String)getCurrentArguments()[0];
						String[] result = declaredDependencies.get(name);
						return result != null ? Arrays.asList(result) : null;
					}
				}).anyTimes();
		expect(mockDependencies.getLastModified()).andReturn(0L).anyTimes();
		replay(mockAggregator);
		replay(mockDependencies);
	}

	@Test
	public void testRequireExpansion() throws Exception {
		List<ModuleDeps> expanded = new ArrayList<ModuleDeps>();
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				null,
				expanded,
				null, false);

		String code, output;

		// Ensure require list is modified
		code = "require([\"foo\"],function(foo){});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"" + placeHolder0 + "\"]"));
		Assert.assertEquals(1, expanded.size());
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		// Ensure require list is modified
		code = "require([\"foo\"]);";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"" + placeHolder0 + "\"]"));
		Assert.assertEquals(1, expanded.size());
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		// Ensure only array literals are expanded
		code = "require(\"foo\");";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals(code, output);

		// Ensure variables are not modified
		code = "require([\"foo\", jsvar])";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",jsvar,\"" + placeHolder0 + "\"]"));
		Assert.assertEquals(1, expanded.size());
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

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
		Assert.assertTrue(output.contains("[\"foo\",\"a\"+\"b\",\"x/y\",\"" + placeHolder0 + "\"]"));
		Assert.assertEquals(1, expanded.size());
		System.out.println( expanded.get(0).keySet());
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b", "x/y/z"})),
				expanded.get(0).getModuleIds());

		// Ensure relative paths are resolved based on module name
		moduleName = "a/a";
		code = "require([\"foo\",\"./c\"]);";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"./c\",\"" + placeHolder0 + "\"]"));
		Assert.assertEquals(1, expanded.size());
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b", "c/d"})),
				expanded.get(0).getModuleIds());
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
		Assert.assertTrue(output.contains("[\"foo\",\"" + placeHolder0 + "\"]"));
		Assert.assertEquals(1, expanded.size());
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		// multiple require calls
		code = "define([\"module\"],function(bar){require([\"foo\"]); var abc = 123; require([\"x/y\"]);});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(output.contains("[\"foo\",\"" + placeHolder0 + "\"]"));
		Assert.assertTrue(output.contains("[\"x/y\",\"" + placeHolder1 + "\"]"));
		Assert.assertEquals(2, expanded.size());
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"foo", "bar", "a/b", "x/y/z"})),
				expanded.get(1).getModuleIds());

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
		Assert.assertTrue(output.contains("[\"foo\",\"" + placeHolder0 + "\"]"));

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

	private static final List<JSSourceFile> externs = Collections.emptyList();

	/*
	 * Ensure that arrays of strings are not mutated into expressions of the form
	 * "abc bcd cde".split(" ") when the require list expansion placeholder
	 * module name is inserted at the end of the array list.
	 */
	@Test
	public void testArrayMutation() throws Exception {
		String code = "define([\"module\"],function(bar){require([\"foo\", \"abc\", \"bcd\", \"cde\", \"def\", \"efg\", \"fgh\", \"ghi\"]);});";
		JSSourceFile sf = JSSourceFile.fromCode("test", code);
		List<JSSourceFile> sources = new ArrayList<JSSourceFile>();
		sources.add(sf);
		Compiler compiler = new Compiler();
		CompilerOptions compiler_options = new CompilerOptions();
		CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(compiler_options);
		compiler.compile(externs, sources, compiler_options);
		String output = compiler.toSource();
		System.out.println(output);
		// verfiy that array mutation occurs
		Assert.assertTrue(output.endsWith("fgh ghi\".split(\" \"))});"));

		List<ModuleDeps> expanded = new ArrayList<ModuleDeps>();
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				null,
				expanded,
				null, false);
		compiler = new Compiler();
		compiler_options = new CompilerOptions();
		CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(compiler_options);
		compiler_options.customPasses = HashMultimap.create();
		compiler_options.customPasses.put(CustomPassExecutionTime.BEFORE_CHECKS, pass);
		compiler.compile(externs, sources, compiler_options);
		output = compiler.toSource();
		System.out.println(output);
		// Verify that array mutation doesn't occur
		Assert.assertFalse(output.contains(".split("));
		Assert.assertTrue(output.endsWith("\"ghi\",\"" + placeHolder0 + "\"])});"));
	}

	@Test
	public void testRequireDepsExpansion() throws Exception {
		List<ModuleDeps> expanded = new ArrayList<ModuleDeps>();
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				null,
				expanded,
				null, false);

		String code, output;

		// Ensure dependency list is modified for the following
		code = "require = { deps:[\"foo\"] };";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("require={deps:[\"foo\",\"" + placeHolder0 + "\"]};", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "require.deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("require.deps=[\"foo\",\"" + placeHolder0 + "\"];", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "this.require.deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("this.require.deps=[\"foo\",\"" + placeHolder0 + "\"];", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "obj.require.deps = [\"foo\"];";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("obj.require.deps=[\"foo\",\"" + placeHolder0 + "\"];", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "this.require = {deps:[\"foo\"]};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("this.require={deps:[\"foo\",\"" + placeHolder0 + "\"]};", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "obj.require = {deps:[\"foo\"]};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("obj.require={deps:[\"foo\",\"" + placeHolder0 + "\"]};", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "var require = {deps:[\"foo\"]};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("var require={deps:[\"foo\",\"" + placeHolder0 + "\"]};", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "obj = {require: {deps: [\"foo\"]}};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("obj={require:{deps:[\"foo\",\"" + placeHolder0 + "\"]}};", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "var obj = {require: {deps:[\"foo\"]}};";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("var obj={require:{deps:[\"foo\",\"" + placeHolder0 + "\"]}};", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

		code = "call({require: {deps:[\"foo\"]}});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("call({require:{deps:[\"foo\",\"" + placeHolder0 + "\"]}});", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());

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
		Assert.assertEquals("require.deps=[first,\"foo\",third,\"" + placeHolder0 + "\"];", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"bar", "a/b"})),
				expanded.get(0).getModuleIds());
	}

	@Test
	public void testHasPluginResolution() throws Exception {
		Features features = new Features();
		Set<String> dependentFeatures = new TreeSet<String>();
		features.put("feature1", true);
		features.put("feature2", true);
		List<ModuleDeps> expanded = new ArrayList<ModuleDeps>();
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				features,
				dependentFeatures,
				expanded,
				null, false);

		String code, output;
		code = "require([\"has!feature1?has1\",\"has!feature2?has2\"]);";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\",\"" + placeHolder0 + "\"]);", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"dep1", "dep2"})),
				expanded.get(0).getModuleIds());

		features.put("feature2", false);
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\",\"" + placeHolder0 + "\"]);", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"dep1"})),
				expanded.get(0).getModuleIds());

		features.put("feature1", false);
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\"]);", output);
		Assert.assertEquals(0 ,	expanded.get(0).getModuleIds().size());

		features.remove("feature2");
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\",\"" + placeHolder0 + "\"]);", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"has!feature2?dep2"})),
				expanded.get(0).getModuleIds());

		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASPLUGINBRANCHING, true);
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\"]);", output);
		Assert.assertEquals(0 ,	expanded.get(0).getModuleIds().size());

		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASPLUGINBRANCHING, false);
		features.put("feature1", true);
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\",\"" + placeHolder0 + "\"]);", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"dep1", "has!feature2?dep2"})),
				expanded.get(0).getModuleIds());

		features.remove("feature1");
		dependentFeatures.clear();
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\",\"" + placeHolder0 + "\"]);", output);
		Assert.assertEquals(new LinkedHashSet<String>(Arrays.asList(new String[]{"has!feature1?dep1", "has!feature2?dep2"})),
				expanded.get(0).getModuleIds());

		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASPLUGINBRANCHING, true);
		output = runPass(pass, code);
		Assert.assertEquals("[feature1, feature2]", dependentFeatures.toString());
		Assert.assertEquals("require([\"has!feature1?has1\",\"has!feature2?has2\"]);", output);
		Assert.assertEquals(0 ,	expanded.get(0).getModuleIds().size());
	}

	@Test
	public void testDependencyVerificationException() throws Exception {
		Features features = new Features();
		Set<String> dependentFeatures = new TreeSet<String>();
		features.put("feature1", true);
		features.put("feature2", true);
		List<ModuleDeps> expanded = new ArrayList<ModuleDeps>();
		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				features,
				dependentFeatures,
				expanded,
				null, false);

		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, true);
		mockAggregator.getOptions().setOption(IOptions.VERIFY_DEPS, true);
		String code, output;
		moduleName = "x/y";
		code = "define([\"x/y/z\", \"foo\", \"dep3\"], function(z, foo, dep3) {});";
		try {
			output = runPass(pass, code);
			Assert.fail();
		} catch (RuntimeDependencyVerificationException ex) {
		}

		// Ensure that duplicate dependencies in define statement doesn't
		//  throw DependencyVerificationException
		code = "define([\"x/y/z\", \"foo\", \"x/y/z\"], function(z, foo) {});";
		output = runPass(pass, code);
		System.out.println(output);

	}

	@Test
	public void testLogging() throws Exception {

		RequireExpansionCompilerPass pass = new RequireExpansionCompilerPass(
				mockAggregator,
				new Features(),
				null,
				new ArrayList<ModuleDeps>(),
				null, true);

		String code, output;
		code = "require([\"foo\"],function(){});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue("Expected pattern not found.", Pattern.compile("console\\.log\\(\\\"[^)\"]*Expanding requires list").matcher(output).find());
		Assert.assertTrue("Output does not contain expected value.", output.contains("foo (Declared.)"));
		Assert.assertTrue("Output does not contain expected value.", output.contains("bar (Referenced by foo)"));
		Assert.assertTrue("Output does not contain expected value.", output.contains("a/b (Referenced by bar)"));

	}

	private String runPass(RequireExpansionCompilerPass pass, String code) {
		Compiler compiler = new Compiler();
		Node root = compiler.parse(JSSourceFile.fromCode(moduleName, code));
		pass.process(null, root);
		CodeBuilder cb = new CodeBuilder();
		compiler.toSource(cb, 0, root);
		return cb.toString();
	}
}
