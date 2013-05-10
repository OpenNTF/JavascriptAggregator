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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.eclipse.osgi.service.urlconversion.URLConverter;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

public class BundleResourceFactoryTests {

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
		ServiceReference mockUrlConverterSR = EasyMock.createMock(ServiceReference.class);
		URLConverter mockUrlConverter = EasyMock.createMock(URLConverter.class);
		
		/*
		 * Test when URLConverter.toFileUrl returns a value
		 */
		factory = new TestBundleResourceFactory();
		factory.setInitializationData(mockContext, mockUrlConverterSR);
		EasyMock.expect(mockContext.getService(mockUrlConverterSR)).andReturn(mockUrlConverter).once();
		EasyMock.expect(mockContext.ungetService(mockUrlConverterSR)).andReturn(true).once();
		EasyMock.expect(mockUrlConverter.toFileURL(bundleUrl)).andReturn(fileUrl);
		EasyMock.replay(mockContext, mockUrlConverterSR, mockUrlConverter);
		FileResource res = (FileResource)factory.newResource(bundleUrl.toURI());
		EasyMock.verify(mockContext, mockUrlConverter);
		Assert.assertTrue(factory == res.getFactory());
		Assert.assertEquals(bundleUrl.toURI(), res.getRefUri());
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
		res = (FileResource)factory.newResource(namedBundleUrl.toURI());
		EasyMock.verify(mockContext, mockUrlConverter, mockBundle);
		Assert.assertTrue(factory == res.getFactory());
		Assert.assertEquals(bundleUrl.toURI(), res.getRefUri());
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
}
