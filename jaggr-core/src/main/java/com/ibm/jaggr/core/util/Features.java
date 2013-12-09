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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Convenience class for defining all of the features passed to the aggregator.
 * Provides methods to determine if they exist, and if they are true or false.
 */
public class Features {
	public static final Features emptyFeatures;
	static {
		Map<String, Boolean> emptyMap = Collections.emptyMap();
		emptyFeatures = new Features(emptyMap);
	}

	private final Map<String, Boolean> features;
	public Features() {
		features = new HashMap<String, Boolean>();
	}
	
	public Features(Features features) {
		this.features = new HashMap<String, Boolean>(features.features);
	}
	
	private Features(Map<String, Boolean> features) {
		this();
		this.features.putAll(features);
	}
	
	public void put(String name, boolean value) {
		features.put(name, Boolean.valueOf(value));
	}
	
	public Set<String> featureNames() {
		return features.keySet();
	}
	/**
	 * Check to see if a feature is present.
	 * 
	 * @param feature
	 *            The featuer name to check.
	 * @return If the feature was specified.
	 */
	public boolean contains(String feature) {
		return features.containsKey(feature);
	}

	public void remove(String feature) {
		features.remove(feature);
	}
	
	/**
	 * Checks the value of the specified feature. Will return false for features
	 * that have not been specified. Implementations should rely on
	 * {@link #contains(String)} in addition to this method.
	 * 
	 * @param feature
	 *            The feature to check
	 * @return If the feature is true.
	 */
	public boolean isFeature(String feature) {
		Boolean result = features.get(feature);
		return (result != null) ? result : false;
	}
	
	public Features unmodifiableFeatures() {
		return new Features(Collections.unmodifiableMap(features));
	}
	
	@Override 
	public String toString() {
		return features.toString();
	}
}
