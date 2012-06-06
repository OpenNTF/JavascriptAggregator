/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.config;

/**
 * Listener interface for config changes.  To receive notification 
 * of config events, register the implementing class as an OSGi service,
 * specifying the servlet name under the <code>name</code> property in 
 * the service properties.
 *  
 * @author chuckd@us.ibm.com
 */
public interface IConfigListener {
	/**
	 * This method is called when the config is loaded/reloaded, after the
	 * config has been modified by any registered {@link IConfigModifier}
	 * services.
	 * 
	 * @param config
	 *            The new config object. In order to detect and react to config
	 *            changes (e.g. clearing the aggregator cache), you can save the
	 *            value returned from {@link IConfig#getRawConfig()} and compare
	 *            it with the value returned in subsequent configLoaded events
	 *            using {@link Object#equals(Object)}. Raw config objects are
	 *            serializable, and so may be persisted across server restarts.
	 * @param sequence
	 *            The sequence number. Notifications for different listener
	 *            events (options, config, dependencies) that have the same
	 *            cause have the same sequence number. Notifications resulting
	 *            from servlet initialization have the sequence number 1. The
	 *            sequence number is incremented for subsequent event causes,
	 *            but there is no guarantee about the the values for subsequent
	 *            notifications other than that they will be increasing.
	 */
	public void configLoaded(IConfig config, long sequence);
}
