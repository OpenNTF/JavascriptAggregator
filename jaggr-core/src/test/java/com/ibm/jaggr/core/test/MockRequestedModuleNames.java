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

import com.ibm.jaggr.core.BadRequestException;
import com.ibm.jaggr.core.transport.IRequestedModuleNames;

import java.util.Collections;
import java.util.List;

public class MockRequestedModuleNames implements IRequestedModuleNames{

	private List<String> modules = Collections.emptyList();
	private List<String> deps = Collections.emptyList();
	private List<String> preloads = Collections.emptyList();
	private List<String> scripts = Collections.emptyList();
	private List<String> excludes = Collections.emptyList();
	private List<String> reqExpExcludes = Collections.emptyList();
	private String layer = null;
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

	public void setLayer(String layer) {
		this.layer = layer;
	}

	@Override
	public String getLayer() throws BadRequestException {
		return layer;
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

	public List<String> getExcludes() {
		return excludes;
	}

	public void setExcludes(List<String> excludes) {
		if (excludes == null) {
			throw new NullPointerException();
		}
		this.excludes = excludes;
	}

	public void setReqExpExcludes(List<String> reqExpExcludes) {
		if (reqExpExcludes == null) {
			throw new NullPointerException();
		}
		this.reqExpExcludes = reqExpExcludes;
	}

	@Override
	public List<String> getRequireExpansionExcludes() throws BadRequestException {
		return reqExpExcludes;
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
			if (reqExpExcludes != null && !reqExpExcludes.isEmpty()) {
				sb.append(sb.length() > 0 ? ";":"").append("reqExpExcludes:").append(reqExpExcludes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
			if (excludes != null && !excludes.isEmpty()) {
				sb.append(sb.length() > 0 ? ";":"").append("excludes:").append(excludes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			result = sb.toString();
		}
		return result;
	}
}
