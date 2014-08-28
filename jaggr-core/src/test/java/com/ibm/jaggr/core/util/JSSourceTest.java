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
package com.ibm.jaggr.core.util;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;

import org.junit.Test;

import java.io.IOException;

import junit.framework.Assert;

public class JSSourceTest {

	@Test
	public void testInsert() throws IOException {
		String str1 = "This is the first line";
		String str2 = "This is the second line";
		String str = str1 + "\r\n" + str2;
		JSSource source = new JSSource(str, null);
		Assert.assertEquals(str, source.toString());

		source.insert("[insertfront]", 2, 0);
		Assert.assertEquals("This is the first line\r\n[insertfront]This is the second line", source.toString());

		source.insert("[insertmiddle]", 2, 4);
		Assert.assertEquals("This is the first line\r\n[insertfront]This[insertmiddle] is the second line", source.toString());

		source.insert("[insertend]", 2, str2.length());
		Assert.assertEquals("This is the first line\r\n[insertfront]This[insertmiddle] is the second line[insertend]", source.toString());

		try {
			source.insert("[fail]", 2, str2.length()+1);
			Assert.fail("Expected exception");
		} catch (IllegalStateException ex) {}


		source = new JSSource(str, null);
		source.insert("[insertend]", 2, str2.length());
		source.insert("[insertmiddle]", 2, 4);
		source.insert("[insertfront]", 2, 0);
		Assert.assertEquals("This is the first line\r\n[insertfront]This[insertmiddle] is the second line[insertend]", source.toString());
		try {
			source.insert("[fail]", 2, str2.length()+1);
			Assert.fail("Expected exception");
		} catch (IllegalStateException ex) {}

		try {
			source.insert("[fail]", 0, 0);
			Assert.fail("Expected exception");
		} catch (IllegalStateException ex) {}

		try {
			source.insert("[fail]", 3, 0);
			Assert.fail("Expected exception");
		} catch (IllegalStateException ex) {}
		try {
			source.insert("[fail]", 1, -1);
			Assert.fail("Expected exception");
		} catch (IllegalStateException ex) {}
	}

	@Test
	public void testAppendToArray_basic() throws IOException {
		String code =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(['dep1', 'dep2'], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";
		String expected =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(['dep1', 'dep2',\"insertedDep\"], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";

		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode("file", "name", code));
		Node array = findRequireDeps(root);
		JSSource source = new JSSource(code, null);
		source.appendToArrayLit(array, "insertedDep");
		Assert.assertEquals(expected, source.toString());
	}

	@Test
	public void testAppendToArray_var() throws IOException {
		String code =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(['dep1', vardep], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";
		String expected =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(['dep1', vardep,\"insertedDep\"], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";

		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode("file", "name", code));
		Node array = findRequireDeps(root);
		JSSource source = new JSSource(code, null);
		source.appendToArrayLit(array, "insertedDep");
		Assert.assertEquals(expected, source.toString());
	}

	@Test
	public void testAppendToArray_newline() throws IOException {
		String code =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'\r\n"+
			"   ]\r\n" +
			", function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";
		String expected =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'\r\n"+
			"   ,\"insertedDep\"]\r\n"+
			", function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";

		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode("file", "name", code));
		Node array = findRequireDeps(root);
		JSSource source = new JSSource(code, null);
		source.appendToArrayLit(array, "insertedDep");
		Assert.assertEquals(expected, source.toString());
	}

	@Test
	public void testAppendToArray_newlineLineComment() throws IOException {
		String code =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'  // [comment] \r\n"+
			"   ], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";
		String expected =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'  // [comment] \r\n"+
			"   ,\"insertedDep\"], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";

		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode("file", "name", code));
		Node array = findRequireDeps(root);
		JSSource source = new JSSource(code, null);
		source.appendToArrayLit(array, "insertedDep");
		Assert.assertEquals(expected, source.toString());
	}

	@Test
	public void testAppendToArray_singleLineBlockComment() throws IOException {
		String code =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'  /* ]comment // fake */\r\n"+
			"   ], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";
		String expected =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'  /* ]comment // fake */\r\n"+
			"   ,\"insertedDep\"], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";

		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode("file", "name", code));
		Node array = findRequireDeps(root);
		JSSource source = new JSSource(code, null);
		source.appendToArrayLit(array, "insertedDep");
		Assert.assertEquals(expected, source.toString());
	}

	@Test
	public void testAppendToArray_multiLineBlockComment() throws IOException {
		String code =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'  /* ]comment[\r\n"+
			"      another comment line]\r\n"+
			"//  */], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";
		String expected =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'  /* ]comment[\r\n"+
			"      another comment line]\r\n"+
			"//  */,\"insertedDep\"], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";

		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode("file", "name", code));
		Node array = findRequireDeps(root);
		JSSource source = new JSSource(code, null);
		source.appendToArrayLit(array, "insertedDep");
		Assert.assertEquals(expected, source.toString());
	}

	@Test
	public void testAppendToArray_multiLineSpaces() throws IOException {
		String code =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'\r\n"+
			"\r\n"+
			"/*\r\n"+
			" * hello world\r\n"+
			" */\r\n"+
			"\r\n"+
			"], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";
		String expected =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(\r\n["+
			"      'dep1',\r\n"+
			"	   'dep2'\r\n"+
			"\r\n"+
			"/*\r\n"+
			" * hello world\r\n"+
			" */\r\n"+
			"\r\n"+
			",\"insertedDep\"], function(dep1, dep2) {\r\n" +
			"      alert('hello world');\r\n"+
			"   });\r\n" +
			"});";

		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode("file", "name", code));
		Node array = findRequireDeps(root);
		JSSource source = new JSSource(code, null);
		source.appendToArrayLit(array, "insertedDep");
		Assert.assertEquals(expected, source.toString());

	}

	@Test
	public void testAppendToArray_lastLine() throws IOException {
		String code =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(['dep1', 'dep2'\r\n"+
			"\r\n"+
			"   ], function(dep1, dep2) {alert('hello world');});});";

		String expected =
			"define(['foo','bar'], function(foo, bar) {\r\n" +
			"   require(['dep1', 'dep2'\r\n"+
			"\r\n"+
			"   ,\"insertedDep\"], function(dep1, dep2) {alert('hello world');});});";

		Compiler compiler = new Compiler();
		Node root = compiler.parse(SourceFile.fromCode("file", "name", code));
		Node array = findRequireDeps(root);
		JSSource source = new JSSource(code, null);
		source.appendToArrayLit(array, "insertedDep");
		Assert.assertEquals(expected, source.toString());
	}

	private Node findRequireDeps(Node node) {
		for (Node cursor = node.getFirstChild(); cursor != null; cursor = cursor.getNext()) {
			Node deps = null;
			if ((deps = NodeUtil.moduleDepsFromRequire(cursor)) != null) {
				return deps;
			}
			// Recursively call this method to process the child nodes
			if (cursor.hasChildren()) {
				deps = findRequireDeps(cursor);
				if (deps != null) {
					return deps;
				}
			}
		}
		return null;
	}
}
