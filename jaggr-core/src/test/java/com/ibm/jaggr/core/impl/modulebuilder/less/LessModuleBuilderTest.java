/*
 * (C) Copyright 2014, IBM Corporation
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

package com.ibm.jaggr.core.impl.modulebuilder.less;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.StringResource;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.util.CopyUtil;

import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Scriptable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class LessModuleBuilderTest extends EasyMock {
	static File tmpdir;
	static File testdir;
	public static final String LESS = "@import \"colors.less\";\n\nbody{\n  " +
            "background: @mainColor;\n}";

	Map<String, String[]> requestParams = new HashMap<String, String[]>();
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	Scriptable configScript;
	IAggregator mockAggregator;
	Ref<IConfig> configRef;
	HttpServletRequest mockRequest;
	LessModuleBuilderTester builder;
	List<ICacheKeyGenerator> keyGens;
	long seq = 1;

	class LessModuleBuilderTester extends LessModuleBuilder {
		public LessModuleBuilderTester(IAggregator aggr) {
			super(aggr);
		}
		@Override
		protected Reader getContentReader(
				String mid,
				IResource resource,
				HttpServletRequest req,
				List<ICacheKeyGenerator> keyGens
		) throws IOException {
			return super.getContentReader(mid, resource, req, keyGens);
		}
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = new File(System.getProperty("java.io.tmpdir"));
		testdir = new File(tmpdir, "LessModuleBuilderTest");
		testdir.mkdir();
		// create file to import
		CopyUtil.copy("@mainColor: #FF0000;", new FileWriter(new File(testdir,
				"colors.less")));
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TestUtils.deleteRecursively(testdir);
	}

	@Before
	public void setUp() throws Exception {
		configRef = new Ref<IConfig>(null);
		mockAggregator = TestUtils.createMockAggregator(configRef, testdir);
		mockRequest = TestUtils.createMockRequest(mockAggregator,
				requestAttributes, requestParams, null, null);
		replay(mockRequest);
		replay(mockAggregator);
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		builder = new LessModuleBuilderTester(mockAggregator);
		builder.configLoaded(cfg, seq++);
		keyGens = builder.getCacheKeyGenerators
				(mockAggregator);
	}

	@Test
	public void testLessCompilationWithImport() throws Exception {
		String output;
		URI resUri = new File(testdir, "colors.less").toURI();
		output = buildLess(new StringResource(LESS, resUri));
		Assert.assertEquals("body{background:#ff0000}", output);
	}

	private String buildLess(StringResource less) throws IOException {
		Reader reader = builder.getContentReader("colors.less", less, mockRequest,
				keyGens);
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		String output = writer.toString();
		System.out.println(output);
		return output;
	}
}
