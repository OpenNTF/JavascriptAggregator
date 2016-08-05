/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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
package com.ibm.jaggr.core.impl.cache;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.impl.cache.ResourceConverterCacheImpl.IConverter;
import com.ibm.jaggr.core.impl.resource.FileResourceFactory;
import com.ibm.jaggr.core.impl.resource.NotFoundResource;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactoryExtensionPoint;
import com.ibm.jaggr.core.resource.StringResource;

import com.google.common.io.Files;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lesscss.deps.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

public class ResourceConverterCacheImplTest {

	private File cacheDir;

	@Before
	public void setup() throws Exception {
		cacheDir = Files.createTempDir();
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.deleteDirectory(cacheDir);
	}

	@Test
	public void testConvert() throws Throwable {
		final Mutable<CountDownLatch> mutableLatch = new MutableObject<CountDownLatch>();

		@SuppressWarnings("serial")
		IConverter testConverter = new IConverter() {
			@Override
			public void generateCacheContent(IResource source, File cacheFile) throws IOException {
				InputStream in = source.getInputStream();
				CountDownLatch latch = mutableLatch.getValue();
				if (latch != null) {
					try {
						latch.await();
					} catch (InterruptedException e) {
						throw new IOException(e);
					}
				}
				String content;
				try {
					content = IOUtils.toString(in);
				} finally {
					IOUtils.closeQuietly(in);;
				}
				FileUtils.write(cacheFile, content.toUpperCase());
			}
		};
		final ResourceConverterCacheImpl impl = new ResourceConverterCacheImpl(testConverter, "foo", "bar");
		assertSame(testConverter, impl.getConverter());
		assertSame("foo",  impl.getPrefix());
		assertSame("bar", impl.getSuffix());

		final Mutable<String> deleteFileName = new MutableObject<String>();
		IAggregatorExtension mockExt = createMock(IAggregatorExtension.class);
		IAggregator mockAggregator = createMock(IAggregator.class);
		ICacheManager mockCacheMgr = createMock(ICacheManager.class);
		expect(mockExt.getInstance()).andReturn(new FileResourceFactory()).anyTimes();
		expect(mockExt.getAttribute("scheme")).andReturn("file").anyTimes();
		expect(mockAggregator.getCacheManager()).andReturn(mockCacheMgr).anyTimes();
		expect(mockAggregator.getExtensions(IResourceFactoryExtensionPoint.ID)).andReturn(Arrays.asList(new IAggregatorExtension[]{mockExt}));
		expect(mockCacheMgr.getCacheDir()).andReturn(cacheDir).anyTimes();
		mockCacheMgr.deleteFileDelayed(isA(String.class));
		expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override public Void answer() throws Throwable {
				deleteFileName.setValue((String)getCurrentArguments()[0]);
				return null;
			}
		}).anyTimes();
		replay(mockAggregator, mockCacheMgr, mockExt);
		impl.setAggregator(mockAggregator);

		long lastmod = new Date().getTime() - 10000;
		IResource source = new StringResource("Hello world", new URI("/test/resource.txt"), lastmod);
		IResource result = impl.convert("testResource", source);
		assertEquals("HELLO WORLD", FileUtils.readFileToString(new File(result.getURI())));
		File cacheFile = new File(result.getURI());
		assertTrue(Math.abs(lastmod - cacheFile.lastModified()) < 1000);
		assertNull(deleteFileName.getValue());
		assertEquals(new URI("/test/resource.txt"), result.getReferenceURI());

		URI uri = result.getURI();
		// request the source again and make sure we get back the same result URI form the cache
		result = impl.convert("testResource", source);
		assertEquals(uri, result.getURI());
		assertTrue(Math.abs(lastmod - cacheFile.lastModified()) < 1000);
		assertNull(deleteFileName.getValue());

		// Change the source string but don't change last mod.  Should get old result
		source = new StringResource("Hello world 2", new URI("/test/resource.txt"), lastmod);
		result = impl.convert("testResource", source);
		assertEquals(uri, result.getURI());
		assertEquals("HELLO WORLD", FileUtils.readFileToString(new File(result.getURI())));
		assertTrue(Math.abs(lastmod - cacheFile.lastModified()) < 1000);
		assertNull(deleteFileName.getValue());

		// Now update the last modified date of the source resource
		lastmod += 10000;
		source = new StringResource("Hello world 2", new URI("/test/resource.txt"), lastmod);
		result = impl.convert("testResource", source);
		assertFalse(uri.equals(result.getURI()));
		assertEquals("HELLO WORLD 2", FileUtils.readFileToString(new File(result.getURI())));
		assertEquals(deleteFileName.getValue(), cacheFile.getName());
		cacheFile = new File(result.getURI());
		assertTrue(Math.abs(lastmod - cacheFile.lastModified()) < 1000);

		// test exception handling
		source = new NotFoundResource(new URI("/test/noexist.txt"));
		boolean exceptionThrown = false;
		try {
			result = impl.convert("notFound", source);
		} catch (NotFoundException ex) {
			exceptionThrown = true;
		}
		assertTrue(exceptionThrown);
		// make sure there's no cache entry
		assertNull(impl.get("notFound"));

		// test threading
		URI latchedUri = new URI("/test/latched.txt");
		final IResource latchedSource = new StringResource("Latched", latchedUri, new Date().getTime());
		final Mutable<IResource> result1 = new MutableObject<IResource>();
		final Mutable<IResource> result2 = new MutableObject<IResource>();
		final Mutable<Throwable> exception = new MutableObject<Throwable>();
		mutableLatch.setValue(new CountDownLatch(1));
		final CountDownLatch finished = new CountDownLatch(2);
		new Thread() {
			@Override public void run() {
				try {
					result1.setValue(impl.convert("latched", latchedSource));
				} catch (Throwable t) {
					exception.setValue(t);
				}
				finished.countDown();
			}
		}.start();
		Thread.sleep(250);
		new Thread() {
			@Override public void run() {
				try {
					result2.setValue(impl.convert("latched", latchedSource));
				} catch (Throwable t) {
					exception.setValue(t);
				}
				finished.countDown();
			}
		}.start();
		Thread.sleep(250);
		// assert that the second thread is waiting for the first thread to complete
		assertNull(result1.getValue());
		assertNull(result2.getValue());
		// let the latched thread go
		mutableLatch.getValue().countDown();
		// wait for both threads to complete
		finished.await();
		// verify that both results are using the same cache file.
		if (exception.getValue() != null) throw exception.getValue();
		assertEquals(result1.getValue().getURI(), result2.getValue().getURI());
		cacheFile = new File(result1.getValue().getURI());
		assertEquals("LATCHED", FileUtils.readFileToString(cacheFile));
	}


}
