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

import com.google.common.collect.ImmutableList;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.List;

import junit.framework.Assert;

public class FileResourceFactoryTest {
	private IMocksControl control;

	private ClassLoader classLoader;
	private FileResourceFactory factory;

	@Before
	public void setup() {
		control = EasyMock.createControl();
		classLoader = control.createMock(ClassLoader.class);
		factory = new FileResourceFactory(classLoader);
	}

	@Test
	public void testErrorLoadingClass() throws ClassNotFoundException {
		List<Throwable> throwables = ImmutableList.<Throwable>builder()
				.add(new ClassNotFoundException())
				.add(new UnsupportedClassVersionError())
		.build();

		for (Throwable throwable : throwables) {
			// Start with a brand new factory each time.
			factory = new FileResourceFactory(classLoader);

			control.reset();
			EasyMock.expect(classLoader.loadClass(EasyMock.isA(String.class)))
				.andThrow(throwable).once();

			control.replay();
			Constructor<?> cons = factory.getNIOFileResourceConstructor();
			Assert.assertNull(cons);

			// Don't attempt it again.
			cons = factory.getNIOFileResourceConstructor();
			control.verify();

			Assert.assertNull(cons);
		}
	}

	@Test
	public void testErrorLoadingClassConstructor() throws ClassNotFoundException {
	  EasyMock.expect(classLoader.loadClass(EasyMock.isA(String.class)))
			.andThrow(new ClassNotFoundException()).once();

		control.replay();
		Constructor<?> cons = factory.getNIOFileResourceConstructor();
		Assert.assertNull(cons);

		// Don't attempt it again.
		cons = factory.getNIOFileResourceConstructor();
		control.verify();

		Assert.assertNull(cons);
	}

	@Test
	public void testgetNIOResource() {
		// sanity check for the reflection method.
		factory = new FileResourceFactory();
		Assert.assertNotNull(factory.getNIOFileResourceConstructor(URI.class));
	}
}
