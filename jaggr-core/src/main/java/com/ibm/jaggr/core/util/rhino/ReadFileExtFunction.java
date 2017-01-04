/*
 * (C) Copyright IBM Corp. 2014, 2016 All Rights Reserved.
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
package com.ibm.jaggr.core.util.rhino;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigListener;
import com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilder;
import com.ibm.jaggr.core.readers.CommentStrippingReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.TypeUtil;

import com.google.javascript.rhino.head.Undefined;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Implements the JavaScript readFileExt function used by the LESS module builder to load &#64import
 * resources. If the config specifies the {@link CSSModuleBuilder#INCLUDEAMDPATHS_CONFIGPARAM}
 * property, then file names that look like AMD module ids will be resolved as such <b>before</b>
 * attempting to resolve the filename as a simple relative path. To disambiguate, prefix the import
 * name with './' to specify a relative path instead of an AMD module name.
 */
public class ReadFileExtFunction extends FunctionObject implements IConfigListener, IShutdownListener {
	private static final long serialVersionUID = 8795456757297823566L;
	static final private String sourceClass = ReadFileExtFunction.class.getName();
	static final private Logger log = Logger.getLogger(sourceClass);

	static final public String FUNCTION_NAME = "readFileExt"; //$NON-NLS-1$

	static final private Pattern NON_AMD_PATH_PAT = Pattern.compile("^(?:[a-z-]+:|\\/|\\.)"); //$NON-NLS-1$
	static final public Pattern WEBPACK_MODULE_PAT = Pattern.compile("^~[^/]"); //$NON-NLS-1$


	private final IAggregator aggregator;
	private boolean isIncludeAMDPaths = false;
	private List<IServiceRegistration> registrations = new LinkedList<IServiceRegistration>();

	static Method readFileExtMethod;

	static {
		try {
			readFileExtMethod = ReadFileExtFunction.class.getMethod(FUNCTION_NAME, Context.class,
					Scriptable.class, Object[].class, Function.class);
		} catch (SecurityException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		} catch (NoSuchMethodException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	public ReadFileExtFunction(Scriptable scope, IAggregator aggregator) {
		super(FUNCTION_NAME, readFileExtMethod, scope);
		final String sourceMethod = "<ctor>"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[] { scope, aggregator });
		}
		this.aggregator = aggregator;

		// Register config and shutdown listeners.
		Hashtable<String, String> props;
		props = new Hashtable<String, String>();
		props.put("name", aggregator.getName()); //$NON-NLS-1$
		registrations.add(aggregator.getPlatformServices().registerService(IConfigListener.class.getName(), this, props));
		props = new Hashtable<String, String>();
		props.put("name", aggregator.getName()); //$NON-NLS-1$
		registrations.add(aggregator.getPlatformServices().registerService(IShutdownListener.class.getName(), this, props));
		IConfig config = aggregator.getConfig();
		if (config != null) {
			configLoaded(config, 1);
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, this);
		}
	}

	/**
	 * Read file function called by JavaScript code. The JavaScript function has the signature:
	 *
	 * <pre>
	 *    readFileExt({
	 *       ref: &lt;reference path - usually the fully qualified name of the importing file&gt;
	 *       file: &lt;the file name as specified in the &#64import statement&gt;
	 *    });
	 * </pre>
	 *
	 * On return, the ref property is updated with the fully qualified filename (URI) of the
	 * file that was read, or that was attempted to be read if an exception is thrown.
	 *
	 * @param cx
	 *            the Rhino context
	 * @param thisObj
	 *            the reference to the JavaScript context object (e.g. 'this')
	 * @param args
	 *            the arguments passed to the JavaScript function
	 * @param funObj
	 *            reference to the Java instance of this function object.
	 * @return The content of the specified file.
	 * @throws IOException
	 */
	public static Object readFileExt(Context cx, Scriptable thisObj, Object[] args, Function funObj)
			throws IOException {
		final String sourceMethod = "readFileExt"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[] { cx, thisObj, args, funObj });
		}
		Object result = Context.getUndefinedValue();
		ReadFileExtFunction javaThis = (ReadFileExtFunction) funObj;
		Scriptable params = (Scriptable) args[0];
		// Retrieve arguments from 'params' property
		String file = (String) params.get("file", params); //$NON-NLS-1$
		String ref = (String) params.get("ref", params); //$NON-NLS-1$
		if (isTraceLogging) {
			log.logp(Level.FINER, sourceClass, sourceMethod,
					"JS request args: file = " + file + ", ref = " + ref); //$NON-NLS-1$ //$NON-NLS-2$
		}
		URI fileUri = null;
		IResource res = null;
		if (WEBPACK_MODULE_PAT.matcher(file).find()) {
			// Leading tilde means that name is a module id and not a url
			fileUri = javaThis.aggregator.getConfig().locateModuleResource(file.substring(1), false);
			if (fileUri != null) {
				res = javaThis.aggregator.newResource(fileUri);
			} else {
				throw new FileNotFoundException(res != null ? res.getReferenceURI().toString() : file);
			}
		} else if (javaThis.isIncludeAMDPaths && !NON_AMD_PATH_PAT.matcher(file).find()) {
			// TODO: Remove this in favor of using webpack style module id above
			fileUri = javaThis.aggregator.getConfig().locateModuleResource(file, false);
			if (fileUri != null) {
				res = javaThis.aggregator.newResource(fileUri);
			}
		}
		if (res == null || !res.exists()) {
			fileUri = URI.create(file);
			// Not and AMD module or resource doesn't exist.  Try resolving against reference URI
			if (!fileUri.isAbsolute() && ref != null && ref != Scriptable.NOT_FOUND && ref != Undefined.instance) {
				URI refUri = URI.create(ref);
				fileUri = refUri.resolve(file);
			}
			res = javaThis.aggregator.newResource(fileUri);
		}
		// Update the 'ref' property of the input params with the filename we are going to try to load.
		params.put("ref", params, res != null ? res.getReferenceURI().toString() : file); //$NON-NLS-1$
		if (isTraceLogging) {
			log.logp(Level.FINER, sourceClass, sourceMethod,
					"Attempting to load file " + ref); //$NON-NLS-1$
		}
		if (res == null || !res.exists()) {
			throw new FileNotFoundException(res != null ? res.getReferenceURI().toString() : file);
		}
		Reader in = new CommentStrippingReader(
				new InputStreamReader(res.getURI().toURL().openStream(), "UTF-8" //$NON-NLS-1$
		));
		StringWriter out = new StringWriter();
		CopyUtil.copy(in, out);
		result = out.toString();

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, result);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.IShutdownListener#shutdown(com.ibm.jaggr.core.IAggregator)
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		final String sourceMethod = "shutdown"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[] { aggregator });
		}
		aggregator = null;
		for (IServiceRegistration reg : registrations) {
			reg.unregister();
		}
		registrations.clear();

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.config.IConfigListener#configLoaded(com.ibm.jaggr.core.config.IConfig, long)
	 */
	@Override
	public void configLoaded(IConfig config, long sequence) {
		final String sourceMethod = "configLoaded"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[] { aggregator });
		}
		// update the config property
		isIncludeAMDPaths = TypeUtil.asBoolean(
				config.getProperty(CSSModuleBuilder.INCLUDEAMDPATHS_CONFIGPARAM, Boolean.class)
		);
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}
}
