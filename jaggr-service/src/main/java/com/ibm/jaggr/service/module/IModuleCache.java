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

package com.ibm.jaggr.service.module;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Interface for module cache object
 */
public interface IModuleCache {
	
	/**
	 * Adds the module to the cache if a module with the specified key
	 * is not already in the cache
	 * 
	 * @param key the module key
	 * @param newModule the module to add
	 * @return the existing module, or null if the module was added
	 */
	IModule putIfAbsent(String key, IModule newModule);
	
	/**
	 * Returns the module with the specified key, or null if the 
	 * module with the specified key is not in the cache
	 * 
	 * @param key the module key
	 * @return the requested module or null
	 */
	IModule get(String key);
	
	/**
	 * Returns true if the module with the specified key is in the 
	 * cache
	 * 
	 * @param key the module key
	 * @return true if the specified module is in the cache
	 */
	boolean contains(String key);
	
	/**
	 * Removes the module with the specified key from the cache
	 * 
	 * @param key the module key
	 * @return The module that was removed, or null if not in the cache
	 */
	IModule remove(String key);

	/**
	 * Remove all modules in the cache 
	 */
	public void clear();
	
	/**
	 * Returns the number of entries in the layer cache
	 * 
	 * @return the number of entries
	 */
	public int size();
	
	/**
	 * Returns the set of keys associated with entries in the cache
	 * 
	 * @return the key set
	 */
	public Set<String> getKeys();
	
	/**
	 * Implementors of this interface must provide the clone method
	 * 
	 * @return the cloned object
	 * @throws CloneNotSupportedException
	 */
	public Object clone() throws CloneNotSupportedException;

    /**
     * Output the cache info to the specified Writer
     * 
     * @param writer the target Writer
     * @param filter Optional filter argument
     * @throws IOException
     */
	public void dump(Writer writer, Pattern filter) throws IOException;
}
