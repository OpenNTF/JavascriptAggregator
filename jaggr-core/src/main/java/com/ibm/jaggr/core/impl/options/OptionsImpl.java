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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.jaggr.core.impl.PlatformAggregatorFactory;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.options.IOptionsListener;
import com.ibm.jaggr.core.util.SequenceNumberProvider;

public class OptionsImpl  implements IOptions {
	private static final Logger log = Logger.getLogger(OptionsImpl.class.getName());

	private static final Map<String, String> defaults;
	
	static {
		Map<String, String> map = new HashMap<String, String>();
		map.put(DEVELOPMENT_MODE,	Boolean.FALSE.toString());
		map.put(DISABLE_HASFILTERING,	Boolean.FALSE.toString());
		map.put(DISABLE_REQUIRELISTEXPANSION,	Boolean.FALSE.toString());
		map.put(DISABLE_HASPLUGINBRANCHING, Boolean.FALSE.toString());
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
	
	private boolean updating = false;
	
	public OptionsImpl(String registrationName) {
		this(registrationName, true);
	}
	
	public OptionsImpl(String registrationName, boolean loadFromPropertiesFile) {
		
		this.registrationName = registrationName;
		Properties defaultOptions = new Properties(getDefaultOptions());
		if (loadFromPropertiesFile) {
			setProps(loadProps(defaultOptions));
		} else {
			setProps(defaultOptions);
		}
	}
	
	public OptionsImpl(boolean loadFromPropertiesFile) {		
		Properties defaultOptions = new Properties(getDefaultOptions());
		if (loadFromPropertiesFile) {
			setProps(loadProps(defaultOptions));
		} else {
			setProps(defaultOptions);
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
	 * @see com.ibm.jaggr.core.options.IOptions#setOption(java.lang.String, boolean)
	 */
	@Override
	public void setOption(String name, boolean value) throws IOException {
		setOption(name, Boolean.toString(value));
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.options.IOptions#setOption(java.lang.String, java.lang.String)
	 */
	@Override 
	public synchronized void setOption(String name, String value) throws IOException {
		if (isUpdating()) {
			// This will happen if setOption is called from within an options 
			// update listener.
			throw new ConcurrentModificationException();
		}
		setUpdating(true);
		try {
			// update the property
			Properties props = getProps();
			if (value == null) {
				props.remove(name);
			} else {
				props.setProperty(name, value);
			}
			setProps(props);

			// Notify update listeners
			updateNotify(SequenceNumberProvider.incrementAndGetSequenceNumber());

			// Persist the new properties
			saveProps(props);
		} finally {
			setUpdating(false);
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.options.IOptions#getOption(java.lang.String)
	 */
	@Override
	public String getOption(String name) {
		return getOptionsMap().get(name);
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.options.IOptions#getOptions()
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
	    	InputStream in = null;
	    	try {
	    		// Try to load the file from the user's home directory
		    	if (file.exists()) {
		    		in = new FileInputStream(file);
		    	}
		    	if (in == null) {
		    		// Try to load it from the bundle		   			
		   			URL url = PlatformAggregatorFactory.getPlatformAggregator().getResource(getPropsFilename());
		    		if (url != null) { 
		    			in = url.openStream();
		    		}
		    	}
	    		if (in == null) {
	    			// try using the class loader
	   				in = this.getClass().getClassLoader().getResourceAsStream(getPropsFilename());
	    		}
		    	if (in != null) {
			        props.load(in);
			        in.close();
		    	}
	    	} catch (IOException e) {
	    		if (log.isLoggable(Level.WARNING)) {
	    			log.log(Level.SEVERE, e.getMessage(), e);
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
	protected void updateNotify(long sequence) {
		
		Object[] refs = null;
		try {
			if(PlatformAggregatorFactory.getPlatformAggregator() != null){
				refs = PlatformAggregatorFactory.getPlatformAggregator().getServiceReferences(IOptionsListener.class.getName(),"(name=" + registrationName + ")");			
				if (refs != null) {
					for (Object ref : refs) {
						IOptionsListener listener = (IOptionsListener)PlatformAggregatorFactory.getPlatformAggregator().getService(ref);
						if (listener != null) {
							try {
								listener.optionsUpdated(this, sequence);
							} catch (Throwable ignore) {
							} finally {
								PlatformAggregatorFactory.getPlatformAggregator().ungetService(ref);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
	}
	
	/**
	 * Returns the default options for this the aggregator service.
	 * 
	 * @return The default options.
	 */
	protected Properties getDefaultOptions() {
		Properties defaultValues = new Properties();
		defaultValues.putAll(defaults);
		return defaultValues;
	}

	/**
	 * Returns true if properties are being updated, including calling
	 * of options update listeners resulting from an update.
	 * 
	 * @return The update flag
	 */
	protected boolean isUpdating() {
		return updating;
	}
	
	/**
	 * Sets the update flag.
	 * 
	 * @param updating The new value of the update flag.
	 */
	protected void setUpdating(boolean updating) {
		this.updating = updating;
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
