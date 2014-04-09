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
package com.ibm.jaggr.core.test;

import com.ibm.jaggr.core.transport.IRequestedModuleNames;

import java.util.Collections;
import java.util.List;

public class MockRequestedModuleNames implements IRequestedModuleNames{

	private List<String> modules = Collections.emptyList();
	private List<String> deps = Collections.emptyList();
	private List<String> preloads = Collections.emptyList();
	private List<String> scripts = Collections.emptyList();
	private String strRep = null;

	public List<String> getModules() {
		return modules;
	}

	public void setModules(List<String> modules) {
		if (modules == null) {
			throw new NullPointerException();
		}
		this.modules = modules;
	}

	public List<String> getDeps() {
		return deps;
	}

	public void setDeps(List<String> deps) {
		if (modules == null) {
			throw new NullPointerException();
		}
		this.deps = deps;
	}

	public List<String> getPreloads() {
		return preloads;
	}

	public void setPreloads(List<String> preloads) {
		if (modules == null) {
			throw new NullPointerException();
		}
		this.preloads = preloads;
	}

	public List<String> getScripts() {
		return scripts;
	}

	public void setScripts(List<String> scripts) {
		if (scripts == null) {
			throw new NullPointerException();
		}
		this.scripts = scripts;
	}

	public void setString(String strRep) {
		this.strRep = strRep;
	}

	@Override
	public String toString() {
		String result = null;
		if (strRep != null) {
			result = strRep;
		} else {
			StringBuffer sb = new StringBuffer();
			if (modules != null && !modules.isEmpty()) {
				sb.append(modules);
			}
			if (scripts != null && !scripts.isEmpty()) {
				sb.append(sb.length() > 0 ? ";":"").append("scripts:").append(scripts); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			if (deps != null && !deps.isEmpty()) {
				sb.append(sb.length() > 0 ? ";":"").append("deps:").append(deps); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			if (preloads != null && !preloads.isEmpty()) {
				sb.append(sb.length() > 0 ? ";":"").append("preloads:").append(preloads); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			result = sb.toString();
		}
		return result;
	}


}
