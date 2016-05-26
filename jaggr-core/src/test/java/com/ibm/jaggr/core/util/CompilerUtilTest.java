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
package com.ibm.jaggr.core.util;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.impl.config.ConfigTest.AggregatorProxy;
import com.ibm.jaggr.core.test.TestUtils;

import com.google.common.io.Files;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.AccessibleObject;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompilerUtilTest {

	File tmpFile = null;
	URI tmpDir = null;
	IAggregator mockAggregator;

	@Before
	public void setup() throws Exception {
		tmpFile = Files.createTempDir();
		tmpDir = tmpFile.toURI();
		IAggregator easyMockAggregator = TestUtils.createMockAggregator(null, null, null, AggregatorProxy.class, null);
		EasyMock.replay(easyMockAggregator);
		mockAggregator = new AggregatorProxy(easyMockAggregator);
	}
	@After
	public void tearDown() throws Exception {
		if (tmpFile != null) {
			TestUtils.deleteRecursively(tmpFile);
			tmpFile = null;
		}
	}
	@Test
	public void testAddCompilerOptionsFromConfig() throws Exception {
		Map<AccessibleObject, List<Object>> map = new HashMap<AccessibleObject, List<Object>>();
		// Test simple boolean setter
		String config = "{compilerOptions:{acceptConstKeyword:true}}";
		ConfigImpl cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		CompilerOptions mockOptions = EasyMock.createMock(CompilerOptions.class);
		mockOptions.setAcceptConstKeyword(true);
		EasyMock.expectLastCall().once();
		EasyMock.replay(mockOptions);
		int numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(0,  numFailed);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(mockOptions, map);
		EasyMock.verify(mockOptions);
		Assert.assertEquals(0,  numFailed);

		// Test setter with multiple paramaters
		config = "{compilerOptions:{defineToBooleanLiteral:['defineName', true]}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		mockOptions = EasyMock.createMock(CompilerOptions.class);
		mockOptions.setDefineToBooleanLiteral("defineName", true);
		EasyMock.expectLastCall().once();
		EasyMock.replay(mockOptions);
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(0,  numFailed);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(mockOptions, map);
		EasyMock.verify(mockOptions);
		Assert.assertEquals(0,  numFailed);

		// Test with enum value as parameter
		config = "{compilerOptions:{checkGlobalThisLevel:'WARNING'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		mockOptions = EasyMock.createMock(CompilerOptions.class);
		mockOptions.setCheckGlobalThisLevel(CheckLevel.WARNING);
		EasyMock.expectLastCall().once();
		EasyMock.replay(mockOptions);
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(0,  numFailed);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(mockOptions, map);
		EasyMock.verify(mockOptions);
		Assert.assertEquals(0,  numFailed);


		// Test with list property (needs to be passed as nested array and gets converted from NativeArray to a Set)
		config = "{compilerOptions:{aliasableStrings:[['foo', 'bar']]}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		mockOptions = EasyMock.createMock(CompilerOptions.class);
		mockOptions.setAliasableStrings(EasyMock.isA(Set.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
			@Override
			public Object answer() throws Throwable {
				Set<String> strings = (Set<String>)EasyMock.getCurrentArguments()[0];
				Assert.assertTrue(strings.contains("foo"));
				Assert.assertTrue(strings.contains("bar"));
				return null;
			}
		}).once();
		EasyMock.replay(mockOptions);
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(0,  numFailed);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(mockOptions, map);
		EasyMock.verify(mockOptions);
		Assert.assertEquals(0,  numFailed);

		// Test setting multiple properties
		config = "{compilerOptions:{aliasableGlobals:'foo,bar', aliasExternals:true}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		mockOptions = EasyMock.createMock(CompilerOptions.class);
		mockOptions.setAliasableGlobals("foo,bar");
		EasyMock.expectLastCall().once();
		mockOptions.setAliasExternals(true);
		EasyMock.expectLastCall().once();
		EasyMock.replay(mockOptions);
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(0,  numFailed);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(mockOptions, map);
		EasyMock.verify(mockOptions);
		Assert.assertEquals(0,  numFailed);

		// Test setting non-existant property
		config = "{compilerOptions:{noExist:true}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(1,  numFailed);

		// Test setting an existing property with wrong parameter type
		config = "{compilerOptions:{acceptConstKeyword:'foo'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(1,  numFailed);

		// Test with one failed and one successful property
		config = "{compilerOptions:{noExist:true,checkGlobalThisLevel:'WARNING'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		mockOptions = EasyMock.createMock(CompilerOptions.class);
		mockOptions.setCheckGlobalThisLevel(CheckLevel.WARNING);
		EasyMock.expectLastCall().once();
		EasyMock.replay(mockOptions);
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(1,  numFailed);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(mockOptions, map);
		EasyMock.verify(mockOptions);
		Assert.assertEquals(0,  numFailed);

		// Throw exception from setter
		config = "{compilerOptions:{checkGlobalThisLevel:'WARNING'}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		mockOptions = EasyMock.createMock(CompilerOptions.class);
		mockOptions.setCheckGlobalThisLevel(CheckLevel.WARNING);
		EasyMock.expectLastCall().andThrow(new RuntimeException());
		EasyMock.replay(mockOptions);
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(0,  numFailed);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(mockOptions, map);
		EasyMock.verify(mockOptions);
		Assert.assertEquals(1,  numFailed);

		// Test prop field setter
		config = "{compilerOptions:{checkSuspiciousCode:true}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		CompilerOptions options = new CompilerOptions();
		numFailed = CompilerUtil.compilerOptionsMapFromConfig(cfg, map, true);
		Assert.assertEquals(0,  numFailed);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(options, map);
		Assert.assertEquals(0,  numFailed);
		Assert.assertTrue(options.checkSuspiciousCode);

		// Test setting warning level with named DiagnosticGroup
		config = "{compilerOptions:{warningLevel:['UNDEFINED_VARIABLES', 'WARNING']}}";
		cfg = new ConfigImpl(mockAggregator, tmpDir, config);
		CompilerUtil.compilerOptionsMapFromConfig(cfg, map);
		Assert.assertEquals(0,  numFailed);
		mockOptions = EasyMock.createMock(CompilerOptions.class);
		mockOptions.setWarningLevel(EasyMock.eq(DiagnosticGroups.UNDEFINED_VARIABLES), EasyMock.eq(CheckLevel.WARNING));
		EasyMock.expectLastCall().once();
		EasyMock.replay(mockOptions);
		numFailed = CompilerUtil.applyCompilerOptionsFromMap(mockOptions, map);
		Assert.assertEquals(0, numFailed);
		EasyMock.verify(mockOptions);

	}
}
