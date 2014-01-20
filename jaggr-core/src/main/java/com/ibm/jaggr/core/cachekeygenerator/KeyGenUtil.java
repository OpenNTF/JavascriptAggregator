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

package com.ibm.jaggr.core.cachekeygenerator;

import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;


/**
 * Defines static utility methods for working with arrays of cache
 * key generators
 */
public class KeyGenUtil {
	
	/**
	 * Return true if any of the cache key generators in the array is a
	 * provisional cache key generator.
	 * 
	 * @param keyGens
	 *            The array
	 * @return True if there is a provisional cache key generator in the array
	 */
	static public boolean isProvisional(Iterable<ICacheKeyGenerator> keyGens) {
		boolean provisional = false;
		for (ICacheKeyGenerator keyGen : keyGens) {
			if (keyGen.isProvisional()) {
				provisional = true;
				break;
			}
		}
		return provisional;
	}
	
	/**
	 * Generates a cache key by aggregating (concatenating) the output of
	 * each of the cache key generators in the array.
	 * 
	 * @param request The request object
	 * @param keyGens The array
	 * @return The aggregated cache key
	 */
	static public String generateKey(HttpServletRequest request, Iterable<ICacheKeyGenerator> keyGens) {
		StringBuffer sb = new StringBuffer();
		for (ICacheKeyGenerator keyGen : keyGens) {
			String key = keyGen.generateKey(request);
			if (key != null && key.length() > 0) {
				sb.append(sb.length() > 0 ? ";" : "").append(key); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		return sb.toString();
	}
	
	/**
	 * Builds a string by combining the toString output of the cache key
	 * generators in the array
	 * 
	 * @param keyGens
	 *            The array
	 * @return The aggregated toString output of the cache key generators
	 */
	static public String toString(Iterable<ICacheKeyGenerator> keyGens) {
		StringBuffer sb = new StringBuffer();
		if (keyGens == null) {
			return "null"; //$NON-NLS-1$
		}
		for (ICacheKeyGenerator keyGen : keyGens) {
			sb.append(sb.length() > 0 ? ";" : "").append(keyGen.toString()); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return sb.toString();
	}
	
	/**
	 * Returns a new list containing the results of combining each element in a with the
	 * corresponding element in b.  If the result contains the same elements as a, then 
	 * a is returned.
	 * <p>
	 * The input arrays must be the same length, and the types of the array elements at each
	 * index must be the same
	 * 
	 * @param a
	 * @param b
	 * @return The list of combined cache key generators
	 */
	static public List<ICacheKeyGenerator> combine(List<ICacheKeyGenerator> a, List<ICacheKeyGenerator> b) {
		if (a == null) {
			return b;
		} else if (b == null) {
			return a;
		}
		if (a.size() != b.size()) {
			throw new IllegalStateException();
		}
		ICacheKeyGenerator[] combined = new ICacheKeyGenerator[a.size()];
		boolean modified = false;
		for (int i = 0; i < a.size(); i++) {
			combined[i] = a.get(i).combine(b.get(i));
			modified = modified || combined[i] != a.get(i);
		}
		return modified ? Arrays.asList(combined) : a;
	}
}
