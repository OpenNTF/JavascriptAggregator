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
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

import com.google.common.io.Files;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.deps.ModuleDepInfo;
import com.ibm.jaggr.service.deps.ModuleDeps;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;

public class DependencyListTest {
	
	URI tmpDir = null;
	IAggregator mockAggregator;
	IDependencies mockDependencies;
	Ref<IConfig> configRef;
	Map<String, String[]> moduleDeps;
	Set<String> dependentFeatures;
	Features features;
	
	@Before 
	public void setup() throws Exception {
		tmpDir = Files.createTempDir().toURI();
		configRef = new Ref<IConfig>(null);
		moduleDeps  = new HashMap<String, String[]>();
		mockAggregator = TestUtils.createMockAggregator(configRef, null);
		mockDependencies = createMock(IDependencies.class);
		dependentFeatures = new HashSet<String>();
		features = new Features();
		expect(mockAggregator.getDependencies()).andReturn(mockDependencies).anyTimes();
		expect(mockDependencies.getLastModified()).andReturn(0L).anyTimes();
		expect(mockDependencies.getDelcaredDependencies(isA(String.class))).andAnswer(new IAnswer<String[]>() {
			@Override public String[] answer() throws Throwable {
				return moduleDeps.get((String)getCurrentArguments()[0]);
			}
		}).anyTimes();
		replay(mockAggregator, mockDependencies);
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{}"));
	}		

	@Test
	public void testNoExpandedDeps() throws Exception {
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{}"));
		
		Set<String> names = new HashSet<String>(Arrays.asList(new String[]{"foo/test", "bar/test"}));
		DependencyList depList = new DependencyList(names, mockAggregator, features, true, false) {
			@Override
			void processDep(String name, ModuleDeps deps, ModuleDepInfo callerInfo, Set<String> recursionCheck, String dependee) {
				deps.add(name, new ModuleDepInfo(null, null, null));
			}
		};
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/test", "bar/test"})),depList.getExplicitDeps().getModuleIds());
		assertTrue(depList.getExpandedDeps().isEmpty());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}
	
	@Test 
	public void testExpandedDeps() throws Exception {
		Set<String> names = new HashSet<String>(Arrays.asList(new String[]{"foo/test", "bar/test"}));
		moduleDeps.put("foo/test", new String[]{"foo/dep1", "foo/dep2"});
		moduleDeps.put("bar/test", new String[]{"bar/dep"});
		DependencyList depList = new DependencyList(names, mockAggregator, features, true, false);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/test", "bar/test"})),depList.getExplicitDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"foo/dep1", "foo/dep2", "bar/dep"})),depList.getExpandedDeps().getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}
	
	@Test
	public void testExpandedDepsWithNameReplacement() throws Exception {
		configRef.set(new ConfigImpl(mockAggregator, tmpDir, "{aliases:[['foo/test','bar/test']]}"));
		Set<String> names = new HashSet<String>(Arrays.asList(new String[]{"foo/test"}));
		moduleDeps.put("bar/test", new String[]{"bar/dep1", "bar/dep2"});
		DependencyList depList = new DependencyList(names, mockAggregator, features, true, false);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"bar/test"})),depList.getExplicitDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"bar/dep1", "bar/dep2"})),depList.getExpandedDeps().getModuleIds());
		assertTrue(depList.getDependentFeatures().isEmpty());
	}

	@Test
	public void testExpandedDepsWithANDedHasConditioning() throws Exception {
		Set<String> names = new HashSet<String>(Arrays.asList(new String[]{"has!test?foo/test"}));
		moduleDeps.put("foo/test", new String[]{"has!zzz?foo/dep1", "foo/dep2"});
		DependencyList depList = new DependencyList(names, mockAggregator, features, true, false);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"has", "has!test?foo/test"})),depList.getExplicitDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"has!test?has", "has!test?zzz?foo/dep1", "has!test?foo/dep2"})),depList.getExpandedDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test", "zzz"})), depList.getDependentFeatures());
	}

	@Test
	public void testExpandedDepsWithORedHasConditioning() throws Exception {
		Set<String> names = new HashSet<String>(Arrays.asList(new String[]{"has!test?foo/test"}));
		moduleDeps.put("foo/test", new String[]{"has!zzz?foo/dep", "has!yyy?foo/dep"});
		DependencyList depList = new DependencyList(names, mockAggregator, features, true, false);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"has", "has!test?foo/test"})),depList.getExplicitDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"has!test?has", "has!test?zzz?foo/dep", "has!test?yyy?foo/dep"})),depList.getExpandedDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test", "yyy", "zzz"})), depList.getDependentFeatures());
	}
	
	@Test
	public void testResolveExcplicitDeps() throws Exception {
		Set<String> names = new HashSet<String>(Arrays.asList(new String[]{"has!test?foo/test"}));
		moduleDeps.put("foo/test", new String[]{"has!zzz?foo/dep", "has!yyy?foo/dep"});
		features.put("test", true);
		DependencyList depList = new DependencyList(names, mockAggregator, features, true, false);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"has", "foo/test"})),depList.getExplicitDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"has", "has!zzz?foo/dep", "has!yyy?foo/dep"})),depList.getExpandedDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test", "yyy", "zzz"})), depList.getDependentFeatures());
	}

	@Test
	public void testResolveExpandedDeps() throws Exception {
		Set<String> names = new HashSet<String>(Arrays.asList(new String[]{"has!test?foo/test"}));
		moduleDeps.put("foo/test", new String[]{"has!zzz?foo/dep1", "has!yyy?foo/dep2"});
		features.put("yyy", true);
		features.put("zzz", false);
		DependencyList depList = new DependencyList(names, mockAggregator, features, true, false);
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"has", "has!test?foo/test"})),depList.getExplicitDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"has!test?has", "has!test?foo/dep2"})),depList.getExpandedDeps().getModuleIds());
		assertEquals(new HashSet<String>(Arrays.asList(new String[]{"test", "yyy", "zzz"})), depList.getDependentFeatures());
	}
}
