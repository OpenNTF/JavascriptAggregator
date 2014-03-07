/*
 * (C) Copyright 2014, IBM Corporation
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

package com.ibm.jaggr.core.impl.modulebuilder.less;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessException;
import com.ibm.jaggr.core.*;
import com.ibm.jaggr.core.cachekeygenerator.AbstractCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigListener;
import com.ibm.jaggr.core.impl.modulebuilder.text.TextModuleBuilder;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.TypeUtil;
import org.mozilla.javascript.Scriptable;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

/**
 * This class compiles LESS resources that are loaded by the AMD aggregator.
 * If {@link #COMPRESS_PARAM} is true, then the output will be compressed.
 */
public class LessModuleBuilder extends TextModuleBuilder implements
		IExtensionInitializer, IConfigListener, IShutdownListener {
	// Configuration Fields
	public static final String COMPRESS_PARAM = "compress";
	public static final boolean COMPRESS_DEFAULT_VALUE = false;
	private static Boolean compress;

	private List<ServiceRegistration> registrations = new
			LinkedList<ServiceRegistration>();

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.impl.text
	 * .TextModuleBuilder#getContentReader(java.lang.String,
	 * com.ibm.jaggr.service.resource.IResource,
	 * javax.servlet.http.HttpServletRequest, com.ibm.jaggr.service.module
	 * .ICacheKeyGenerator)
	 */
	@Override
	protected Reader getContentReader(
			String mid,
			IResource resource,
			HttpServletRequest request,
			List<ICacheKeyGenerator> keyGens)
			throws IOException {

		String less = readToString(resource.getReader());
		LessEngine engine = new LessEngine();
		try {
			return new StringReader(engine.compile(less, resource.getURI().toString
					(), compress));
		} catch (LessException e) {
			e.printStackTrace();
		}
		return new StringReader("/*COMPILATIONERROR*/\n" + less);
	}

	/**
	 * Copies the contents of the specified {@link Reader} to a String.
	 *
	 * @param in The input Reader
	 * @return The contents of the Reader as a String
	 * @throws IOException
	 */
	protected String readToString(Reader in) throws IOException {
		StringWriter out = new StringWriter();
		CopyUtil.copy(in, out);
		return out.toString();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.IModuleBuilder#handles(java.lang
	 * .String, com.ibm.jaggr.service.resource.IResource)
	 */
	@Override
	public boolean handles(String mid, IResource resource) {
		return mid.endsWith(".less"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IExtensionInitializer#initialize(com.ibm.jaggr
	 * .service.IAggregator, com.ibm.jaggr.service.IAggregatorExtension,
	 * com.ibm.jaggr.service.IExtensionInitializer.IExtensionRegistrar)
	 */
	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension
			extension, IExtensionRegistrar registrar) {
		Hashtable<String, String> props = new Hashtable<String, String>();
		props.put("name", aggregator.getName());
		registrations.add(aggregator.getPlatformServices().registerService
				(IConfigListener.class.getName(), this, props));
		props = new Hashtable<String, String>();
		props.put("name", aggregator.getName());
		registrations.add(aggregator.getPlatformServices().registerService
				(IShutdownListener.class.getName(), this, props));
		IConfig config = aggregator.getConfig();
		if (config != null) {
			configLoaded(config, 1);
		}
	}


	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfigListener#configLoaded(com.ibm
	 * .jaggr.service.config.IConfig, long)
	 */
	@Override
	public void configLoaded(IConfig conf, long sequence) {
		Scriptable config = conf.getRawConfig();
		/** True if the output should be compressed **/
		Object obj = config.get(COMPRESS_PARAM, config);
		compress = TypeUtil.asBoolean(obj == Scriptable.NOT_FOUND ? null : obj
				.toString(), COMPRESS_DEFAULT_VALUE);
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IShutdownListener#shutdown(com.ibm.jaggr
	 * .service.IAggregator)
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		for (ServiceRegistration reg : registrations) {
			reg.unregister();
		}
	}
}
