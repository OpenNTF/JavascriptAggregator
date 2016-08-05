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
package com.ibm.jaggr.core.util;

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.List;

import junit.framework.Assert;

public class ConcurrentAddOnlyListTest {

	@Test
	public void testEquals() {
		List<String> list = Arrays.asList(new String[]{"1", "2", "3"});

		ConcurrentListBuilder<String> bldr1 = new ConcurrentListBuilder<String>();
		bldr1.add("1");
		bldr1.add("2");
		bldr1.add("3");
		Assert.assertEquals(list.toString(), bldr1.toList().toString());

		ConcurrentListBuilder<String> bldr2 = new ConcurrentListBuilder<String>();
		bldr2.add("1");
		bldr2.add("2");
		bldr2.add("3");
		Assert.assertEquals(bldr1.toList(), bldr2.toList());

		ConcurrentListBuilder<String> bldr3 = new ConcurrentListBuilder<String>();
		bldr3.add("3");
		bldr3.add("2");
		bldr3.add("1");
		Assert.assertFalse(bldr1.toList().equals(bldr3));

		bldr2.add("4");
		Assert.assertFalse(bldr1.toList().equals(bldr2.toList()));
		Assert.assertFalse(bldr2.toList().equals(bldr1.toList()));

		ConcurrentListBuilder<Integer> intBldr = new ConcurrentListBuilder<Integer>();
		intBldr.add(1);
		intBldr.add(2);
		intBldr.add(3);
		Assert.assertEquals(list.toString(), intBldr.toList().toString());

		Assert.assertEquals(list, bldr1.toList());
	}

	@Test
	public void testExpansion() {
		ConcurrentListBuilder<String> lst = new ConcurrentListBuilder<String>(3);
		Object[] items = (Object[])Whitebox.getInternalState(lst, "items");
		Assert.assertEquals(3, items.length);
		Assert.assertEquals(0, lst.size());
		lst.add("1");
		Assert.assertSame(items, (Object[])Whitebox.getInternalState(lst, "items"));
		Assert.assertEquals(1, lst.size());
		lst.add("2");
		Assert.assertSame(items, (Object[])Whitebox.getInternalState(lst, "items"));
		Assert.assertEquals(2, lst.size());
		lst.add("3");
		Assert.assertSame(items, (Object[])Whitebox.getInternalState(lst, "items"));
		Assert.assertEquals(3, lst.size());

		lst.add("4");
		Assert.assertNotSame(items, (Object[])Whitebox.getInternalState(lst, "items"));
		items = (Object[])Whitebox.getInternalState(lst, "items");
		Assert.assertEquals(6, items.length);
		Assert.assertEquals(4, lst.size());
		Assert.assertEquals(Arrays.asList(new String[]{"1", "2", "3", "4"}), lst.toList());
	}
}
