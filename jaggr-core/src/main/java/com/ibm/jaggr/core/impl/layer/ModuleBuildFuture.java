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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.readers.ModuleBuildReader;

/**
 * This class encapsulates a {@link Future} for a {@link ModuleBuildReader},
 * plus associated information about the module. Instances of this object are
 * the elements used in the layer build queue which is exposed via the
 * {@link ILayer#BUILDFUTURESQUEUE_REQATTRNAME} request attribute.
 */
public class ModuleBuildFuture implements Future<ModuleBuildReader> {
	
	private final Future<ModuleBuildReader> future;
	private final ModuleSpecifier moduleSpecifier;
	private final IModule module;

	public ModuleBuildFuture(IModule module, Future<ModuleBuildReader> future, ModuleSpecifier moduleSpecifier) {
		this.module = module;
		this.future = future;
		this.moduleSpecifier = moduleSpecifier;
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return future.cancel(mayInterruptIfRunning);
	}

	@Override
	public boolean isCancelled() {
		return future.isCancelled();
	}

	@Override
	public boolean isDone() {
		return future.isDone();
	}

	@Override
	public ModuleBuildReader get() throws InterruptedException,
			ExecutionException {
		return future.get();
	}

	@Override
	public ModuleBuildReader get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return future.get(timeout, unit);
	}
	
	public ModuleSpecifier getModuleSpecifier() {
		return moduleSpecifier;
	}
	
	public IModule getModule() {
		return module;
	}
}
