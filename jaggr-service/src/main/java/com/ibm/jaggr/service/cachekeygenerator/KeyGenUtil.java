/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.cachekeygenerator;

import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;


/**
 * Defines static utility methods for working with arrays of cache
 * key generators
 * 
 * @author chuckd@us.ibm.com
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
	static public boolean isProvisional(ICacheKeyGenerator[] keyGens) {
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
	
	static public String generateKey(HttpServletRequest request, ICacheKeyGenerator[] keyGens) {
		return generateKey(request, Arrays.asList(keyGens));
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
	
	static public String toString(ICacheKeyGenerator[] keyGens) {
		return toString(Arrays.asList(keyGens));
	}
}
