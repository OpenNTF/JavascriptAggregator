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

package com.ibm.jaggr.core.impl.options;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IPlatformServices;
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.PlatformServicesException;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.util.SequenceNumberProvider;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OptionsImpl  implements IOptions, IShutdownListener {
	private static final Logger log = Logger.getLogger(OptionsImpl.class.getName());

	private static final Map<String, String> defaults;

	static {
		Map<String, String> map = new HashMap<String, String>();
		map.put(DEVELOPMENT_MODE,	Boolean.FALSE.toString());
		map.put(DISABLE_HASFILTERING,	Boolean.FALSE.toString());
		map.put(DISABLE_REQUIRELISTEXPANSION,	Boolean.FALSE.toString());
		map.put(DISABLE_HASPLUGINBRANCHING, Boolean.FALSE.toString());
		map.put(DISABLE_MODULENAMEIDENCODING, Boolean.FALSE.toString());
		map.put(VERIFY_DEPS,		Boolean.TRUE.toString());
		map.put(DELETE_DELAY, 		Integer.toString(DEFAULT_DELETE_DELAY));
		defaults = Collections.unmodifiableMap(map);
	};

	/**
	 * The properties object.  We use {@link Properties} for the main
	 * store because of its load feature and its support for default values.
	 */
	private Properties props;

	/**
	 * An un-modifiable map that is used to shadow the properties object and is
	 * the value that is returned to callers of {@link #getOptionsMap()}
	 */
	private Map<String, String> shadowMap;

	private String registrationName = null;

	private Properties defaultOptions;

	private IAggregator aggregator = null;

	private Collection<IServiceRegistration> serviceRegistrations;

	public OptionsImpl(boolean loadFromPropertiesFile, IAggregator aggregator) {
		this.aggregator = aggregator;
		this.registrationName = aggregator != null ? aggregator.getName() : ""; //$NON-NLS-1$
		defaultOptions = new Properties(initDefaultOptions());
		if (loadFromPropertiesFile) {
			setProps(loadProps(defaultOptions));
		} else {
			setProps(defaultOptions);
		}

		// Create the properties file if necessary
		tryCreatePropsFile();

		serviceRegistrations = new LinkedList<IServiceRegistration>();
		if (aggregator != null) {
			// Register properties file changed service listener
			Dictionary<String, String> dict = new Hashtable<String, String>();
			dict.put("name", registrationName); //$NON-NLS-1$
			dict.put("propsFileName", getPropsFile().getAbsolutePath()); //$NON-NLS-1$
			serviceRegistrations.add(aggregator.getPlatformServices().registerService(OptionsImpl.class.getName(), this, dict));

			// Register shutdown listener
			dict = new Hashtable<String, String>();
			dict.put("name", registrationName); //$NON-NLS-1$
			serviceRegistrations.add(aggregator.getPlatformServices().registerService(IShutdownListener.class.getName(), this, dict));
		}
	}

	@Override
	public boolean isVerifyDeps() {
		return Boolean.parseBoolean(getOption(VERIFY_DEPS));
	}

	@Override
	public boolean isDisableRequireListExpansion() {
		return Boolean.parseBoolean(getOption(DISABLE_REQUIRELISTEXPANSION));
	}

	@Override
	public boolean isDevelopmentMode() {
		return Boolean.parseBoolean(getOption(DEVELOPMENT_MODE));
	}

	@Override
	public boolean isDebugMode() {
		return Boolean.parseBoolean(getOption(DEBUG_MODE));
	}

	@Override
	public boolean isDisableHasFiltering() {
		return Boolean.parseBoolean(getOption(DISABLE_HASFILTERING));
	}

	@Override
	public boolean isDisableHasPluginBranching() {
		return Boolean.parseBoolean(getOption(DISABLE_HASPLUGINBRANCHING));
	}

	@Override
	public boolean isDisableModuleNameIdEncoding() {
		return Boolean.parseBoolean(getOption(DISABLE_MODULENAMEIDENCODING));
	}

	@Override
	public String getCacheBust() {
		return getOption(CACHEBUST);
	}

	@Override
	public String getCacheDirectory() {
		return getOption(CACHE_DIRECTORY);
	}

	@Override
	public int getDeleteDelay() {
		int result = DEFAULT_DELETE_DELAY;
		String value = getOption(DELETE_DELAY);
		if (value != null) {
			try {
				result = Integer.parseInt(value);
			} catch (NumberFormatException ignore) {}
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.options.IOptions#setOption(java.lang.String, boolean)
	 */
	@Override
	public void setOption(String name, boolean value) throws IOException {
		setOption(name, Boolean.toString(value));
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.options.IOptions#setOption(java.lang.String, java.lang.String)
	 */
	@Override
	public synchronized void setOption(String name, String value) throws IOException {
		// update the property
		Properties props = getProps();
		if (value == null) {
			props.remove(name);
		} else {
			props.setProperty(name, value);
		}
		setProps(props);
		// Persist the new properties
		saveProps(props);

		long seq = SequenceNumberProvider.incrementAndGetSequenceNumber();
		// Notify update listeners
		updateNotify(seq);
		// Notify options instances using the same properties file
		propsFileUpdateNotify(props, seq);

	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.options.IOptions#getOption(java.lang.String)
	 */
	@Override
	public String getOption(String name) {
		return getOptionsMap().get(name);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.options.IOptions#getOptions()
	 */
	@Override
	public Map<String, String> getOptionsMap() {
		return shadowMap;
	}

	@Override
	public String getName() {
		return registrationName;
	}

	/**
	 * Returns the filename of the properties file that the options
	 * are loaded from/saved to.
	 *
	 * @return The Options properties file name
	 */
	public String getPropsFilename() {
		return propsFilename;
	}

	/**
	 * Returns a {@code File} object to the properties file.
	 *
	 * @return The properties file.
	 */
	public File getPropsFile() {
		String homedir = System.getProperty("user.home"); //$NON-NLS-1$
		String filename = getPropsFilename();
		return filename != null ? new File(homedir, filename) : null;
	}

	/**
	 * Loads the Options properties from the aggregator properties file
	 * into the specified properties object.  If the properties file
	 * does not exist, then try to load from the bundle.
	 *
	 * @param props
	 *            The properties object to load. May be initialized with default
	 *            values.
	 * @return The properties file specified by {@code props}.
	 */
	protected Properties loadProps(Properties props) {

		// try to load the properties file first from the user's home directory
		File file = getPropsFile();
		if (file != null) {
			// Try to load the file from the user's home directory
			if (file.exists()) {
				try {
					URL url = file.toURI().toURL();
					loadFromUrl(props, url);
				} catch (MalformedURLException ex) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, ex.getMessage(), ex);
					}
				}
			}
		}
		return props;
	}

	/**
	 * Saves the specified Options properties to the properties file for the
	 * aggregator.
	 *
	 * @param props
	 *            The properties to save
	 * @throws IOException
	 */
	protected void saveProps(Properties props) throws IOException {
		// Persist the change to the properties file.
		File file = getPropsFile();
		if (file != null) {
			FileWriter writer = new FileWriter(file);
			try {
				props.store(writer, null);
			} finally {
				writer.close();
			}
		}
	}

	/**
	 * Notify options change listeners that Options have been updated
	 *
	 * @param sequence The change sequence number.
	 */
	protected void updateNotify (long sequence) {

		IServiceReference[] refs = null;
		try {
			if(aggregator != null && aggregator.getPlatformServices() != null){
				refs = aggregator.getPlatformServices().getServiceReferences(IOptionsListener.class.getName(),"(name=" + registrationName + ")");	//$NON-NLS-1$ //$NON-NLS-2$
				if (refs != null) {
					for (IServiceReference ref : refs) {
						IOptionsListener listener = (IOptionsListener)aggregator.getPlatformServices().getService(ref);
						if (listener != null) {
							try {
								listener.optionsUpdated(this, sequence);
							} catch (Throwable ignore) {
							} finally {
								aggregator.getPlatformServices().ungetService(ref);
							}
						}
					}
				}
			}
		} catch (PlatformServicesException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}

	/**
	 * Listener method for being informed of changes to the properties file by another
	 * instance of this class.  Called by other instances of this class for instances
	 * that use the same properties file when properties are saved.
	 *
	 * @param updated
	 *            the updated properties
	 * @param sequence
	 *            the update sequence number
	 */
	protected void propertiesFileUpdated(Properties updated, long sequence) {
		Properties newProps = new Properties(getDefaultOptions());
		Enumeration<?> propsEnum = updated.propertyNames();
		while (propsEnum.hasMoreElements()) {
			String name = (String)propsEnum.nextElement();
			newProps.setProperty(name, updated.getProperty(name));
		}
		setProps(newProps);
		updateNotify(sequence);
	}

	void tryCreatePropsFile() {
		// If the properties file doesn't exist, create it
		File propsFile = getPropsFile();
		if (!propsFile.exists()) {
			try {
				new File(FilenameUtils.getFullPath(propsFile.getAbsolutePath())).mkdirs();
				saveProps(props);
			} catch (IOException ex) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, ex.getMessage(), ex);
				}
			}
		}

	}
	/**
	 * Notify implementations of this class that are listening for properties file updates
	 * on the same properties file that the properties file has been updated.
	 *
	 * @param updatedProps
	 *            the updated properties
	 * @param sequence
	 *            the update sequence number
	 */
	protected void propsFileUpdateNotify(Properties updatedProps, long sequence) {
		if(aggregator == null || aggregator.getPlatformServices() == null) return;	// unit tests?
		IPlatformServices platformServices = aggregator.getPlatformServices();
		try {
			IServiceReference[] refs = platformServices.getServiceReferences(OptionsImpl.class.getName(),
					"(propsFileName=" + getPropsFile().getAbsolutePath().replace("\\", "\\\\") + ")");	//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			if (refs != null) {
				for (IServiceReference ref : refs) {
					String name = ref.getProperty("name"); //$NON-NLS-1$
					if (!registrationName.equals(name)) {
						OptionsImpl impl = (OptionsImpl)platformServices.getService(ref);
						if (impl != null) {
							try {
								impl.propertiesFileUpdated(updatedProps, sequence);
							} catch (Throwable ignore) {}
							finally {
								platformServices.ungetService(ref);
							}
						}
					}
				}
			}
		} catch (PlatformServicesException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IShutdownListener#shutdown(com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		for(IServiceRegistration reg : serviceRegistrations) {
			reg.unregister();
		}
		serviceRegistrations.clear();
		aggregator = null;
	}


	/**
	 * Load the properties in the specified url into <code>props</code>.
	 *
	 * @param props the properties object to update
	 * @param url the properties file to load
	 */
	protected void loadFromUrl(Properties props, URL url) {
		InputStream is = null;
		try {
			is = url.openStream();
			props.load(is);
		} catch (IOException ex) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, ex.getMessage(), ex);
			}
		} finally {
			try { if (is != null) is.close(); } catch (IOException ignore) {}
		}
	}

	/**
	 * Returns the default options for this the aggregator service.
	 *
	 * @return The default options.
	 */
	protected Properties initDefaultOptions() {
		Properties defaultValues = new Properties();
		defaultValues.putAll(defaults);

		// See if there's an aggregator.properties in the class loader's root
		ClassLoader cl = OptionsImpl.class.getClassLoader();
		URL url = cl.getResource(getPropsFilename());
		if (url != null) {
			loadFromUrl(defaultValues, url);
		}

		// If the bundle defines properties, then load those too
		if(aggregator != null){
			url = aggregator.getPlatformServices().getResource(getPropsFilename());
			if (url != null) {
				loadFromUrl(defaultValues, url);
			}
		}
		return defaultValues;
	}

	protected Properties getDefaultOptions() {
		return defaultOptions;
	}

	/**
	 * Returns the properties object for the current options.
	 *
	 * @return The properties object
	 */
	protected Properties getProps() {
		return props;
	}

	/**
	 * Sets the properties object for the current options and updates
	 * the shadow map that is returned to callers of {@link #getOptionsMap()}.
	 *
	 * @param props The new properties.
	 */
	protected void setProps(Properties props) {
		this.props = props;
		// Update the shadow map
		Map<String, String> map = new HashMap<String, String>();
		for (String name : props.stringPropertyNames()) {
			map.put(name, (String)props.getProperty(name));
		}
		shadowMap = Collections.unmodifiableMap(map);
	}
}
