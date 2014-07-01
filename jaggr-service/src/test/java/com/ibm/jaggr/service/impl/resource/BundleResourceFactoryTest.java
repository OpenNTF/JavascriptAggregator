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
package com.ibm.jaggr.service.impl.resource;

import static org.junit.Assert.assertEquals;

import com.ibm.jaggr.core.impl.resource.NotFoundResource;
import com.ibm.jaggr.core.resource.IResource;

import com.ibm.jaggr.service.impl.Activator;

import com.google.common.collect.ImmutableList;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.eclipse.osgi.service.urlconversion.URLConverter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Activator.class)
public class BundleResourceFactoryTest {

	static private Map<String, Bundle> bundleNameMap = new HashMap<String, Bundle>();
	/**
	 * Dummy URL stream handler for creating bundleresource URLs on
	 * non-OSGi platforms (e.g. when unit testing).
	 *
	 */
	static class DummyStreamHandler extends URLStreamHandler {
		@Override
		protected URLConnection openConnection(URL u) throws IOException {
			throw new UnsupportedOperationException();
		}
		@Override
		public boolean equals(URL url1, URL url2) {
			return url1.toString().equals(url2.toString());
		}
	}

	private BundleResourceFactory factory = new TestBundleResourceFactory();

	static class TestBundleResourceFactory extends BundleResourceFactory {
		/**
		 * Override the default toURL method in BundleResourceFactory
		 * so that we can convert URI's to URL's when OSGi isn't
		 * available.
		 */
		@Override
		protected URL toURL(URI uri) throws IOException {
			return new URL(null, uri.toString(), new DummyStreamHandler());
		}
		@Override
		protected Bundle getBundle(String bundleName) {
			return bundleNameMap.get(bundleName);
		}
	}

	@Test
	public void getNBRBundleName() {
		URI uri = URI.create("namedbundleresource:///bundle/file");
		assertEquals("Bundle matches.", "bundle", factory.getNBRBundleName(uri));
	}

	@Test
	public void getNBRBundleNameUnderscore() {
		URI uri = URI.create("namedbundleresource:///bundle_name/file");
		assertEquals("Bundle matches.", "bundle_name", factory.getNBRBundleName(uri));
	}

	@Test
	public void getNBRBundleNameBackCompat() {
		URI uri = URI.create("namedbundleresource://bundle/file");
		assertEquals("Bundle matches.", "bundle", factory.getNBRBundleName(uri));
	}

	@Test
	public void getNBRBundleNameBackCompatUnderscore() {
		URI uri = URI.create("namedbundleresource://bundle_name/file");
		assertEquals("Bundle matches.", "bundle_name", factory.getNBRBundleName(uri));
	}

	@Test
	public void getNBRPath() {
		URI uri = URI.create("namedbundleresource:///bundle/file");
		assertEquals("Bundle matches.", "/file", factory.getNBRPath(factory.getNBRBundleName(uri), uri));
	}

	@Test
	public void getNBRPathUnderscore() {
		URI uri = URI.create("namedbundleresource:///bundle_name/file");
		assertEquals("Bundle matches.", "/file", factory.getNBRPath(factory.getNBRBundleName(uri), uri));
	}

	@Test
	public void getNBRPathBackCompat() {
		URI uri = URI.create("namedbundleresource://bundle/file");
		assertEquals("Bundle matches.", "/file", factory.getNBRPath(factory.getNBRBundleName(uri), uri));
	}

	@Test
	public void getNBRPathBackCompatUnderscore() {
		URI uri = URI.create("namedbundleresource://bundle_name/file");
		assertEquals("Bundle matches.", "/file", factory.getNBRPath(factory.getNBRBundleName(uri), uri));
	}

