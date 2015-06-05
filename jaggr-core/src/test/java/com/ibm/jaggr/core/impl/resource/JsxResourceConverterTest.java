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
package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.cache.ICache;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.cache.ICacheManagerListener;
import com.ibm.jaggr.core.cache.IResourceConverterCache;
import com.ibm.jaggr.core.impl.cache.ResourceConverterCacheImpl.IConverter;
import com.ibm.jaggr.core.resource.AbstractResourceBase;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.resource.IResourceVisitor.Resource;
import com.ibm.jaggr.core.resource.StringResource;

import com.google.common.io.Files;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lesscss.deps.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Dictionary;
import java.util.List;

import junit.framework.Assert;

public class JsxResourceConverterTest {

	@Before
	public void setUp() throws Exception {}

	@After
	public void tearDown() throws Exception {}

	@SuppressWarnings("unchecked")
	@Test
	public void testConvertResource() throws Exception {
		// create mock objects
		IAggregator mockAggregator = EasyMock.createMock(IAggregator.class);
		IPlatformServices mockPlatformServices = EasyMock.createMock(IPlatformServices.class);
		ICacheManager mockCacheManager = EasyMock.createMock(ICacheManager.class);
		final IResourceConverterCache mockConverterCache = EasyMock.createMock(IResourceConverterCache.class);
		final IServiceRegistration mockRegistration = EasyMock.createMock(IServiceRegistration.class);
		final Mutable<ICacheManagerListener> listenerWrapper = new MutableObject<ICacheManagerListener>();
		EasyMock.expect(mockAggregator.getName()).andReturn("test");
		EasyMock.expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices);
		EasyMock.expect(mockPlatformServices.registerService(
				EasyMock.eq(ICacheManagerListener.class.getName()),
				EasyMock.isA(ICacheManagerListener.class),
				EasyMock.isA(Dictionary.class))).andAnswer(new IAnswer<IServiceRegistration>() {
					@Override
					public IServiceRegistration answer() throws Throwable {
						Dictionary dict = (Dictionary)EasyMock.getCurrentArguments()[2];
						Assert.assertEquals("test", dict.get("name"));
						listenerWrapper.setValue((ICacheManagerListener)EasyMock.getCurrentArguments()[1]);
						return mockRegistration;
					}
				});
		EasyMock.replay(mockAggregator, mockPlatformServices);
		JsxResourceConverter converter = new JsxResourceConverter() {
			@Override protected IResourceConverterCache newCache(IConverter converter, String prefix, String suffix) {
				Assert.assertTrue(converter instanceof JsxResourceConverter.JsxConverter);
				Assert.assertTrue("jsx.".equals(prefix));
				Assert.assertTrue("".equals(suffix));
				return mockConverterCache;
			}
		};
		// initialize the converter
		converter.initialize(mockAggregator, null, null);
		EasyMock.verify(mockAggregator, mockPlatformServices);

		EasyMock.reset(mockAggregator, mockPlatformServices);
		mockRegistration.unregister();
		EasyMock.expectLastCall();
		mockConverterCache.setAggregator(mockAggregator);
		EasyMock.expectLastCall();
		ICache mockCache = EasyMock.createMock(ICache.class);
		EasyMock.expect(mockCacheManager.getCache()).andReturn(mockCache);
		EasyMock.expect(mockCache.putIfAbsent(EasyMock.eq(JsxResourceConverter.JSX_CACHE_NAME), EasyMock.isA(IResourceConverterCache.class))).andReturn(null);
		EasyMock.replay(mockRegistration, mockConverterCache, mockCacheManager, mockCache);

		// now invoke the cache manager listener
		listenerWrapper.getValue().initialized(mockCacheManager);
		EasyMock.verify(mockRegistration, mockConverterCache, mockCacheManager, mockCache);
		EasyMock.reset(mockRegistration, mockConverterCache, mockCacheManager, mockCache);

