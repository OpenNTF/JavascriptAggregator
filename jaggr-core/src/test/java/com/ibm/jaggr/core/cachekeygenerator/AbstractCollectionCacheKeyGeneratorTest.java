/*
 * (C) Copyright IBM Corp. 2012, 2016
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
package com.ibm.jaggr.core.cachekeygenerator;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import javax.servlet.http.HttpServletRequest;

public class AbstractCollectionCacheKeyGeneratorTest {

	@SuppressWarnings("serial")
	private class TestCollectionCacheKeyGenerator extends AbstractCollectionCacheKeyGenerator<String> {
		private boolean isProvisional;
		private Collection<String> collection;
		TestCollectionCacheKeyGenerator(Collection<String> collection, boolean isProvisional) {
			this.isProvisional = isProvisional;
			this.collection = collection;
		}
		@Override
		public Collection<String> getCollection() {
			return collection;
		}
		@Override
		protected AbstractCollectionCacheKeyGenerator<String> newKeyGen(Collection<String> col,
				boolean isProvisional) {
			return new TestCollectionCacheKeyGenerator(col, isProvisional);
		}
		@Override
		public String generateKey(HttpServletRequest request) {
			return toString();
		}
		@Override
		public String toString() {
			return collection.toString() + ":" + isProvisional;
		}
		@Override
		public boolean isProvisional() {
			return isProvisional;
		}
	}

	@Test
	public void testCombine() {
		TestCollectionCacheKeyGenerator gen1 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("a", "b", "c")), false);
		Assert.assertSame(gen1, gen1.combine(gen1));

		TestCollectionCacheKeyGenerator prov1 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("p1")), true);
		TestCollectionCacheKeyGenerator prov2 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("p2")), true);
		Assert.assertSame(gen1, gen1.combine(prov1));
		Assert.assertSame(gen1, prov1.combine(gen1));
		boolean exceptionThrown = false;
		try {
			prov1.combine(prov2);
		} catch (IllegalStateException ex) {
			exceptionThrown = true;
		}
		Assert.assertTrue(exceptionThrown);

		TestCollectionCacheKeyGenerator gen2 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("a", "b")), false);
		Assert.assertSame(gen1, gen1.combine(gen2));
		Assert.assertSame(gen1, gen2.combine(gen1));

		TestCollectionCacheKeyGenerator gen3 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("c", "d")), false);
		ICacheKeyGenerator combined = gen1.combine(gen3);
		Assert.assertEquals(new HashSet<String>(Arrays.asList("a", "b", "c", "d")),
				new HashSet<String>(((TestCollectionCacheKeyGenerator)combined).getCollection()));
		combined = gen3.combine(gen1);
		Assert.assertEquals(new HashSet<String>(Arrays.asList("a", "b", "c", "d")),
				new HashSet<String>(((TestCollectionCacheKeyGenerator)combined).getCollection()));

		TestCollectionCacheKeyGenerator nullCol = new TestCollectionCacheKeyGenerator(null, false);
		Assert.assertSame(nullCol, gen1.combine(nullCol));
		Assert.assertSame(nullCol, nullCol.combine(gen1));
		Assert.assertSame(nullCol, nullCol.combine(nullCol));
	}

	@Test
	public void testEquals() {
		TestCollectionCacheKeyGenerator gen1 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("a", "b")), false);
		TestCollectionCacheKeyGenerator gen2 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("a", "b")), false);
		TestCollectionCacheKeyGenerator gen3 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("a", "b")), true);
		TestCollectionCacheKeyGenerator gen4 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("b", "c")), false);
		TestCollectionCacheKeyGenerator nulcol1 = new TestCollectionCacheKeyGenerator(null, true);
		TestCollectionCacheKeyGenerator nulcol2 = new TestCollectionCacheKeyGenerator(null, true);
		TestCollectionCacheKeyGenerator nulcol3 = new TestCollectionCacheKeyGenerator(null, false);

		Assert.assertFalse(gen1.equals(null));
		Assert.assertTrue(gen1.equals(gen1));
		Assert.assertFalse(gen1.equals(null));
		Assert.assertFalse(gen1.equals(new Object()));
		Assert.assertTrue(gen1.equals(gen2));
		Assert.assertFalse(gen1.equals(gen3));
		Assert.assertFalse(gen1.equals(gen4));
		Assert.assertTrue(nulcol1.equals(nulcol1));
		Assert.assertTrue(nulcol1.equals(nulcol2));
		Assert.assertFalse(nulcol1.equals(nulcol3));
		Assert.assertFalse(gen1.equals(nulcol1));
		Assert.assertFalse(gen1.equals(nulcol3));
		Assert.assertFalse(gen3.equals(nulcol1));
		Assert.assertFalse(gen3.equals(nulcol3));
	}

	@Test
	public void testHashCode() {
		TestCollectionCacheKeyGenerator gen1 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("a", "b")), false);
		TestCollectionCacheKeyGenerator gen2 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("a", "b")), false);
		TestCollectionCacheKeyGenerator gen3 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("a", "b")), true);
		TestCollectionCacheKeyGenerator gen4 = new TestCollectionCacheKeyGenerator(new HashSet<String>(Arrays.asList("b", "c")), false);
		TestCollectionCacheKeyGenerator nulcol1 = new TestCollectionCacheKeyGenerator(null, true);
		TestCollectionCacheKeyGenerator nulcol2 = new TestCollectionCacheKeyGenerator(null, true);
		TestCollectionCacheKeyGenerator nulcol3 = new TestCollectionCacheKeyGenerator(null, false);

		Assert.assertTrue(gen1.hashCode() == gen2.hashCode());
		Assert.assertFalse(gen1.hashCode() == gen3.hashCode());
		Assert.assertFalse(gen1.hashCode() == gen4.hashCode());
		Assert.assertTrue(nulcol1.hashCode() == nulcol2.hashCode());
		Assert.assertFalse(nulcol1.hashCode() == nulcol3.hashCode());
		Assert.assertFalse(gen1.hashCode() == nulcol1.hashCode());
		Assert.assertFalse(gen1.hashCode() == nulcol3.hashCode());
		Assert.assertFalse(gen3.hashCode() == nulcol1.hashCode());
		Assert.assertFalse(gen3.hashCode() == nulcol3.hashCode());
	}
}
