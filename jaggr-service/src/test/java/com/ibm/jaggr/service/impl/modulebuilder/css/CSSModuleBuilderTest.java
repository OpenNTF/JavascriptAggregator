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

package com.ibm.jaggr.service.impl.modulebuilder.css;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

import org.apache.commons.codec.binary.Base64;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Scriptable;

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
	

	Map<String, String[]> requestParams = new HashMap<String, String[]>();
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	Scriptable configScript;
	IAggregator mockAggregator;
	HttpServletRequest mockRequest;
	CSSModuleBuilderTester builder = new CSSModuleBuilderTester(); 
	List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators(mockAggregator);
	long seq = 1;
	
	class CSSModuleBuilderTester extends CSSModuleBuilder {
		@Override
		protected Reader getContentReader(
				String mid, 
				IResource resource, 
				HttpServletRequest request, 
				List<ICacheKeyGenerator> keyGens) 
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
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		replay(mockRequest);
		replay(mockAggregator);
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}");
		configScript = cfg.getRawConfig();
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
		
		// create file to import
		css = "/* Importe file */\r\n\r\n.imported {\r\n\tcolor : black;\r\n}";
		CopyUtil.copy(css, new FileWriter(new File(testdir, "imported.css")));
		/*
		 * Make sure imported css files get inlined
		 */
		configScript.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, configScript, Boolean.TRUE);
		IConfig config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
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
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, new String[]{"false"});
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"subdir/imported.css\"", output);
		
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, new String[]{"true"});
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".background-image:url('#images/img.jpg');", output);
		
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, new String[]{"0"});
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"subdir/imported.css\"", output);

		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, new String[]{"1"});
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
		byte[] bytes = Base64.decodeBase64(base64PngData.getBytes());
		File images = new File(testdir, "images");
		images.mkdir();
		File image = new File(images, "testImage.png");
		OutputStream out = new FileOutputStream(image);
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		CopyUtil.copy(in, out);
		
		css = ".foo {background-image:url(images/testImage.png)}";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		configScript.put(CSSModuleBuilder.INLINEIMPORTS_CONFIGPARAM, configScript, "true");
		IConfig config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		
		long size = image.length();
		configScript.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, configScript, Long.toString(size));
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		// Set the size threshold just below the image size and make sure the image isn't inlined
		size--;
		configScript.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, configScript, Long.toString(size));
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		size++;
		configScript.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, configScript, Long.toString(size));
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		// Make sure we can disable image inlining by request parameter
		requestParams.put(CSSModuleBuilder.INLINEIMAGES_REQPARAM_NAME, new String[]{"false"});
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);

		requestParams.put(CSSModuleBuilder.INLINEIMAGES_REQPARAM_NAME, new String[]{"true"});
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		// Make sure that the image is inlined if it is specifically included
		configScript.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, configScript, Long.toString(0));
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "testImage.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));

		// Test wildcard matching algorithm for include/exclude filenames
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "*.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "testImage*");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "*");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "*Image.pn?");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "test*.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "te?tIma???png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "testImage.*png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "???Image.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "test*.??");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		// Ensure exclude list overrides include list
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "*");
		configScript.put(CSSModuleBuilder.EXCLUDELIST_CONFIGPARAM, configScript, "*.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		configScript.put(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM, configScript, "testImage.png");
		configScript.put(CSSModuleBuilder.EXCLUDELIST_CONFIGPARAM, configScript, "testImage.png");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(images/testImage.png)}", output);
		
		CopyUtil.copy("hello world!\r\n", new FileWriter(new File(testdir, "hello.txt")));
		css = ".foo {background-image:url(hello.txt)}";
		configScript.put(CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM, configScript, Long.toString(size));
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(hello.txt)}", output);

		// Should not be inlined because there's still an include list
		configScript.put(CSSModuleBuilder.IMAGETYPES_CONFIGPARAM, configScript, "text/plain");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(hello.txt)}", output);

		// Remove the include list and now we should be able to load the new content type
		configScript.delete(CSSModuleBuilder.INCLUDELIST_CONFIGPARAM);
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:text\\/plain;base64\\,[^']*'\\)\\}"));
		
		// Ensure exclude list overrides content type list
		configScript.put(CSSModuleBuilder.EXCLUDELIST_CONFIGPARAM, configScript, "*.txt");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url(hello.txt)}", output);
		
		// Make sure type content/unknown lets us load anything not excluded
		CopyUtil.copy("hello world!\r\n", new FileWriter(new File(testdir, "hello.foo")));
		css = ".foo {background-image:url(hello.foo)}";
		configScript.put(CSSModuleBuilder.IMAGETYPES_CONFIGPARAM, configScript, "content/unknown");
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), configScript);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:content\\/unknown;base64\\,[^']*'\\)\\}"));
	}
	
	@Test
	public void testCacheKeyGen() throws Exception {
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators(mockAggregator);
		Assert.assertEquals("expn:0;css:0:0:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestParams.put(CSSModuleBuilder.INLINEIMAGES_REQPARAM_NAME, new String[]{"true"});
		Assert.assertEquals("expn:0;css:0:1:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, new String[]{"true"});
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
