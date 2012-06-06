/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.modulebuilder.javascript;

import junit.framework.Assert;

import org.junit.Test;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.Compiler.CodeBuilder;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;

public class ExportModuleNameCompilerPassTest {
	
	private ExportModuleNameCompilerPass pass = new ExportModuleNameCompilerPass();

	@Test
	public void testProcess() {
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

	private String runPass(String moduleName, String code) {
		Compiler compiler = new Compiler();
		Node root = compiler.parse(JSSourceFile.fromCode(moduleName, code));
		pass.process(null, root);
		CodeBuilder cb = new CodeBuilder();
		compiler.toSource(cb, 0, root);
		return cb.toString();
	}
	
}
