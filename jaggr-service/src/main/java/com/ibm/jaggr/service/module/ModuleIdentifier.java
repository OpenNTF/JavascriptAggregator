/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.module;

import java.io.Serializable;


public class ModuleIdentifier implements Serializable, Cloneable{
	private static final long serialVersionUID = -7213543840330075922L;

	private final String moduleName;
	private final String pluginName;
	
	/**
	 * Default constructor implementation.
	 * 
	 * @param moduleId
	 *            The requested module name, including any loader plugin
	 *            identifier
	 */
	public ModuleIdentifier(String moduleId) {
		if (moduleId.contains("?")) { //$NON-NLS-1$
			throw new IllegalArgumentException(moduleId);
		}
        int index = moduleId.indexOf("!"); //$NON-NLS-1$
        if (index != -1) {
        	this.pluginName = moduleId.substring(0, index);
        	this.moduleName = moduleId.substring(index+1);
        } else {
        	this.pluginName = null;
        	this.moduleName = moduleId;
        }
	}

	/**
	 * Returns the module id, minus any plugin identifier that was specified
	 * in the name passed to the constructor
	 * 
	 * @return The module name
	 */
	public String getModuleName() {
		return moduleName;
	}
	
	/**
	 * Returns the plugin name specified in the module id or null.
	 * 
	 * @return the plugin name
	 */
	public String getPluginName() {
		return pluginName;
	}
	
	public String getModuleId() {
		return (pluginName == null ? "" : (pluginName + "!")) + moduleName; //$NON-NLS-1$ //$NON-NLS-2$
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
}
