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

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.cache.ICacheManager.CreateCompletionCallback;
import com.ibm.jaggr.core.test.TestUtils;

import com.google.common.io.Files;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.regex.Pattern;

import junit.framework.Assert;

public class CacheManagerImplTest {

	private File tmpDir;
	private IAggregator mockAggregator;

	private static class TestObj implements Serializable {
		private static final long serialVersionUID = 1L;
		private final boolean canSerialize;
		private final String str;
		public TestObj(String str, boolean canSerialize) { this.str = str; this.canSerialize = canSerialize;}
		@Override public String toString() { return str; }
		private void writeObject(ObjectOutputStream out) throws IOException {
			if (canSerialize) {
				out.defaultWriteObject();
			} else {
				throw new NotSerializableException();
			}
		}


	}

	@Before
	public void setup() throws Exception {
		tmpDir = Files.createTempDir();
		mockAggregator = TestUtils.createMockAggregator(null, tmpDir);
		EasyMock.replay(mockAggregator);

	}

	@Test
	public void testExternalizeObjectAsync() throws Exception {
		// mockAggregator uses a synchronous executor, so the callback
		// executes before the externalizeObjectAsync returns.
		final boolean[] callbackCalled = new boolean[]{false};
		final String[] cacheFilename = new String[1];
		final ICacheManager cacheMgr = mockAggregator.getCacheManager();
		cacheMgr.externalizeCacheObjectAsync("testObj.", new TestObj("Hello World!", true), new CreateCompletionCallback() {
			@Override public void completed(String filename, Exception e) {
				cacheFilename[0] = filename;
				Assert.assertTrue(Pattern.compile("testObj\\.[^.]+\\.cache").matcher(filename).find());
				callbackCalled[0] = true;
			}
		});
		Assert.assertTrue(callbackCalled[0]);
		// Now deserialize the object and make sure it's valid
		File cacheFile = new File(cacheMgr.getCacheDir(), cacheFilename[0]);
		ObjectInputStream is = new ObjectInputStream(new FileInputStream(cacheFile));
		TestObj cached = (TestObj) is.readObject();
		is.close();
		Assert.assertEquals(cached.toString(), "Hello World!");

		// Now try to serialize an object that will throw an exception
		callbackCalled[0] = false;
		cacheMgr.externalizeCacheObjectAsync("testObj.", new TestObj("Hello World!", false), new CreateCompletionCallback() {
			@Override public void completed(String filename, Exception e) {
				Assert.assertTrue(Pattern.compile("testObj\\.[^.]+\\.cache").matcher(filename).find());
				Assert.assertEquals(e.getClass(), NotSerializableException.class);
				callbackCalled[0] = true;
			}
		});
	}
}
