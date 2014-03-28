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

import org.junit.Test;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;

public class ConcurrentAddOnlyListTest {

	@Test
	public void testEquals() {
		List<String> list = Arrays.asList(new String[]{"1", "2", "3"});

		ConcurrentAddOnlyList<String> lst1 = new ConcurrentAddOnlyList<String>();
		lst1.add("1");
		lst1.add("2");
		lst1.add("3");
		Assert.assertEquals(list.toString(), lst1.toString());

		ConcurrentAddOnlyList<String> lst2 = new ConcurrentAddOnlyList<String>();
		lst2.add("1");
		lst2.add("2");
		lst2.add("3");
		Assert.assertEquals(lst1, lst2);

		ConcurrentAddOnlyList<String> lst3 = new ConcurrentAddOnlyList<String>();
		lst3.add("3");
		lst3.add("2");
		lst3.add("1");
		Assert.assertFalse(lst1.equals(lst3));

		Assert.assertTrue(lst1.hashCode() == lst2.hashCode());
		Assert.assertFalse(lst1.hashCode() == lst3.hashCode());

		lst2.add("4");
		Assert.assertFalse(lst1.equals(lst2));
		Assert.assertFalse(lst2.equals(lst1));

		ConcurrentAddOnlyList<Integer> intLst = new ConcurrentAddOnlyList<Integer>();
		intLst.add(1);
		intLst.add(2);
		intLst.add(3);
		Assert.assertEquals(list.toString(), intLst.toString());

		Assert.assertEquals(list, lst1.toList());
	}

	@Test
	public void testIterator() {
		ConcurrentAddOnlyList<String> lst = new ConcurrentAddOnlyList<String>();
		lst.add("1");
		lst.add("2");
		lst.add("3");
		Iterator<String> iter = lst.iterator();
		Assert.assertTrue(iter.hasNext());
		Assert.assertEquals("1", iter.next());
		Assert.assertTrue(iter.hasNext());
		Assert.assertEquals("2", iter.next());
		Assert.assertTrue(iter.hasNext());
		Assert.assertEquals("3", iter.next());
		Assert.assertFalse(iter.hasNext());
	}

	@Test (expected=UnsupportedOperationException.class)
	public void testIteratorRemove() {
		ConcurrentAddOnlyList<String> lst = new ConcurrentAddOnlyList<String>();
		lst.add("1");
		lst.add("2");
		lst.add("3");
		Iterator<String> iter = lst.iterator();
		iter.next();
		iter.remove();
	}

	@Test
	public void testExpansion() {
		ConcurrentAddOnlyList<String> lst = new ConcurrentAddOnlyList<String>(3);
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
