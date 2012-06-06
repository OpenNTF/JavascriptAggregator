/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.module;

import java.io.Serializable;
import java.net.URI;
import java.text.MessageFormat;
import java.util.concurrent.Future;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cache.ICacheManager;
import com.ibm.jaggr.service.impl.layer.CompletedFuture;
import com.ibm.jaggr.service.module.IModule;
import com.ibm.jaggr.service.module.ModuleIdentifier;
import com.ibm.jaggr.service.readers.ErrorModuleReader;
import com.ibm.jaggr.service.readers.ModuleBuildReader;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.util.StringUtil;

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
