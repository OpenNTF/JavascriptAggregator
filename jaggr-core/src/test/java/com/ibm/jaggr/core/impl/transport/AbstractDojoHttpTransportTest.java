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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class AbstractDojoHttpTransportTest {

	File tmpDir = null;
	IAggregator mockAggregator;
	HttpServletRequest mockRequest;
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
		mockRequest = TestUtils.createMockRequest(mockAggregator);
		EasyMock.replay(mockAggregator, mockRequest);
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
		AbstractDojoHttpTransport transport = new TestUtils.TestDojoHttpTransport() {
			@Override public List<String[]> getClientConfigAliases() {
				return super.getClientConfigAliases();
			}
		};
		Context.enter();
		Scriptable rawConfig = cfg.getRawConfig();
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
				modifiedConfig.getPaths().get(AbstractDojoHttpTransport.dojoTextPluginAliasFullPath).getPrimary().toString());
		Assert.assertNull(modifiedConfig.getPaths().get(AbstractDojoHttpTransport.dojoTextPluginAliasFullPath).getOverride());
		Assert.assertEquals(transport.getComboUri().resolve(AbstractDojoHttpTransport.textPluginPath).toString(),
				modifiedConfig.getPaths().get(AbstractDojoHttpTransport.dojoTextPluginFullPath).getPrimary().toString());
		Assert.assertEquals(transport.getComboUri(), modifiedConfig.getPaths().get("combo").getPrimary());
		Assert.assertEquals(1, modifiedConfig.getAliases().size());
		Assert.assertEquals(AbstractDojoHttpTransport.dojoTextPluginAlias, modifiedConfig.getAliases().get(0).getPattern());
		Assert.assertEquals(AbstractDojoHttpTransport.dojoTextPluginAliasFullPath, modifiedConfig.getAliases().get(0).getReplacement());
		Assert.assertEquals(2, transport.getClientConfigAliases().size());
		String[] alias = transport.getClientConfigAliases().get(0);
		Assert.assertEquals(AbstractDojoHttpTransport.dojoTextPluginAlias, alias[0]);
		Assert.assertEquals(AbstractDojoHttpTransport.dojoTextPluginAliasFullPath, alias[1]);
		alias = transport.getClientConfigAliases().get(1);
		Assert.assertEquals(AbstractDojoHttpTransport.aggregatorTextPluginAlias, alias[0]);
		Assert.assertEquals("combo/text", alias[1]);

		// make sure overrides are handled properly
		config = "{packages:[{name:\"dojo\", location:[\"namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo\"]}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		rawConfig = cfg.getRawConfig();
		transport.modifyConfig(mockAggregator, rawConfig);
		modifiedConfig = new ConfigImpl(
				mockAggregator,
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig);
		System.out.println(Context.toString(rawConfig));
		IConfig.Location loc = modifiedConfig.getPaths().get(AbstractDojoHttpTransport.dojoTextPluginAliasFullPath);
		Assert.assertEquals("namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo/text", loc.getPrimary().toString());
		Assert.assertNull(loc.getOverride());

		config = "{packages:[{name:\"dojo\", location:[\"namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo\", \"file:/c:/customizations/dojo\"]}]}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		rawConfig = cfg.getRawConfig();
		transport.modifyConfig(mockAggregator, rawConfig);
		modifiedConfig = new ConfigImpl(
				mockAggregator,
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig);
		System.out.println(Context.toString(rawConfig));
		loc = modifiedConfig.getPaths().get(AbstractDojoHttpTransport.dojoTextPluginAliasFullPath);
		Assert.assertEquals("namedbundleresource://com.ibm.jaggr.sample.dojo/WebContent/dojo/text", loc.getPrimary().toString());
		Assert.assertEquals("file:/c:/customizations/dojo/text", loc.getOverride().toString());

		// No dojo package in config
		transport.getClientConfigAliases().clear();
		cfg = new ConfigImpl(mockAggregator, tmpDir, "{}");
		rawConfig = cfg.getRawConfig();
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
		rawConfig = cfg.getRawConfig();
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
				modifiedConfig.getPaths().get(AbstractDojoHttpTransport.dojoTextPluginAliasFullPath).getPrimary().toString());
		Assert.assertEquals(transport.getComboUri().resolve(AbstractDojoHttpTransport.textPluginPath).toString(),
				modifiedConfig.getPaths().get(AbstractDojoHttpTransport.dojoTextPluginFullPath).getPrimary().toString());
		Assert.assertEquals(2, transport.getClientConfigAliases().size());
		alias = transport.getClientConfigAliases().get(0);
		Assert.assertEquals(AbstractDojoHttpTransport.dojoTextPluginAlias, alias[0]);
		Assert.assertEquals(AbstractDojoHttpTransport.dojoTextPluginAliasFullPath, alias[1]);
		alias = transport.getClientConfigAliases().get(1);
		Assert.assertEquals(AbstractDojoHttpTransport.aggregatorTextPluginAlias, alias[0]);
		Assert.assertEquals("combo/text", alias[1]);
		Context.exit();
	}

	@Test
	public void testBeforeLayerModule() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{textPluginDelegators:[\"dojo/text\"],jsPluginDelegators:[\"dojo/i18n\"]}"));
		String result = transport.beforeLayerModule(mockRequest, "module");
		Assert.assertEquals("\"module\":function(){", result);
		result = transport.beforeLayerModule(mockRequest, "plugin!module");
		Assert.assertEquals("\"plugin!module\":function(){", result);
		// Test output modified by text plugin delegators
		result = transport.beforeLayerModule(mockRequest, "dojo/text!module");
		Assert.assertEquals("\"url:module\":", result);
		// Test output modified by js plugin delegators
		result = transport.beforeLayerModule(mockRequest, "dojo/i18n!module");
		Assert.assertEquals("\"module\":function(){", result);
	}

	@Test
	public void testAfterLayerModule() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		configRef.set(new ConfigImpl(mockAggregator, URI.create(tmpDir.toURI().toString()), "{textPluginDelegators:[\"dojo/text\"]}"));
		String result = transport.afterLayerModule(mockRequest, "module");
		Assert.assertEquals("}", result);
		result = transport.afterLayerModule(mockRequest, "plugin!module");
		Assert.assertEquals("}", result);
		// Test output modified by text plugin delegators
		result = transport.afterLayerModule(mockRequest, "dojo/text!module");
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
		Assert.assertSame(arg, mockRequest.getAttribute(AbstractDojoHttpTransport.ADD_REQUIRE_DEPS_REQATTRNAME));
	}

	@Test
	public void testEndResponse() throws Exception {
		TestDojoHttpTransport transport = new TestDojoHttpTransport();
		String result = transport.endResponse(mockRequest, null);
		Assert.assertEquals("", result);

		// Add required modules
		Set<String> required = new LinkedHashSet<String>();
		mockRequest.setAttribute(AbstractDojoHttpTransport.ADD_REQUIRE_DEPS_REQATTRNAME, required);
		result = transport.endResponse(mockRequest, null);
		Assert.assertEquals("", result);

		required.add("module1");
		required.add("module2");
		result = transport.endResponse(mockRequest, null);
		Assert.assertEquals("require([\"module1\",\"module2\"]);", result);
	}

	class TestDojoHttpTransport extends AbstractDojoHttpTransport {
		@Override protected URI getComboUri() { return null; }
		@Override protected String getTransportId() {return null; }
		@Override protected String getResourcePathId() { return null; }
	}
}
