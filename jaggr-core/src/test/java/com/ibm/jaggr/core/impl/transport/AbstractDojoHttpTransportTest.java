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

package com.ibm.jaggr.core.impl.transport;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.ModuleInfo;
import com.ibm.jaggr.core.util.TypeUtil;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class AbstractDojoHttpTransportTest {

	File tmpDir = null;
	IAggregator mockAggregator;
	HttpServletRequest mockRequest;
	Map<String, String[]> requestParams;
	Ref<IConfig> configRef = new Ref<IConfig>(null);

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setup() throws Exception {
		tmpDir = new File(System.getProperty("user.dir"));
		mockAggregator = TestUtils.createMockAggregator(configRef, tmpDir);
		requestParams = new HashMap<String, String[]>();
		mockRequest = TestUtils.createMockRequest(mockAggregator, new HashMap<String, Object>(), requestParams, null, null);
		EasyMock.replay(mockAggregator, mockRequest);
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{}"));
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testModifyConfig() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		IAggregatorExtension mockExtension = EasyMock.createNiceMock(IAggregatorExtension.class);
		EasyMock.expect(mockExtension.getAttribute(IModuleBuilderExtensionPoint.EXTENSION_ATTRIBUTE)).andReturn("*").anyTimes();
		EasyMock.replay(mockExtension);
		final List<IAggregatorExtension> extensions = new LinkedList<IAggregatorExtension>();
		extensions.add(mockExtension);
		EasyMock.expect(mockAggregator.getExtensions(IModuleBuilderExtensionPoint.ID)).andAnswer(new IAnswer<Iterable<IAggregatorExtension>>() {
			@Override public Iterable<IAggregatorExtension> answer() throws Throwable {
				return extensions;
			}
		}).anyTimes();
		EasyMock.replay(mockAggregator);
		String config = "{packages:[{name:\"dojo\", location:\"namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo\"}]}";
		URI tmpDir = new File(System.getProperty("java.io.tmpdir")).toURI();
		IConfig cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		DojoHttpTransport transport = new TestUtils.TestDojoHttpTransport() {
			@Override public List<String[]> getClientConfigAliases() {
				return super.getClientConfigAliases();
			}
		};
		Context.enter();
		Scriptable rawConfig = (Scriptable)cfg.getRawConfig();
		IConfig modifiedConfig;
		transport.modifyConfig(mockAggregator, rawConfig);
		System.out.println(Context.toString(rawConfig));
		// verify config has the changes we expect
		modifiedConfig = new ConfigImpl(
				mockAggregator,
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig);
		Assert.assertEquals(3, modifiedConfig.getPaths().size());
		Assert.assertEquals("namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo/text",
				modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginAliasFullPath).getPrimary().toString());
		Assert.assertNull(modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginAliasFullPath).getOverride());
		Assert.assertEquals(transport.getComboUri().resolve(DojoHttpTransport.textPluginPath).toString(),
				modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginFullPath).getPrimary().toString());
		Assert.assertEquals(transport.getComboUri(), modifiedConfig.getPaths().get("combo").getPrimary());
		Assert.assertEquals(1, modifiedConfig.getAliases().size());
		Assert.assertEquals(DojoHttpTransport.dojoTextPluginAlias, modifiedConfig.getAliases().get(0).getPattern());
		Assert.assertEquals(DojoHttpTransport.dojoTextPluginAliasFullPath, modifiedConfig.getAliases().get(0).getReplacement());
		Assert.assertEquals(2, transport.getClientConfigAliases().size());
		String[] alias = transport.getClientConfigAliases().get(0);
		Assert.assertEquals(DojoHttpTransport.dojoTextPluginAlias, alias[0]);
		Assert.assertEquals(DojoHttpTransport.dojoTextPluginAliasFullPath, alias[1]);
		alias = transport.getClientConfigAliases().get(1);
		Assert.assertEquals(DojoHttpTransport.aggregatorTextPluginAlias, alias[0]);
		Assert.assertEquals("combo/text", alias[1]);

		// make sure overrides are handled properly
		config = "{packages:[{name:\"dojo\", location:[\"namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo\"]}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		rawConfig = (Scriptable)cfg.getRawConfig();
		transport.modifyConfig(mockAggregator, rawConfig);
		modifiedConfig = new ConfigImpl(
				mockAggregator,
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig);
		System.out.println(Context.toString(rawConfig));
		IConfig.Location loc = modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginAliasFullPath);
		Assert.assertEquals("namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo/text", loc.getPrimary().toString());
		Assert.assertNull(loc.getOverride());

		config = "{packages:[{name:\"dojo\", location:[\"namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo\", \"file:/c:/customizations/dojo\"]}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		rawConfig = (Scriptable)cfg.getRawConfig();
		transport.modifyConfig(mockAggregator, rawConfig);
		modifiedConfig = new ConfigImpl(
				mockAggregator,
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig);
		System.out.println(Context.toString(rawConfig));
		loc = modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginAliasFullPath);
		Assert.assertEquals("namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo/text", loc.getPrimary().toString());
		Assert.assertEquals("file:/c:/customizations/dojo/text", loc.getOverride().toString());

		// No dojo package in config
		transport.getClientConfigAliases().clear();
		cfg = new ConfigImpl(mockAggregator, tmpDir, "{}");
		rawConfig = (Scriptable)cfg.getRawConfig();
		transport.modifyConfig(mockAggregator, rawConfig);
		modifiedConfig = new ConfigImpl(
				mockAggregator,
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig);
		System.out.println(Context.toString(rawConfig));
		Assert.assertEquals(1, modifiedConfig.getPaths().size());
		Assert.assertEquals(0, modifiedConfig.getAliases().size());
		Assert.assertEquals(transport.getComboUri(), modifiedConfig.getPaths().get("combo").getPrimary());
		Assert.assertEquals(0, transport.getClientConfigAliases().size());

		// More that one text module builder.  Make sure last one is used
		extensions.add(mockExtension);
		mockExtension = EasyMock.createNiceMock(IAggregatorExtension.class);
		EasyMock.expect(mockExtension.getAttribute(IModuleBuilderExtensionPoint.EXTENSION_ATTRIBUTE)).andReturn("*").anyTimes();
		EasyMock.replay(mockExtension);
		extensions.add(mockExtension);
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		rawConfig = (Scriptable)cfg.getRawConfig();
		transport.modifyConfig(mockAggregator, rawConfig);
		modifiedConfig = new ConfigImpl(
				mockAggregator,
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig);
		System.out.println(Context.toString(rawConfig));
		Assert.assertEquals(3, modifiedConfig.getPaths().size());
		Assert.assertEquals(1, modifiedConfig.getAliases().size());
		Assert.assertEquals(transport.getComboUri(), modifiedConfig.getPaths().get("combo").getPrimary());
		Assert.assertEquals("namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo/text",
				modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginAliasFullPath).getPrimary().toString());
		Assert.assertEquals(transport.getComboUri().resolve(DojoHttpTransport.textPluginPath).toString(),
				modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginFullPath).getPrimary().toString());
		Assert.assertEquals(2, transport.getClientConfigAliases().size());
		alias = transport.getClientConfigAliases().get(0);
		Assert.assertEquals(DojoHttpTransport.dojoTextPluginAlias, alias[0]);
		Assert.assertEquals(DojoHttpTransport.dojoTextPluginAliasFullPath, alias[1]);
		alias = transport.getClientConfigAliases().get(1);
		Assert.assertEquals(DojoHttpTransport.aggregatorTextPluginAlias, alias[0]);
		Assert.assertEquals("combo/text", alias[1]);
		Context.exit();
	}

	@Test
	public void testDecorateRequest() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		transport.setAggregator(mockAggregator);

		transport.decorateRequest(mockRequest);
		Assert.assertTrue(TypeUtil.asBoolean(mockRequest.getAttribute(AbstractHttpTransport.NOTEXTADORN_REQATTRNAME)));
	}

	@Test
	public void testBeforeModule() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{}"));
		String result = transport.beforeModule(mockRequest, new ModuleInfo("module", true));
		Assert.assertEquals("", result);
		result = transport.beforeModule(mockRequest, new ModuleInfo("textModule", false));
		Assert.assertEquals("define(", result);
		mockRequest.setAttribute(IHttpTransport.SERVEREXPANDLAYERS_REQATTRNAME, true);
		result = transport.beforeModule(mockRequest, new ModuleInfo("module", true));
		Assert.assertEquals("", result);
		result = transport.beforeModule(mockRequest, new ModuleInfo("textModule", false));
		Assert.assertEquals("define(", result);
	}

	@Test
	public void testAfterModule() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{}"));
		String result = transport.afterModule(mockRequest, new ModuleInfo("module", true));
		Assert.assertEquals("", result);
		result = transport.afterModule(mockRequest, new ModuleInfo("textModule", false));
		Assert.assertEquals(");", result);
		mockRequest.setAttribute(IHttpTransport.SERVEREXPANDLAYERS_REQATTRNAME, true);
		result = transport.afterModule(mockRequest, new ModuleInfo("module", true));
		Assert.assertEquals("", result);
		result = transport.afterModule(mockRequest, new ModuleInfo("textModule", false));
		Assert.assertEquals(");", result);
	}

	@Test
	public void testBeforeLayerModule() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{textPluginDelegators:[\"dojo/text\"],jsPluginDelegators:[\"dojo/i18n\"]}"));
		String result = transport.beforeLayerModule(mockRequest, new ModuleInfo("module", true));
		Assert.assertEquals("\"module\":function(){", result);
		// Test output modified by text plugin delegators
		result = transport.beforeLayerModule(mockRequest, new ModuleInfo("dojo/text!module", false));
		Assert.assertEquals("\"url:module\":", result);
		// Test output modified by js plugin delegators
		result = transport.beforeLayerModule(mockRequest, new ModuleInfo("dojo/i18n!module", true));
		Assert.assertEquals("\"module\":function(){", result);
	}

	@Test
	public void testAfterLayerModule() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{textPluginDelegators:[\"dojo/text\"]}"));
		String result = transport.afterLayerModule(mockRequest, new ModuleInfo("module", true));
		Assert.assertEquals("}", result);
		result = transport.afterLayerModule(mockRequest, new ModuleInfo("plugin!module", true));
		Assert.assertEquals("}", result);
		// Test output modified by text plugin delegators
		result = transport.afterLayerModule(mockRequest, new ModuleInfo("dojo/text!module", false));
		Assert.assertEquals("", result);
	}

	@Test
	public void testBeginLayerModules() {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		String result = transport.beginLayerModules(mockRequest, null);
		Assert.assertEquals("require({cache:{", result);
	}

	@Test
	public void testEndLayerModules() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		Object arg = new Object();
		String result = transport.endLayerModules(mockRequest, arg);
		Assert.assertEquals("}});require({cache:{}});", result);
		Assert.assertSame(arg, mockRequest.getAttribute(DojoHttpTransport.ADD_REQUIRE_DEPS_REQATTRNAME));
	}

	@Test
	public void testEndResponse() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		String result = transport.endResponse(mockRequest, null);
		Assert.assertEquals("", result);

		// Add required modules
		Set<String> required = new LinkedHashSet<String>();
		mockRequest.setAttribute(DojoHttpTransport.ADD_REQUIRE_DEPS_REQATTRNAME, required);
		result = transport.endResponse(mockRequest, null);
		Assert.assertEquals("", result);

		required.add("module1");
		required.add("module2");
		result = transport.endResponse(mockRequest, null);
		Assert.assertEquals("require([\"module1\",\"module2\"]);", result);
	}

	class TestDojoHttpTransport extends DojoHttpTransport {
		private IAggregator aggregator = null;
		public void setAggregator(IAggregator aggregator) { this.aggregator = aggregator; }
		@Override protected URI getComboUri() { return null; }
		@Override protected String getTransportId() {return null; }
		@Override protected String getResourcePathId() { return null; }
		@Override protected IAggregator getAggregator() { return aggregator; }
	}
}
