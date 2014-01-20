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

package com.ibm.jaggr.core.module;

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
