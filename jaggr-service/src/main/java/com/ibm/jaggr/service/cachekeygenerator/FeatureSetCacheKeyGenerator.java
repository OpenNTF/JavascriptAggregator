/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.cachekeygenerator;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.config.IConfig;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.Features;

/**
 * Cache key generator for content that depends on the feature set specified in
 * the request.
 * 
 * @author chuckd@us.ibm.com
 */
public final class FeatureSetCacheKeyGenerator implements ICacheKeyGenerator {

	private static final long serialVersionUID = 8764291680091811800L;

	private static final String eyecatcher = "has"; //$NON-NLS-1$
	/**
	 * The features this key generator depends on.
	 */
	private final Collection<String> depFeatures;

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
				Collections.unmodifiableCollection(new HashSet<String>(dependentFeatures));
		this.provisional = provisional;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator#generateKey(javax.servlet.http.HttpServletRequest)
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
	 * @see com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator#combine(com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator)
	 */
	@Override
	public ICacheKeyGenerator combine(ICacheKeyGenerator otherKeyGen) {
		FeatureSetCacheKeyGenerator other = (FeatureSetCacheKeyGenerator)otherKeyGen;
		if (provisional || other.provisional) {
			// should never happen
			throw new IllegalStateException();
		}
		Set<String> combined = null;
		if (depFeatures != null && other.depFeatures != null) {
			combined = new HashSet<String>(depFeatures);
			combined.addAll(other.depFeatures);
		}
		return new FeatureSetCacheKeyGenerator(combined, false);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator#isProvisional()
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
	public ICacheKeyGenerator[] getCacheKeyGenerators(HttpServletRequest request) {
		return null;
	}
}
