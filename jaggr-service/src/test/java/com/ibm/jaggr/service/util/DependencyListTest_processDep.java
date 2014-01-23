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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.Messages;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;

import com.google.common.io.Files;

import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;

public class DependencyListTest_processDep {

	URI tmpDir = null;
	Ref<IConfig> configRef;
	IAggregator mockAggregator;
	IDependencies mockDependencies;
	Features features;
	ModuleDeps explicitDeps;

	@Before
	public void setup() throws Exception {
		tmpDir = Files.createTempDir().toURI();
		configRef = new Ref<IConfig>(null);
		mockAggregator = TestUtils.createMockAggregator(configRef, null);
		mockDependencies = createMock(IDependencies.class);
		expect(mockDependencies.getLastModified()).andReturn(0L).anyTimes();
		expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		replay(mockAggregator, mockDependencies);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{}"));
		features = new Features();
		explicitDeps = new ModuleDeps();
	}



	@Test
	public void testProcessDep_simple() throws Exception{
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/test"})), explicitDeps.getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}

	@Test
	public void testProcessDep_withAlias() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test', 'bar/test']]}"));
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"bar/test"})), explicitDeps.getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}

	@Test
	public void testProcessDep_withConditionalizedAlias() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[[/^foo\\//, function(s){return (has('test')?'xxx':'yyy')+'/'}]]}"));
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"yyy/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
		depList.getDependentFeatures().clear();

		explicitDeps = new ModuleDeps();
		features.put("test", true);
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"xxx/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
		depList.getDependentFeatures().clear();

		explicitDeps = new ModuleDeps();
		features.put("test", false);
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"yyy/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withPlugin() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("bar/plugin!foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"bar/plugin!foo/test", "bar/plugin"})), explicitDeps.getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}

	@Test
	public void testProcessDep_withPluginNoModule() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("bar/plugin!", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"bar/plugin!", "bar/plugin"})), explicitDeps.getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}

	@Test
	public void testProcessDep_withAliasIntroducedPlugin() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test', 'foo/plugin!foo/test']]}"));
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/plugin", "foo/plugin!foo/test"})), explicitDeps.getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}

	@Test
	public void testProcessDep_withAliasedPlugin() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[[/^bar\\//, function(s){return (has('test')?'xxx':'yyy')+'/'}]]}"));
		depList.processDep("bar/plugin!foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"yyy/plugin!foo/test", "yyy/plugin"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withAliasedPluginAndModule() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[[/^foo\\//, function(s){return (has('testFoo')?'xxx':'yyy')+'/'}],[/^bar\\//, function(s){return (has('testBar')?'www':'zzz')+'/'}]]}"));
		depList.processDep("bar/plugin!foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"zzz/plugin!yyy/test", "zzz/plugin"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"testFoo", "testBar"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withAliasResolutionUsingDefinedFeatures1() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		features.put("testFoo", true);
		features.put("testBar", false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[[/^foo\\//, function(s){return (has('testFoo')?'xxx':'yyy')+'/'}],[/^bar\\//, function(s){return (has('testBar')?'www':'zzz')+'/'}]]}"));
		depList.processDep("bar/plugin!foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"zzz/plugin!xxx/test", "zzz/plugin"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"testFoo", "testBar"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withAliasResolutionUsingDefinedFeatures2() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		features.put("testFoo", true);
		features.put("testBar", true);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[[/^foo\\//, function(s){return (has('testFoo')?'xxx':'yyy')+'/'}],[/^bar\\//, function(s){return (has('testBar')?'www':'zzz')+'/'}]]}"));
		depList.processDep("bar/plugin!foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"www/plugin!xxx/test", "www/plugin"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"testFoo", "testBar"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withHasPluginUsingDefinedFeatures() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("dojo/has!test?foo/test:bar/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"dojo/has", "dojo/has!test?foo/test", "dojo/has!test?:bar/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withHasPluginTermsThatCancel() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("dojo/has!test?foo/test:foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"dojo/has", "foo/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withCompoundHasExpression() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("dojo/has!test?test1?foo1/test:bar1/test:test2:foo2/test:bar2/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(5, explicitDeps.size());
		assertEquals(new HashSet<String>(Arrays.asList(
				new String[]{
						"dojo/has",
						"dojo/has!test?test1?foo1/test",
						"dojo/has!test?test1?:bar1/test",
						"dojo/has!test?:test2?foo2/test",
						"dojo/has!test?:test2?:bar2/test"
				}
				)), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test", "test1", "test2"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withCompoundHasExpressionAndDefinedFeatures() throws Exception {
		// Defined features should have no impact on results.
		features.put("test", true);
		features.put("test1", false);
		features.put("test2", false);
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("dojo/has!test?test1?foo1/test:bar1/test:test2:foo2/test:bar2/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(5, explicitDeps.size());
		assertEquals(new HashSet<String>(Arrays.asList(
				new String[]{
						"dojo/has",
						"dojo/has!test?test1?foo1/test",
						"dojo/has!test?test1?:bar1/test",
						"dojo/has!test?:test2?foo2/test",
						"dojo/has!test?:test2?:bar2/test"
				}
				)), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test", "test1", "test2"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withAliasIntroducedHasPlugin() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test', 'dojo/has!test?foo/fooTest:foo/barTest']]}"));
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"dojo/has", "dojo/has!test?foo/fooTest", "dojo/has!test?:foo/barTest"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withHasPluginResultsWithPlugins() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("dojo/has!test?foo/plugin!foo/test:bar/plugin!bar/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(
				new String[]{
						"dojo/has",
						"dojo/has!test?foo/plugin",
						"dojo/has!test?foo/plugin!foo/test",
						"dojo/has!test?:bar/plugin",
						"dojo/has!test?:bar/plugin!bar/test"
				})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	@Test
	public void testProcessDep_withCompoundHasBranchingWithAliasResolution() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/bar', 'dojo/has!test1?foo/test:bar/test'],['bar/test','dojo/has!test2?foo/xxx:foo/yyy']]}"));
		depList.processDep("foo/bar", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(
				new String[]{
						"dojo/has",
						"dojo/has!test1?foo/test",
						"dojo/has!test1?:test2?foo/xxx",
						"dojo/has!test1?:test2?:foo/yyy"
				})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test1", "test2"})), depList.getDependentFeatures());
	}

	@Test
	public void recursionTests_withPluginNameLoop() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test', 'bar/test!foo/test'],['bar/test', 'foo/test!bar/test']]}"));
		depList.processDep("foo/test!foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"bar/test!foo/test", "foo/test!bar/test", "bar/test!foo/test!bar/test!foo/test"})), explicitDeps.getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}

	public void recursionTests_withHasBranchingLoop() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test', 'dojo/has!test?foo/test:bar/test']]}"));
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"dojo/has", "dojo/has!test?:bar/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	@Test
	public void recursionTests_withMultiLevelHasBranchingLoop() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test', 'dojo/has!test1?foo/xxx:foo/zzz'], ['foo/xxx', 'dojo/has!test2?bar/xxx:bar/yyy'], ['bar/xxx', 'dojo/has!test3?foo/test:bar/test']]}"));
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(
				new String[]{
						"dojo/has",
						"dojo/has!test1?:foo/zzz",
						"dojo/has!test1?test2?:bar/yyy",
						"dojo/has!test1?test2?test3?:bar/test"
				}
				)), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test1", "test2", "test3"})), depList.getDependentFeatures());
	}

	@Test
	public void loggingTests_simple()  throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, true);
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(1, explicitDeps.size());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/test"})), explicitDeps.getModuleIds());
		assertEquals(Messages.DependencyList_0, explicitDeps.get("foo/test").getComment().trim());
	}

	@Test
	public void loggingTests_withAliasing()  throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, true);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test', 'foo/bar']]}"));
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(1, explicitDeps.size());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/bar"})), explicitDeps.getModuleIds());
		assertEquals(Messages.DependencyList_0 + ", Aliased from: foo/test", explicitDeps.get("foo/bar").getComment());
	}
	@Test
	public void loggingTests_withImplicitPluginDependency()  throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, true);
		depList.processDep("foo/plugin!foo/bar", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(2, explicitDeps.size());
		assertEquals(Messages.DependencyList_1, explicitDeps.get("foo/plugin").getComment());
		assertEquals(Messages.DependencyList_0, explicitDeps.get("foo/plugin!foo/bar").getComment());
	}

	@Test
	public void loggingTests_withHasBranching()  throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, true);
		depList.processDep("dojo/has!test?foo/test:bar/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(3, explicitDeps.size());
		assertEquals(Messages.DependencyList_1, explicitDeps.get("dojo/has").getComment());
		String msg = MessageFormat.format(Messages.DependencyList_2, new Object[]{"dojo/has!test?foo/test:bar/test"});
		assertEquals(msg, explicitDeps.get("foo/test").getComment());
		assertEquals(msg, explicitDeps.get("bar/test").getComment());
	}

	@Test
	public void hasBranchingDisabledTests_noDefinedFeatures() throws Exception {
		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASPLUGINBRANCHING, true);
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		depList.processDep("dojo/has!test?foo/test:bar/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(2, explicitDeps.size());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"dojo/has", "dojo/has!test?foo/test:bar/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	@Test
	public void hasBranchingDisabledTests_withDefinedFeature() throws Exception {
		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASPLUGINBRANCHING, true);
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		features.put("test", true);
		depList.processDep("dojo/has!test?foo/test:bar/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(2, explicitDeps.size());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"dojo/has", "foo/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test"})), depList.getDependentFeatures());
	}

	public void hasBranchingDisabledTests_withDefinedFeatures() throws Exception {
		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASPLUGINBRANCHING, true);
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, true, false);
		features.put("test", true);
		features.put("test1", true);
		depList.processDep("dojo/has!test?test1?foo1/test:bar1/test:test2:foo2/test:bar2/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(2, explicitDeps.size());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"dojo/has", "foo1/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test", "test1"})), depList.getDependentFeatures());
	}

	@Test
	public void resolveAliasesDisabledTests() throws Exception {
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, false, false);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test', 'bar/test']]}"));
		explicitDeps = new ModuleDeps();
		depList.processDep("foo/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/test"})), explicitDeps.getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}

	@Test
	public void resolveAliasesDisabledAndHasBranchingDisabled_withDefinedFeatures() throws Exception {
		mockAggregator.getOptions().setOption(IOptions.DISABLE_HASPLUGINBRANCHING, true);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo1/test', 'bar/test']]}"));
		DependencyList depList = new DependencyList(new HashSet<String>(), mockAggregator, features, false, false);
		features.put("test", true);
		features.put("test1", true);
		depList.processDep("dojo/has!test?test1?foo1/test:bar1/test:test2:foo2/test:bar2/test", explicitDeps, null, new HashSet<String>(), null);
		assertEquals(2, explicitDeps.size());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"dojo/has", "foo1/test"})), explicitDeps.getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test", "test1"})), depList.getDependentFeatures());
	}
}