		final Mutable<IResource> newResourceWrapper = new MutableObject<IResource>();
		EasyMock.expect(mockAggregator.getCacheManager()).andReturn(mockCacheManager).anyTimes();
		EasyMock.expect(mockAggregator.newResource(EasyMock.isA(URI.class))).andAnswer(new IAnswer<IResource>() {
			@Override public IResource answer() throws Throwable {
				IResource answer = newResourceWrapper.getValue();
				if (answer == null) {
					throw new NullPointerException();
				}
				URI uri = (URI)EasyMock.getCurrentArguments()[0];
				Assert.assertEquals(answer.getURI(), uri);
				return answer;
			}
		}).anyTimes();
		EasyMock.expect(mockCacheManager.getCache()).andReturn(mockCache).anyTimes();
		EasyMock.expect(mockCache.getCache(JsxResourceConverter.JSX_CACHE_NAME)).andReturn(mockConverterCache).anyTimes();
		EasyMock.replay(mockAggregator, mockCacheManager, mockCache);

		// make sure .js files aren't converted
		long lastmod = new Date().getTime();
		IResource res = new StringResource("var foo = 'foo';", new URI("test.js"));
		Assert.assertSame(res, converter.convert(res));

		// make sure not found .js files are not converted if there is no matching .jsx file
		res = new NotFoundResource(new URI("test.js"));
		newResourceWrapper.setValue(new NotFoundResource(new URI("test.jsx")));
		Assert.assertSame(res, converter.convert(res));

		// now provide a .jsx file to convert
		newResourceWrapper.setValue(new StringResource("hello", new URI("test.jsx"), lastmod));
		EasyMock.reset(mockConverterCache);
		EasyMock.expect(mockConverterCache.convert("test.js", newResourceWrapper.getValue())).andAnswer(new TestConverterAnswer());
		EasyMock.replay(mockConverterCache);
		IResource converted = converter.convert(res);
		EasyMock.verify(mockConverterCache);
		Assert.assertEquals("test.js", converted.getReferenceURI().toString());

		// now we retrieve the existing entry from the cache
		EasyMock.reset(mockConverterCache);
		EasyMock.expect(mockConverterCache.convert("test.js", newResourceWrapper.getValue())).andReturn(converted);
		EasyMock.replay(mockConverterCache);
		Assert.assertSame(converter.convert(res), converted);
		EasyMock.verify(mockConverterCache);
		Assert.assertEquals("test.js", converted.getReferenceURI().toString());

		// Test last-modified handling
		newResourceWrapper.setValue(new StringResource("hello", new URI("test.jsx"), lastmod+10000));
		EasyMock.reset(mockConverterCache);
		EasyMock.expect(mockConverterCache.convert("test.js", newResourceWrapper.getValue())).andAnswer(new TestConverterAnswer());
		EasyMock.replay(mockConverterCache);
		converted = converter.convert(res);
		EasyMock.verify(mockConverterCache);
		Assert.assertTrue(Math.abs(lastmod+10000 - converted.lastModified()) < 1000);

		// Make sure an exception resource is returned if the converter throws an exception
		newResourceWrapper.setValue(new StringResource("hello", new URI("test.jsx"), lastmod+20000));
		EasyMock.reset(mockConverterCache);
		EasyMock.expect(mockConverterCache.convert("test.js", newResourceWrapper.getValue())).andThrow(new IOException("test"));
		EasyMock.replay(mockConverterCache);
		converted = converter.convert(res);
		EasyMock.verify(mockConverterCache);
		boolean exceptionThrown = false;
		try {
			converted.getInputStream();
		} catch (IOException e) {
			Assert.assertEquals("test", e.getMessage());
			exceptionThrown = true;
		}
		Assert.assertTrue(exceptionThrown);

