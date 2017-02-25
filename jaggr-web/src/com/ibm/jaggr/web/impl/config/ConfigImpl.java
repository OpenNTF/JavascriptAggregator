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

package com.ibm.jaggr.web.impl.config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Enumeration;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.InitParams;

public class ConfigImpl extends com.ibm.jaggr.core.impl.config.ConfigImpl {

	protected Scriptable defaultConfig;
	protected Scriptable overrideConfig;

	public ConfigImpl(IAggregator aggregator) throws IOException {
		super(aggregator);
	}

	/**
	 * Initializes and returns the URI to the server-side config JavaScript
	 * 
	 * @return The config URI
	 * @throws URISyntaxException
	 * @throws FileNotFoundException
	 * @throws
	 */

	@Override
	protected URI loadConfigUri() throws URISyntaxException,
			FileNotFoundException {
		URI configUri = null;
		URL configUrl = null;
		Collection<String> configNames = getAggregator().getInitParams()
				.getValues(InitParams.CONFIG_INITPARAM);
		if (configNames.size() != 1) {
			throw new IllegalArgumentException(InitParams.CONFIG_INITPARAM);
		}
		String configName = configNames.iterator().next();

		try {
			configUrl = ((com.ibm.jaggr.web.impl.AggregatorImpl) this.getAggregator())
					.getAggregatorServletContext().getResource(
							"/WEB-INF/" + configName); //$NON-NLS-1$
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			configUri = new URI(configUrl.toString());
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return configUri;

	}

	protected void loadConfig() throws IOException, URISyntaxException {
		this.setConfigUri(loadConfigUri());
		// Try to convert to an IResource in case the URI specifies
		// an IResource supported scheme like 'namedbundleresource'.
		URI uri;
		try {
			uri = this.getAggregator().newResource(this.getConfigUri())
					.getURI();
		} catch (UnsupportedOperationException e) {
			// Not fatal. Just use the configUri as is
			uri = this.getConfigUri();
		}
		URLConnection connection = uri.toURL().openConnection();
		this.setLastModified(connection.getLastModified());

		overrideConfig = loadConfig(connection.getInputStream());
		this.setRawConfig(overrideConfig);
	}

	protected Location loadBaseURI(Scriptable cfg) throws URISyntaxException {
		Object baseObj = cfg.get(BASEURL_CONFIGPARAM, cfg);
		Location result;
		if (baseObj == Scriptable.NOT_FOUND) {
			result = new Location(getConfigUri().resolve(".")); //$NON-NLS-1$
		} else {
			Location loc = loadBaseLocation(baseObj, true);
			Location configLoc = new Location(getConfigUri(),
					loc.getOverride() != null ? getConfigUri() : null);
			result = configLoc.resolve(loc);
		}
		return result;
	}

	private Location loadBaseLocation(Object baseObj, boolean b) {

		String str = Context.toString(baseObj);
		Location result = null;

		try {
			ClassLoader classLoader = Thread.currentThread()
					.getContextClassLoader();
			Enumeration<URL> resources = classLoader.getResources(str);
			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				String resourceUrl = resource.toString();
				result = new Location(new URI(resourceUrl));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
}
