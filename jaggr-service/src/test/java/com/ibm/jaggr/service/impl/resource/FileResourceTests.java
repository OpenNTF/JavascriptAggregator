package com.ibm.jaggr.service.impl.resource;

import java.io.File;
import java.net.URI;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.junit.Test;

import com.ibm.jaggr.service.resource.IResourceFactory;

public class FileResourceTests {
	
	@Test
	public void testAuthority() throws Exception {
		FileResource res = new FileResource(new URI("file://server/path/name.ext"));
		if (File.separatorChar == '\\') {
			Assert.assertEquals("\\\\server\\path\\name.ext", res.file.getCanonicalPath());
		} else {
			Assert.assertEquals("//server/path/name.ext", res.file.getCanonicalPath());
		}
		Assert.assertEquals("file://server/path/name.ext", res.getURI().toString());
	}
	
	@Test 
	public void testResolve() throws Exception {
		URI bundleUri = new URI("bundleresource://25.5/path/name.ext");
		URI relativeBundleUri = new URI("bundleresource://25.5/relative");
		URI fileUri = new URI("file:///temp/path/name.ext");
		URI relativeFileUri = new URI("file:///temp/relative");
		final IResourceFactory mockBundleResourceFactory = 
				EasyMock.createMock(IResourceFactory.class);
		FileResource relativeResource = new FileResource(relativeBundleUri, mockBundleResourceFactory, relativeFileUri);
		EasyMock.expect(mockBundleResourceFactory.newResource(relativeBundleUri))
				.andReturn(relativeResource).once();
		EasyMock.replay(mockBundleResourceFactory);
		
		FileResource res = new FileResource(bundleUri, mockBundleResourceFactory, fileUri);
		FileResource relativeRes = (FileResource)res.resolve("../relative");
		URI resolved = relativeRes.getURI();
		EasyMock.verify(mockBundleResourceFactory);
		Assert.assertEquals(new File(fileUri).toURI().resolve("../relative"), resolved);
		Assert.assertEquals(relativeBundleUri, relativeRes.getRefUri());
		Assert.assertTrue(mockBundleResourceFactory == relativeRes.getFactory());
	}
}