		// Now test conversion of folder resources
		IResource folderResource = new AbstractResourceBase(new URI("/folder/")) {
			@Override public Reader getReader() throws IOException {
				throw new IOException();
			}
			@Override public InputStream getInputStream() throws IOException {
				throw new IOException();
			}
			@Override public boolean isFolder() { return true; }
			@Override public void walkTree(final IResourceVisitor visitor) throws IOException {
				visitor.visitResource(new Resource() {
					URI uri = URI.create("/folder/test.jsx");
					@Override public URI getURI() {
						return uri;
					}
					@Override public boolean isFolder() {
						return false;
					}
					@Override public long lastModified() {
						return 0;
					}
					@Override public IResource newResource(IAggregator aggregator) {
						return new StringResource("test", uri);
					}
				}, "test.jsx");
			}
		};

		converted = converter.convert(folderResource);
		Assert.assertTrue(converted.isFolder());
		newResourceWrapper.setValue(new NotFoundResource(URI.create("/folder/test.js")));
		final List<String> visited = new ArrayList<String>();
		converted.walkTree(new IResourceVisitor() {
			@Override
			public boolean visitResource(Resource resource, String pathName) throws IOException {
				Assert.assertEquals(URI.create("/folder/").resolve(pathName), resource.getURI());
				visited.add(pathName);
				if ("test.js".equals(pathName)) {
					// make sure that calling resource.newResource() will call aggregator.runConverters()
					// with a NotFoundResource
					IAggregator mockAggr = EasyMock.createMock(IAggregator.class);
					EasyMock.expect(mockAggr.runConverters(EasyMock.isA(IResource.class))).andAnswer(new IAnswer<IResource>() {
						@Override public IResource answer() throws Throwable {
							IResource res = (IResource)EasyMock.getCurrentArguments()[0];
							Assert.assertTrue(res instanceof NotFoundResource);
							Assert.assertEquals("/folder/test.js", res.getURI().toString());
							return new StringResource("converted", res.getURI());
						}
					});
					EasyMock.replay(mockAggr);
					IResource converted = resource.newResource(mockAggr);
					Assert.assertEquals("converted", IOUtils.toString(converted.getInputStream()));
					EasyMock.verify(mockAggr);
				}
				return false;
			}
		});
		// Assert that we visited the jsx resource that was in the folder plus the synthesized
		// .js resource
		Assert.assertEquals(Arrays.asList(new String[]{"test.js", "test.jsx"}), visited);
	}

	static final String jsxSource = "var myDivElement = <div className=\"foo\" />;";
	static final String transpiledJs = "var myDivElement = React.createElement(\"div\", {className: \"foo\"});";

	@Test
	public void testJsxConverter() throws Exception {
		File tmpdir = Files.createTempDir();
		File cacheFile = new File(tmpdir, "test.js");
		IConverter jsxConverter = new JsxResourceConverter.JsxConverter();
		IResource res = new StringResource(jsxSource, new URI("test.jsx"));
		jsxConverter.generateCacheContent(res, cacheFile);
		Assert.assertEquals(transpiledJs, FileUtils.readFileToString(cacheFile));

		// Make sure the converter works following serialization/de-serialization
		File file = new File(tmpdir, "converter.ser");
		ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
		os.writeObject(jsxConverter);
		os.close();
		ObjectInputStream is = new ObjectInputStream(new FileInputStream(file));
		jsxConverter = (JsxResourceConverter.JsxConverter)is.readObject();
		is.close();
		jsxConverter.generateCacheContent(res, cacheFile);
		Assert.assertEquals(transpiledJs, FileUtils.readFileToString(cacheFile));
	}

	static class TestConverterAnswer implements IAnswer<IResource>{

		public IResource answer() throws Throwable {
			IResource res = (IResource)EasyMock.getCurrentArguments()[1];
			Assert.assertEquals("test.jsx", res.getURI().toString());
			Assert.assertEquals("test.js", res.getReferenceURI().toString());
			IResource answer = new StringResource("converted", new URI("converted.js"), res.lastModified());
			answer.setReferenceURI(res.getReferenceURI());
			return answer;
		}
	}

}
