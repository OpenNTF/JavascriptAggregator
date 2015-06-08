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

import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.modulebuilder.ModuleBuildFuture;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.TypeUtil;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

/**
 * Sorts the readers from the provided list of futures into separate module-id/reader maps
 * according to the future's {@link ModuleSpecifier}
 */
public class SortedReaders {
	private final Map<IModule, ModuleBuildReader> scripts = new LinkedHashMap<IModule, ModuleBuildReader>();
	private final Map<IModule, ModuleBuildReader> modules = new LinkedHashMap<IModule, ModuleBuildReader>();
	private final Map<IModule, ModuleBuildReader> cacheEntries = new LinkedHashMap<IModule, ModuleBuildReader>();
	private final boolean noAddModules;

	public SortedReaders(List<ModuleBuildFuture> futures, HttpServletRequest request) throws IOException {
		noAddModules = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOADDMODULES_REQATTRNAME));
		for (ModuleBuildFuture future : futures) {
			sortFuture(future);
		}
	}

	public Map<IModule, ModuleBuildReader> getScripts() {
		return scripts;
	}

	public Map<IModule, ModuleBuildReader> getModules() {
		return modules;
	}

	public Map<IModule, ModuleBuildReader> getCacheEntries() {
		return cacheEntries;
	}

	private void sortFuture(ModuleBuildFuture future) throws IOException {
		try {
			ModuleSpecifier spec = future.getModuleSpecifier();
			ModuleBuildReader reader = future.get();
			IModule module = future.getModule();
			switch (spec) {
			case SCRIPTS:
				scripts.put(module, reader);
				break;
			case LAYER:
			case BUILD_ADDED:
				cacheEntries.put(module, reader);
				break;
			case MODULES:
				modules.put(module, reader);
				break;
			}
			if (!noAddModules) {
				for (ModuleBuildFuture extra : reader.getExtraBuilds()) {
					sortFuture(extra);
				}
			}
		} catch (InterruptedException e) {
			throw new IOException(e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			}
			throw new IOException(e);
		}
	}

}