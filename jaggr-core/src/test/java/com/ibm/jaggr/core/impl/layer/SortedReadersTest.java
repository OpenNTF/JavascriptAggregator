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
package com.ibm.jaggr.core.impl.layer;

import com.ibm.jaggr.core.impl.module.ModuleImpl;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.modulebuilder.ModuleBuildFuture;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.transport.IHttpTransport;

import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class SortedReadersTest {

	static final IModule scriptsModule = new ModuleImpl("script", URI.create("script")),
			             modulesModule = new ModuleImpl("module", URI.create("module")),
			             layerModule = new ModuleImpl("layer", URI.create("layer"));
	static final ModuleBuildReader scriptsReader = new ModuleBuildReader("script", true),
	                               modulesReader = new ModuleBuildReader("module", true),
	                               layerReader = new ModuleBuildReader("layer", true);
	@AfterClass
	public static void tearDownAfterClass() throws Exception {}

	@Test
	public void test() throws Exception {
		List<ModuleBuildFuture> futures = new ArrayList<ModuleBuildFuture>();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(null);
		EasyMock.replay(mockRequest);

		futures.add(new ModuleBuildFuture(
				scriptsModule,
				new CompletedFuture<ModuleBuildReader>(scriptsReader),
				ModuleSpecifier.SCRIPTS)
		);
		futures.add(new ModuleBuildFuture(
				modulesModule,
				new CompletedFuture<ModuleBuildReader>(modulesReader),
				ModuleSpecifier.MODULES)
		);
		futures.add(new ModuleBuildFuture(
				layerModule,
				new CompletedFuture<ModuleBuildReader>(layerReader),
				ModuleSpecifier.LAYER)
		);
		SortedReaders sorted = new SortedReaders(futures, mockRequest);
		Assert.assertEquals(sorted.getScripts().size(), 1);
		Assert.assertEquals(sorted.getScripts().get(scriptsModule), scriptsReader);
		Assert.assertEquals(sorted.getModules().size(), 1);
		Assert.assertEquals(sorted.getModules().get(modulesModule), modulesReader);
		Assert.assertEquals(sorted.getCacheEntries().size(), 1);
		Assert.assertEquals(sorted.getCacheEntries().get(layerModule), layerReader);
	}

	@Test
	public void testWithExtraBuildModule() throws Exception {
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		IModule extraModule = new ModuleImpl("extra", URI.create("extra"));
		List<ModuleBuildFuture> futures = new ArrayList<ModuleBuildFuture>();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(null, requestAttributes);
		EasyMock.replay(mockRequest);
		ModuleBuildReader reader = new ModuleBuildReader("module", true),
				          extraReader = new ModuleBuildReader("extra", true);

		reader.addExtraBuild(new ModuleBuildFuture(
				extraModule,
				new CompletedFuture<ModuleBuildReader>(extraReader),
				ModuleSpecifier.BUILD_ADDED)
		);

		futures.add(new ModuleBuildFuture(
				scriptsModule,
				new CompletedFuture<ModuleBuildReader>(scriptsReader),
				ModuleSpecifier.SCRIPTS)
		);
		futures.add(new ModuleBuildFuture(
				modulesModule,
				new CompletedFuture<ModuleBuildReader>(reader),
				ModuleSpecifier.MODULES)
		);
		futures.add(new ModuleBuildFuture(
				layerModule,
				new CompletedFuture<ModuleBuildReader>(layerReader),
				ModuleSpecifier.LAYER)
		);
		SortedReaders sorted = new SortedReaders(futures, mockRequest);
		Assert.assertEquals(sorted.getScripts().size(), 1);
		Assert.assertEquals(sorted.getScripts().get(scriptsModule), scriptsReader);
		Assert.assertEquals(sorted.getModules().size(), 1);
		Assert.assertEquals(sorted.getModules().get(modulesModule), reader);
		Assert.assertEquals(sorted.getCacheEntries().size(), 2);
		Assert.assertEquals(sorted.getCacheEntries().get(layerModule), layerReader);
		Assert.assertEquals(sorted.getCacheEntries().get(extraModule), extraReader);

		// verify that IHttpTransport.NOADDMODULES_REQATTRNAME disables extra build expansion
		mockRequest.setAttribute(IHttpTransport.NOADDMODULES_REQATTRNAME, true);
		sorted = new SortedReaders(futures, mockRequest);
		Assert.assertEquals(sorted.getScripts().size(), 1);
		Assert.assertEquals(sorted.getScripts().get(scriptsModule), scriptsReader);
		Assert.assertEquals(sorted.getModules().size(), 1);
		Assert.assertEquals(sorted.getModules().get(modulesModule), reader);
		Assert.assertEquals(sorted.getCacheEntries().size(), 1);
		Assert.assertEquals(sorted.getCacheEntries().get(layerModule), layerReader);
	}
}
