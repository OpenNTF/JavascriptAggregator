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
package com.ibm.jaggr.core.impl.layer;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.test.TestUtils;

import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;

import javax.servlet.http.HttpServletRequest;

public class CacheEntryTest {

	private static final String TEST_LAYER_CONTENT = "Test Layer Content";
	private static final String TEST_SOURCEMAP_CONTENT = "Test Source Map Content";
	static File tmpdir = null;

	private HttpServletRequest mockRequest;
	private IAggregator mockAggregator;
	private ICacheManager mockCacheManager;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = Files.createTempDir();
		TestUtils.createTestFiles(tmpdir);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	@Before
	public void setUp() throws Exception {
		mockAggregator = EasyMock.createNiceMock(IAggregator.class);
		mockRequest = TestUtils.createMockRequest(mockAggregator);
		mockCacheManager = EasyMock.createNiceMock(ICacheManager.class);
		EasyMock.expect(mockAggregator.getCacheManager()).andReturn(mockCacheManager);
		EasyMock.replay(mockAggregator, mockRequest);
	}

	@After
	public void tearDown() throws Exception {}

	@Test
	public void testTryGetInputStream() throws Exception {
		CacheEntry entry = new CacheEntry(0, "key", -1);
		InputStream in = entry.tryGetInputStream(mockRequest, null);
		Assert.assertNull(in);


	}

	@Test
	public void testGetInputStream_nonPersisted() throws Exception {
		CacheEntry entry = new CacheEntry(0, "key", -1);
		boolean exceptionThrown = false;
		InputStream in;
		try {
			in = entry.getInputStream(mockRequest, null);
		} catch (IOException e) {
			exceptionThrown = true;
		}
		Assert.assertTrue(exceptionThrown);

		// test with in memory bytes set
		entry.setBytes(TEST_LAYER_CONTENT.getBytes());
		in = entry.getInputStream(mockRequest, null);
		Assert.assertEquals(TEST_LAYER_CONTENT.getBytes().length, entry.getSize());
		Assert.assertEquals(TEST_LAYER_CONTENT, IOUtils.toString(in));
		Assert.assertEquals(0,  ((Integer)Whitebox.getInternalState(entry, "sourceMapSize")).intValue());

		// with a source map reference
		MutableObject<byte[]> sourceMap = new MutableObject<byte[]>();
		in = entry.getInputStream(mockRequest, sourceMap);
		Assert.assertEquals(TEST_LAYER_CONTENT, IOUtils.toString(in));
		Assert.assertNull(sourceMap.getValue());

		// with source map data
		entry.setData(TEST_LAYER_CONTENT.getBytes(), TEST_SOURCEMAP_CONTENT.getBytes());
		in = entry.getInputStream(mockRequest, sourceMap);
		Assert.assertEquals(TEST_LAYER_CONTENT, IOUtils.toString(in));
		Assert.assertEquals(TEST_SOURCEMAP_CONTENT, new String(sourceMap.getValue()));
		Assert.assertEquals(TEST_LAYER_CONTENT.getBytes().length, entry.getSize());
		Assert.assertEquals(TEST_SOURCEMAP_CONTENT.getBytes().length,  ((Integer)Whitebox.getInternalState(entry, "sourceMapSize")).intValue());
	}

