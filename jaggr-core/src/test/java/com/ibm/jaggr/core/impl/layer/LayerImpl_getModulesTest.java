/*
 * (C) Copyright IBM Corp. 2012, 2016
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
package com.ibm.jaggr.core.impl.layer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.impl.layer.ModuleList.ModuleListEntry;
import com.ibm.jaggr.core.impl.transport.AbstractHttpTransport;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.test.MockRequestedModuleNames;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.Features;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

@RunWith(PowerMockRunner.class)
@PrepareForTest({LayerImpl.class})
public class LayerImpl_getModulesTest {

	private IAggregator mockAggregator;
	private HttpServletRequest mockRequest;
	private Map<String, Object> requestAttributes;
	private Map<String, String[]> requestParams;
	private Features features;

	@Before
	public void setUp() throws Exception {
		requestAttributes = new HashMap<String, Object>();
		requestParams = new HashMap<String, String[]>();
		features = new Features();
		mockAggregator = TestUtils.createMockAggregator();
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		EasyMock.replay(mockAggregator, mockRequest);
		requestAttributes.put(IHttpTransport.FEATUREMAP_REQATTRNAME, features);
	}

	@Test(expected=BadRequestException.class)
	public void testGetModules_nullRequestedModules() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		layer.getModules(mockRequest);
	}

	@Test(expected=BadRequestException.class)
	public void testGetModules_emptyRequestedModules() throws Exception {
		// Empty requested modules
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		layer.getModules(mockRequest);
	}

	@Test
	public void testGetModules_moduleListOnly() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		names.setModules(Arrays.asList(new String[]{"module/a", "module/b"}));
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		ModuleList modules = layer.getModules(mockRequest);
		assertTrue(modules.getDependentFeatures().isEmpty());
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		List<IModule> expected = makeModuleList("module/a", "module/b");
		assertEquals(expected, modules.getModules());
		for (ModuleListEntry entry : modules) {
			assertEquals(ModuleSpecifier.MODULES, entry.getSource());
		}
	}

	@Test
	public void testGetModules_moduleListWithScript() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		names.setModules(Arrays.asList(new String[]{"module/a", "module/b"}));
		names.setScripts(Arrays.asList(new String[]{"script/a"}));
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		ModuleList modules = layer.getModules(mockRequest);
		assertTrue(modules.getDependentFeatures().isEmpty());
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		List<IModule> expected = makeModuleList("module/a", "module/b","script/a");
		assertEquals(expected, modules.getModules());
		for (ModuleListEntry entry : modules) {
			if ("script/a".equals(entry.getModule().getModuleName())) {
				assertEquals(ModuleSpecifier.SCRIPTS, entry.getSource());
			} else {
				assertEquals(ModuleSpecifier.MODULES, entry.getSource());
			}
		}
	}

	@Test
	public void testGetModules_moduleList_with_serverExpandLayers() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		names.setModules(Arrays.asList(new String[]{"module/a"}));
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		mockRequest.setAttribute(IHttpTransport.SERVEREXPANDLAYERS_REQATTRNAME, true);
		ModuleDeps depsExplicitDeps = new ModuleDeps();
		ModuleDeps depsExpandedDeps = new ModuleDeps();
		depsExplicitDeps.add("module/a", new ModuleDepInfo());
		depsExpandedDeps.add("dep/moduledep", new ModuleDepInfo());
		DependencyList depsDependencies = new DependencyList(depsExplicitDeps, depsExpandedDeps, new HashSet<String>(Arrays.asList("feature")));
		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_MODULEDEPS),
				EasyMock.eq(Arrays.asList(new String[]{"module/a"})),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(depsDependencies).once();
		PowerMock.replay(DependencyList.class);
		ModuleList modules = layer.getModules(mockRequest);
		PowerMock.verify(DependencyList.class);
		assertEquals(new HashSet<String>(Arrays.asList("feature")), modules.getDependentFeatures());
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		List<IModule> expected = makeModuleList("module/a", "dep/moduledep");
		assertEquals(expected, modules.getModules());
		for (ModuleListEntry entry : modules) {
			String moduleName = entry.getModule().getModuleName();
			if ("module/a".equals(moduleName)) {
				assertEquals(ModuleSpecifier.MODULES, entry.getSource());
			} else if ("dep/moduledep".equals(moduleName)) {
				assertEquals(ModuleSpecifier.LAYER, entry.getSource());
			} else {
				fail("unexpected entry " + moduleName);
			}
		}
	}

	@Test
	public void testGetModules_deps() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		names.setDeps(Arrays.asList(new String[]{"dep/a"}));
		ModuleDeps depsExplicitDeps = new ModuleDeps();
		ModuleDeps depsExpandedDeps = new ModuleDeps();
		depsExplicitDeps.add("dep/a", new ModuleDepInfo());
		depsExpandedDeps.add("dep/adep", new ModuleDepInfo());
		DependencyList depsDependencies = new DependencyList(depsExplicitDeps, depsExpandedDeps, new HashSet<String>(Arrays.asList("feature")));
		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQDEPS),
				EasyMock.eq(Arrays.asList(new String[]{"dep/a"})),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(depsDependencies).once();
		PowerMock.replay(DependencyList.class);

		ModuleList modules = layer.getModules(mockRequest);
		PowerMock.verify(DependencyList.class);
		List<IModule> expected = makeModuleList("dep/a", "dep/adep");
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertNull(requestAttributes.get(LayerImpl.EXPANDEDDEPS_PROPNAME));
		assertEquals(expected, modules.getModules());
		assertEquals(new HashSet<String>(Arrays.asList("dep/a")), modules.getRequiredModules());
		assertEquals(new HashSet<String>(Arrays.asList("feature")), modules.getDependentFeatures());
		assertEquals(ModuleSpecifier.LAYER, modules.get(0).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(1).getSource());
	}

	@Test
	public void testGetModules_depsAndPreloads() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		names.setDeps(Arrays.asList("dep/a"));
		names.setPreloads(Arrays.asList("preload/a"));

		ModuleDeps depsExplicitDeps = new ModuleDeps();
		ModuleDeps depsExpandedDeps = new ModuleDeps();
		depsExplicitDeps.add("dep/a", new ModuleDepInfo());
		depsExpandedDeps.add("dep/adep", new ModuleDepInfo());
		ModuleDeps preloadsExplicitDeps = new ModuleDeps();
		ModuleDeps preloadsExpandedDeps = new ModuleDeps();
		preloadsExplicitDeps.add("preload/a", new ModuleDepInfo());
		preloadsExpandedDeps.add("preload/adep", new ModuleDepInfo());
		DependencyList depsDependencies = new DependencyList(depsExplicitDeps, depsExpandedDeps, new HashSet<String>(Arrays.asList("feature1")));
		DependencyList preloadsDependencies = new DependencyList(preloadsExplicitDeps, preloadsExpandedDeps, new HashSet<String>(Arrays.asList("feature2")));

		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQDEPS),
				EasyMock.eq(Arrays.asList("dep/a")),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(depsDependencies).once();

		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQPRELOADS),
				EasyMock.eq(Arrays.asList("preload/a")),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(preloadsDependencies).once();
		PowerMock.replay(DependencyList.class);

		ModuleList modules = layer.getModules(mockRequest);
		PowerMock.verify(DependencyList.class);
		List<IModule> expected = makeModuleList("dep/a", "dep/adep", "preload/a", "preload/adep");
		assertEquals(expected, modules.getModules());
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertNull(requestAttributes.get(LayerImpl.EXPANDEDDEPS_PROPNAME));
		assertEquals(new HashSet<String>(Arrays.asList("dep/a")), modules.getRequiredModules());
		assertEquals(new HashSet<String>(Arrays.asList("feature1", "feature2")), modules.getDependentFeatures());
		assertEquals(ModuleSpecifier.LAYER, modules.get(0).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(1).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(2).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(3).getSource());
	}

	@Test
	public void testGetModules_pluginDelegators() throws Exception {

		Ref<IConfig> configRef = new Ref<IConfig>(null);
		File tmpdir = new File(System.getProperty("user.dir"));
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpdir);
		EasyMock.replay(mockAggregator);
		configRef.set(new ConfigImpl(mockAggregator, tmpdir.toURI(), "{textPluginDelegators:['js/css', 'dojo/text'], jsPluginDelegators:['dojo/i18n']}", true));
		mockRequest.setAttribute(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);

		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		names.setDeps(Arrays.asList("dep/a"));
		names.setPreloads(Arrays.asList("preload/a"));

		ModuleDeps depsExplicitDeps = new ModuleDeps();
		ModuleDeps depsExpandedDeps = new ModuleDeps();
		depsExplicitDeps.add("dep/a", new ModuleDepInfo());
		depsExpandedDeps.add("dojo/text!dep/adep", new ModuleDepInfo());
		depsExpandedDeps.add("foo!dep/bdep", new ModuleDepInfo());
		ModuleDeps preloadsExplicitDeps = new ModuleDeps();
		ModuleDeps preloadsExpandedDeps = new ModuleDeps();
		preloadsExplicitDeps.add("preload/a", new ModuleDepInfo());
		preloadsExpandedDeps.add("js/css!preload/adep", new ModuleDepInfo());
		preloadsExpandedDeps.add("dojo/i18n!preload/nls/adep", new ModuleDepInfo());
		DependencyList depsDependencies = new DependencyList(depsExplicitDeps, depsExpandedDeps, new HashSet<String>(Arrays.asList("feature1")));
		DependencyList preloadsDependencies = new DependencyList(preloadsExplicitDeps, preloadsExpandedDeps, new HashSet<String>(Arrays.asList("feature2")));

		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQDEPS),
				EasyMock.eq(Arrays.asList("dep/a")),
				EasyMock.isA(IAggregator.class),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(depsDependencies).once();

		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQPRELOADS),
				EasyMock.eq(Arrays.asList("preload/a")),
				EasyMock.isA(IAggregator.class),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(preloadsDependencies).once();
		PowerMock.replay(DependencyList.class);

		ModuleList modules = layer.getModules(mockRequest);
		PowerMock.verify(DependencyList.class);
		List<IModule> expected = makeModuleList("dep/a", "combo/text!dep/adep", "preload/a", "combo/text!preload/adep", "preload/nls/adep");
		assertEquals(expected, modules.getModules());
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertNull(requestAttributes.get(LayerImpl.EXPANDEDDEPS_PROPNAME));
		assertEquals(new HashSet<String>(Arrays.asList("dep/a")), modules.getRequiredModules());
		assertEquals(new HashSet<String>(Arrays.asList("feature1", "feature2")), modules.getDependentFeatures());
		assertEquals(ModuleSpecifier.LAYER, modules.get(0).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(1).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(2).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(3).getSource());
	}

	@Test
	public void testGetModules_depsAndPreloadsWithReqExpLogging() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		mockRequest.setAttribute(AbstractHttpTransport.ASSERTNONLS_REQATTRNAME, true);
		names.setDeps(Arrays.asList("dep/a"));
		names.setPreloads(Arrays.asList("preload/a"));

		ModuleDeps depsExplicitDeps = new ModuleDeps();
		ModuleDeps depsExpandedDeps = new ModuleDeps();
		depsExplicitDeps.add("dep/a", new ModuleDepInfo());
		depsExpandedDeps.add("dep/adep", new ModuleDepInfo());
		ModuleDeps preloadsExplicitDeps = new ModuleDeps();
		ModuleDeps preloadsExpandedDeps = new ModuleDeps();
		preloadsExplicitDeps.add("preload/a", new ModuleDepInfo());
		preloadsExpandedDeps.add("preload/adep", new ModuleDepInfo());
		DependencyList depsDependencies = new DependencyList(depsExplicitDeps, depsExpandedDeps, new HashSet<String>(Arrays.asList("feature1")));
		DependencyList preloadsDependencies = new DependencyList(preloadsExplicitDeps, preloadsExpandedDeps, new HashSet<String>(Arrays.asList("feature2")));

		// Save original constructor so we can reused it in the mock
		final Constructor<DependencyList> ctor = DependencyList.class.getConstructor(new Class[]{ModuleDeps.class, ModuleDeps.class, Set.class});

		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQDEPS),
				EasyMock.eq(Arrays.asList("dep/a")),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(true),
				EasyMock.eq(false))
			.andReturn(depsDependencies).once();

		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQPRELOADS),
				EasyMock.eq(Arrays.asList("preload/a")),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(true),
				EasyMock.eq(false))
			.andReturn(preloadsDependencies).once();

		// Mock this version of the constructor to use the original ctor
		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.isA(ModuleDeps.class),
				EasyMock.isA(ModuleDeps.class),
				EasyMock.isA(Set.class)).andAnswer(new IAnswer<DependencyList>() {
					@Override
					public DependencyList answer() throws Throwable {
						return (DependencyList)ctor.newInstance(EasyMock.getCurrentArguments());
					}
				}).once();

		PowerMock.replay(DependencyList.class);
		mockRequest.setAttribute(IHttpTransport.EXPANDREQUIRELISTS_REQATTRNAME, true);
		mockRequest.setAttribute(IHttpTransport.DEPENDENCYEXPANSIONLOGGING_REQATTRNAME, true);
		mockAggregator.getOptions().setOption("developmentMode", "true");

		ModuleList modules = layer.getModules(mockRequest);
		PowerMock.verify(DependencyList.class);
		List<IModule> expected = makeModuleList("dep/a", "dep/adep", "preload/a", "preload/adep");
		assertEquals(expected, modules.getModules());
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertEquals(new HashSet<String>(Arrays.asList("dep/a")), modules.getRequiredModules());
		assertEquals(new HashSet<String>(Arrays.asList("feature1", "feature2")), modules.getDependentFeatures());
		assertEquals(ModuleSpecifier.LAYER, modules.get(0).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(1).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(2).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(3).getSource());

		DependencyList bootLayerDeps = (DependencyList)requestAttributes.get(LayerImpl.EXPANDEDDEPS_PROPNAME);
		ModuleDeps expectedExplicitDeps = new ModuleDeps();
		expectedExplicitDeps.add("dep/a", new ModuleDepInfo());
		expectedExplicitDeps.add("preload/a", new ModuleDepInfo());
		assertEquals(expectedExplicitDeps, bootLayerDeps.getExplicitDeps());
		ModuleDeps expectedExpandedDeps = new ModuleDeps();
		expectedExpandedDeps.add("dep/adep", new ModuleDepInfo());
		expectedExpandedDeps.add("preload/adep", new ModuleDepInfo());
		assertEquals(expectedExpandedDeps, bootLayerDeps.getExpandedDeps());
		assertEquals(new HashSet<String>(Arrays.asList("feature1", "feature2")), bootLayerDeps.getDependentFeatures());

	}

	/*
	 * Verify that an exception is thrown if any of the modules that would be returned is
	 * an nls resource
	 */
	@Test (expected=BadRequestException.class)
	public void testGetModules_depsAndPreloadsWithReqExpLogging_failNoNLS() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		mockRequest.setAttribute(AbstractHttpTransport.ASSERTNONLS_REQATTRNAME, true);
		names.setDeps(Arrays.asList("dep/a"));
		names.setPreloads(Arrays.asList("preload/a"));

		ModuleDeps depsExplicitDeps = new ModuleDeps();
		ModuleDeps depsExpandedDeps = new ModuleDeps();
		depsExplicitDeps.add("dep/a", new ModuleDepInfo());
		depsExpandedDeps.add("dep/adep", new ModuleDepInfo());
		ModuleDeps preloadsExplicitDeps = new ModuleDeps();
		ModuleDeps preloadsExpandedDeps = new ModuleDeps();
		preloadsExplicitDeps.add("preload/a", new ModuleDepInfo());
		preloadsExpandedDeps.add("preload/nls/a", new ModuleDepInfo());
		DependencyList depsDependencies = new DependencyList(depsExplicitDeps, depsExpandedDeps, new HashSet<String>(Arrays.asList("feature1")));
		DependencyList preloadsDependencies = new DependencyList(preloadsExplicitDeps, preloadsExpandedDeps, new HashSet<String>(Arrays.asList("feature2")));

		// Save original constructor so we can reused it in the mock
		final Constructor<DependencyList> ctor = DependencyList.class.getConstructor(new Class[]{ModuleDeps.class, ModuleDeps.class, Set.class});

		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQDEPS),
				EasyMock.eq(Arrays.asList("dep/a")),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(depsDependencies).once();

		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQPRELOADS),
				EasyMock.eq(Arrays.asList("preload/a")),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(preloadsDependencies).once();

		// Mock this version of the constructor to use the original ctor
		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.isA(ModuleDeps.class),
				EasyMock.isA(ModuleDeps.class),
				EasyMock.isA(Set.class)).andAnswer(new IAnswer<DependencyList>() {
					@Override
					public DependencyList answer() throws Throwable {
						return (DependencyList)ctor.newInstance(EasyMock.getCurrentArguments());
					}
				}).once();

		PowerMock.replay(DependencyList.class);
		layer.getModules(mockRequest);
	}

	@Test
	public void testGetModules_includeUndefinedFeatureDeps() throws Exception {
		LayerImpl layer = new TestLayerImpl();
		MockRequestedModuleNames names = new MockRequestedModuleNames();
		mockRequest.setAttribute(AbstractHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME, names);
		names.setDeps(Arrays.asList(new String[]{"dep/a"}));
		ModuleDeps depsExplicitDeps = new ModuleDeps();
		ModuleDeps depsExpandedDeps = new ModuleDeps();
		depsExplicitDeps.add("dep/a", new ModuleDepInfo());
		depsExpandedDeps.add("dep/adep", new ModuleDepInfo());
		depsExpandedDeps.add("foo", new ModuleDepInfo("has", new BooleanTerm("feature"), null));
		depsExpandedDeps.add("bar", new ModuleDepInfo("has", new BooleanTerm("!feature"), null));
		DependencyList depsDependencies = new DependencyList(depsExplicitDeps, depsExpandedDeps, new HashSet<String>(Arrays.asList("feature")));
		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQDEPS),
				EasyMock.eq(Arrays.asList(new String[]{"dep/a"})),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(depsDependencies).once();
		PowerMock.replay(DependencyList.class);

		ModuleList modules = layer.getModules(mockRequest);
		PowerMock.verify(DependencyList.class);
		List<IModule> expected = makeModuleList("dep/a", "dep/adep");
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertNull(requestAttributes.get(LayerImpl.EXPANDEDDEPS_PROPNAME));
		assertEquals(expected, modules.getModules());
		assertEquals(new HashSet<String>(Arrays.asList("dep/a")), modules.getRequiredModules());
		assertEquals(new HashSet<String>(Arrays.asList("feature")), modules.getDependentFeatures());
		assertEquals(ModuleSpecifier.LAYER, modules.get(0).getSource());
		assertEquals(ModuleSpecifier.LAYER, modules.get(1).getSource());

		PowerMock.reset(DependencyList.class);
		PowerMock.expectNew(
				DependencyList.class,
				EasyMock.eq(LayerImpl.DEPSOURCE_REQDEPS),
				EasyMock.eq(Arrays.asList(new String[]{"dep/a"})),
				EasyMock.eq(mockAggregator),
				EasyMock.eq(features),
				EasyMock.eq(true),
				EasyMock.eq(false),
				EasyMock.eq(false))
			.andReturn(depsDependencies).once();

		PowerMock.replay(DependencyList.class);
		mockRequest.removeAttribute(LayerImpl.MODULE_FILES_PROPNAME);
		mockRequest.setAttribute(IHttpTransport.INCLUDEUNDEFINEDFEATUREDEPS_REQATTRNAME, true);
		modules = layer.getModules(mockRequest);
		PowerMock.verify(DependencyList.class);
		expected = makeModuleList("dep/a", "dep/adep", "foo", "bar");
		assertSame(modules, requestAttributes.get(LayerImpl.MODULE_FILES_PROPNAME));
		assertNull(requestAttributes.get(LayerImpl.EXPANDEDDEPS_PROPNAME));
		assertEquals(expected, modules.getModules());

	}


	@SuppressWarnings("serial")
	public class TestLayerImpl extends LayerImpl {

		public TestLayerImpl() {
			super("test", 1);
		}

		@Override
		protected IModule newModule(HttpServletRequest request, String mid) {
			return new TestModule(mid);
		}
	}

	@SuppressWarnings("serial")
	public class TestModule implements IModule {

		private final String moduleId;

		public TestModule(String moduleId) {
			this.moduleId = moduleId;
		}

		@Override
		public Future<ModuleBuildReader> getBuild(HttpServletRequest request) throws IOException {
			return null;
		}

		@Override
		public void clearCached(ICacheManager mgr) {}

		@Override
		public String getModuleId() {
			return moduleId;
		}

		@Override
		public String getModuleName() {
			int idx = moduleId.indexOf("!");
			return idx == -1 ? moduleId : moduleId.substring(idx+1);
		}

		@Override
		public String getPluginName() {
			int idx = moduleId.indexOf("!");
			return idx == -1 ? moduleId : moduleId.substring(0, idx);
		}

		@Override
		public URI getURI() {
			return URI.create("file:///" + moduleId);
		}

		@Override
		public IResource getResource(IAggregator aggregator) {
			return aggregator.newResource(getURI());
		}

		@Override
		public boolean equals(Object other) {
			if (other == null) return false;
			if (other instanceof TestModule) {
				return moduleId.equals(((TestModule)other).moduleId);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return moduleId.hashCode();
		}

		@Override
		public String toString() {
			return moduleId;
		}

	}

	public List<IModule> makeModuleList(String... names) {
		ArrayList<IModule> result = new ArrayList<IModule>(names.length);
		for (String name : names) {
			result.add(new TestModule(name));
		}
		return result;
	}

}
