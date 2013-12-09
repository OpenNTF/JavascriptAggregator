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

package com.ibm.jaggr.core.deps;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.jaggr.core.util.BooleanTerm;

/**
 * This class extends LinkedHashMap to provide additional methods for 
 * managing a map of module names to ModuleDepInfo objects.
 * 
 * This class is not thread safe, so external provisions for thread
 * safety need to be made if instances of this class are to be shared
 * by multiple threads.
 */
public class ModuleDeps extends LinkedHashMap<String, ModuleDepInfo> {
	
	private static final long serialVersionUID = -8057569894102155520L;

	public ModuleDeps() {
	}
	
	public ModuleDeps(ModuleDeps other) {
		addAll(other);
	}
	
	/**
	 * Adds the specified pair to the map. If an entry for the key exists, then
	 * the specified module dep info is added to the existing module dep info.
	 * 
	 * @param key
	 *            The module name to associate with the dep info object
	 * @param info
	 *            the ModuleDepInfo object
	 * @return true if the map was modified
	 */
	public boolean add(String key, ModuleDepInfo info) {
		if (info == null) {
			throw new NullPointerException();
		}
		boolean modified = false;
		ModuleDepInfo existing = get(key);
		if (!containsKey(key) || existing != info) {
			if (existing != null) {
				modified = existing.add(info);
			} else {
				super.put(key, info);
				modified = true;
			}
		}
		return modified;
	}
	
	/**
	 * Adds all of the map entries from other to this map
	 * 
	 * @param other
	 *            the map containing entries to add
	 * @return true if the map was modified
	 */
	public boolean addAll(ModuleDeps other) {
		boolean modified = false;
		for (Map.Entry<String, ModuleDepInfo> entry : other.entrySet()) {
			modified |= add(entry.getKey(), entry.getValue());
		}
		return modified;
	}

	/**
	 * Returns true if the map contains the module name with the specified term.
	 * See {@link ModuleDepInfo#containsTerm(BooleanTerm)} for a description of
	 * containment.
	 * 
	 * @param moduleName
	 *            The module name
	 * @param term
	 *            the boolean term
	 * @return True if the map contains the module name with the specified term.
	 */
	public boolean containsDep(String moduleName, BooleanTerm term) {
		boolean result = false;
		ModuleDepInfo existing = get(moduleName);
		if (existing != null) {
			result = existing.containsTerm(term);
		}
		return result;
	}
	
	/**
	 * Subtracts the terms in <code>subInfo</code> from the terms in the module
	 * dep info object associated with the specified key.
	 * <p>
	 * See {@link ModuleDepInfo#subtract(ModuleDepInfo)} for a description of
	 * what it means to subtract boolean terms.
	 * 
	 * @param key
	 *            The module name
	 * @param subInfo
	 *            The object containing the terms to subtract
	 * @return true if this map was modified
	 */
	public boolean subtract(String key, ModuleDepInfo subInfo) {
		boolean modified = false;
		ModuleDepInfo info = get(key);
		if (info != null) {
			modified = info.subtract(subInfo);
		}
		return modified;
	}
	
	/**
	 * Calls {@link #subtract(String, ModuleDepInfo)} for each of the map
	 * entries in <code>toSub</code>
	 * 
	 * @param toSub
	 *            The map to subtract from this map
	 * @return True if this map was modified
	 */
	public boolean subtractAll(ModuleDeps toSub) {
		boolean modified = false;
		for (Map.Entry<String, ModuleDepInfo> entry : toSub.entrySet()) {
			modified |= subtract(entry.getKey(), entry.getValue());
		}
		return modified;
	}

	/**
	 * Returns a set of module ids for the keys in this map. If the module dep
	 * info objects associated with a key specifies has plugin prefixes, then
	 * the entries will include the prefixes. Note that one map entry may result
	 * in multiple (or zero) result entries depending on the evaluation of the
	 * boolean formula which represents the has conditionals.
	 * 
	 * @return The set of module ids
	 */
	public Set<String> getModuleIds() {
		Set<String> result = new LinkedHashSet<String>();
		for (Map.Entry<String, ModuleDepInfo> entry : entrySet()) {
			Collection<String> prefixes = entry.getValue().getHasPluginPrefixes();
			if (prefixes == null) {
				result.add(entry.getKey());
			} else {
				for (String prefix : prefixes) {
					result.add(prefix + entry.getKey());
				}
			}
		}
		return result;
	}
	
	/**
	 * Returns a map of module ids with comments for the keys in this map. If
	 * the module dep info objects associated with a key specifies has plugin
	 * prefixes, then the entries will include the prefixes. Note that one map
	 * entry may result in multiple (or zero) result entries depending on the
	 * evaluation of the boolean formula which represents the has conditionals.
	 * 
	 * @return The map of module id, comment pairs
	 */
	public Map<String, String> getModuleIdsWithComments() {
		Map<String, String> result = new LinkedHashMap<String, String>();
		for (Map.Entry<String, ModuleDepInfo> entry : entrySet()) {
			String comment = entry.getValue().getComment();
			Collection<String> prefixes = entry.getValue().getHasPluginPrefixes();
			if (prefixes == null) {
				result.put(entry.getKey(), comment);
			} else {
				for (String prefix : prefixes) {
					result.put(prefix + entry.getKey(), comment);
				}
			}
		}
		return result;
	}
	
	/**
	 * Calls {@link ModuleDepInfo#simplify()} on each of the
	 * values in the map.
	 */
	public ModuleDeps simplify() {
		for (ModuleDepInfo info : values()) {
			info.simplify();
		}
		return this;
	}
	
	/**
	 * Calls {@link ModuleDepInfo#simplifyInvariants()} on each of the
	 * values in the map.
	 */
	public ModuleDeps simplifyInvariants() {
		for (ModuleDepInfo info : values()) {
			info.simplifyInvariants();
		}
		return this;
	}
	
	@Override
	public ModuleDepInfo put(String key, ModuleDepInfo value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map<? extends String, ? extends ModuleDepInfo> map) {
		throw new UnsupportedOperationException();
	}


}
