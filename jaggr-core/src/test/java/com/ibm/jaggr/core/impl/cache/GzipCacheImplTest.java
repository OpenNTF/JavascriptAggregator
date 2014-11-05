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
package com.ibm.jaggr.core.impl.cache;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.cache.IGzipCache;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.impl.executors.ExecutorsImpl;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.util.CopyUtil;

import com.google.common.io.Files;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.zip.GZIPInputStream;

public class GzipCacheImplTest {

	static final String testData = "This is a test.";
	static final String newTestData = "This is a new test";
	static final String newTestData2 = "This is a new new test";
	File tempdir;
	File tempfile;
	CountDownLatch latch1;
	CountDownLatch latch2;
	IAggregator mockAggregator;
	ICacheManager mockCacheManager;
	IExecutors executors;
	List<String> deletedCacheFiles;

	@Before
	public void setup() throws IOException {
		latch1 = new CountDownLatch(1);
		latch2 = new CountDownLatch(1);
		tempdir = Files.createTempDir();
		tempfile = new File(tempdir, "source");
		deletedCacheFiles = new ArrayList<String>();
		FileWriter writer = new FileWriter(tempfile);
		writer.append(testData);
		writer.close();
		mockAggregator = EasyMock.createNiceMock(IAggregator.class);
		mockCacheManager = EasyMock.createNiceMock(ICacheManager.class);
		executors = new ExecutorsImpl(null, null, null, null);
		EasyMock.expect(mockAggregator.getCacheManager()).andReturn(mockCacheManager).anyTimes();
		EasyMock.expect(mockAggregator.getExecutors()).andAnswer(new IAnswer<IExecutors>() {
			@Override public IExecutors answer() throws Throwable {
				return executors;
			}
		}).anyTimes();
		EasyMock.expect(mockCacheManager.getCacheDir()).andReturn(tempdir).anyTimes();
		mockCacheManager.createCacheFileAsync(EasyMock.isA(String.class), EasyMock.isA(InputStream.class), EasyMock.isA(ICacheManager.CreateCompletionCallback.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
			@Override
			public Object answer() throws Throwable {
				final String prefix = (String)EasyMock.getCurrentArguments()[0];
				final InputStream in = (InputStream)EasyMock.getCurrentArguments()[1];
				final ICacheManager.CreateCompletionCallback callback = (ICacheManager.CreateCompletionCallback)EasyMock.getCurrentArguments()[2];
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							latch1.await();
							File cacheFile = File.createTempFile(prefix, ".cache", tempdir);
							CopyUtil.copy(in, new FileOutputStream(cacheFile));
							callback.completed(cacheFile.getName(), null);
							latch2.countDown();
						} catch (Exception ex) {
							callback.completed(null, ex);
						}
					}
				}).start();
				return null;
			}
		}).anyTimes();
		mockCacheManager.deleteFileDelayed(EasyMock.isA((String.class)));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
			@Override
			public Object answer() throws Throwable {
				deletedCacheFiles.add((String)EasyMock.getCurrentArguments()[0]);
				return null;
			}
		}).anyTimes();
	}

	@After
	public void teardown() throws Exception {
		if (tempdir != null) {
			TestUtils.deleteRecursively(tempdir);
			tempdir = null;
		}
	}

	@Test
	public void testGetInputStream() throws Exception {

		GzipCacheImpl impl = new GzipCacheImpl();

		EasyMock.replay(mockAggregator, mockCacheManager);
		impl.setAggregator(mockAggregator);
		MutableInt retLength = new MutableInt();

		// Get the input stream.  Should be a ByteArrayInputStream until
		// the async thread waiting on latch1 gets to write the cache file.
		InputStream is = impl.getInputStream("key", tempfile.toURI(), retLength);
		Assert.assertTrue(is instanceof ByteArrayInputStream);

		// Validate the gzipped data
		Assert.assertEquals(testData, unzipInputStream(is, retLength.getValue()));

		// Validate the cache entry fields
		IGzipCache.ICacheEntry cacheEntry = impl.get("key");
		Assert.assertNotNull(Whitebox.getInternalState(cacheEntry, "bytes"));
		Assert.assertEquals(Long.valueOf(tempfile.lastModified()), (Long)Whitebox.getInternalState(cacheEntry, "lastModified"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "ex"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "file"));

		// Request the input stream again.  Make sure we get a new ByteArrayInputStream
		// to the same content.
		is = impl.getInputStream("key", tempfile.toURI(), retLength);
		Assert.assertTrue(is instanceof ByteArrayInputStream);
		Assert.assertEquals(testData, unzipInputStream(is, retLength.getValue()));

		// now release the cache file writer thread and wait for it to complete
		latch1.countDown();
		latch2.await();

		// The input stream this time should be a FileInputStream
		is = impl.getInputStream("key", tempfile.toURI(), retLength);
		Assert.assertTrue(is instanceof FileInputStream);
		// validate the data
		Assert.assertEquals(testData, unzipInputStream(is, retLength.getValue()));

		// validate the cache entry to make sure the fields were updated as expected
		Assert.assertSame(cacheEntry, impl.get("key"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "bytes"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "ex"));
		File cacheFile = (File)Whitebox.getInternalState(cacheEntry, "file");
		Assert.assertTrue(cacheFile.getName().startsWith("source.gzip."));
		Assert.assertTrue(cacheFile.getName().endsWith(".cache"));
		Assert.assertEquals(tempfile.lastModified(), cacheFile.lastModified());
	}

	@Test
	public void testGetInputStreamLastModified() throws Exception {
		GzipCacheImpl impl = new GzipCacheImpl();
		latch2 = new CountDownLatch(2);
		long oldLastMod = new Date().getTime()-10000;
		tempfile.setLastModified(oldLastMod);

		EasyMock.replay(mockAggregator, mockCacheManager);
		impl.setAggregator(mockAggregator);
		MutableInt retLength = new MutableInt();

		// Get the input stream.  Should be a ByteArrayInputStream until
		// the async thread waiting on latch1 gets to write the cache file.
		InputStream is = impl.getInputStream("key", tempfile.toURI(), retLength);
		Assert.assertTrue(is instanceof ByteArrayInputStream);

		// Validate the gzipped data
		Assert.assertEquals(testData, unzipInputStream(is, retLength.getValue()));

		// Validate the cache entry fields
		IGzipCache.ICacheEntry cacheEntry = impl.get("key");
		Assert.assertNotNull(Whitebox.getInternalState(cacheEntry, "bytes"));
		Assert.assertEquals(Long.valueOf(tempfile.lastModified()), (Long)Whitebox.getInternalState(cacheEntry, "lastModified"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "ex"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "file"));

		// Now update the last-modified date of the input resource
		FileWriter writer = new FileWriter(tempfile);
		writer.append(newTestData);
		writer.close();

		// Get the input stream again and ensure it returns the updated contents
		is = impl.getInputStream("key", tempfile.toURI(), retLength);
		Assert.assertTrue(is instanceof ByteArrayInputStream);
		Assert.assertEquals(newTestData, unzipInputStream(is, retLength.getValue()));

		// Validate the cache entry fields
		Assert.assertNotSame(cacheEntry, impl.get("key"));
		cacheEntry = impl.get("key");
		Assert.assertNotNull(Whitebox.getInternalState(cacheEntry, "bytes"));
		Assert.assertEquals(Long.valueOf(tempfile.lastModified()), (Long)Whitebox.getInternalState(cacheEntry, "lastModified"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "ex"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "file"));

		// now write the cache file to disk
		// now release the cache file writer thread and wait for it to complete
		latch1.countDown();
		latch2.await();

		// The input stream this time should be a FileInputStream
		is = impl.getInputStream("key", tempfile.toURI(), retLength);
		Assert.assertTrue(is instanceof FileInputStream);
		// validate the data
		Assert.assertEquals(newTestData, unzipInputStream(is, retLength.getValue()));

		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "bytes"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "ex"));
		File cacheFile1 = (File)Whitebox.getInternalState(cacheEntry, "file");
		Assert.assertTrue(cacheFile1.getName().startsWith("source.gzip."));
		Assert.assertTrue(cacheFile1.getName().endsWith(".cache"));
		long newTestDataLastMod = cacheFile1.lastModified();
		Assert.assertTrue("oldLastMod = " + oldLastMod + ", newTestDataLastMod = " + newTestDataLastMod,
				Math.abs(oldLastMod - newTestDataLastMod) >= 9000 /* account for rounding on unix */);

		// reset the latches
		latch1 = new CountDownLatch(1);
		latch2 = new CountDownLatch(1);
		// Now update the last-modified date of the input resource
		writer = new FileWriter(tempfile);
		writer.append(newTestData2);
		writer.close();
		tempfile.setLastModified(new Date().getTime() + 10000);

		// The input stream this time should be a ByteArrayInputStream
		is = impl.getInputStream("key", tempfile.toURI(), retLength);
		Assert.assertTrue(is instanceof ByteArrayInputStream);
		Assert.assertNotSame(cacheEntry, impl.get("key"));
		cacheEntry = impl.get("key");
		// validate the data
		Assert.assertEquals(newTestData2, unzipInputStream(is, retLength.getValue()));
		Assert.assertEquals(tempfile.lastModified(), Whitebox.getInternalState(cacheEntry, "lastModified"));

		// now write the cache file to disk
		// now release the cache file writer thread and wait for it to complete
		latch1.countDown();
		latch2.await();

		is = impl.getInputStream("key", tempfile.toURI(), retLength);
		Assert.assertTrue(is instanceof FileInputStream);
		Assert.assertSame(cacheEntry, impl.get("key"));
		Assert.assertEquals(newTestData2, unzipInputStream(is, retLength.getValue()));

		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "bytes"));
		Assert.assertNull(Whitebox.getInternalState(cacheEntry, "ex"));
		File cacheFile2 = (File)Whitebox.getInternalState(cacheEntry, "file");
		Assert.assertTrue(cacheFile2.getName().startsWith("source.gzip."));
		Assert.assertTrue(cacheFile2.getName().endsWith(".cache"));
		Assert.assertFalse(cacheFile1.getName().equals(cacheFile2.getName()));
		long newTestData2LastMod = cacheFile2.lastModified();
		Assert.assertTrue("newTestDataLastMod = " + newTestDataLastMod + ", newTestData2LastMod = " + newTestData2LastMod,
				Math.abs(newTestDataLastMod - newTestData2LastMod) >= 9000  /* account for rounding on unix */);
		Assert.assertEquals(cacheFile1.getName(), deletedCacheFiles.get(0));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetInputStreamExceptionHandling() throws Exception {
		final GzipCacheImpl impl = new GzipCacheImpl();
		EasyMock.replay(mockAggregator, mockCacheManager);
		impl.setAggregator(mockAggregator);
		final MutableInt retLength = new MutableInt();
		final CountDownLatch latch3 = new CountDownLatch(1);

		EasyMock.reset(mockCacheManager);
		EasyMock.expect(mockCacheManager.getCacheDir()).andReturn(tempdir).anyTimes();
		mockCacheManager.createCacheFileAsync(EasyMock.isA(String.class), EasyMock.isA(InputStream.class), EasyMock.isA(ICacheManager.CreateCompletionCallback.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
			@Override
			public Object answer() throws Throwable {
				latch1.countDown();
				latch2.await();
				throw new IOException("test generated exception");
			}
		}).anyTimes();
		EasyMock.replay(mockCacheManager);


		// get input stream (should throw execption because file was deleted
		final MutableBoolean exceptionCaught = new MutableBoolean(false);
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					InputStream is = impl.getInputStream("key", tempfile.toURI(), retLength);
				} catch (Exception ex) {
					exceptionCaught.setTrue();
				}
				latch3.countDown();
			}
		}).start();

		latch1.await();
		IGzipCache.ICacheEntry cacheEntry = impl.get("key");
		latch2.countDown();
		latch3.await();

		Assert.assertTrue(exceptionCaught.isTrue());
		Assert.assertNotNull(Whitebox.getInternalState(cacheEntry, "ex"));
		Assert.assertNull(impl.get("key"));

	}

	private String unzipInputStream(InputStream in, int expectedLength) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		CopyUtil.copy(in, bos);
		byte[] bytes = bos.toByteArray();
		Assert.assertEquals(bytes.length, expectedLength);
		ByteArrayOutputStream unzippedBos = new ByteArrayOutputStream();
		CopyUtil.copy(new GZIPInputStream(new ByteArrayInputStream(bytes)), unzippedBos);
		return new String(unzippedBos.toByteArray());
	}
}
