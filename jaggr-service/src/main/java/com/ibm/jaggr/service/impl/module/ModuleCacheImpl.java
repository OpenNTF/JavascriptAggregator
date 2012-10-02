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

package com.ibm.jaggr.service.impl.module;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.module.IModuleCache;

/**
 * This class implements the {@link IModuleCache} interface by extending {@link ConcurrentHashMap}
 * and adds methods for cloning and dumping the cache contents.
 */
public class ModuleCacheImpl extends ConcurrentHashMap<String, IModule> implements IModuleCache {
	private static final long serialVersionUID = 2506609170016466623L;

	// Default constructor
	public ModuleCacheImpl() {
	}
	
	// Copy constructor
	// Creates a shallow copy of the map
	private ModuleCacheImpl(ModuleCacheImpl other) {
		super(other);
	}
	
	/* (non-Javadoc)
	 * @see java.util.AbstractMap#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		ModuleCacheImpl cloned = new ModuleCacheImpl(this);
		// The copy constructor creates a shallow copy, so we
		// we need to clone the individual values.  Keys don't need to be cloned since
		// strings are immutable.
		for (Map.Entry<String, IModule> entry : cloned.entrySet()) {
			entry.setValue((IModule)entry.getValue().clone());
		}
		return cloned;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.IModuleCache#dump(java.io.Writer, java.util.regex.Pattern)
	 */
	@Override
	public void dump(Writer writer, Pattern filter) throws IOException {
    	String linesep = System.getProperty("line.separator"); //$NON-NLS-1$
    	for (Map.Entry<String, IModule> entry : entrySet()) {
    		if (filter != null) {
    			Matcher m = filter.matcher(entry.getKey());
    			if (!m.find())
    				continue;
    		}
    		writer.append("IModule key: ").append(entry.getKey()).append(linesep); //$NON-NLS-1$
    		writer.append(entry.getValue().toString()).append(linesep).append(linesep);
    	}
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.IModuleCache#remove(java.lang.String)
	 */
	@Override
	public IModule remove(String key) {
		return super.remove(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.IModuleCache#get(java.lang.String)
	 */
	@Override
	public IModule get(String key) {
		return super.get(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.IModuleCache#contains(java.lang.String)
	 */
	@Override
	public boolean contains(String key) {
		return super.contains(key);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.IModuleCache#getKeys()
	 */
	@Override
	public Set<String> getKeys() {
		return super.keySet();
	}
}
