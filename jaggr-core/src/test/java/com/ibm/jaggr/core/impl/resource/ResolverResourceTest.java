package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;

import org.easymock.EasyMock;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.File;
import java.net.URI;

import junit.framework.Assert;

public class ResolverResourceTest {
	@Test
	public void testResolve() throws Exception {
		URI bundleUri = new URI("bundleresource://25.5/path/name.ext");
		URI relativeBundleUri = new URI("bundleresource://25.5/relative");
		URI fileUri = new URI("file:///temp/path/name.ext");
		URI relativeFileUri = new URI("file:///temp/relative");
		final IResourceFactory mockBundleResourceFactory =
				EasyMock.createMock(IResourceFactory.class);
		ResolverResource relativeResource = new ResolverResource(new FileResource(relativeFileUri),  relativeBundleUri, mockBundleResourceFactory);
		EasyMock.expect(mockBundleResourceFactory.newResource(relativeBundleUri))
		.andReturn(relativeResource).once();
		EasyMock.replay(mockBundleResourceFactory);

		ResolverResource res = new ResolverResource(new FileResource(fileUri), bundleUri, mockBundleResourceFactory);
		IResource relativeRes = res.resolve("../relative");
		URI resolved = relativeRes.getURI();
		EasyMock.verify(mockBundleResourceFactory);
		Assert.assertEquals(new File(fileUri).toURI().resolve("../relative"), resolved);
		Assert.assertEquals(relativeBundleUri, Whitebox.getInternalState(relativeRes, "originalUri"));
		Assert.assertTrue(mockBundleResourceFactory == Whitebox.getInternalState(relativeRes, "factory"));
	}

}
