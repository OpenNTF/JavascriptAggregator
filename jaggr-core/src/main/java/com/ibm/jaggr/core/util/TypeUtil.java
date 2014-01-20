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
