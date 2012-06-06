/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.transport;

import java.io.File;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.IAggregatorExtension;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.modulebuilder.IModuleBuilderExtensionPoint;
import com.ibm.jaggr.service.test.TestUtils;

public class DojoHttpTransportTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
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
		EasyMock.expect(mockAggregator.getModuleBuilderExtensions()).andAnswer(new IAnswer<Iterable<IAggregatorExtension>>() {
			@Override public Iterable<IAggregatorExtension> answer() throws Throwable {
				return extensions;
			}
		}).anyTimes();
		EasyMock.replay(mockAggregator);
		String config = "{packages:[{name:\"dojo\", location:\"namedbundleresource://com.ibm.jaggr.service.sample.dojo.1.7.resources/WebContent/dojo\"}]}";
		URI tmpDir = new File(System.getProperty("java.io.tmpdir")).toURI();
		IConfig cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		final URI comboUri = new URI(DojoHttpTransport.comboUriStr);
		DojoHttpTransport transport = new DojoHttpTransport() {
			@Override protected String getResourcePathId() {return "combo";}
			@Override protected URI getComboUri() { return comboUri; }
			@Override public List<String[]> getClientConfigAliases() {
				return super.getClientConfigAliases();
			}
		};
		Map<String, Object> rawConfig = cfg.getRawConfig();
		transport.modifyConfig(mockAggregator, rawConfig);
		System.out.println(rawConfig);
		
		// verify config has the changes we expect
		IConfig modifiedConfig = new ConfigImpl(
				mockAggregator, 
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig.toString());
		Assert.assertEquals(3, modifiedConfig.getPaths().size());
		Assert.assertEquals("namedbundleresource://com.ibm.jaggr.service.sample.dojo.1.7.resources/WebContent/dojo/text",
				modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginAliasFullPath).toString());
		Assert.assertEquals(DojoHttpTransport.textPluginProxyUriStr,
				modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginFullPath).toString());
		Assert.assertEquals(comboUri, modifiedConfig.getPaths().get("combo"));
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
		
		// No dojo package in config
		transport.getClientConfigAliases().clear();
		cfg = new ConfigImpl(mockAggregator, tmpDir, "{}");
		rawConfig = cfg.getRawConfig();
		transport.modifyConfig(mockAggregator, rawConfig);
		modifiedConfig = new ConfigImpl(
				mockAggregator, 
				new File(System.getProperty("java.io.tmpdir")).toURI(),
				rawConfig.toString());
		System.out.println(rawConfig);
		Assert.assertEquals(1, modifiedConfig.getPaths().size());
		Assert.assertEquals(0, modifiedConfig.getAliases().size());
		Assert.assertEquals(comboUri, modifiedConfig.getPaths().get("combo"));
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
				rawConfig.toString());
		System.out.println(rawConfig);
		Assert.assertEquals(3, modifiedConfig.getPaths().size());
		Assert.assertEquals(1, modifiedConfig.getAliases().size());
		Assert.assertEquals(comboUri, modifiedConfig.getPaths().get("combo"));
		Assert.assertEquals("namedbundleresource://com.ibm.jaggr.service.sample.dojo.1.7.resources/WebContent/dojo/text",
				modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginAliasFullPath).toString());
		Assert.assertEquals(DojoHttpTransport.textPluginProxyUriStr,
				modifiedConfig.getPaths().get(DojoHttpTransport.dojoTextPluginFullPath).toString());
		Assert.assertEquals(2, transport.getClientConfigAliases().size());
		alias = transport.getClientConfigAliases().get(0);
		Assert.assertEquals(DojoHttpTransport.dojoTextPluginAlias, alias[0]);
		Assert.assertEquals(DojoHttpTransport.dojoTextPluginAliasFullPath, alias[1]);
		alias = transport.getClientConfigAliases().get(1);
		Assert.assertEquals(DojoHttpTransport.aggregatorTextPluginAlias, alias[0]);
		Assert.assertEquals("combo/text", alias[1]);
	}

}
