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

import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.JavaScriptBuildRenderer;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.JavaScriptModuleBuilder;
import com.ibm.jaggr.core.test.TestUtils;

import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class JavaScriptBuildRendererTest {
	static final String content = "define([],function() {require(\"foo\",\"" +
			String.format(JavaScriptBuildRenderer.REQUIRE_EXPANSION_PLACEHOLDER_FMT, 0) +
			"\");require(\"bar\", \"" +
			String.format(JavaScriptBuildRenderer.REQUIRE_EXPANSION_PLACEHOLDER_FMT, 1) +
			"\")});";

	@Test
	public void testRenderBuild() throws Exception {
		ModuleDeps deps1 = new ModuleDeps();
		ModuleDeps deps2 = new ModuleDeps();
		deps1.add("foodep1", new ModuleDepInfo());
		deps1.add("foodep2", new ModuleDepInfo());
		deps2.add("bardep", new ModuleDepInfo());
		List<ModuleDeps> depsList = Arrays.asList(new ModuleDeps[]{deps1, deps2});
		JavaScriptBuildRenderer compiled = new JavaScriptBuildRenderer("test", content, depsList, false);

		// validate the rendered output
		HttpServletRequest mockRequest = TestUtils.createMockRequest(TestUtils.createMockAggregator());
		EasyMock.replay(mockRequest);
		String result = compiled.renderBuild(mockRequest, Collections.<String>emptySet());
		System.out.println(result);
		Assert.assertEquals("define([],function() {require(\"foo\",\"foodep1\",\"foodep2\");require(\"bar\",\"bardep\")});", result);

		ModuleDeps enclosingDeps = new ModuleDeps();
		enclosingDeps.add("foodep2", new ModuleDepInfo());
		enclosingDeps.add("bardep", new ModuleDepInfo());
		mockRequest.setAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES, enclosingDeps);
		result = compiled.renderBuild(mockRequest, Collections.<String>emptySet());
		System.out.println(result);
		Assert.assertEquals("define([],function() {require(\"foo\",\"foodep1\");require(\"bar\")});", result);
	}

	@Test
	public void testRenderBuild_withDetails() throws Exception {
		String contentWithComments = content +
				"console.log(\"deps1=" +
				String.format(JavaScriptBuildRenderer.REQUIRE_EXPANSION_LOG_PLACEHOLDER_FMT, 0) +
				"\");console.log(\"deps2=" +
				String.format(JavaScriptBuildRenderer.REQUIRE_EXPANSION_LOG_PLACEHOLDER_FMT, 1) +
				"\");";
		ModuleDeps deps1 = new ModuleDeps();
		ModuleDeps deps2 = new ModuleDeps();
		deps1.add("foodep1", new ModuleDepInfo());
		deps1.add("foodep2", new ModuleDepInfo());
		deps2.add("bardep", new ModuleDepInfo());
		List<ModuleDeps> depsList = Arrays.asList(new ModuleDeps[]{deps1, deps2, deps1, deps2});
		JavaScriptBuildRenderer compiled = new JavaScriptBuildRenderer("test", contentWithComments, depsList, true);

		// validate the rendered output
		HttpServletRequest mockRequest = TestUtils.createMockRequest(TestUtils.createMockAggregator());
		EasyMock.replay(mockRequest);
		String result = compiled.renderBuild(mockRequest, Collections.<String>emptySet());
		System.out.println(result);
		Assert.assertEquals(
				"define([],function() {require(\"foo\",\"foodep1\",\"foodep2\");require(\"bar\",\"bardep\")});console.log(\"deps1=foodep1, foodep2\");console.log(\"deps2=bardep\");",
				result);

		ModuleDeps enclosingDeps = new ModuleDeps();
		enclosingDeps.add("foodep2", new ModuleDepInfo());
		enclosingDeps.add("bardep", new ModuleDepInfo());
		mockRequest.setAttribute(JavaScriptModuleBuilder.EXPANDED_DEPENDENCIES, enclosingDeps);
		result = compiled.renderBuild(mockRequest, Collections.<String>emptySet());
		Assert.assertEquals(
				"define([],function() {require(\"foo\",\"foodep1\");require(\"bar\")});console.log(\"deps1=foodep1\");console.log(\"deps2=\");",
				result);
		System.out.println(result);
	}
}
