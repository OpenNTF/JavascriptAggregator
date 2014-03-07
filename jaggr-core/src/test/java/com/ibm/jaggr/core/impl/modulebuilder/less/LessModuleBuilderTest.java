package com.ibm.jaggr.core.impl.modulebuilder.less;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.config.ConfigImpl;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.StringResource;
import com.ibm.jaggr.core.test.TestUtils;
import com.ibm.jaggr.core.util.CopyUtil;
import junit.framework.Assert;
import org.easymock.EasyMock;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mozilla.javascript.Scriptable;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LessModuleBuilderTest extends EasyMock {
	static File tmpdir;
	static File testdir;
	public static final String LESS = "@import \"colors.less\";\n\nbody{\n  " +
			"background: @mainColor;\n}";

	Map<String, String[]> requestParams = new HashMap<String, String[]>();
	Map<String, Object> requestAttributes = new HashMap<String, Object>();
	Scriptable configScript;
	IAggregator mockAggregator;
	HttpServletRequest mockRequest;
	LessModuleBuilderTester builder = new LessModuleBuilderTester();
	List<ICacheKeyGenerator> keyGens = builder.getCacheKeyGenerators
			(mockAggregator);
	long seq = 1;

	class LessModuleBuilderTester extends LessModuleBuilder {
		@Override
		protected Reader getContentReader(
				String mid,
				IResource resource,
				HttpServletRequest req,
				List<ICacheKeyGenerator> keyGens
		) throws IOException {
			return super.getContentReader(mid, resource, req, keyGens);
		}
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		tmpdir = new File(System.getProperty("java.io.tmpdir"));
		testdir = new File(tmpdir, "LessModuleBuilderTest");
		testdir.mkdir();
		// create file to import
		CopyUtil.copy("@mainColor: #FF0000;", new FileWriter(new File(testdir,
				"colors.less")));
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TestUtils.deleteRecursively(testdir);
	}

	@Before
	public void setUp() throws Exception {
		mockAggregator = TestUtils.createMockAggregator();
		mockRequest = TestUtils.createMockRequest(mockAggregator,
				requestAttributes, requestParams, null, null);
		replay(mockRequest);
		replay(mockAggregator);
		IConfig cfg = new ConfigImpl(mockAggregator, tmpdir.toURI(), "{}");
		configScript = cfg.getRawConfig();
	}

	@Test
	public void testImportWithCompression() throws Exception {
		String output;
		URI resUri = testdir.toURI();
		configScript.put(LessModuleBuilder.COMPRESS_PARAM, configScript,
				Boolean.TRUE);
		IConfig config = new ConfigImpl(mockAggregator, tmpdir.toURI(),
				configScript);
		builder.configLoaded(config, seq++);
		output = buildLess(new StringResource(LESS, resUri));
		Assert.assertEquals("body{background:#f00}", output);
	}

	@Test
	public void testImportWithoutCompression() throws Exception {
		String output;
		URI resUri = testdir.toURI();
		configScript.put(LessModuleBuilder.COMPRESS_PARAM, configScript,
				Boolean.FALSE);
		IConfig config = new ConfigImpl(mockAggregator, tmpdir.toURI(),
				configScript);
		builder.configLoaded(config, seq++);
		output = buildLess(new StringResource(LESS, resUri));
		Assert.assertEquals("body {\n  background: #ff0000;\n}\n", output);
	}

	private String buildLess(StringResource less) throws IOException {
		Reader reader = builder.getContentReader("test", less, mockRequest,
				keyGens);
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		String output = writer.toString();
		System.out.println(output);
		return output;
	}
}
