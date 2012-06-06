/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.config;

import java.util.Map;

import com.ibm.jaggr.service.IAggregator;

/**
 * Interface for config modifier class. Instances of this class are registered
 * as an OSGi service with the name property set to the name of the aggregator
 * that the config object is associated with. The
 * {@link #modifyConfig(IAggregator, Map)} method is called whenever the
 * config is loaded/reloaded, before any config listeners are called.
 * 
 * @author chuckd@us.ibm.com
 */
public interface IConfigModifier {
	/**
	 * Called whenever the config is loaded/reloaded
	 * 
	 * @param aggregator
	 *            The aggregator that this config is associated with
	 * @param rawConfig
	 *            The raw config object from the config JSON. See
	 *            {@link IConfig#getRawConfig()} for a description of this
	 *            object. Implementors of this method may modify the config
	 *            object as needed. Objects added to the raw config should be
	 *            limited to Maps, Lists and Strings, Numbers and Booleans, or
	 *            JSON proxy objects. All elements must be serializable and
	 *            cloneable and support element comparison using the the
	 *            equals() method.
	 */
	void modifyConfig(IAggregator aggregator, Map<String, Object> rawConfig);
}
