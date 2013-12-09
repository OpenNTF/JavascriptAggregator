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

package com.ibm.jaggr.core.impl.module;

import java.io.Serializable;
import java.net.URI;
import java.text.MessageFormat;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cache.ICacheManager;
import com.ibm.jaggr.core.impl.layer.CompletedFuture;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.ModuleIdentifier;
import com.ibm.jaggr.core.readers.ErrorModuleReader;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.util.StringUtil;

@SuppressWarnings("serial")
public class NotFoundModule extends ModuleIdentifier implements IModule, Cloneable, Serializable {

	private final URI uri;
	
	public NotFoundModule(String mid, URI uri) {
		super(mid);
		this.uri = uri;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.domino.servlets.aggrsvc.modules.Module#get(com.ibm.domino.servlets.aggrsvc.modules.CacheManager, com.ibm.domino.servlets.aggrsvc.Options, java.util.Map)
	 */
	@Override
	public Future<ModuleBuildReader> getBuild(HttpServletRequest request) {
		return 
			new CompletedFuture<ModuleBuildReader>(
					new ModuleBuildReader(
							new ErrorModuleReader(
									ErrorModuleReader.ConsoleMethod.warn,
									MessageFormat.format(
										Messages.NotFoundModule_0,
										new Object[]{StringUtil.escapeForJavaScript(uri.toString())}
									),
									getModuleName(),
									request
							), null, true
					)
			);
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.domino.servlets.aggrsvc.modules.Module#deleteCached(com.ibm.domino.servlets.aggrsvc.modules.CacheManager, int)
	 */
	@Override
	public void clearCached(ICacheManager mgr) {
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	@Override
	public URI getURI() {
		return uri;
	}
	
	@Override
	public IResource getResource(IAggregator aggregator) {
		throw new UnsupportedOperationException();
	}
}
