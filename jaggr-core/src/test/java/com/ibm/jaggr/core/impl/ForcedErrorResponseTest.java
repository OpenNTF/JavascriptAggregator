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
package com.ibm.jaggr.core.impl;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import junit.framework.Assert;

public class ForcedErrorResponseTest {

	@Test
	public void testConstructor() throws Exception {
		ForcedErrorResponse test = new ForcedErrorResponse("500");
		Assert.assertEquals(500, test.peekStatus());
		Assert.assertEquals(1, test.getCount());
		Assert.assertEquals(0, test.getSkip());
		Assert.assertEquals(null, test.getResponseBody());

		test = new ForcedErrorResponse("500 3");
		Assert.assertEquals(500, test.peekStatus());
		Assert.assertEquals(3, test.getCount());
		Assert.assertEquals(0, test.getSkip());
		Assert.assertEquals(null, test.getResponseBody());

		test = new ForcedErrorResponse("500 3 5");
		Assert.assertEquals(500, test.peekStatus());
		Assert.assertEquals(3, test.getCount());
		Assert.assertEquals(5, test.getSkip());
		Assert.assertEquals(null, test.getResponseBody());

		test = new ForcedErrorResponse("500 3 5 file://c:/error.html");
		Assert.assertEquals(500, test.peekStatus());
		Assert.assertEquals(3, test.getCount());
		Assert.assertEquals(5, test.getSkip());
		Assert.assertEquals(new URI("file://c:/error.html"), test.getResponseBody());

		test = new ForcedErrorResponse("500 file://c:/error.html");
		Assert.assertEquals(500, test.peekStatus());
		Assert.assertEquals(1, test.getCount());
		Assert.assertEquals(0, test.getSkip());
		Assert.assertEquals(new URI("file://c:/error.html"), test.getResponseBody());

		test = new ForcedErrorResponse("500 2 file://c:/error.html");
		Assert.assertEquals(500, test.peekStatus());
		Assert.assertEquals(2, test.getCount());
		Assert.assertEquals(0, test.getSkip());
		Assert.assertEquals(new URI("file://c:/error.html"), test.getResponseBody());

	}

	@Test (expected=URISyntaxException.class)
	public void testConstructorErrors1() throws Exception {
		new ForcedErrorResponse("500 file:// error.html");
	}

	@Test (expected=NumberFormatException.class)
	public void testConstructorErrors2() throws Exception {
		new ForcedErrorResponse("foo");
	}

	@Test (expected=NumberFormatException.class)
	public void testConstructorErrors3() throws Exception {
		new ForcedErrorResponse("");
	}

	@Test
	public void testGetStatus() throws Exception{
		ForcedErrorResponse test = new ForcedErrorResponse("500");
		Assert.assertEquals(500, test.getStatus());
		Assert.assertEquals(-1, test.getStatus());
		Assert.assertEquals(-1, test.getStatus());

		test = new ForcedErrorResponse("500 2");
		Assert.assertEquals(500, test.getStatus());
		Assert.assertEquals(500, test.getStatus());
		Assert.assertEquals(-1, test.getStatus());
		Assert.assertEquals(-1, test.getStatus());

		test = new ForcedErrorResponse("500 2 1");
		Assert.assertEquals(0, test.getStatus());
		Assert.assertEquals(500, test.getStatus());
		Assert.assertEquals(500, test.getStatus());
		Assert.assertEquals(-1, test.getStatus());
		Assert.assertEquals(-1, test.getStatus());


	}

}
