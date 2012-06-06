/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.layer;

import java.util.LinkedList;
import java.util.Set;

import com.ibm.jaggr.service.cachekeygenerator.FeatureSetCacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.module.IModule;

class ModuleList extends LinkedList<ModuleList.ModuleListEntry> {
	private static final long serialVersionUID = -4578312833010749563L;
	
	private static final ICacheKeyGenerator[] emptyKeyGens = new ICacheKeyGenerator[0];

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
	
	ICacheKeyGenerator[] getCacheKeyGenerators() {
		ICacheKeyGenerator[] result = emptyKeyGens;
		if (dependentFeatures != null && dependentFeatures.size() >= 0) {
			result = new ICacheKeyGenerator[]{new FeatureSetCacheKeyGenerator(dependentFeatures, false)};
		}
		return result;
	}
}