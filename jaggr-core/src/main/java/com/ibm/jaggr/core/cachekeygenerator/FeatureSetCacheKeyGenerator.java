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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.Features;

/**
 * Cache key generator for content that depends on the feature set specified in
 * the request.
 */
public final class FeatureSetCacheKeyGenerator implements ICacheKeyGenerator {

	private static final long serialVersionUID = 8764291680091811800L;

	private static final String eyecatcher = "has"; //$NON-NLS-1$
	/**
	 * The features this key generator depends on.
	 */
	private final Set<String> depFeatures;

	/**
	 * true if this cache key generator is provisional.
	 */
	private final boolean provisional;

	/**
	 * Element constructor.
	 * 
	 * @param dependentFeatures
	 *            Set of feature names that this cache key generator depends on.
	 *            The key output by this key generator will contain only those
	 *            features from the request that are included in
	 *            {@code dependentFeatures}.  If the value is null, then all
	 *            the features specified in the request are included in the 
	 *            generated cache key.
	 * @param provisional
	 *            True if this is a provisional cache key generator.
	 */
	public FeatureSetCacheKeyGenerator(Set<String> dependentFeatures,
			boolean provisional) {
		depFeatures = dependentFeatures == null ? null : 
				Collections.unmodifiableSet(new HashSet<String>(dependentFeatures));
		this.provisional = provisional;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator#generateKey(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public String generateKey(HttpServletRequest request) {

		Features features = (Features) request
				.getAttribute(IHttpTransport.FEATUREMAP_REQATTRNAME);
		if (features == null) {
			features = Features.emptyFeatures;
		}
		IAggregator aggr = (IAggregator) request
				.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IConfig config = aggr.getConfig();
		// First, create a sorted map from the provided feature map and then
		// trim unused features.  We use a sorted map to ensure consistency
		// in the order that the feature names appear within the key.
		SortedMap<String, Boolean> map = new TreeMap<String, Boolean>();
		for (String featureName : features.featureNames()) {
			map.put(featureName, features.isFeature(featureName));
		}

		if (depFeatures != null) {
			// If features we depend that are not specified in the request should
			// be regarded as being false, then add them to the request feature map.
			if (config.isCoerceUndefinedToFalse()) {
				for (String s : depFeatures) {
					if (!map.keySet().contains(s)) {
						map.put(s, false);
					}
				}
			}
			
			// Remove from the map all the features that this generator doesn't 
			// depend on.
			map.keySet().retainAll(depFeatures);
		}
		StringBuffer sb = new StringBuffer();
		// Now build the key from the defined pruned feature set.
		for (Map.Entry<String, Boolean> entry : map.entrySet()) {
			if (sb.length() > 1) {
				sb.append(","); //$NON-NLS-1$
			}
			sb.append(entry.getValue() ? "" : "!"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append(entry.getKey());
		}
		sb.insert(0, eyecatcher + "{").append("}").toString(); //$NON-NLS-1$ //$NON-NLS-2$
		return sb.toString(); 
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator#combine(com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator)
	 */
	@Override
	public FeatureSetCacheKeyGenerator combine(ICacheKeyGenerator otherKeyGen) {
		if (this.equals(otherKeyGen)) {
			return this;
		}
		FeatureSetCacheKeyGenerator other = (FeatureSetCacheKeyGenerator)otherKeyGen;
		if (provisional && other.provisional) {
			// should never happen
			throw new IllegalStateException();
		}
		if (provisional) {
			return other;
		} else if (other.provisional) {
			return this;
		}
		Set<String> combined = new HashSet<String>();
		if (depFeatures != null) {
			combined.addAll(depFeatures);
		} 
		if (other.depFeatures != null) {
			combined.addAll(other.depFeatures);
		}
		return new FeatureSetCacheKeyGenerator(combined, false);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator#isProvisional()
	 */
	@Override
	public boolean isProvisional() {
		return provisional;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		// Map features into sorted set so we get predictable ordering of
		// items in the output.
		SortedSet<String> set = depFeatures == null ? null : new TreeSet<String>(depFeatures);
		StringBuffer sb = new StringBuffer(eyecatcher).append(":"); //$NON-NLS-1$
		sb.append(set == null ? "null" : set.toString()); //$NON-NLS-1$
		if (isProvisional()) {
			sb.append(":provisional"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	@Override
	public List<ICacheKeyGenerator> getCacheKeyGenerators(HttpServletRequest request) {
		// results are not request dependent.
		return null;
	}
	
	public Collection<String> getFeatureSet() {
		return depFeatures;
	}

	@Override
	public boolean equals(Object other) {
		return other != null && getClass().equals(other.getClass()) && 
				provisional == ((FeatureSetCacheKeyGenerator)other).provisional &&
				(
					depFeatures != null && depFeatures.equals(((FeatureSetCacheKeyGenerator)other).depFeatures) ||
					depFeatures == null && ((FeatureSetCacheKeyGenerator)other).depFeatures == null
				);
		
	}
	
	@Override
	public int hashCode() {
		int result = getClass().hashCode();
		result = result * 31 + Boolean.valueOf(provisional).hashCode();
		if (depFeatures != null) {
			result = result * 31 + depFeatures.hashCode();
		}
		return result;
	}
}
