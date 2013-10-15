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

package com.ibm.jaggr.service.impl.layer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ExportNamesCacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.impl.resource.FileResource;
import com.ibm.jaggr.service.module.ModuleSpecifier;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.readers.ModuleBuildReader;
import com.ibm.jaggr.service.test.TestUtils;
import com.ibm.jaggr.service.test.TestUtils.Ref;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.transport.IHttpTransport.LayerContributionType;
import com.ibm.jaggr.service.util.CopyUtil;

public class LayerBuilderTest {
	public static final Pattern moduleNamePat = Pattern.compile("^module[0-9]$");

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	public IHttpTransport createMockTransport() {
		IHttpTransport mock = EasyMock.createNiceMock(IHttpTransport.class);
		EasyMock.expect(mock.getLayerContribution(
				(HttpServletRequest)EasyMock.anyObject(HttpServletRequest.class),
				(LayerContributionType)EasyMock.anyObject(LayerContributionType.class),
				EasyMock.anyObject()
		)).andAnswer(new IAnswer<String>() {
			@SuppressWarnings("unused")
			public String answer() throws Throwable {
				HttpServletRequest request = (HttpServletRequest)EasyMock.getCurrentArguments()[0];
				LayerContributionType type = (LayerContributionType)EasyMock.getCurrentArguments()[1];
				Object arg = EasyMock.getCurrentArguments()[2];
				switch (type) {
				case BEGIN_RESPONSE:
					Assert.assertNull(arg);
					return "[";
				case END_RESPONSE:
					Assert.assertNull(arg);
					return "]";
				case BEGIN_MODULES:
					Assert.assertNull(arg);
					return "(";
				case END_MODULES:
					Assert.assertNull(arg);
					return ")";
				case BEFORE_FIRST_MODULE:
					return "\"<"+arg+">";
				case BEFORE_SUBSEQUENT_MODULE:
					return ",\"<"+arg+">";
				case AFTER_MODULE:
					return "<"+arg+">\"";
				case BEGIN_REQUIRED_MODULES:
					return arg.toString()+"{";
				case END_REQUIRED_MODULES:
					return "}"+arg;
				case BEFORE_FIRST_REQUIRED_MODULE:
					return "'<"+arg+">";
				case BEFORE_SUBSEQUENT_REQUIRED_MODULE:
					return ",'<"+arg+">";
				case AFTER_REQUIRED_MODULE:
					return "<"+arg+">'";
				}
				throw new IllegalArgumentException();
			}
		}).anyTimes();
		EasyMock.replay(mock);
		return mock;
	}
	
	@Test
	public void testBuild() throws Exception {
		Map<String, Object> requestAttributes = new HashMap<String, Object>();
		IHttpTransport mockTransport = createMockTransport();
		IAggregator mockAggregator = TestUtils.createMockAggregator(mockTransport);
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		EasyMock.replay(mockRequest);
		EasyMock.replay(mockAggregator);
		List<ICacheKeyGenerator> keyGens = new LinkedList<ICacheKeyGenerator>();

		// Single module specified with 'modules' query arg
		LayerBuilder builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		ModuleBuildReader mbr = new ModuleBuildReader(new StringReader("foo"));
		ModuleBuildFuture future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.MODULES
		);
		
		Reader reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		String output = toString(reader);
		Assert.assertEquals("[(\"<m1>foo<m1>\")]", output);
		System.out.println(output);
		
