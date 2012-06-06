/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.modulebuilder.text;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.impl.resource.FileResource;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.transport.IHttpTransport;

/**s
 * @author chuckd@us.ibm.com
 *
 */
public class TxtModuleContentProviderTest extends EasyMock {

	File tmpdir = null;
	HttpServletRequest mockRequest = createNiceMock(HttpServletRequest.class);

	@BeforeClass
	public static void setupBeforeClass() {
	}

	@Before
	public void setup() {
		replay(mockRequest);
	}
	/**
	 * @throws java.lang.ExceptionOs
	 */
	@After
	public void tearDown() throws Exception {
		if (tmpdir != null) {
			TestUtils.deleteRecursively(tmpdir);
			tmpdir = null;
		}
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.impl.modulebuilder.text.TextModuleBuilder#getCacheKey(javax.servlet.http.HttpServletRequest, java.util.Set)}.
	 */
	@Test
	public void testGetCacheKeyGenerator() {
		TextModuleBuilder builder = new TextModuleBuilder();
		ICacheKeyGenerator[] generators = builder.getCacheKeyGenerators(null);
		String key = KeyGenUtil.generateKey(mockRequest, generators);
		Assert.assertEquals("expn:0", key);
		reset(mockRequest);
		expect(mockRequest.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME)).andReturn(Boolean.TRUE).anyTimes();
		replay(mockRequest);
		key = KeyGenUtil.generateKey(mockRequest, generators);
		Assert.assertEquals("expn:1", key);
	}

	/**
	 * Test method for {@link com.ibm.jaggr.service.impl.modulebuilder.text.TextModuleBuilder#getJSSource()}.
	 * @throws IOException 
	 */
	@Test
	public void testGetContentReader() throws Exception {
		tmpdir = Files.createTempDir();
		File test = new File(tmpdir, "test.txt");
		Writer ow = new FileWriter(test);
		ow.write("'This is a test'. ''  Test's'");
		ow.close();
		TextModuleBuilder builder = new TextModuleBuilder();
		String code = builder.build(
				"test.txt", 
				new FileResource(test.toURI()), 
				mockRequest, null).getBuildOutput();
		System.out.println(code);
		assertEquals("define('\\'This is a test\\'. \\'\\'  Test\\'s\\'');", code);
	}
}
