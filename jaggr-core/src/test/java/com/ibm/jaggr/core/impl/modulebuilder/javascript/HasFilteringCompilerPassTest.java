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

import com.ibm.jaggr.core.impl.modulebuilder.javascript.HasFilteringCompilerPass;
import com.ibm.jaggr.core.util.Features;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.Compiler.CodeBuilder;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

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