	@Test
	public void testNewInstance() throws Exception {
		URL fileUrl = new URL("file:///temp/path/name.ext");
		URL bundleUrl = new URL(null, "bundleresource://25-5/path/name.ext", new DummyStreamHandler());
		BundleContext mockContext = EasyMock.createMock(BundleContext.class);
		Bundle mockContributingBundle = EasyMock.createNiceMock(Bundle.class);
		ServiceReference mockUrlConverterSR = EasyMock.createMock(ServiceReference.class);
		URLConverter mockUrlConverter = EasyMock.createMock(URLConverter.class);
		PowerMock.mockStatic(Activator.class);
		EasyMock.expect(Activator.getBundleContext()).andReturn(mockContext).anyTimes();

		/*
		 * Test when URLConverter.toFileUrl returns a value
		 */
		factory = new TestBundleResourceFactory();
		factory.setInitializationData(mockContributingBundle, mockUrlConverterSR);
		EasyMock.expect(mockContext.getService(mockUrlConverterSR)).andReturn(mockUrlConverter).once();
		EasyMock.expect(mockContext.ungetService(mockUrlConverterSR)).andReturn(true).once();
		EasyMock.expect(mockUrlConverter.toFileURL(bundleUrl)).andReturn(fileUrl);
		PowerMock.replay(Activator.class, mockContributingBundle, mockContext, mockUrlConverterSR, mockUrlConverter);
		IResource res = factory.newResource(bundleUrl.toURI());
		EasyMock.verify(mockContext, mockUrlConverter);
		Assert.assertTrue(factory == Whitebox.getInternalState(res, "factory"));
		Assert.assertEquals(bundleUrl.toURI(), Whitebox.getInternalState(res, "resolvableUri"));
		Assert.assertEquals(new File(fileUrl.toURI()).toURI(), res.getURI());

		/*
		 * Test when URLConverter.toFileUrl throws FileNotFoundException
		 */
		EasyMock.reset(mockContext, mockUrlConverter);
		EasyMock.expect(mockContext.getService(mockUrlConverterSR)).andReturn(mockUrlConverter).once();
		EasyMock.expect(mockContext.ungetService(mockUrlConverterSR)).andReturn(true).once();
		EasyMock.expect(mockContext.getBundle(25)).andReturn(null).once();
		EasyMock.expect(mockUrlConverter.toFileURL(bundleUrl)).andThrow(new FileNotFoundException());
		EasyMock.replay(mockContext, mockUrlConverter);
		BundleResource bres = (BundleResource)factory.newResource(bundleUrl.toURI());
		EasyMock.verify(mockContext, mockUrlConverter);
		Assert.assertEquals(bundleUrl.toURI(), bres.getURI());

		/*
		 * Test namedbundleresource scheme
		 */
		EasyMock.reset(mockContext, mockUrlConverter);
		EasyMock.expect(mockContext.getService(mockUrlConverterSR)).andReturn(mockUrlConverter).once();
		EasyMock.expect(mockContext.ungetService(mockUrlConverterSR)).andReturn(true).once();
		EasyMock.expect(mockUrlConverter.toFileURL(bundleUrl)).andReturn(fileUrl);
		URL namedBundleUrl = new URL(null, "namedbundleresource:///com.test.bundle/path/name.ext", new DummyStreamHandler());
		EasyMock.replay(mockContext, mockUrlConverter);
		Bundle mockBundle = EasyMock.createMock(Bundle.class);
		EasyMock.expect(mockBundle.getEntry(EasyMock.anyObject(String.class))).andAnswer(new IAnswer<URL>() {
			@Override
			public URL answer() throws Throwable {
				String path = (String)EasyMock.getCurrentArguments()[0];
				return new URL(null, "bundleresource://25-5" + path, new DummyStreamHandler());
			}
		}).once();
		EasyMock.replay(mockBundle);
		bundleNameMap.put("com.test.bundle", mockBundle);
		res = factory.newResource(namedBundleUrl.toURI());
		EasyMock.verify(mockContext, mockUrlConverter, mockBundle);
		Assert.assertTrue(factory == Whitebox.getInternalState(res, "factory"));
		Assert.assertEquals(bundleUrl.toURI(), Whitebox.getInternalState(res, "resolvableUri"));
		Assert.assertEquals(new File(fileUrl.toURI()).toURI(), res.getURI());

		/*
		 * Test namedbundleresource with not found path
		 */
		EasyMock.reset(mockBundle);
		EasyMock.expect(mockBundle.getEntry(EasyMock.anyObject(String.class))).andReturn(null).once();
		EasyMock.replay(mockBundle);
		NotFoundResource nfr = (NotFoundResource)factory.newResource(namedBundleUrl.toURI());
		EasyMock.verify(mockBundle);

		/*
		 * Test namedbundleresource with not found bundle
		 */
		bundleNameMap.clear();
		nfr = (NotFoundResource)factory.newResource(namedBundleUrl.toURI());
		assertEquals(namedBundleUrl.toURI(), nfr.getURI());

		/*
		 * Test unrecognized scheme
		 */
		boolean exceptionThrown = false;
		try {
			factory.newResource(new URI("unknown:///path/name.ext"));
		} catch (UnsupportedOperationException e) {
			exceptionThrown = true;
		}
		Assert.assertTrue(exceptionThrown);
	}


	@Test
	public void testErrorLoadingClass() throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		List<Throwable> throwables = ImmutableList.<Throwable>builder()
				.add(new ClassNotFoundException())
				.add(new UnsupportedClassVersionError())
		.build();

		BundleResourceFactory factory;
		ClassLoader classLoader = EasyMock.createMock(ClassLoader.class);

		for (Throwable throwable : throwables) {
			// Start with a brand new factory each time.
			factory = new BundleResourceFactory(classLoader);

			EasyMock.reset(classLoader);
			EasyMock.expect(classLoader.loadClass(EasyMock.isA(String.class)))
				.andThrow(throwable).once();

			EasyMock.replay(classLoader);
			Method m = PowerMock.method(BundleResourceFactory.class, "getNIOFileResourceConstructor", Class[].class);
			Constructor<?> cons = (Constructor<?>)m.invoke(factory, new Object[]{new Class[]{}});
			Assert.assertNull(cons);

			// Don't attempt it again.
			cons = (Constructor<?>)m.invoke(factory, new Object[]{new Class[]{}});
			EasyMock.verify(classLoader);

			Assert.assertNull(cons);
		}
	}


	@Test
	public void testgetNIOResource() throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
		// sanity check for the reflection method.
		factory = new TestBundleResourceFactory();
		Method m = PowerMock.method(BundleResourceFactory.class, "getNIOFileResourceConstructor", Class[].class);
		Constructor<?> cons = (Constructor<?>)m.invoke(factory, new Object[]{new Class[]{URI.class}});
		Assert.assertNotNull(cons);
	}

}