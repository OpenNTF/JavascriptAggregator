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

package com.ibm.jaggr.core.impl.layer;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.cache.ICache;
import com.ibm.jaggr.core.cachekeygenerator.ExportNamesCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.impl.AggregatorLayerListener;
import com.ibm.jaggr.core.impl.layer.ModuleList.ModuleListEntry;
import com.ibm.jaggr.core.impl.module.ModuleImpl;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.layer.ILayerListener.EventType;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.modulebuilder.ModuleBuildFuture;
import com.ibm.jaggr.core.modulebuilder.SourceMap;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.test.TestCacheManager;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType;
import com.ibm.jaggr.core.transport.IHttpTransport.ModuleInfo;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.RequestUtil;

import com.google.debugging.sourcemap.SourceMapGeneratorV3;

import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.wink.json4j.JSONObject;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

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
							return "\"<"+((ModuleInfo)arg).getModuleId()+">";
						case BEFORE_SUBSEQUENT_MODULE:
							return ",\"<"+((ModuleInfo)arg).getModuleId()+">";
						case AFTER_MODULE:
							return "<"+((ModuleInfo)arg).getModuleId()+">\"";
						case BEGIN_LAYER_MODULES:
							return arg.toString()+"{";
						case END_LAYER_MODULES:
							return "}"+arg;
						case BEFORE_FIRST_LAYER_MODULE:
							return "'<"+((ModuleInfo)arg).getModuleId()+">";
						case BEFORE_SUBSEQUENT_LAYER_MODULE:
							return ",'<"+((ModuleInfo)arg).getModuleId()+">";
						case AFTER_LAYER_MODULE:
							return "<"+((ModuleInfo)arg).getModuleId()+">'";
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
		final HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator, requestAttributes);
		EasyMock.replay(mockRequest);
		EasyMock.replay(mockAggregator);
		List<ICacheKeyGenerator> keyGens = new LinkedList<ICacheKeyGenerator>();
		ModuleList moduleList;
		Map<String, String[]> content = new HashMap<String, String[]>();
		content.put("s1", new String[]{"script1", "sourceMap1"});
		content.put("s2", new String[]{"script2", "sourceMap2"});
		content.put("m1", new String[]{"foo", null});
		content.put("m2", new String[]{"bar", null});

		// Single script file
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("s1", new URI("file:/c:/a1.js")), ModuleSpecifier.SCRIPTS)
		}));
		TestLayerBuilder builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		String output = builder.build();
		System.out.println(output);
		Assert.assertEquals("[script1]", output);

		// Single module specified with 'modules' query arg
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES)
		}));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		System.out.println(output);
		Assert.assertEquals("[(\"<m1>foo<m1>\")]", output);

		// Two modules specified with 'modules' query arg

		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.MODULES),
		}));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		System.out.println(output);
		Assert.assertEquals("[(\"<m1>foo<m1>\",\"<m2>bar<m2>\")]", output);

		// two scripts and two modules
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("s1", new URI("file:/c:/s1.js")), ModuleSpecifier.SCRIPTS),
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("s2", new URI("file:/c:/s2.js")), ModuleSpecifier.SCRIPTS),
		}));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		System.out.println(output);
		Assert.assertEquals("[script1script2(\"<m1>foo<m1>\",\"<m2>bar<m2>\")]", output);

		// Test developmentMode and showFilenames
		IOptions options = mockAggregator.getOptions();
		options.setOption("developmentMode", "true");
		requestAttributes.put(IHttpTransport.SHOWFILENAMES_REQATTRNAME, Boolean.TRUE);
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES)
		}));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals(
				"/* " + Messages.LayerImpl_1 + " */\r\n[("+String.format(AggregatorLayerListener.PREAMBLEFMT, "file:/c:/m1.js") + "\"<m1>foo<m1>\")]",
				output);
		System.out.println(output);

		// debugMode and showFilenames
		options.setOption("developmentMode", Boolean.FALSE);
		options.setOption("debugMode", Boolean.TRUE);
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals(
				"/* " + Messages.LayerImpl_2 + " */\r\n[("+String.format(AggregatorLayerListener.PREAMBLEFMT, "file:/c:/m1.js") + "\"<m1>foo<m1>\")]",
				output);
		System.out.println(output);

		// showFilenames only (no filenames output)
		options.setOption("debugMode", Boolean.FALSE);
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals("[(\"<m1>foo<m1>\")]", output);
		System.out.println(output);

		// debugMode only (no filenames output)
		options.setOption("debugMode", Boolean.TRUE);
		requestAttributes.remove(IHttpTransport.SHOWFILENAMES_REQATTRNAME);
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals(
				"/* " + Messages.LayerImpl_2 + " */\r\n[(\"<m1>foo<m1>\")]",
				output);
		System.out.println(output);

		// 1 required module
		options.setOption("debugMode", "false");
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.LAYER)
		}));
		moduleList.setRequiredModules(new HashSet<String>(Arrays.asList(new String[]{"m1"})));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals("[[m1]{'<m1>foo<m1>'}[m1]]", output);
		System.out.println(output);

		// two required modules
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.LAYER),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.LAYER),
		}));
		moduleList.setRequiredModules(new HashSet<String>(Arrays.asList(new String[]{"m1", "m2"})));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals("[[m1, m2]{'<m1>foo<m1>','<m2>bar<m2>'}[m1, m2]]", output);
		System.out.println(output);

		// one module and one required modules
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.LAYER),
		}));
		moduleList.setRequiredModules(new HashSet<String>(Arrays.asList(new String[]{"m2"})));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals("[[m2]{'<m2>bar<m2>'}[m2](\"<m1>foo<m1>\")]", output);
		System.out.println(output);

		// one required module followed by one module
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.LAYER),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.MODULES),
		}));
		moduleList.setRequiredModules(new HashSet<String>(Arrays.asList(new String[]{"m1"})));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals("[[m1]{'<m1>foo<m1>'}[m1](\"<m2>bar<m2>\")]", output);
		System.out.println(output);

		// one script, one module and one layer module
		// one required module followed by one module
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.LAYER),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("s1", new URI("file:/c:/s1.js")), ModuleSpecifier.SCRIPTS)
		}));
		moduleList.setRequiredModules(new HashSet<String>(Arrays.asList(new String[]{"m1"})));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		output = builder.build();
		Assert.assertEquals("[script1[m1]{'<m1>foo<m1>'}[m1](\"<m2>bar<m2>\")]", output);
		System.out.println(output);


		// test addExtraBuild with required module
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.LAYER),
		}));
		moduleList.setRequiredModules(new HashSet<String>(Arrays.asList(new String[]{"m1"})));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content) {
			@Override
			protected List<ModuleBuildFuture> collectFutures(ModuleList moduleList, HttpServletRequest request)
					throws IOException {
				List<ModuleBuildFuture> futures = super.collectFutures(moduleList, request);
				try {
					ModuleBuildReader mbr = futures.get(0).get();
					mbr.addExtraBuild(
							new ModuleBuildFuture(
									new ModuleImpl("mExtra", new URI("file:/c:/mExtra.js")),
									new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"), true)),
									ModuleSpecifier.BUILD_ADDED)
							);
				} catch (Exception e) {
					throw new IOException(e.getMessage(), e);
				}
				return futures;
			}
		};
		output = builder.build();
		Assert.assertEquals("[[m1]{'<m1>foo<m1>','<mExtra>bar<mExtra>'}[m1]]", output);
		System.out.println(output);

		// Test addExtraBuild with module
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES)
		}));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content) {
			@Override
			protected List<ModuleBuildFuture> collectFutures(ModuleList moduleList, HttpServletRequest request)
					throws IOException {
				List<ModuleBuildFuture> futures = super.collectFutures(moduleList, request);
				try {
					ModuleBuildReader mbr = futures.get(0).get();
					mbr.addExtraBuild(
							new ModuleBuildFuture(
									new ModuleImpl("mExtra", new URI("file:/c:/mExtra.js")),
									new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("bar"), true)),
									ModuleSpecifier.BUILD_ADDED)
							);
				} catch (Exception e) {
					throw new IOException(e.getMessage(), e);
				}
				return futures;
			}
		};
		output = builder.build();
		Assert.assertEquals("[[]{'<mExtra>bar<mExtra>'}[](\"<m1>foo<m1>\")]", output);
		System.out.println(output);

		// Make sure cache key generators are added to the keygen list
		Assert.assertEquals(0, keyGens.size());
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES),
		}));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		Map<String, List<ICacheKeyGenerator>> keyGenMap = new HashMap<String, List<ICacheKeyGenerator>>();
		keyGenMap.put("m1", Arrays.asList(new ICacheKeyGenerator[]{new ExportNamesCacheKeyGenerator()}));
		builder.setKeyGens(keyGenMap);
		builder.build();
		Assert.assertEquals(1, keyGens.size());

		// required and non-required modules with extra modules
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.LAYER)
		}));
		moduleList.setRequiredModules(new HashSet<String>(Arrays.asList(new String[]{"m2"})));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content) {
			@Override
			protected List<ModuleBuildFuture> collectFutures(ModuleList moduleList, HttpServletRequest request)
					throws IOException {
				List<ModuleBuildFuture> futures = super.collectFutures(moduleList, request);
				try {
					ModuleBuildReader mbr = futures.get(0).get();
					mbr.addExtraBuild(
							new ModuleBuildFuture(
									new ModuleImpl("mExtra1", new URI("file:/c:/mExtra1.js")),
									new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("extra1"), true)),
									ModuleSpecifier.BUILD_ADDED)
							);
					mbr = futures.get(1).get();
					mbr.addExtraBuild(
							new ModuleBuildFuture(
									new ModuleImpl("mExtra2", new URI("file:/c:/mExtra2.js")),
									new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("extra2"), true)),
									ModuleSpecifier.BUILD_ADDED)
							);
				} catch (Exception e) {
					throw new IOException(e.getMessage(), e);
				}
				return futures;
			}
		};
		output = builder.build();
		Assert.assertEquals("[[m2]{'<mExtra1>extra1<mExtra1>','<m2>bar<m2>','<mExtra2>extra2<mExtra2>'}[m2](\"<m1>foo<m1>\")]", output);
		System.out.println(output);

		// Make sure NOADDMODULES request attribute disables extra module expansion
		requestAttributes.put(IHttpTransport.NOADDMODULES_REQATTRNAME, Boolean.TRUE);
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.LAYER)
		}));
		moduleList.setRequiredModules(new HashSet<String>(Arrays.asList(new String[]{"m2"})));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content) {
			@Override
			protected List<ModuleBuildFuture> collectFutures(ModuleList moduleList, HttpServletRequest request)
					throws IOException {
				List<ModuleBuildFuture> futures = super.collectFutures(moduleList, request);
				try {
					ModuleBuildReader mbr = futures.get(0).get();
					mbr.addExtraBuild(
							new ModuleBuildFuture(
									new ModuleImpl("mExtra1", new URI("file:/c:/mExtra1.js")),
									new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("extra1"), true)),
									ModuleSpecifier.BUILD_ADDED)
							);
					mbr = futures.get(1).get();
					mbr.addExtraBuild(
							new ModuleBuildFuture(
									new ModuleImpl("mExtra2", new URI("file:/c:/mExtra2.js")),
									new CompletedFuture<ModuleBuildReader>(new ModuleBuildReader(new StringReader("extra2"), true)),
									ModuleSpecifier.BUILD_ADDED)
							);
				} catch (Exception e) {
					throw new IOException(e.getMessage(), e);
				}
				return futures;
			}
		};
		output = builder.build();
		Assert.assertEquals("[[m2]{'<m2>bar<m2>'}[m2](\"<m1>foo<m1>\")]", output);
		System.out.println(output);


		// Make sure listener prologue, epilogue and interlude gets added
		requestAttributes.put(IHttpTransport.NOADDMODULES_REQATTRNAME, Boolean.TRUE);
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.MODULES)
		}));
		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content) {
			@Override
			public String notifyLayerListeners(ILayerListener.EventType type, HttpServletRequest request, IModule module) throws IOException {
				String result = "<no type>";
				@SuppressWarnings("unchecked")
				Set<String> layerDependentFeatures = (Set<String>)request.getAttribute(ILayer.DEPENDENT_FEATURES);
				if (type == EventType.BEGIN_LAYER) {
					layerDependentFeatures.add("prologueFeature");
					result = "prologue";
				} else if (type == EventType.END_LAYER) {
					layerDependentFeatures.add("epilogueFeature");
					result = "epilogue";
				} else if (type == EventType.BEGIN_MODULE) {
					result = "interlude " + module.getURI();
					layerDependentFeatures.add("interludeFeature");
				} else if (type == EventType.BEGIN_AMD) {
					layerDependentFeatures.add("AMDFeature");
					result = "AMD";
				}
				return result;
			}
			@Override
			protected void processReader(IModule mid, ModuleBuildReader reader) throws IOException {
				super.processReader(mid, reader);
				@SuppressWarnings("unchecked")
				Set<String> dependentFeatures = (Set<String>)mockRequest.getAttribute(ILayer.DEPENDENT_FEATURES);
				dependentFeatures.add("processReader");
			}
		};
		output = builder.build();
		Assert.assertEquals("prologue[AMD(interlude file:/c:/m1.js\"<m1>foo<m1>\"interlude file:/c:/m2.js,\"<m2>bar<m2>\")epilogue]", output);
		Assert.assertEquals(
				(Set<String>)new HashSet<String>(Arrays.asList(new String[]{
						"prologueFeature", "AMDFeature", "interludeFeature", "processReader", "epilogueFeature"
				})),
				moduleList.getDependentFeatures());
		System.out.println(output);

		// Test source map support
		requestAttributes.put(IHttpTransport.GENERATESOURCEMAPS_REQATTRNAME, true);
		mockAggregator.getOptions().setOption(IOptions.SOURCE_MAPS, true);
		moduleList = new ModuleList(Arrays.asList(new ModuleListEntry[] {
				new ModuleListEntry(new ModuleImpl("s1", new URI("file:/c:/s1.js")), ModuleSpecifier.SCRIPTS),
				new ModuleListEntry(new ModuleImpl("m1", new URI("file:/c:/m1.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("m2", new URI("file:/c:/m2.js")), ModuleSpecifier.MODULES),
				new ModuleListEntry(new ModuleImpl("s2", new URI("file:/c:/s2.js")), ModuleSpecifier.SCRIPTS),
		}));

		builder = new TestLayerBuilder(mockRequest, keyGens, moduleList, content);
		SourceMapGeneratorV3 mockGenerator = EasyMock.createMock(SourceMapGeneratorV3.class);
		Whitebox.setInternalState(builder, "smGen", mockGenerator);
		final List<String> maps = new ArrayList<String>();
		mockGenerator.mergeMapSection(EasyMock.anyInt(), EasyMock.anyInt(), EasyMock.isA(String.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override public Void answer() throws Throwable {
				maps.add((String)EasyMock.getCurrentArguments()[2]);
				return null;
			}
		}).times(2);
		mockGenerator.appendTo(EasyMock.isA(Appendable.class), EasyMock.isA(String.class));
		EasyMock.expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override
			public Void answer() throws Throwable {
				Appendable out = (Appendable)EasyMock.getCurrentArguments()[0];
				out.append("{sources:[\"s1\",\"s2\"]}");
				return null;
			}
		}).once();
		EasyMock.replay(mockGenerator);
		output = builder.build();
		System.out.println(output);
		Assert.assertEquals(Arrays.asList("sourceMap1", "sourceMap2"), maps);
		System.out.println(builder.getSourceMap());
		Assert.assertEquals("[script1script2(\"<m1>foo<m1>\",\"<m2>bar<m2>\")]\n//# sourceMappingURL="+ILayer.SOURCEMAP_RESOURSE_PATHCOMP, output);
		Assert.assertEquals(
				new JSONObject("{\"sourcesContent\":[\"script1\",\"script2\"],\"sources\":[\"s1\",\"s2\"]}"),
				new JSONObject(builder.getSourceMap().substring(LayerBuilder.SOURCEMAP_XSSI_PREAMBLE.length())));
	}

	@Test
	public void testCollectFutures() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator);
		ICache mockCache = createMock(ICache.class);
		IModuleCache mockModuleCache = createMock(IModuleCache.class);
		expect(mockCache.getModules()).andReturn(mockModuleCache).anyTimes();
		expect(mockModuleCache.getBuild(eq(mockRequest), isA(IModule.class))).andAnswer(new IAnswer<Future<ModuleBuildReader>>() {
			@Override public Future<ModuleBuildReader> answer() throws Throwable {
				IModule module = (IModule)getCurrentArguments()[1];
				if (module.getModuleName().equals("notfound")) {
					throw new NotFoundException(module.getModuleName());
				}
				return new CompletedFuture<ModuleBuildReader>(
						new ModuleBuildReader(new StringReader(module.getModuleId() + " build"), true)
						);
			}
		}).anyTimes();
		replay(mockAggregator, mockRequest, mockCache, mockModuleCache);
		((TestCacheManager)mockAggregator.getCacheManager()).setCache(mockCache);
		ModuleList moduleList = new ModuleList();
		IModule module1 = new ModuleImpl("module1", new URI("file://module1.js")),
				module2 = new ModuleImpl("module2", new URI("file://module2.js")),
				notfound = new ModuleImpl("notfound", new URI("file://notfound.js"));
		moduleList.add(new ModuleListEntry(module1, ModuleSpecifier.MODULES, true));
		moduleList.add(new ModuleListEntry(module2, ModuleSpecifier.LAYER, true));
		moduleList.add(new ModuleListEntry(notfound, ModuleSpecifier.LAYER, true /* server expanded */));
		LayerBuilder builder = new LayerBuilder(mockRequest, null, moduleList);
		List<ModuleBuildFuture> futures = builder.collectFutures(moduleList, mockRequest);
		Assert.assertEquals(2, futures.size());
		ModuleBuildFuture future = futures.get(0);
		Assert.assertEquals("module1", future.getModule().getModuleId());
		Assert.assertEquals(ModuleSpecifier.MODULES, future.getModuleSpecifier());
		Assert.assertEquals("module1 build", toString(future.get()));
		future = futures.get(1);
		Assert.assertEquals("module2", future.getModule().getModuleId());
		Assert.assertEquals(ModuleSpecifier.LAYER, future.getModuleSpecifier());
		Assert.assertEquals("module2 build", toString(future.get()));

		// now make sure exception is thrown for not found module that isn't server expanded
		moduleList = new ModuleList();
		moduleList.add(new ModuleListEntry(module1, ModuleSpecifier.LAYER, true));
		moduleList.add(new ModuleListEntry(module2, ModuleSpecifier.LAYER, true));
		moduleList.add(new ModuleListEntry(notfound, ModuleSpecifier.MODULES, false /* not server expanded */));
		builder = new LayerBuilder(mockRequest, null, moduleList);
		boolean exceptionCaught = false;
		try {
			builder.collectFutures(moduleList, mockRequest);
		} catch (NotFoundException e) {
			exceptionCaught = true;
		}
		Assert.assertTrue(exceptionCaught);
	}

	@Test
	public void testNotifyLayerListeners() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		final IPlatformServices mockPlatformServices = createMock(IPlatformServices.class);
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator);
		IServiceReference mockServiceRef1 = createMock(IServiceReference.class),
				mockServiceRef2 = createMock(IServiceReference.class);
		IServiceReference[] serviceReferences = new IServiceReference[]{mockServiceRef1, mockServiceRef2};
		final String[] listener1Result = new String[1], listener2Result = new String[1];
		final List<IModule> expectedModuleList = new ArrayList<IModule>();
		final Set<String> dependentFeatures1 = new HashSet<String>(),
				dependentFeatures2 = new HashSet<String>();

		final List<String> checkpoints = new ArrayList<String>();

		ModuleList moduleList = new ModuleList();
		IModule module1 = new ModuleImpl("module1", new URI("file://module1.js")),
				module2 = new ModuleImpl("module2", new URI("file://module2.js"));
		moduleList.add(new ModuleListEntry(module1, ModuleSpecifier.MODULES));
		moduleList.add(new ModuleListEntry(module2, ModuleSpecifier.LAYER));
		ILayerListener testListener1 = new ILayerListener() {
			@Override public String layerBeginEndNotifier(EventType type, HttpServletRequest request, List<IModule> modules, Set<String> dependentFeatures) {
				Assert.assertEquals(expectedModuleList, modules);
				dependentFeatures.addAll(dependentFeatures1);
				checkpoints.add("Listener1_" + type.toString());
				return listener1Result[0];
			}
		}, testListener2 = new ILayerListener() {
			@Override public String layerBeginEndNotifier(EventType type, HttpServletRequest request, List<IModule> modules, Set<String> dependentFeatures) {
				Assert.assertEquals(expectedModuleList, modules);
				dependentFeatures.addAll(dependentFeatures2);
				checkpoints.add("Listener2_" + type.toString());
				return listener2Result[0];
			}
		};

		expect(mockPlatformServices.getServiceReferences(ILayerListener.class.getName(), "(name=test)")).andReturn(serviceReferences).anyTimes();
		expect(mockPlatformServices.getService(mockServiceRef1)).andReturn(testListener1);
		expect(mockPlatformServices.getService(mockServiceRef2)).andReturn(testListener2);
		expect(mockPlatformServices.ungetService(mockServiceRef1)).andReturn(true);
		expect(mockPlatformServices.ungetService(mockServiceRef2)).andReturn(true);
		expect(mockAggregator.getPlatformServices()).andReturn(mockPlatformServices).anyTimes();
		replay(mockAggregator, mockRequest,  mockServiceRef1, mockServiceRef2, mockPlatformServices);

		LayerBuilder builder = new LayerBuilder(mockRequest, null, moduleList) {
			@Override
			public String notifyLayerListeners(ILayerListener.EventType type, HttpServletRequest request, IModule module) throws IOException {
				return super.notifyLayerListeners(type, request, module);
			}
		};
		listener1Result[0] = "foo";
		listener2Result[0] = "bar";
		expectedModuleList.addAll(moduleList.getModules());

		// Test BEGIN_LAYER with two string contributions
		String result = builder.notifyLayerListeners(EventType.BEGIN_LAYER, mockRequest, module1);
		Assert.assertEquals("foobar", result);
		Assert.assertEquals(0,  moduleList.getDependentFeatures().size());
		Assert.assertEquals(Arrays.asList(new String[]{"Listener1_BEGIN_LAYER", "Listener2_BEGIN_LAYER"}), checkpoints);

		reset(mockPlatformServices);
		expect(mockPlatformServices.getServiceReferences(ILayerListener.class.getName(), "(name=test)")).andReturn(serviceReferences).anyTimes();
		expect(mockPlatformServices.getService(mockServiceRef1)).andReturn(testListener1);
		expect(mockPlatformServices.getService(mockServiceRef2)).andReturn(testListener2);
		expect(mockPlatformServices.ungetService(mockServiceRef1)).andReturn(true);
		expect(mockPlatformServices.ungetService(mockServiceRef2)).andReturn(true);
		replay(mockPlatformServices);
		listener2Result[0] = null;
		checkpoints.clear();
		// Test END_LAYER with one null and one string contributions
		result = builder.notifyLayerListeners(EventType.END_LAYER, mockRequest, module1);
		Assert.assertEquals("foo", result);
		Assert.assertEquals(0,  moduleList.getDependentFeatures().size());
		Assert.assertEquals(Arrays.asList(new String[]{"Listener2_END_LAYER", "Listener1_END_LAYER"}), checkpoints);  // ensure reverse order

		reset(mockPlatformServices);
		expect(mockPlatformServices.getServiceReferences(ILayerListener.class.getName(), "(name=test)")).andReturn(serviceReferences).anyTimes();
		expect(mockPlatformServices.getService(mockServiceRef1)).andReturn(testListener1);
		expect(mockPlatformServices.getService(mockServiceRef2)).andReturn(testListener2);
		expect(mockPlatformServices.ungetService(mockServiceRef1)).andReturn(true);
		expect(mockPlatformServices.ungetService(mockServiceRef2)).andReturn(true);
		replay(mockPlatformServices);
		listener1Result[0] = null;
		checkpoints.clear();
		// Test END_LAYER with two null contributions
		result = builder.notifyLayerListeners(EventType.END_LAYER, mockRequest, module1);
		Assert.assertEquals("", result);
		Assert.assertEquals(0,  moduleList.getDependentFeatures().size());
		Assert.assertEquals(Arrays.asList(new String[]{"Listener2_END_LAYER", "Listener1_END_LAYER"}), checkpoints);  // ensure reverse order

		reset(mockPlatformServices);
		expect(mockPlatformServices.getServiceReferences(ILayerListener.class.getName(), "(name=test)")).andReturn(serviceReferences).anyTimes();
		expect(mockPlatformServices.getService(mockServiceRef1)).andReturn(testListener1);
		expect(mockPlatformServices.getService(mockServiceRef2)).andReturn(testListener2);
		expect(mockPlatformServices.ungetService(mockServiceRef1)).andReturn(true);
		expect(mockPlatformServices.ungetService(mockServiceRef2)).andReturn(true);
		replay(mockPlatformServices);
		listener1Result[0] = "foo";
		listener2Result[0] = "bar";
		expectedModuleList.clear();
		expectedModuleList.add(module2);
		checkpoints.clear();
		// Test BEGIN_MODULE with two string contributions
		result = builder.notifyLayerListeners(EventType.BEGIN_MODULE, mockRequest, module2);
		Assert.assertEquals("foobar", result);
		Assert.assertEquals(0,  moduleList.getDependentFeatures().size());
		Assert.assertEquals(Arrays.asList(new String[]{"Listener1_BEGIN_MODULE", "Listener2_BEGIN_MODULE"}), checkpoints);

		reset(mockPlatformServices);
		expect(mockPlatformServices.getServiceReferences(ILayerListener.class.getName(), "(name=test)")).andReturn(serviceReferences).anyTimes();
		expect(mockPlatformServices.getService(mockServiceRef1)).andReturn(testListener1);
		expect(mockPlatformServices.ungetService(mockServiceRef1)).andReturn(true);
		replay(mockPlatformServices);
		// Test exception
		try {
			result = builder.notifyLayerListeners(EventType.BEGIN_MODULE, mockRequest, module1);
			Assert.fail();
		} catch (AssertionFailedError e) {
		}
		// verifies that bundleContext.getService()/ungetService()
		// are matched even though exception was thrown by listener
		reset(mockPlatformServices);
		expect(mockPlatformServices.getServiceReferences(ILayerListener.class.getName(), "(name=test)")).andReturn(serviceReferences).anyTimes();
		expect(mockPlatformServices.getService(mockServiceRef1)).andReturn(testListener1);
		expect(mockPlatformServices.getService(mockServiceRef2)).andReturn(testListener2);
		expect(mockPlatformServices.ungetService(mockServiceRef1)).andReturn(true);
		expect(mockPlatformServices.ungetService(mockServiceRef2)).andReturn(true);
		replay(mockPlatformServices);
		dependentFeatures1.add("feature1");
		dependentFeatures2.add("feature2");
		checkpoints.clear();
		// Verify that dependent features can be updated by listeners
		result = builder.notifyLayerListeners(EventType.BEGIN_MODULE, mockRequest, module2);
		Assert.assertEquals("foobar", result);
		Assert.assertEquals(new HashSet<String>(Arrays.asList(new String[]{"feature1", "feature2"})), builder.getDepenedentFeatures());
		Assert.assertEquals(Arrays.asList(new String[]{"Listener1_BEGIN_MODULE", "Listener2_BEGIN_MODULE"}), checkpoints);
	}

	@Test
	public void testAppendSourcesMappingURL() throws Exception {
		IAggregator mockAggregator = TestUtils.createMockAggregator();
		HttpServletRequest mockRequest = TestUtils.createMockRequest(mockAggregator);
		final MutableObject<String> requestUri = new MutableObject<String>();
		final MutableObject<String> queryString = new MutableObject<String>();
		EasyMock.expect(mockRequest.getRequestURI()).andAnswer(new IAnswer<String>() {
			@Override public String answer() throws Throwable {
				return requestUri.getValue();
			}
		}).anyTimes();
		EasyMock.expect(mockRequest.getQueryString()).andAnswer(new IAnswer<String>() {
			@Override public String answer() throws Throwable {
				return queryString.getValue();
			}

		}).anyTimes();
		EasyMock.replay(mockRequest);
		EasyMock.replay(mockAggregator);
		List<ICacheKeyGenerator> keyGens = new ArrayList<ICacheKeyGenerator>();
		ModuleList modules = new ModuleList();
		LayerBuilder builder = new LayerBuilder(mockRequest, keyGens, modules);
		mockRequest.setAttribute(IHttpTransport.GENERATESOURCEMAPS_REQATTRNAME, true);
		String result = builder.getSourcesMappingEpilogue();
		Assert.assertEquals("//# sourceMappingURL=_sourcemap", result);

		requestUri.setValue("/");
		result = builder.getSourcesMappingEpilogue();
		Assert.assertEquals("//# sourceMappingURL=_sourcemap", result);

		requestUri.setValue("/foo");
		result = builder.getSourcesMappingEpilogue();
		Assert.assertEquals("//# sourceMappingURL=foo/_sourcemap", result);

		requestUri.setValue("/foo/");
		queryString.setValue("debug=true&sourcemaps=1");
		result = builder.getSourcesMappingEpilogue();
		Assert.assertEquals("//# sourceMappingURL=_sourcemap?debug=true&sourcemaps=1", result);

		requestUri.setValue("/foo/bar");
		result = builder.getSourcesMappingEpilogue();
		Assert.assertEquals("//# sourceMappingURL=bar/_sourcemap?debug=true&sourcemaps=1", result);

		requestUri.setValue("/foo/bar/");
		result = builder.getSourcesMappingEpilogue();
		Assert.assertEquals("//# sourceMappingURL=_sourcemap?debug=true&sourcemaps=1", result);


	}

	String toString(Reader reader) throws IOException {
		Writer writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		return writer.toString();

	}

	static class TestLayerBuilder extends LayerBuilder {
		Map<String, String[]> content;
		HttpServletRequest request;
		List<ICacheKeyGenerator> keyGens;
		ModuleList moduleList;
		Map<String, List<ICacheKeyGenerator>> mbrKeygens = null;
		TestLayerBuilder(HttpServletRequest request, List<ICacheKeyGenerator> keyGens, ModuleList moduleList, Map<String, String[]> content) {
			super(request, keyGens, moduleList);
			this.content = content;
			this.request = request;
			this.keyGens = keyGens;
			this.moduleList = moduleList;
		}

		void setKeyGens(Map<String, List<ICacheKeyGenerator>> keyGenerators) {
			mbrKeygens = keyGenerators;
		}

		@Override
		protected List<ModuleBuildFuture> collectFutures(ModuleList moduleList, HttpServletRequest request)
				throws IOException {
			List<ModuleBuildFuture> result = new ArrayList<ModuleBuildFuture>();
			for (ModuleListEntry entry : moduleList) {
				String mid = entry.getModule().getModuleId();
				List<ICacheKeyGenerator> mbrKeygen = null;
				if (mbrKeygens != null) {
					mbrKeygen = mbrKeygens.get(mid);
				}
				ModuleBuildReader mbr = new ModuleBuildReader(new StringReader(content.get(mid)[0]), true, mbrKeygen, null);
				if (RequestUtil.isSourceMapsEnabled(request)) {
					String[] data = content.get(mid);
					if (data[1] != null) {
						mbr.setSourceMap(new SourceMap(mid, data[0], data[1]));
					}
				}
				ModuleBuildFuture future = new ModuleBuildFuture(
						new ModuleImpl(mid, entry.getModule().getURI()),
						new CompletedFuture<ModuleBuildReader>(mbr),
						entry.getSource()
						);
				result.add(future);
			}
			return result;
		}

		@Override
		protected String notifyLayerListeners(ILayerListener.EventType type, HttpServletRequest request, IModule module) throws IOException {
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			ILayerListener listener = new AggregatorLayerListener(aggr);
			String result = listener.layerBeginEndNotifier(type, request,
					type == EventType.BEGIN_MODULE ?
							Arrays.asList(new IModule[]{module}) :
								moduleList.getModules(),
								new HashSet<String>());
			return result != null ? result : "";
		}
	}
}
