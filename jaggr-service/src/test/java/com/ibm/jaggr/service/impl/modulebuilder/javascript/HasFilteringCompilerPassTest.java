/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.modulebuilder.javascript;

import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.Compiler.CodeBuilder;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;
import com.ibm.jaggr.service.util.Features;

public class HasFilteringCompilerPassTest {

	@Test
	public void testProcess() throws Exception {
		String code, output;
    	Features features = new Features();
    	Set<String> discoveredHasConditionals = new HashSet<String>();
    	HasFilteringCompilerPass pass = new HasFilteringCompilerPass(features, discoveredHasConditionals, false);
    	
    	// Test exact equality (should be ignored)
    	features.put("foo", true);
		code = "define([], function() { if (has(\"foo\") === true) { say('hello'); } else {say('bye');} });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 0);
		Assert.assertEquals("define([],function(){if(has(\"foo\")===true)say(\"hello\");else say(\"bye\")});", output);
		
		// test equality to true with true condition (should be ignored)
		discoveredHasConditionals.clear();
		code = "define([], function() { if (has(\"foo\") == true) { say('hello'); } });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 0);
		Assert.assertEquals("define([],function(){if(has(\"foo\")==true)say(\"hello\")});", output);

		// equality to true with false condition (should be ignored)
		discoveredHasConditionals.clear();
		features.put("foo", false);
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 0);
		Assert.assertEquals("define([],function(){if(has(\"foo\")==true)say(\"hello\")});", output);

		
		// inequality to true with true condition (should be ignored)
		discoveredHasConditionals.clear();
		code = "define([], function() { if (has(\"foo\") != true) { say('hello'); } });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 0);
		Assert.assertEquals("define([],function(){if(has(\"foo\")!=true)say(\"hello\")});", output);

		// if operator with false condition
		discoveredHasConditionals.clear();
		code = "define([], function() {if (has(\"foo\")) { say('hello'); } else { say('bye');} });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){if(false)say(\"hello\");else say(\"bye\")});", output);
		
		// not operator with false condition
		discoveredHasConditionals.clear();
		code = "define([], function() {if (!has(\"foo\")) { say('hello'); } else { say('bye');} });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){if(!false)say(\"hello\");else say(\"bye\")});", output);
		
		// if operator with false condition
		features.put("foo", true);
		discoveredHasConditionals.clear();
		code = "define([], function() {if (has(\"foo\")) { say('hello'); } else { say('bye');} });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){if(true)say(\"hello\");else say(\"bye\")});", output);
		
		// not operator with false condition
		discoveredHasConditionals.clear();
		code = "define([], function() {if (!has(\"foo\")) { say('hello'); } else { say('bye');} });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){if(!true)say(\"hello\");else say(\"bye\")});", output);
		
		// tertiary operator
		discoveredHasConditionals.clear();
		features.put("foo", true);
		code = "define([], function() {say(has(\"foo\") ? 'hello' : 'bye')});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){say(true?\"hello\":\"bye\")});", output);
		
		// tertiary operator
		discoveredHasConditionals.clear();
		features.put("foo", false);
		code = "define([], function() {say(has(\"foo\") ? 'hello' : 'bye')});";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){say(false?\"hello\":\"bye\")});", output);
		
		// assignment (should be ignored)
		discoveredHasConditionals.clear();
		features.put("foo", true);
		code = "define([], function() {var v; if (v = has(\"foo\")) { say('hello'); } });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 0);
		Assert.assertEquals("define([],function(){var v;if(v=has(\"foo\"))say(\"hello\")});", output);
		
		// logical and
		discoveredHasConditionals.clear();
		features.put("foo", true);
		code = "define([], function() {has(\"foo\") && say('hello');  });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){true&&say(\"hello\")});", output);
		
		// logical and
		discoveredHasConditionals.clear();
		features.put("foo", false);
		code = "define([], function() {has(\"foo\") && say('hello');  });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){false&&say(\"hello\")});", output);
		
		// logical or
		discoveredHasConditionals.clear();
		features.put("foo", true);
		code = "define([], function() {has(\"foo\") || say('hello');  });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){true||say(\"hello\")});", output);
		
		// logical or
		discoveredHasConditionals.clear();
		features.put("foo", false);
		code = "define([], function() {has(\"foo\") || say('hello');  });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){false||say(\"hello\")});", output);
		
		// test with coerceUndefinedToFalse set
    	pass = new HasFilteringCompilerPass(new Features(), discoveredHasConditionals, true);
		discoveredHasConditionals.clear();
		code = "define([], function() {if (has(\"foo\")) { say('hello'); } else { say('bye');} });";
		output = runPass(pass, code);
		System.out.println(output);
		Assert.assertTrue(discoveredHasConditionals.size() == 1);
		Assert.assertTrue(discoveredHasConditionals.contains("foo"));
		Assert.assertEquals("define([],function(){if(false)say(\"hello\");else say(\"bye\")});", output);
		
		
	}
	
	private String runPass(HasFilteringCompilerPass pass, String code) {
		Compiler compiler = new Compiler();
		Node root = compiler.parse(JSSourceFile.fromCode("test", code));
		pass.process(null, root);
		CodeBuilder cb = new CodeBuilder();
		compiler.toSource(cb, 0, root);
		return cb.toString();
	}
}
