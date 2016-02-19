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
package com.ibm.jaggr.core.util.rhino;

import com.ibm.jaggr.core.util.Features;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements the JavaScript has function object used by the Rhino interpreter when
 * evaluating regular expressions in config aliases.
 * <p>
 * In addition to providing the has function, this class also keeps track of which features
 * has is called for by the JavaScript in {@code dependentFeatures}
 */
public class HasFunction extends FunctionObject {
	private static final long serialVersionUID = 8795456757297823566L;
	static final private String sourceClass = HasFunction.class.getName();
	static final private Logger log = Logger.getLogger(sourceClass);
	private final Features features;
	private final Set<String> dependentFeatures;

	static Method hasMethod;

	static {
		try {
			hasMethod = HasFunction.class.getMethod("has", Context.class, Scriptable.class, Object[].class, Function.class); //$NON-NLS-1$
		} catch (SecurityException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		} catch (NoSuchMethodException e) {
			log.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	public HasFunction(Scriptable scope, Features features) {
		super("has", hasMethod, scope); //$NON-NLS-1$
		final String sourceMethod = "<ctor>"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{scope, features});
		}
		this.features = features;
		this.dependentFeatures = new HashSet<String>();
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, this);
		}
	}

	public static Object has(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
		final String sourceMethod = "has"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{cx, thisObj, args, funObj});
		}
		Object result = Context.getUndefinedValue();
		HasFunction javaThis = (HasFunction)funObj;
		String feature = (String)args[0];
		javaThis.dependentFeatures.add(feature);
		if (javaThis.features.contains(feature)) {
			result = Boolean.valueOf(javaThis.features.isFeature(feature));
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, result);
		}
		return result;
	}

	public Set<String> getDependentFeatures() {
		final String sourceMethod = "getDependentFeatures"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod);
			log.exiting(sourceClass, sourceMethod, dependentFeatures);
		}
		return dependentFeatures;
	}
}