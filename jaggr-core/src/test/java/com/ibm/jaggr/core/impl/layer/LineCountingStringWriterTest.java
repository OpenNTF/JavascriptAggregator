/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LineCountingStringWriterTest {

	@Before
	public void setUp() throws Exception {}

	@After
	public void tearDown() throws Exception {}

	@Test
	public void test() {
		@SuppressWarnings("resource")
		LineCountingStringWriter out = new LineCountingStringWriter();
		// write(int c)
		out.write('1');
		Assert.assertEquals(0, out.getLine());
		Assert.assertEquals(1, out.getColumn());
		out.write('\n');
		Assert.assertEquals(1, out.getLine());
		Assert.assertEquals(0, out.getColumn());
		out.write('2');
		Assert.assertEquals(1, out.getLine());
		Assert.assertEquals(1, out.getColumn());
		out.write('\r');
		Assert.assertEquals(2, out.getLine());
		Assert.assertEquals(0, out.getColumn());
		out.write('\n');
		Assert.assertEquals(2, out.getLine());
		Assert.assertEquals(0, out.getColumn());

		// write(char[], int, int)
		char[] cbuf = new char[]{'h', 'e', 'l', 'l', 'o', '\n'};
		out.write(cbuf,0,cbuf.length-1);
		Assert.assertEquals(2, out.getLine());
		Assert.assertEquals(5, out.getColumn());

		out.write(cbuf, cbuf.length-1, 1);
		Assert.assertEquals(3, out.getLine());
		Assert.assertEquals(0, out.getColumn());

		// write(String)
		out.write("\r\r\n");
		Assert.assertEquals(5, out.getLine());
		Assert.assertEquals(0, out.getColumn());

		// write(String, int, int)
		out.write("testing", 0, 4);
		Assert.assertEquals(5, out.getLine());
		Assert.assertEquals(4, out.getColumn());

		// append(CharSequance)
		out.append("ing\n");
		Assert.assertEquals(6, out.getLine());
		Assert.assertEquals(0, out.getColumn());

		// append(CharSequance, int, int)
		String str = "foobar\n";
		out.append(str, 0, 3);
		Assert.assertEquals(6, out.getLine());
		Assert.assertEquals(3, out.getColumn());
		out.append(str, 3, 6);
		Assert.assertEquals(6, out.getLine());
		Assert.assertEquals(6, out.getColumn());
		out.append(str, 6, 7);
		Assert.assertEquals(7, out.getLine());
		Assert.assertEquals(0, out.getColumn());

		// append(char)
		out.append('1');
		Assert.assertEquals(7, out.getLine());
		Assert.assertEquals(1, out.getColumn());
		out.append('\n');
		Assert.assertEquals(8, out.getLine());
		Assert.assertEquals(0, out.getColumn());

		System.out.println(out.toString());
		Assert.assertEquals("1\n2\r\nhello\n\r\r\ntesting\nfoobar\n1\n", out.toString());
	}
}