		// Two modules specified with 'modules' query arg
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		ModuleBuildFuture future1 = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.MODULES
		);
		
		ModuleBuildFuture future2 = new ModuleBuildFuture(
				"m2", 
				new FileResource(new URI("file:/c:/m2.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"))),
				ModuleSpecifier.MODULES
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future1, future2}));
		output = toString(reader);
		Assert.assertEquals("[(\"<m1>foo<m1>\",\"<m2>bar<m2>\")]", output);
		System.out.println(output);
		
		// Test developmentMode and showFilenames
		IOptions options = mockAggregator.getOptions();
		options.setOption("developmentMode", "true");
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.MODULES
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals(
				"/* " + Messages.LayerImpl_1 + " */\r\n["+String.format(LayerBuilder.PREAMBLEFMT, "file:/c:/m1.js") + "(\"<m1>foo<m1>\")]",
				output);
		System.out.println(output);

		// debugMode and showFilenames
		options.setOption("developmentMode", Boolean.FALSE);
		options.setOption("debugMode", Boolean.TRUE);
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.MODULES
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals(
				"/* " + Messages.LayerImpl_2 + " */\r\n["+String.format(LayerBuilder.PREAMBLEFMT, "file:/c:/m1.js") + "(\"<m1>foo<m1>\")]",
				output);
		System.out.println(output);
		
		// showFilenames only (no filenames output)
		options.setOption("debugMode", Boolean.FALSE);
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.MODULES
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals("[(\"<m1>foo<m1>\")]", output);
		System.out.println(output);
	
		// debugMode only (no filenames output)
		options.setOption("debugMode", Boolean.TRUE);
		requestAttributes.remove(IHttpTransport.SHOWFILENAMES_REQATTRNAME);
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.MODULES
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals(
				"/* " + Messages.LayerImpl_2 + " */\r\n[(\"<m1>foo<m1>\")]",
				output);
		System.out.println(output);
	
		// 1 required module
		options.setOption("debugMode", "false");
		builder = new LayerBuilder(mockRequest, keyGens, new HashSet<String>(Arrays.asList(new String[]{"m1"})));
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.REQUIRED
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals("[[m1]{'<m1>foo<m1>'}[m1]]", output);
		System.out.println(output);

		// two required modules
		builder = new LayerBuilder(mockRequest, keyGens, new LinkedHashSet<String>(Arrays.asList(new String[]{"m1", "m2"})));
		future1 = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.REQUIRED
		);
		
		future2 = new ModuleBuildFuture(
				"m2", 
				new FileResource(new URI("file:/c:/m2.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"))),
				ModuleSpecifier.REQUIRED
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future1, future2}));
		output = toString(reader);
		Assert.assertEquals("[[m1, m2]{'<m1>foo<m1>','<m2>bar<m2>'}[m1, m2]]", output);
		System.out.println(output);
		
		// one module and one required modules
		builder = new LayerBuilder(mockRequest, keyGens, new HashSet<String>(Arrays.asList(new String[]{"m2"})));
		future1 = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.MODULES
		);
		
		future2 = new ModuleBuildFuture(
				"m2", 
				new FileResource(new URI("file:/c:/m2.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"))),
				ModuleSpecifier.REQUIRED
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future1, future2}));
		output = toString(reader);
		Assert.assertEquals("[(\"<m1>foo<m1>\")[m2]{'<m2>bar<m2>'}[m2]]", output);
		System.out.println(output);
		
		// one required module followed by one module (throws exception)
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		future1 = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("foo"))),
				ModuleSpecifier.REQUIRED
		);
		
		future2 = new ModuleBuildFuture(
				"m2", 
				new FileResource(new URI("file:/c:/m2.js")),
				new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"))),
				ModuleSpecifier.MODULES
		);
		boolean exceptionCaught = false;
		try {
			 builder.build(Arrays.asList(new ModuleBuildFuture[]{future1, future2}));
		} catch (IllegalStateException ex) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);
		
		// Test addBefore with required module
		builder = new LayerBuilder(mockRequest, keyGens, new HashSet<String>(Arrays.asList(new String[]{"m1"})));
		mbr = new ModuleBuildReader(new StringReader("foo"));
		mbr.addBefore(
				new ModuleBuildFuture(
						"mBefore", 
						new FileResource(new URI("file:/c:/mBefore.js")),
						new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"))),
						ModuleSpecifier.BUILD_ADDED)	
		);
		
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.REQUIRED
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals("[[m1]{'<mBefore>bar<mBefore>','<m1>foo<m1>'}[m1]]", output);
		System.out.println(output);
	
		// Test addBefore with module
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		mbr = new ModuleBuildReader(new StringReader("foo"));
		mbr.addBefore(
				new ModuleBuildFuture(
						"mBefore", 
						new FileResource(new URI("file:/c:/mBefore.js")),
						new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"))),
						ModuleSpecifier.BUILD_ADDED)	
		);
		
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.MODULES
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals("[(\"<mBefore>bar<mBefore>\",\"<m1>foo<m1>\")]", output);
		System.out.println(output);
		
		// test addAfter with required module
		builder = new LayerBuilder(mockRequest, keyGens, new HashSet<String>(Arrays.asList(new String[]{"m1"})));
		mbr = new ModuleBuildReader(new StringReader("foo"));
		mbr.addAfter(
				new ModuleBuildFuture(
						"mAfter", 
						new FileResource(new URI("file:/c:/mAfter.js")),
						new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"))),
						ModuleSpecifier.BUILD_ADDED)	
		);
		
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.REQUIRED
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals("[[m1]{'<m1>foo<m1>','<mAfter>bar<mAfter>'}[m1]]", output);
		System.out.println(output);
		
		// Test addAfter with module
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		mbr = new ModuleBuildReader(new StringReader("foo"));
		mbr.addAfter(
				new ModuleBuildFuture(
						"mAfter", 
						new FileResource(new URI("file:/c:/mAfter")),
						new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"))),
						ModuleSpecifier.BUILD_ADDED)	
		);
		
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.MODULES
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals("[(\"<m1>foo<m1>\",\"<mAfter>bar<mAfter>\")]", output);
		System.out.println(output);
		
		// Make sure cache key generators are added to the keygen list
		Assert.assertEquals(0, keyGens.size());
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		List<ICacheKeyGenerator> keyGenList = Arrays.asList(new ICacheKeyGenerator[]{new ExportNamesCacheKeyGenerator()}); 
		mbr = new ModuleBuildReader(new StringReader("foo"), keyGenList, false);
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.MODULES
		);
		builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		Assert.assertEquals(1, keyGens.size());

		// required and non-required modules with before and after modules
		builder = new LayerBuilder(mockRequest, keyGens, new LinkedHashSet<String>(Arrays.asList(new String[]{"m2"})));
		mbr = new ModuleBuildReader(new StringReader("foo"));
		mbr.addAfter(
				new ModuleBuildFuture(
						"mAfter", 
						new FileResource(new URI("file:/c:/mAfter.js")),
						new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("after"))),
						ModuleSpecifier.BUILD_ADDED)	
		);
		future1 = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.MODULES
		);
		mbr = new ModuleBuildReader(new StringReader("bar"));
		mbr.addBefore(
				new ModuleBuildFuture(
						"mBefore", 
						new FileResource(new URI("file:/c:/mBefore.js")),
						new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("before"))),
						ModuleSpecifier.BUILD_ADDED)	
		);
		future2 = new ModuleBuildFuture(
				"m2", 
				new FileResource(new URI("file:/c:/m2.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.REQUIRED
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future1, future2}));
		output = toString(reader);
		Assert.assertEquals("[(\"<m1>foo<m1>\",\"<mAfter>after<mAfter>\")[m2]{'<mBefore>before<mBefore>','<m2>bar<m2>'}[m2]]", output);
		System.out.println(output);

		// Make sure NOADDMODULES request attribute disables before and after module expansion
		requestAttributes.put(IHttpTransport.NOADDMODULES_REQATTRNAME, Boolean.TRUE);
		builder = new LayerBuilder(mockRequest, keyGens, new LinkedHashSet<String>(Arrays.asList(new String[]{"m2"})));
		mbr = new ModuleBuildReader(new StringReader("foo"));
		mbr.addAfter(
				new ModuleBuildFuture(
						"mAfter", 
						new FileResource(new URI("file:/c:/mAfter.js")),
						new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("after"))),
						ModuleSpecifier.BUILD_ADDED)	
		);
		future1 = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.MODULES
		);
		mbr = new ModuleBuildReader(new StringReader("bar"));
		mbr.addBefore(
				new ModuleBuildFuture(
						"mBefore", 
						new FileResource(new URI("file:/c:/mBefore.js")),
						new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("before"))),
						ModuleSpecifier.BUILD_ADDED)	
		);
		future2 = new ModuleBuildFuture(
				"m2", 
				new FileResource(new URI("file:/c:/m2.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.REQUIRED
		);
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future1, future2}));
		output = toString(reader);
		Assert.assertEquals("[(\"<m1>foo<m1>\")[m2]{'<m2>bar<m2>'}[m2]]", output);
		System.out.println(output);
		
		
		// Make sure config notice gets added
		Ref<IConfig> configRef = new Ref<IConfig>(null);
		IConfig mockConfig = EasyMock.createNiceMock(IConfig.class);
		EasyMock.expect(mockConfig.getNotice()).andReturn("Hello World").once();
		EasyMock.replay(mockConfig);
		configRef.set(mockConfig);
		mockAggregator = TestUtils.createMockAggregator(configRef,null,null,null,mockTransport);
		EasyMock.replay(mockAggregator);
		requestAttributes.put(IAggregator.AGGREGATOR_REQATTRNAME, mockAggregator);
		builder = new LayerBuilder(mockRequest, keyGens, Collections.<String> emptySet());
		mbr = new ModuleBuildReader(new StringReader("foo"));
		future = new ModuleBuildFuture(
				"m1", 
				new FileResource(new URI("file:/c:/m1.js")),
				new CompletedFuture<ModuleBuildReader>(mbr),
				ModuleSpecifier.MODULES
		);
		
		reader = builder.build(Arrays.asList(new ModuleBuildFuture[]{future}));
		output = toString(reader);
		Assert.assertEquals("Hello World\r\n[(\"<m1>foo<m1>\")]", output);
		System.out.println(output);
	}
	
	// test notice
	
	String toString(Reader reader) throws IOException {
		Writer writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		return writer.toString();
		
	}

}
