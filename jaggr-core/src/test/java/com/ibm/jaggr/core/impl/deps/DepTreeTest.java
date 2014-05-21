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
package com.ibm.jaggr.core.impl.deps;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.impl.AggregatorExtension;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.test.TestUtils;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class DepTreeTest {

	File tmpdir = new File(System.getProperty("user.dir"));
	TestUtils.Ref<IConfig> configRef = new TestUtils.Ref<IConfig>(null);

	@Test
	public void testGetNonJSExtensions() throws Exception {
		final List<IAggregatorExtension> moduleBuilderExtensions = new ArrayList<IAggregatorExtension>();
		IAggregator mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		EasyMock.expect(mockAggregator.getExtensions(IModuleBuilderExtensionPoint.ID)).andAnswer(new IAnswer<Iterable<IAggregatorExtension>>() {
			@Override
			public Iterable<IAggregatorExtension> answer() throws Throwable {
				return moduleBuilderExtensions;
			}
		}).anyTimes();
		EasyMock.replay(mockAggregator);
		String config = "{}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), config);
		configRef.set(cfg);
		DepTree depTree = new DepTree();
		Set<String> exts = depTree.getNonJSExtensions(mockAggregator);
		Set<String> expected = new HashSet<String>(Arrays.asList(IDependencies.defaultNonJSExtensions));
		Assert.assertEquals(expected, exts);

		config = "{" + IDependencies.nonJSExtensionsCfgPropName + ":['foo', 'bar']}";
		cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), config);
		configRef.set(cfg);
		exts = depTree.getNonJSExtensions(mockAggregator);
		expected.addAll(Arrays.asList(new String[]{"foo", "bar"}));
		Assert.assertEquals(expected, exts);

		Properties props = new Properties();
		props.put(IModuleBuilderExtensionPoint.EXTENSION_ATTRIBUTE, "ext1");
		moduleBuilderExtensions.add(new AggregatorExtension(new DummyModuleBuilder(), props, IModuleBuilderExtensionPoint.ID, "1"));
		props = new Properties();
		props.put(IModuleBuilderExtensionPoint.EXTENSION_ATTRIBUTE, "ext2");
		moduleBuilderExtensions.add(new AggregatorExtension(new DummyModuleBuilder(), props, IModuleBuilderExtensionPoint.ID, "2"));
		exts = depTree.getNonJSExtensions(mockAggregator);
		expected.addAll(Arrays.asList(new String[]{"ext1", "ext2"}));
		Assert.assertEquals(expected, exts);
	}

	class DummyModuleBuilder implements IModuleBuilder {
		@Override public ModuleBuild build(String mid, IResource resource, HttpServletRequest request, List<ICacheKeyGenerator> keyGens) throws Exception {	return null; }
		@Override public List<ICacheKeyGenerator> getCacheKeyGenerators(IAggregator aggregator) { return null; }
		@Override public boolean handles(String mid, IResource resource) { return false; }
	}
}
