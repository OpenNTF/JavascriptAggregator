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

import com.ibm.jaggr.core.util.JSSource;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.Compiler.CodeBuilder;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

import org.junit.Test;

import junit.framework.Assert;

public class ExportModuleNameCompilerPassTest {

	private ExportModuleNameCompilerPass pass;

	@Test
	public void testProcess() {
		pass =  new ExportModuleNameCompilerPass(null);

		String code, output;
		code = "define(['a', 'b'], function(a, b) {});";
		output = runPass("test", code);
		System.out.println(output);
		Assert.assertTrue(output.contains("define(\"test\",["));

		code = "(function(){define(['a', 'b'], function(a, b) {})})();";
		output = runPass("test", code);
		System.out.println(output);
		Assert.assertTrue(output.contains("define(\"test\",["));

		code = "(function(){define(['a', 'b'])})();";
		output = runPass("test", code);
		System.out.println(output);
		Assert.assertTrue(output.contains("define(\"test\",["));

		code = "define({color:'black', size:'unisize'});";
		output = runPass("test", code);
		System.out.println(output);
		Assert.assertTrue(output.contains("define(\"test\",{"));

		code = "define(function(){return {color:'black', size:'unisize'}});";
		output = runPass("test", code);
		System.out.println(output);
		Assert.assertTrue(output.contains("define(\"test\",function"));

		code = "define('named', ['a', 'b'], function(a, b) {});";
		output = runPass("test", code);
		System.out.println(output);
		Assert.assertFalse(output.contains("test"));

	}

	@Test
	public void testSourceUpdate() throws Exception {
		String code = "define( ['a', 'b'], function(a, b) {});";
		JSSource source = new JSSource(code, null);
		pass = new ExportModuleNameCompilerPass(source);
		String output = runPass("test", code);
		Assert.assertEquals("define( \"test\",['a', 'b'], function(a, b) {});", source.toString());
		Assert.assertEquals("define(\"test\",[\"a\",\"b\"],function(a,b){});", output);
	}

	private String runPass(String moduleName, String code) {
		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode(moduleName, code));
		pass.process(null, root);
		CodeBuilder cb = new CodeBuilder();
		compiler.toSource(cb, 0, root);
		return cb.toString();
	}

}
