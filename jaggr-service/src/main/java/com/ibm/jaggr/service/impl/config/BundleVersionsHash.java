/*
 * (C) Copyright IBM Corp. 2012, 2016
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
package com.ibm.jaggr.service.impl.config;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.config.IConfigScopeModifier;

import com.ibm.jaggr.service.impl.AggregatorImpl;
import com.ibm.jaggr.service.util.BundleVersionsHashBase;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of {@link IConfigScopeModifier} that provides a JavaScript function which will
 * return a hash of the aggregated <code>Bundle-Version</code> header values for the list of bundles
 * specified in the function arguments. If the first argument to the function is an array of
 * strings, then the first argument specifies the names of the bundle headers that should be
 * included in the hash, followed by the bundle names.
 * <p>
 * Syntax is :
 * <pre>
 *      getBundleVersionsHash('com.acme.bundle1', 'com.acme.bundle2', ...);
 * </pre>
 * or
 * <pre>
 *      getBundleVersionsHash(['Bundle-Version', 'Bnd-LastModified'], 'com.acme.bundle1', 'com.acme.bundle2', ...);
 * </pre>
 * The result of this function may be assigned to the cacheBust config property so that any change
 * in any of the values of these headers for any of the specified bundles will result in a changed
 * cacheBust value.
 * <p>
 * An alternative name for the function may be specified by the <code>propName</code> init-param in
 * the plugin extension xml.
 */
public class BundleVersionsHash extends BundleVersionsHashBase implements IExtensionInitializer, IConfigScopeModifier {
	private static final Logger log = Logger.getLogger(BundleVersionsHash.class.getName());

	private static final String DEFAULT_PROPNAME = "getBundleVersionsHash";  //$NON-NLS-1$

	protected List<IServiceRegistration> serviceRegs = new LinkedList<IServiceRegistration>();


	private String propName = null;		// the name of the function (specified by plugin.xml)

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfigScopeModifier#modifyScope(com.ibm.jaggr.core.IAggregator, org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable)
	 */
	@Override
	public void modifyScope(IAggregator aggregator, Object scope) {
		final String sourceMethod = "prepareEnv"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(BundleVersionsHash.class.getName(), sourceMethod, new Object[]{aggregator, scope});
		}
		FunctionObject fn = new FunctionObject((Scriptable)scope, this);
		ScriptableObject.putProperty((Scriptable)scope, propName, fn);
		if (isTraceLogging) {
			log.exiting(BundleVersionsHash.class.getName(), sourceMethod);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IExtensionInitializer#initialize(com.ibm.jaggr.core.IAggregator, com.ibm.jaggr.core.IAggregatorExtension, com.ibm.jaggr.core.IExtensionInitializer.IExtensionRegistrar)
	 */
	@Override
	public void initialize(IAggregator aggregator, IAggregatorExtension extension,
			IExtensionRegistrar registrar) {

		final String sourceMethod = "initialize"; //$NON-NLS-1$
		boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(BundleVersionsHash.class.getName(), sourceMethod, new Object[]{aggregator, extension, registrar});
		}
		// get the name of the function from the extension attributes
		propName = extension.getInitParams().getValue("propName"); //$NON-NLS-1$
		if (propName == null) {
			propName = DEFAULT_PROPNAME;
		}
		if (isTraceLogging) {
			log.finer("propName = " + propName); //$NON-NLS-1$
		}
		if (aggregator instanceof AggregatorImpl) {		// can be an IAggregator mock when unit testing
			setContributingBundle(((AggregatorImpl)aggregator).getContributingBundle());
		}

		if (isTraceLogging) {
			log.exiting(BundleVersionsHash.class.getName(), sourceMethod);
		}
	}

	static private class FunctionObject extends org.mozilla.javascript.FunctionObject {
		private static final long serialVersionUID = 3107068841668570072L;

		static Method method;

		static final Set<String> defaultHeaders;

		private BundleVersionsHash instance;

		static {
			try {
				method = FunctionObject.class.getMethod("bundleVersionsHash", Context.class, Scriptable.class, Object[].class, Function.class); //$NON-NLS-1$
			} catch (SecurityException e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			} catch (NoSuchMethodException e) {
				log.log(Level.SEVERE, e.getMessage(), e);
			}
			Set<String> headers = new HashSet<String>();
			headers.add("Bundle-Version"); //$NON-NLS-1$
			defaultHeaders = Collections.unmodifiableSet(headers);
		}

		FunctionObject(Scriptable scope, BundleVersionsHash instance) {
			super("bundleVersionsHash", method, scope); //$NON-NLS-1$
			this.instance = instance;
		}

		public static Set<String> getHeaderNames(NativeArray nativeArray) {
			final String sourceMethod = "getHeaderNames"; //$NON-NLS-1$
			boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(BundleVersionsHash.FunctionObject.class.getName(), sourceMethod, new Object[]{nativeArray});
			}
			Set<String> result = new HashSet<String>();
			Object[] items = nativeArray.toArray(new Object[nativeArray.size()]);
			for (Object item : items) {
				if (!(item instanceof String)) {
					throw new IllegalArgumentException(item.toString());
				}
				result.add((String)item);
			}
			if (isTraceLogging) {
				log.exiting(BundleVersionsHash.FunctionObject.class.getName(), sourceMethod, result);
			}
			return result;
		}

		@SuppressWarnings("unused")
		public static Object bundleVersionsHash(Context cx, Scriptable thisObj, Object[] args, Function funObj) throws NotFoundException {
			final String sourceMethod = "bundleVersionsHash"; //$NON-NLS-1$
			boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(BundleVersionsHash.FunctionObject.class.getName(), sourceMethod, new Object[]{cx, thisObj, args, funObj});
			}
			FunctionObject javaThis = (FunctionObject)funObj;
			StringBuffer sb = new StringBuffer();
			Set<String> headersToInclude = defaultHeaders;
			int i = 0;
			// args is an array of bundle names
			List<String> bundleNames = new ArrayList<String>();
			for (Object arg : args) {
				if (i++ == 0 && arg instanceof NativeArray) {
					headersToInclude = getHeaderNames((NativeArray)arg);
					NativeArray nativeArray = (NativeArray)arg;
					Object[] array = nativeArray.toArray(new Object[nativeArray.size()]);
				} else if (arg instanceof String) {
					bundleNames.add(arg.toString());
				} else {
					throw new IllegalArgumentException("Invalid argument type:" + arg.toString()); //$NON-NLS-1$
				}
			}
			Object result = javaThis.instance.generateHash(
					headersToInclude.toArray(new String[headersToInclude.size()]),
					bundleNames.toArray(new String[bundleNames.size()])
			);
			if (result == null) {
				result = Context.getUndefinedValue();
			}
			if (isTraceLogging) {
				log.exiting(BundleVersionsHash.FunctionObject.class.getName(), sourceMethod, result);
			}
			return result;
		}
	}

}
