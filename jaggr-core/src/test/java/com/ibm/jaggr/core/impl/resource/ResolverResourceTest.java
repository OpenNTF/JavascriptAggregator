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
		Assert.assertEquals(relativeBundleUri, Whitebox.getInternalState(relativeRes, "resolvableUri"));
		Assert.assertTrue(mockBundleResourceFactory == Whitebox.getInternalState(relativeRes, "factory"));
	}

}
