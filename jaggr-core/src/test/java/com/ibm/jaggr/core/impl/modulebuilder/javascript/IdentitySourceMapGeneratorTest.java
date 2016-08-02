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

package com.ibm.jaggr.core.impl.modulebuilder.javascript;

import com.ibm.jaggr.core.util.CompilerUtil;

import com.google.debugging.sourcemap.SourceMapConsumerV3;
import com.google.debugging.sourcemap.proto.Mapping.OriginalMapping;
import com.google.javascript.jscomp.CompilerOptions;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;

public class IdentitySourceMapGeneratorTest {
	static final String source =
			"/**\n" +
			" * Comment\n" +
			" */\n" +
			"define([\r\n" +
			"  'foo',\r\n" +
			"  'bar'\n" +
			"], function(foo, bar) {\n" +
			"  var unionSplit = /([^\\s,](?:\"(?:\\\\.|[^\"])+\"|'(?:\\\\.|[^'])+'|[^,])*)/g;\n" +
			"  // another comment\n" +
			"  console.log(foo);\n" +
			"  if(!foo){\n" +
			"    throw new Error(\"No region setting for \" + bar)\n" +
			"  }\n" +
			"});";


	@Test
	public void test() throws Exception {
		CompilerOptions options = CompilerUtil.getDefaultOptions();
		IdentitySourceMapGenerator gen = new IdentitySourceMapGenerator("test", source, options);
		String sm = gen.generateSourceMap();
		System.out.println(sm);
		SourceMapConsumerV3 smcon = new SourceMapConsumerV3();
		smcon.parse(sm);
		Collection<String> sources = smcon.getOriginalSources();
		Assert.assertEquals(Arrays.asList("test"), sources);
		int[][] expectedMappings = new int[][]{
			new int[]{4,1},
			new int[]{4,8},
			new int[]{5,3},
			new int[]{6,3},
			new int[]{7,4},
			new int[]{7,13},
			new int[]{7,18},
			new int[]{7,23},
			new int[]{8,3},
			new int[]{8,7},
			new int[]{8,20},
			new int[]{10,3},
			new int[]{10,11},
			new int[]{10,15},
			new int[]{11,3},
			new int[]{11,7},
			new int[]{12,15},
			new int[]{12,21},
			new int[]{12,48}
		};
		for (int[] expected : expectedMappings) {
			OriginalMapping mapping = smcon.getMappingForLine(expected[0], expected[1]);
			Assert.assertEquals(expected[0], mapping.getLineNumber());
			Assert.assertEquals(expected[1], mapping.getColumnPosition());
		}
	}
}
