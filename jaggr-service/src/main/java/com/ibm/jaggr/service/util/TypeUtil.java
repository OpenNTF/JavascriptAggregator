/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.util;

public class TypeUtil {

	public static Boolean asBoolean(Object obj) {
		Boolean result = Boolean.FALSE;
		if (obj != null) {
			if (obj instanceof Boolean) {
				result = (Boolean)obj;
			} else if (obj instanceof String) {
				result = Boolean.valueOf(
						"true".equalsIgnoreCase((String)obj) || //$NON-NLS-1$
						"1".equals((String)obj)); //$NON-NLS-1$
			}
		}
		return result;
	}
	
	public static Boolean asBoolean(Object obj, boolean defaultValue) {
		if (obj == null) {
			return defaultValue;
		}
		return asBoolean(obj);
	}
	
	public static int asInt(Object obj, int defaultValue) {
		int result = defaultValue;
		if (obj != null) {
			if (obj instanceof Number) {
				result = ((Number)obj).intValue();
			} else if (obj instanceof String) {
				try {
					result = Integer.parseInt((String)obj);
				} catch (NumberFormatException ignore) {}
			}
		}	
		return result;
	}
}
