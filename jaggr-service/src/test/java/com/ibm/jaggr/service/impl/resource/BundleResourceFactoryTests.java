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

import java.net.URI;

import org.junit.Test;

public class BundleResourceFactoryTests {
	private final BundleResourceFactory factory = new BundleResourceFactory();
	
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
}
