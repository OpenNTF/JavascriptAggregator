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

package com.ibm.jaggr.core.impl.layer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.ModuleSpecifier;

// This class is not thread safe.  It is assumed that it does not
// need to be.  If that assumption changes, then the implementation 
// will need to be reworked.
class ModuleList extends LinkedList<ModuleList.ModuleListEntry> {
	private static final long serialVersionUID = -5874021341817546757L;
	
	static class ModuleListEntry {
		final IModule module;
		final ModuleSpecifier source;
		ModuleListEntry(IModule module, ModuleSpecifier source) {
			this.module = module;
			this.source = source;
		}
		ModuleSpecifier getSource() {
			return source;
		}
		IModule getModule() {
			return module;
		}
	}
	private Set<String> dependentFeatures = null;
	private Set<String> requiredModules = null;
	
	ModuleList() {
	}
	
	ModuleList(List<ModuleListEntry> other) {
		super(other);
	}
	
	void setDependenentFeatures(Set<String> dependentFeatures) {
		this.dependentFeatures = dependentFeatures;
	}
	
	void setRequiredModules(Set<String> requiredModules) {
		this.requiredModules = requiredModules;
	}
	
	Set<String> getRequiredModules() {
		if (requiredModules == null) {
			requiredModules = new HashSet<String>();
		}
		return requiredModules;
	}
	
	Set<String> getDependentFeatures() {
		if (dependentFeatures == null) {
			dependentFeatures = new HashSet<String>();
		}
		return dependentFeatures;
	}
	
	List<IModule> getModules() {
		List<IModule> result = new ArrayList<IModule>(size());
		for (ModuleListEntry entry : this) {
			result.add(entry.getModule());
		}
		return result;
	}
}