	@Test
	public void testGetInputStream_persisted() throws Exception {
		CacheEntry entry = new CacheEntry(0, "key", -1);
		final MutableObject<CacheEntry.CreateCompletionCallback> callback = new MutableObject<CacheEntry.CreateCompletionCallback>();
		EasyMock.expect(mockCacheManager.getCacheDir()).andReturn(tmpdir).anyTimes();
		mockCacheManager.createCacheFileAsync(
				EasyMock.isA(String.class),
				EasyMock.isA(InputStream.class),
				EasyMock.isA(CacheEntry.CreateCompletionCallback.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override public Void answer() throws Throwable {
				callback.setValue((CacheEntry.CreateCompletionCallback)EasyMock.getCurrentArguments()[2]);
				Assert.assertEquals(TEST_LAYER_CONTENT, IOUtils.toString((InputStream)EasyMock.getCurrentArguments()[1]));
				return null;
			}
		}).once();
		EasyMock.replay(mockCacheManager);
		// test with in memory bytes set
		entry.setBytes(TEST_LAYER_CONTENT.getBytes());
		entry.persist(mockCacheManager);
		EasyMock.verify(mockCacheManager);
		// verify values before calling the callback
		Assert.assertNotNull(Whitebox.getInternalState(entry, "bytes"));
		Assert.assertNull(entry.getFilename());
		// call the callback and verify changes
		File cacheFile = File.createTempFile("layer.", "", tmpdir);
		String filename = cacheFile.getName();
		callback.getValue().completed(filename, null);
		Assert.assertNull(Whitebox.getInternalState(entry, "bytes"));
		Assert.assertEquals(filename, entry.getFilename());
		// new try to read the data from the cached file
		FileUtils.writeStringToFile(cacheFile, TEST_LAYER_CONTENT);
		InputStream in = entry.getInputStream(mockRequest, null);
		Assert.assertEquals(TEST_LAYER_CONTENT.getBytes().length, entry.getSize());
		Assert.assertEquals(TEST_LAYER_CONTENT, IOUtils.toString(in));
		in.close();
	}

	@Test
	public void testGetInputStream_persisted_withSourceMap() throws Exception {
		CacheEntry entry = new CacheEntry(0, "key", -1);
		final MutableObject<CacheEntry.CreateCompletionCallback> callback = new MutableObject<CacheEntry.CreateCompletionCallback>();
		final MutableObject<CacheEntry.CacheData> data = new MutableObject<CacheEntry.CacheData>();
		EasyMock.expect(mockCacheManager.getCacheDir()).andReturn(tmpdir).anyTimes();
		mockCacheManager.externalizeCacheObjectAsync(
				EasyMock.isA(String.class),
				EasyMock.isA(CacheEntry.CacheData.class),
				EasyMock.isA(CacheEntry.CreateCompletionCallback.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override public Void answer() throws Throwable {
				callback.setValue((CacheEntry.CreateCompletionCallback)EasyMock.getCurrentArguments()[2]);
				data.setValue((CacheEntry.CacheData)EasyMock.getCurrentArguments()[1]);
				return null;
			}
		}).once();
		EasyMock.replay(mockCacheManager);
		// test with in memory bytes set
		entry.setData(TEST_LAYER_CONTENT.getBytes(), TEST_SOURCEMAP_CONTENT.getBytes());
		entry.persist(mockCacheManager);
		EasyMock.verify(mockCacheManager);
		// verify values before calling the callback
		Assert.assertNotNull(Whitebox.getInternalState(entry, "bytes"));
		Assert.assertNotNull(Whitebox.getInternalState(entry, "sourceMap"));
		Assert.assertNull(entry.getFilename());
		// call the callback and verify changes
		File cacheFile = File.createTempFile("layer.", "", tmpdir);
		String filename = cacheFile.getName();
		callback.getValue().completed(filename, null);
		Assert.assertNull(Whitebox.getInternalState(entry, "bytes"));
		Assert.assertNull(Whitebox.getInternalState(entry, "sourceMap"));
		Assert.assertEquals(filename, entry.getFilename());
		// new try to read the data from the cached file
		ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(cacheFile));
		try {
			os.writeObject(data.getValue());
		} finally {
			IOUtils.closeQuietly(os);
		}

		MutableObject<byte[]> sourceMapResult = new MutableObject<byte[]>();
		InputStream in = entry.getInputStream(mockRequest, sourceMapResult);
		Assert.assertEquals(TEST_LAYER_CONTENT.getBytes().length, entry.getSize());
		Assert.assertEquals(TEST_LAYER_CONTENT, IOUtils.toString(in));
		Assert.assertEquals(TEST_SOURCEMAP_CONTENT, new String(sourceMapResult.getValue()));
	}
}
