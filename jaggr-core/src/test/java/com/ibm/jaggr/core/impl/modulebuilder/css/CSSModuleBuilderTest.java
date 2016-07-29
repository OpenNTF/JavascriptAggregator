/*
 * (C) Copyright IBM Corp. 2012, 2016
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

package com.ibm.jaggr.core.impl.modulebuilder.css;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.impl.resource.FileResource;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.StringResource;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.test.TestUtils.Ref;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.mutable.MutableObject;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Scriptable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

public class CSSModuleBuilderTest extends EasyMock {
	static File tmpdir;
	static File testdir;
	static final String base64PngData = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAASCAYAAACaV7S8AAAAAXNSR0IArs4c6QAAACNJREFUCNdj+P///0cmBgaGJ0wMDAyPsbAgXIb////zMZACAIj0DUFA3QqvAAAAAElFTkSuQmCC";

	Map<String, String[]> requestParams = new HashMap<String, String[]>();
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	Scriptable configScript;
	IAggregator mockAggregator;
	Ref<IConfig> configRef;
	HttpServletRequest mockRequest;
	CSSModuleBuilderTester builder;
	List<ICacheKeyGenerator> keyGens;
	long seq = 1;

	class CSSModuleBuilderTester extends CSSModuleBuilder {
		public CSSModuleBuilderTester(IAggregator aggr) {
			super(aggr);
		}
		@Override
		protected Reader getContentReader(
				String mid,
				IResource resource,
				HttpServletRequest request,
				MutableObject<List<ICacheKeyGenerator>> keyGens)
						throws IOException {
			return super.getContentReader(mid, resource, request, keyGens);
		}
	}
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = new File(System.getProperty("java.io.tmpdir"));
		testdir = new File(tmpdir, "CSSModuleBuilderTest");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TestUtils.deleteRecursively(testdir);
	}

	@Before
	public void setUp() throws Exception {
		configRef = new Ref<IConfig>(null);
		mockAggregator = TestUtils.createMockAggregator(configRef, testdir);
		mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes, requestParams, null, null);
		replay(mockRequest);
		replay(mockAggregator);
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}");
		configRef.set(cfg);
		configScript = (Scriptable)cfg.getRawConfig();
		builder = new CSSModuleBuilderTester(mockAggregator);
		builder.configLoaded(cfg, 1);
		keyGens = builder.getCacheKeyGenerators(mockAggregator);
	}

	@After
	public void tearDown() throws Exception {
	}


	@Test
	public void testMinify() throws Exception {

		IConfig config = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{inlineCSSImports: false}");
		configRef.set(config);
		builder.configLoaded(config, seq++);

		// test comment and white space removal
		String css, output;
		// Get a URI to the test resources.  Note that we can't use the class loader
		// here do to the fact that it gets confused by the split packages.
		URI resuri = testdir.toURI();
		css = "/* comments */\r\n.foo\t  {  \tcolor:black; \r\nfont-weight : bold;\r\n/* inline comment */ }\r\n/* trailing comment */\n\t.bar { font-size:small } \r\n";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{color:black;font-weight:bold}.bar{font-size:small}", output);

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
		css = "/* importing file */\n\r@import  \"name  with   spaces.css\"   ;\r\n@import url( name_with_no_spaces.css );\r\n@import url(  \" name  with   spaces.css\"  );\r\n";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"name  with   spaces.css\";@import url(name_with_no_spaces.css);@import url(\" name  with   spaces.css\");", output);

		css = "/* importing file */\n\r@import 'name  with   spaces.css';\r\n  @import url(  ' name  with   spaces.css'  )\r\n";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import 'name  with   spaces.css';@import url(' name  with   spaces.css')", output);
		/*
		 *  test odd-ball use cases that might break us
		 */
		css = "@import \"funny name  with  url(...) inside.css\";  .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"funny name  with  url(...) inside.css\";.foo{color:black}", output);

		css = "@import \"funny 'name'  with  url(\\\"...\\\") and escaped   quotes inside.css\";   .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import \"funny 'name'  with  url(\\\"...\\\") and escaped   quotes inside.css\";.foo{color:black}", output);

		css = "@import url(  'funny name  with  \" single double  quote.css' );   .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import url('funny name  with  \" single double  quote.css');.foo{color:black}", output);

		css = "@import  'funny name  \"with double\"  quote.css';   .foo { color : black }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("@import 'funny name  \"with double\"  quote.css';.foo{color:black}", output);

		css = ".foo:after{content:\"\\a\";white-space:pre}";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(css, output);
	}

	@Test
	public void testConfiguredPlugin() throws Exception {
		URL url = CSSModuleBuilder.class.getClassLoader().getResource("postcssPlugins/colorize.js");

		String configJs = new StringBuffer()
			.append("{")
			.append("  paths: {")
			.append("    seeRed: '").append(url.toURI()).append("'")
			.append("  },")
			.append("  cssScopePoolSize: 1,")
			.append("  postcss: {")
			.append("    plugins: [")
			.append("      [")
			.append("        'seeRed',")
			.append("        function(m) {")
			.append("          return m('red');")
			.append("        }")
			.append("      ]")
			.append("    ]")
			.append("  }")
			.append("}").toString();

		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), configJs);
		configRef.set(cfg);
		builder.configLoaded(cfg, 2);
		Assert.assertEquals(1, builder.getThreadScopes().size());

		String css, output;
		URI resuri = testdir.toURI();
		css = "div { color: black; }";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals("div{color:red}", output);
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

		css = "/* importing file */\n\r@import url(\"././imported.css\")";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);

		css = "/* importing file */\n\r@import url('foo/../imported.css')";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);

		css = "/* importing file */\n\r@import url( \"./foo/bar/.././../imported.css\" )";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);

		css = "/* importing file */\n\r@import \"foo/bar/../../imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);

		// Test forced LESS import
		css = "/* importing file */\n\r@import (less) \"imported.css\"";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".imported{color:black}", output);

		// This should fail
		css = "/* importing file */\n\r@import \"foo/imported.css\"";
		boolean exceptionCaught = false;
		try {
			output = buildCss(new StringResource(css, resuri));
		} catch (NotFoundException e) {
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
		Assert.assertEquals("/*"+CSSModuleBuilder.PREAMBLE+"subdir/imported.css */.background-image:url('#images/img.jpg');", output);

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

		// Test with quotes and spaces in URL parameter
		css = ".foo {background-image:url('images/testImage.png')}";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));

		css = ".foo {background-image:url(\"images/testImage.png\")}";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));

		css = ".foo {background-image:url( 'images/testImage.png' )}";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.foo\\{background-image:url\\('data:image\\/png;base64\\,[^']*'\\)\\}"));

		// Test for Issue #250 (https://github.com/OpenNTF/JavascriptAggregator/issues/250)
		// Nested url() functions
		String dataUrl = "data:image/svg+xml;utf8, <svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\" viewBox=\"0 0 16 16\" enable-background=\"new 0 0 16 16\" xml:space=\"preserve\"><g><defs><circle id=\"SVGID_1_\" cx=\"2.4\" cy=\"-7.5\" r=\"2.5\"/></defs><clipPath id=\"SVGID_2_\"><use xlink:href=\"#SVGID_1_\" overflow=\"visible\"/></clipPath><g clip-path=\"url(#SVGID_2_)\"><path fill=\"#264a60\" d=\"M10.5-2C9.2-2,8.2-1.2,8,0H4.9C4.8-0.3,4.7-0.6,4.5-0.9l4.5-4.5C9.5-5.2,9.9-5,10.5-5 C11.8-5,13-6.1,13-7.5S11.8-10,10.5-10C9.2-10,8.2-9.2,8-8H4.9c-0.2-1.1-1.2-2-2.4-2C1.1-10,0-8.9,0-7.5S1.1-5,2.5-5 c1.2,0,2.2-0.9,2.4-2H8c0.1,0.3,0.2,0.6,0.4,0.9L3.8-1.6C3.4-1.9,3-2,2.5-2C1.1-2,0-0.9,0,0.5S1.1,3,2.5,3c1.2,0,2.2-0.9,2.4-2 H8c0.2,1.1,1.2,2,2.4,2C11.8,3,13,1.9,13,0.5S11.8-2,10.5-2z M10.5-9C11.3-9,12-8.3,12-7.5S11.3-6,10.5-6S9-6.7,9-7.5 S9.6-9,10.5-9z M2.5-6C1.6-6,1-6.7,1-7.5S1.6-9,2.5-9S4-8.3,4-7.5S3.3-6,2.5-6z M2.5,2C1.6,2,1,1.3,1,0.5S1.6-1,2.5-1 S4-0.3,4,0.5S3.3,2,2.5,2z\"/></g></g><polygon fill=\"#264a60\" points=\"2,10 2,12 2,12 6.4,16 8,16 8,10 \"/><rect x=\"2\" fill=\"#264a60\" width=\"11\" height=\"1\"/><rect x=\"2\" fill=\"#264a60\" width=\"1\" height=\"2\"/><rect x=\"12\" fill=\"#264a60\" width=\"1\" height=\"2\"/><polygon fill=\"#264a60\" points=\"15,9 13,9 13,7 2,7 2,9 0,9 0,2 15,2 \"/><rect x=\"12\" y=\"7\" fill=\"#264a60\" width=\"1\" height=\"9\"/><rect x=\"7\" y=\"15\" fill=\"#264a60\" width=\"6\" height=\"1\"/><rect x=\"2\" y=\"7\" fill=\"#264a60\" width=\"1\" height=\"4\"/></svg>";
		css = ".foo {background-image:url( '" + dataUrl + "' )}";
		output = buildCss(new StringResource(css, resuri));
		Assert.assertEquals(".foo{background-image:url('" + dataUrl + "')}", output);

		// Set the size threshold just below the image size and make sure the image isn't inlined
		css = ".foo {background-image:url(images/testImage.png)}";
		output = buildCss(new StringResource(css, resuri));
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

		// Test specifying inlineableImageTypes as property map works.
		CopyUtil.copy("hello world!\r\n", new FileWriter(new File(testdir, "hello.svg")));
		css = ".hello {background-image:url(hello.svg)}";
		String cfg = "{" + CSSModuleBuilder.IMAGETYPES_CONFIGPARAM + ":{svg:'image/svg+xml'}," +
		                   CSSModuleBuilder.SIZETHRESHOLD_CONFIGPARAM + ":1000}";
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), cfg);
		builder.configLoaded(config, seq++);
		output = buildCss(new StringResource(css, resuri));
		Assert.assertTrue(output.matches("\\.hello\\{background-image:url\\('data:image\\/svg\\+xml;base64\\,[^']*'\\)\\}"));
	}

	@Test
	public void testCacheKeyGen() throws Exception {
		List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators(mockAggregator);
		Assert.assertEquals("txt:0:0;css:0:0:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestParams.put(CSSModuleBuilder.INLINEIMAGES_REQPARAM_NAME, new String[]{"true"});
		Assert.assertEquals("txt:0:0;css:0:1:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestParams.put(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME, new String[]{"true"});
		Assert.assertEquals("txt:0:0;css:1:1:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestAttributes.put(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME, Boolean.TRUE);
		Assert.assertEquals("txt:0:1;css:1:1:0", KeyGenUtil.generateKey(mockRequest, keyGens));
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		Assert.assertEquals("txt:0:1;css:1:1:1", KeyGenUtil.generateKey(mockRequest, keyGens));
	}

	@Test
	public void testToRegexp() {
		CSSModuleBuilder builder = new CSSModuleBuilder() {
			@Override
			public Pattern toRegexp(String filespec) {
				return super.toRegexp(filespec);
			}
		};
		Pattern regexp = builder.toRegexp("test?.*");
		Assert.assertEquals("(^|/)test[^/]\\.[^/]*?$", regexp.toString());
		Assert.assertTrue(regexp.matcher("test1.abc").find());
		Assert.assertFalse(regexp.matcher("test11.abc").find());
		Assert.assertTrue(regexp.matcher("/test1.abc").find());
		Assert.assertTrue(regexp.matcher("/some/path/test1.abc").find());
		regexp = builder.toRegexp("test/*/hello@$!.???");
		Assert.assertEquals("(^|/)test/[^/]*?/hello@\\$!\\.[^/][^/][^/]$", regexp.toString());
		Assert.assertTrue(regexp.matcher("test/abc/hello@$!.foo").find());
		Assert.assertTrue(regexp.matcher("/test/xyz/hello@$!.123").find());
		Assert.assertTrue(regexp.matcher("somepath/test/xyz/hello@$!.123").find());
		Assert.assertFalse(regexp.matcher("/test/xyz/hello@$!.ab").find());
		Assert.assertFalse(regexp.matcher("/test/hello@$!.123").find());
		regexp = builder.toRegexp("/a?c");
		Assert.assertEquals("/a[^/]c$", regexp.toString());
	}

	@Test
	public void testNonRelativeImports() throws Exception {
		String css, output;

		//create a subdirectory and put a css file in it
		File subdir = new File(testdir, "randomDir");
		File imported = new File(subdir, "randomFile.css");
		subdir.mkdir();
		String importedCss = "/* Importe file */\r\n\r\n.imported {\r\n\tcolor : black;\r\n}";
		CopyUtil.copy(importedCss, new FileWriter(imported));

		//test that a non relative import fails when includeAMDPaths is false
		IConfig config = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{paths: {AMDDir:'" + subdir.toURI() + "'}, cssEnableAMDIncludePaths: false}");
		builder.configLoaded(config, seq++);
		css = "/* importing file */\n\r@import 'AMDDir/randomFile.css'";
		File importingFile = new File(subdir, "importingFile.css");
		CopyUtil.copy(css, new FileWriter(importingFile));
		boolean exceptionCaught = false;
		try {
			output = buildCss(new FileResource(importingFile.toURI()));
		} catch (NotFoundException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue("Expected FileNotFoundException", exceptionCaught);

		//test that non relative imports work when includeAMDPaths is true
		config = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{paths: {AMDDir:'" + subdir.toURI() + "'}, cssEnableAMDIncludePaths: true}");
		configRef.set(config);
		builder.configLoaded(config, seq++);
		css = "/* importing file */\n\r@import 'AMDDir/randomFile.css'";
		importingFile = new File(subdir, "importingFile.css");
		CopyUtil.copy(css, new FileWriter(importingFile));
		output = buildCss(new FileResource(importingFile.toURI()));
		Assert.assertEquals(".imported{color:black}", output);
	}

	private String buildCss(IResource css) throws Exception {
		Reader reader = builder.getContentReader("test", css, mockRequest, new MutableObject<List<ICacheKeyGenerator>>(keyGens));
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		String output = writer.toString();
		System.out.println(output);
		return output;
	}

}
