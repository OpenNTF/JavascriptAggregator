/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.modulebuilder.css;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.wink.json4j.JSONObject;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import sun.misc.BASE64Decoder;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.impl.config.ConfigImpl;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.StringResource;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.CopyUtil;

public class CSSModuleBuilderTest extends EasyMock {
	
	static File tmpdir;
	static File testdir;
	static final String base64PngData = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAASCAYAAACaV7S8AAAAAXNSR0IArs4c6QAAACNJREFUCNdj+P///0cmBgaGJ0wMDAyPsbAgXIb////zMZACAIj0DUFA3QqvAAAAAElFTkSuQmCC";
	

	Map<String, String> requestParams = new HashMap<String, String>();
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	@SuppressWarnings("unchecked")
	Map<String, Object> configJson = new JSONObject();
	IAggregator mockAggregator;
	HttpServletRequest mockRequest;
	CSSModuleBuilderTester builder = new CSSModuleBuilderTester(); 
	ICacheKeyGenerator[] keyGens = builder.getCacheKeyGenerators(mockAggregator);
	long seq = 1;
	
	class CSSModuleBuilderTester extends CSSModuleBuilder {
		@Override
		protected Reader getContentReader(
				String mid, 
				IResource resource, 
				HttpServletRequest request, 
				ICacheKeyGenerator[] keyGens) 
		throws IOException {
			return super.getContentReader(mid, resource, request, keyGens);
		}			
	}
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = new File(System.getProperty("java.io.tmpdir"));
		testdir = new File(tmpdir, "CSSModuleBuilderTest");
		testdir.mkdir();
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TestUtils.deleteRecursively(testdir);
	}

	@Before
	public void setUp() throws Exception {
		mockAggregator = TestUtils.createMockAggregator();
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams);
		replay(mockRequest);
		replay(mockAggregator);
	}

	@After
	public void tearDown() throws Exception {
	}

	
	@Test
	public void testMinify() throws Exception {
		// test comment and white space removal
		String css, output;
		// Get a URI to the test resources.  Note that we can't use the class loader
		// here do to the fact that it gets confused by the split packages.
		URI resuri = testdir.toURI();
		css = "/* comments */\r\n.foo\t  {  \tcolor:black; \r\nfont-weight : bold;\r\n/* inline comment */ }\r\n/* trailing comment */\n\t.bar { font-size:small } \r\n";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{color:black;font-weight:bold} .bar{font-size:small}", output);
		
		// create file to import
		css = "/* Importe file */\r\n\r\n.imported {\r\n\tcolor : black;\r\n}";
		CopyUtil.copy(css, new FileWriter(new File(testdir, "imported.css")));
		
		/*
		 * Make sure imports are not inlined by default
		 */
		css = "/* importing file */\n\r@import \"imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"imported.css\"", output);
		
		/*
		 * Make sure that quoted strings and url(...) patterns are not 
		 * minified
		 */
		css = "/* importing file */\n\r@import \"name  with   spaces.css\"\r\n@import url(name  with   spaces.css)\r\n@import url(  \" name  with   spaces.css\"  )\r\n";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"name  with   spaces.css\" @import url(name  with   spaces.css) @import url(\" name  with   spaces.css\")", output);

		css = "/* importing file */\n\r@import 'name  with   spaces.css'\r\n@import url(  ' name  with   spaces.css'  )\r\n";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import 'name  with   spaces.css' @import url(' name  with   spaces.css')", output);
		/*
		 *  test odd-ball use cases that might break us
		 */
		css = "@import \"funny name  with  url(...) inside.css\"  .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"funny name  with  url(...) inside.css\" .foo{color:black}", output);
		
		css = "@import url(  funny name ' with  \"embedded  \"  quotes.css  )  .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import url(funny name ' with  \"embedded  \"  quotes.css) .foo{color:black}", output);
		
		css = "@import \"funny 'name'  with  url(\"...\") and embedded   quotes inside.css\"   .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"funny 'name'  with  url(\"...\") and embedded   quotes inside.css\" .foo{color:black}", output);

		css = "@import url(  'funny name  with  \" single double  quote.css' )   .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import url('funny name  with  \" single double  quote.css') .foo{color:black}", output);
		
		css = "@import  'funny name  \"with double\"  quote.css'   .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import 'funny name  \"with double\"  quote.css' .foo{color:black}", output);
	}

	@Test
	public void testImport() throws Exception {
		String css, output;
		URI resuri = testdir.toURI();
		
		/*
		 * Make sure imported css files get inlined
		 */
		configJson.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, Boolean.TRUE);
		IConfig config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		css = "/* importing file */\n\r@import \"imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);
		
		// Test relative url resolution
		css = "/* importing file */\n\r@import \"./imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);
		
		css = "/* importing file */\n\r@import \"././imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);
		
		css = "/* importing file */\n\r@import \"foo/../imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);
	
		css = "/* importing file */\n\r@import \"./foo/bar/.././../imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);
		
		css = "/* importing file */\n\r@import \"foo/bar/../../imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);
		
		// This should fail
		css = "/* importing file */\n\r@import \"foo/imported.css\"";
		boolean exceptionCaught = false;
		try {
			output = buildCss(new StringResource(css, resuri));
		} catch (FileNotFoundException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue("Expected FileNotFoundException", exceptionCaught);

		css = "/* Importe file */\r\n\r\n.background-image: url( \"images/img.jpg\" );";
		CopyUtil.copy(css, new FileWriter(new File(testdir, "imported.css")));
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url(\"images/img.jpg\");", output);

		File subdir = new File(testdir, "subdir");
		File imported = new File(subdir, "imported.css");
		subdir.mkdir();
		String importedCss = "/* Importe file */\r\n\r\n.background-image:  url( \"images/img.jpg\" );";
		CopyUtil.copy(importedCss, new FileWriter(imported));
		css = "/* importing file */\n\r@import \"subdir/imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url(\"subdir/images/img.jpg\");", output);
		
		// test imported path normalizing
		importedCss = "/* Importe file */\r\n\r\n.background-image:  url('./images/./img.jpg' );";
		CopyUtil.copy(importedCss, new FileWriter(imported));
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('subdir/images/img.jpg');", output);
		
		importedCss = "/* Importe file */\r\n\r\n.background-image:  url( \"./images/foo/../img.jpg\" );";
		CopyUtil.copy(importedCss, new FileWriter(imported));
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url(\"subdir/images/img.jpg\");", output);
		
		importedCss = "/* Importe file */\r\n\r\n.background-image:  url(./images/foo/bar/../../img.jpg);";
		CopyUtil.copy(importedCss, new FileWriter(imported));
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url(subdir/images/img.jpg);", output);
		
		importedCss = "/* Importe file */\r\n\r\n.background-image:  url( images/../images/img.jpg );";
		CopyUtil.copy(importedCss, new FileWriter(imported));
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url(subdir/images/img.jpg);", output);
		
		importedCss = "/* Importe file */\r\n\r\n.background-image:  url( '/images/img.jpg' );";
		CopyUtil.copy(importedCss, new FileWriter(imported));
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('/images/img.jpg');", output);
		
		importedCss = "/* Importe file */\r\n\r\n.background-image:  url( 'http://server.com/images/img.jpg' );";
		CopyUtil.copy(importedCss, new FileWriter(imported));
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('http://server.com/images/img.jpg');", output);
		
		importedCss = "/* Importe file */\r\n\r\n.background-image:  url( '#images/img.jpg' );";
		CopyUtil.copy(importedCss, new FileWriter(imported));
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('#images/img.jpg');", output);

		// Make sure we can disable inlining of imports by request parameter
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, "false");
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"subdir/imported.css\"", output);
		
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, "true");
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('#images/img.jpg');", output);
		
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, "0");
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"subdir/imported.css\"", output);

		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, "1");
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('#images/img.jpg');", output);
		
		// make sure we can output filenames if requested and development mode is enabled
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('#images/img.jpg');", output);
		
		mockAggregator.getOptions().setOption(IOptions.DEVELOPMENT_MODE, "true");
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("/* @import subdir/imported.css */\r\n.background-image:url('#images/img.jpg');", output);
		
		requestAttributes.remove(IHttpTransport.SHOWFILENAMES_REQATTRNAME);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('#images/img.jpg');", output);

	}
	
	@Test
	public void testInlineUrls() throws Exception {
		String css, output;
		URI resuri = testdir.toURI();

		// Test image inlining options
		File images = new File(testdir, "images");
		images.mkdir();
		File image = new File(images, "testImage.png");
		BASE64Decoder decoder = new BASE64Decoder();
		InputStream in = new ReaderInputStream(new StringReader(base64PngData));
		OutputStream out = new FileOutputStream(image);
		decoder.decodeBuffer(in, out);
		in.close();
		out.close();
		
		css = ".foo {background-image:url(images/testImage.png)}";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		configJson.put(CSSModuleBuilder.INLINEIMPORTS_CONFIGPARAM, "true");
		IConfig config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		
		long size = image.length();
		configJson.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, Long.toString(size));
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		// Set the size threshold just below the image size and make sure the image isn't inlined
		size--;
		configJson.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, Long.toString(size));
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		size++;
		configJson.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, Long.toString(size));
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		// Make sure we can disable image inlining by request parameter
		requestParams.put(CSSModuleBuilder.INLINEIMAGES_REQPARAM_NAME, "false");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);

		requestParams.put(CSSModuleBuilder.INLINEIMAGES_REQPARAM_NAME, "true");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		// Make sure that the image is inlined if it is specifically included
		configJson.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, Long.toString(0));
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "testImage.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));

		// Test wildcard matching algorithm for include/exclude filenames
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "*.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "testImage*");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "*");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "*Image.pn?");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "test*.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "te?tIma???png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "testImage.*png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "???Image.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "test*.??");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		// Ensure exclude list overrides include list
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "*");
		configJson.put(CSSModuleBuilder.EXCLUDELIST_CONFIGPARAM, "*.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		configJson.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, "testImage.png");
		configJson.put(CSSModuleBuilder.EXCLUDELIST_CONFIGPARAM, "testImage.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		CopyUtil.copy("hello world!\r\n", new FileWriter(new File(testdir, "hello.txt")));
		css = ".foo {background-image:url(hello.txt)}";
		configJson.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, Long.toString(size));
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(hello.txt)}", output);

		// Should not be inlined because there's still an include list
		configJson.put(CSSModuleBuilder.IMAGETYPES_CONFIGPARAM, "text/plain");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(hello.txt)}", output);

		// Remove the include list and now we should be able to load the new content type
		configJson.remove(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM);
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:text\\/plain;base64\\,[^']*'\\)\\}"));
		
		// Ensure exclude list overrides content type list
		configJson.put(CSSModuleBuilder.EXCLUDELIST_CONFIGPARAM, "*.txt");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(hello.txt)}", output);
		
		// Make sure type content/unknown lets us load anything not excluded
		CopyUtil.copy("hello world!\r\n", new FileWriter(new File(testdir, "hello.foo")));
		css = ".foo {background-image:url(hello.foo)}";
		configJson.put(CSSModuleBuilder.IMAGETYPES_CONFIGPARAM, "content/unknown");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJson);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:content\\/unknown;base64\\,[^']*'\\)\\}"));
	}
	
	@Test
	public void testCacheKeyGen() throws Exception {
		ICacheKeyGenerator[] keyGens = builder.getCacheKeyGenerators(mockAggregator);
		Assert.assertEquals("expn:0;css:0:0:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestParams.put(CSSModuleBuilder.INLINEIMAGES_REQPARAM_NAME, "true");
		Assert.assertEquals("expn:0;css:0:1:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, "true");
		Assert.assertEquals("expn:0;css:1:1:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestAttributes.put(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		Assert.assertEquals("expn:1;css:1:1:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		Assert.assertEquals("expn:1;css:1:1:1", KeyGenUtil.generateKey(mockRequest, keyGens));
	}
	
	private String buildCss(IResource css) throws Exception {
		Reader reader = builder.getContentReader("test", css, mockRequest, keyGens);
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		String output = writer.toString();
		System.out.println(output);
		return output;
	}
	
}
