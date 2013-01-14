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

package com.ibm.jaggr.service.impl.layer;

import java.util.LinkedList;
import java.util.Set;

import com.ibm.jaggr.service.module.IModule;

class ModuleList extends LinkedList<ModuleList.ModuleListEntry> {
	private static final long serialVersionUID = -4578312833010749563L;
	
	static class ModuleListEntry {
		enum Type {
			MODULES,
			REQUIRED
		};
		final IModule module;
		final Type type;
		ModuleListEntry(IModule module, Type type) {
			this.module = module;
			this.type = type;
		}
		Type getType() {
			return type;
		}
		IModule getModule() {
			return module;
		}
	}
	private Set<String> dependentFeatures;
	private String requiredModuleId;
	
	ModuleList() {
		dependentFeatures = null;
		requiredModuleId = null;
	}
	
	void setDependenentFeatures(Set<String> dependentFeatures) {
		this.dependentFeatures = dependentFeatures;
	}
	
	void setRequiredModuleId(String id) {
		requiredModuleId = id;
	}
	
	String getRequiredModuleId() {
		return requiredModuleId;
	}

	Set<String> getDependentFeatures() {
		return dependentFeatures;
	}
}