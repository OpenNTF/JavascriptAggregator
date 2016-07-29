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
package com.ibm.jaggr.core.util;

import com.ibm.jaggr.core.config.IConfig;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.DiagnosticGroup;
import com.google.javascript.jscomp.DiagnosticGroups;

import org.apache.commons.lang3.mutable.MutableObject;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CompilerUtil {
	private static final String sourceClass = CompilerUtil.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);

	public static final String COMPILEROPTIONS_CONFIGPARAM = "compilerOptions"; //$NON-NLS-1$

	public static final ImmutableMap<Class<?>, Class<?>> PRIMITIVE_TO_CLASS =
		    new ImmutableMap.Builder<Class<?>, Class<?>>()
	           .put(boolean.class, Boolean.class)
	           .put(byte.class, Byte.class)
	           .put(char.class, Character.class)
	           .put(short.class, Short.class)
	           .put(int.class, Integer.class)
	           .put(long.class, Long.class)
	           .put(float.class, Float.class)
	           .put(double.class, Double.class)
	           .build();

	public static final ImmutableMap<String, Field> FIELD_NAME_MAP;

	static {
		Field[] fields = CompilerOptions.class.getFields();
		ImmutableMap.Builder<String, Field> fieldNameMapBuilder = new ImmutableMap.Builder<String, Field>();
		for (Field field : fields) {
			fieldNameMapBuilder.put(field.getName(), field);
		}
		FIELD_NAME_MAP = fieldNameMapBuilder.build();
	}

	/**
	 * Converts and returns the specified class, if it represents a primitive, to the associated
	 * non-primitive class, or else returns the specified class if it is not a primitive.
	 *
	 * @param clazz
	 *            the input class
	 * @return the associated non-primitive class, or the input class if it is not a primitive
	 */
	private static Class<?> fromPrimitive(Class<?> clazz) {
		Class<?> clazz2 = PRIMITIVE_TO_CLASS.get(clazz);
		return clazz2 == null ? clazz : clazz2;
	}

	/**
	 * Returns a {@link CompilerOptions} with default settings used by the Aggregator
	 *
	 * @return a new object with default settings
	 */
	public static CompilerOptions getDefaultOptions() {
		final String sourceMethod = "getDefaultOptions"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod);
		}
		CompilerOptions options = new CompilerOptions();
		options.setLanguageIn(LanguageMode.ECMASCRIPT5);
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, options);
		}
		return options;
	}

	/**
	 * Builds a map of accessor/value pairs that can be used to quickly apply the compiler options
	 * specified in the config to a {$link CompilerOptions} object using
	 * {@link #applyCompilerOptionsFromMap(CompilerOptions, Map)}.  Returns the number of config
	 * properties that could not be mapped to a compiler option.
	 * <p>
	 * Compiler config options are specified using {@link CompilerOptions} defined property
	 * name/value pairs. The names can be any property which is either declared as a public field or
	 * has a setter method which can be mapped to the property name using standard JavaBean property
	 * naming convention. The value is an array who's number of elements matches the number of
	 * formal parameters defined by the setter method. If the setter method takes exactly one
	 * parameter, or the property is a public field which has no setter method, then the array
	 * notation may be dispensed with and the single parameter may be specified directly as in the
	 * following examples:
	 * <p>
	 *
	 * <pre>
	 * compilerOptions: {
	 *    defineToBooleanLiteral:['defineName', true]  // calls setDefineToBooleanLiteral("defineName", true);
	 * }
	 *
	 * compilerOptions: {
	 *    acceptConstKeyword:true           // this is identical to acceptConstKeyword:[true].  Both
	 *                                      // will invoke setAcceptConstKeyword(true);
	 * }
	 *
	 * compilerOptions: {
	 *    aliasableStrings:[['foo', 'bar']] // setAliasableStrings takes a {@link Set}, so need to use a
	 *                                      // nested array so that the string set will be passed as a single
	 *                                      // parameter.
	 * }
	 * </pre>
	 * <p>
	 * This method will perform the following property value conversions when attempting to match
	 * the provided values to the value types declared by the class:
	 * <ul>
	 * <li>Values specified as a JavaScript array will be converted to either a {@link List} or a
	 * {@link Set} as needed.</li>
	 * <li>If the declared type is an Enum and the provided value is a string which matches one of
	 * the Enum named constants, then the matching constant value will be used</li>
	 * </ul>
	 * <p>
	 *
	 * <pre>
	 * compilerOptions: {
	 *    checkGlobalThisLevel:'WARNING'    // This invokes setCheckGlobalThisLevel(CheckLevel.WARNING);
	 * }
	 * </pre>
	 *
	 * @param config
	 *            the server-side AMD config object
	 * @param resultMap
	 *            Output map. The keys are Method or Field objects and the values are the properties.
	 *            This map may be passed to {@link #applyCompilerOptionsFromMap(CompilerOptions, Map)}
	 *            to apply the compiler options defined in the config to a given CompilerOptions instance.
	 * @return the number of failed attempts to map a config property (primarily for unit testing)
	 */
	public static int compilerOptionsMapFromConfig(IConfig config, Map<AccessibleObject, List<Object>> resultMap) {
		return compilerOptionsMapFromConfig(config, resultMap, false);
	}


	/**
	 * @see #compilerOptionsMapFromConfig(IConfig, Map)
	 *
	 * @param config
	 *            the server-side AMD config object
	 * @param resultMap
	 *            Output map. The keys are Method or Field objects and the values are the properties.
	 *            This map may be passed to {@link #applyCompilerOptionsFromMap(CompilerOptions, Map)}
	 *            to apply the compiler options defined in the config to a given CompilerOptions instance.
	 * @param propFieldsOnly
	 *            if true, property setters will be ignored and field assignments will be used
	 *            exclusively (for use by unit tests).
	 * @return the number of failed attempts to map a config property (for use by unit tests)
	 */
	static int compilerOptionsMapFromConfig(IConfig config, Map<AccessibleObject, List<Object>> resultMap, boolean propFieldsOnly /* for unit testing */) {
		final String sourceMethod = "addCompilerOptionsFromConfig"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{config});
		}
		int numFailed = 0;
		CompilerOptions test_options = new CompilerOptions();
		Map<AccessibleObject, List<Object>> test_map = new HashMap<AccessibleObject, List<Object>>();
		resultMap.clear();
		Object optionsParamObj = config.getProperty(COMPILEROPTIONS_CONFIGPARAM, Map.class);
		if (optionsParamObj != null && optionsParamObj instanceof Map) {
			Map<?,?> optionsParam = (Map<?,?>)optionsParamObj;
			for (Map.Entry<?, ?> entry : optionsParam.entrySet()) {
				String key = entry.getKey().toString();
				Object value = entry.getValue();

				List<Object> args = new ArrayList<Object>();
				if (value instanceof List) {
					// Add the elements in the array to the args list
					@SuppressWarnings("unchecked")
					List<Object> listValue = (List<Object>)value;
					args.addAll(listValue);
				} else {
					args = new ArrayList<Object>();
					args.add(value);
				}
				// find a matching setter in the CompilerOptions class
				String setterMethodName = "set" + key.substring(0,1).toUpperCase() + key.substring(1); //$NON-NLS-1$
				Method setterMethod = findSetterMethod(setterMethodName, args);
				test_map.clear();
				if (setterMethod != null && !propFieldsOnly) {
					test_map.put(setterMethod, args);
				} else if (args.size() == 1){
					// See if there is a public property with the matching name and type
					MutableObject<Object> valueHolder = new MutableObject<Object>(args.get(0));
					Field field = findField(key, valueHolder);
					if (field != null) {
						args = new ArrayList<Object>(1);
						args.add(valueHolder.getValue());
						test_map.put(field, args);
					} else {
						numFailed++;
						if (log.isLoggable(Level.WARNING)) {
							log.logp(Level.WARNING, sourceClass, sourceMethod,
								MessageFormat.format(
									Messages.CompilerUtil_2, new Object[]{
										key + "=" + value //$NON-NLS-1$
									}
								)
							);
						}
					}
				}
				if (!test_map.isEmpty()) {
					// Apply the option to a test instance to make sure it works
					if (applyCompilerOptionsFromMap(test_options, test_map) == 0) {
						// option was successfully applied.  Add the option to the result map
						resultMap.putAll(test_map);
					} else {
						++numFailed;
					}
				}
			}
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, numFailed);
		}
		return numFailed;
	}

	/**
	 * Applies the config specified compiler options to a given {@link CompilerOptions} instance.
	 * The options to be applied are specified by <code>map</code> which was obtained by calling
	 * {@link #compilerOptionsMapFromConfig(IConfig, Map)}.
	 *
	 * @param options
	 *            the {@link CompilerOptions} object to apply the options to
	 * @param map
	 *            a map obtained from a previous call to
	 *            {@link #compilerOptionsMapFromConfig(IConfig, Map)}. The keys are instances of
	 *            {@link Method} or {@link Field}. The values are the method parameters (if the key
	 *            is a Method) or the value to assign to the field (if the key is a Field).
	 *
	 * @return the number of failed attempts to apply the config options
	 */
	public static int applyCompilerOptionsFromMap(CompilerOptions options, Map<AccessibleObject, List<Object>> map) {
		final String sourceMethod = "applyCompilerOptionsFromMap"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{options, map});
		}
		int numFailed = 0;
		for (Map.Entry<AccessibleObject, List<Object>> entry : map.entrySet()) {
			if (entry.getKey() instanceof Method) {
				Method method = (Method)entry.getKey();
				Object[] args = entry.getValue().toArray();
				try {
					method.invoke(options, args);
				} catch (Exception e) {
					++numFailed;
					if (log.isLoggable(Level.WARNING)) {
						log.logp(Level.WARNING, sourceClass, sourceMethod,
							MessageFormat.format(
								Messages.CompilerUtil_0, new Object[]{
									formatForDisplay(method.getName(), entry.getValue())
								}
							), e
						);
					}
				}
			} else {
				Field field = (Field)entry.getKey();
				try {
					field.set(options, entry.getValue().get(0));
				} catch (Exception e) {
					++numFailed;
					if (log.isLoggable(Level.WARNING)) {
						log.logp(Level.WARNING, sourceClass, sourceMethod,
							MessageFormat.format(
								Messages.CompilerUtil_1, new Object[]{
									field.getName() + "=" + entry.getValue().get(0) //$NON-NLS-1$
								}
							), e
						);
					}
				}
			}
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, numFailed);
		}
		return numFailed;
	}

	/**
	 * Returns the field matching the specified name if the type of the value specified in
	 * <code>valueHolder</code> can be assigned to the field (with conversion if necessary), or
	 * null.
	 *
	 * @param name
	 *            the field name
	 * @param valueHolder
	 *            (input/output) On input, the value being assigned. On output, possibly converted
	 *            value that will be accepted by the field. See {@link #marshalArg(Class, Object)}
	 *            for conversion rules.
	 * @return matching field or null
	 */
	private static Field findField(String name, MutableObject<Object> valueHolder) {
		final String sourceMethod = "findField"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{name, valueHolder});
		}
		Field field = FIELD_NAME_MAP.get(name);
		if (field != null) {
			Object arg = marshalArg(field.getType(), valueHolder.getValue());
			if (arg != null) {
				valueHolder.setValue(arg);
			} else {
				field = null;
			}
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, field);
		}
		return field;
	}

	/**
	 * Finds and returns the setter method in the {@link CompilerOptions} class that matches the
	 * specified name and argument types. If no matching method can be found, then returns null.
	 * <p>
	 * An input argument type of string will match an Enum argument type if the Enum contains a
	 * constant who's name matches the string. In this case, the string value will be replaced with
	 * the Enum constant in the input <code>args</code> list.
	 *
	 * @param methodName
	 *            the setter method name to match
	 * @param args
	 *            (input/output) the list of arguments. On input, the list of the arguments provided
	 *            in the config. On output, the possibly converted list of arguments that may be
	 *            passed to the setter method. See {@link #marshalArg(Class, Object)} for conversion
	 *            rules.
	 * @return the matching method, or null if no match can be found
	 */
	private static Method findSetterMethod(String methodName, List<Object> args) {
		final String sourceMethod = "findSetterMethod"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{methodName, args});
		}
		Method[] methods = CompilerOptions.class.getMethods();
		Method result = null;
		for (Method method : methods) {
			if (method.getName().equals(methodName) && method.getParameterTypes().length == args.size()) {
				List<Object> marshalledArgs = new ArrayList<Object>(args.size());
				for (int i = 0; i < args.size(); i++) {
					Object arg = marshalArg(method.getParameterTypes()[i], args.get(i));
					if (arg != null) {
						marshalledArgs.add(arg);
					} else {
						break;
					}
				}
				if (marshalledArgs.size() == args.size()) {
					result = method;
					args.clear();
					args.addAll(marshalledArgs);
					break;
				}
			}
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, result);
		}
		return result;
	}

	/**
	 * Marshals the specified argument to the declared type. May involve conversion. The following
	 * argument conversions may occur:
	 * <ul>
	 * <li>List to Set if the argument type is a List (i.e. NativeArray) and the declared type is Set
	 * </li>
	 * <li>Sting to Enum constant if the declared type is an Enum and the argument is a string which
	 * matches one of the Enum constant names.</li>
	 * </ul>
	 *
	 * @param declaredType
	 *            the type of the argument declared in the class definition
	 * @param value
	 *            the argument value provided in the config
	 * @return the possibly converted argument value, or null if the value cannot be converted to
	 *         the declared type
	 */
	private static Object marshalArg(Class<?> declaredType, Object value) {
		final String sourceMethod = "marshalArg"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{declaredType, value});
		}
		Object result = null;
		Class<?> argType = value.getClass();
		if (!fromPrimitive(declaredType).isAssignableFrom(fromPrimitive(argType))) {
			if (declaredType.isAssignableFrom(Set.class) && value instanceof List) {
				// Convert the list to a set
				Set<?> set = new HashSet<Object>((List<?>)value);
				result = set;
			} else if (declaredType.isEnum() && argType.equals(String.class)) {
				// If the declared type is an enum and the provided type is a string,
				// then see if the string matches the name of an enum constant in the declared type.
				@SuppressWarnings("unchecked")
				Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>)declaredType;
				Object enumValue = getEnumNamesMap(enumType).get(value.toString());
				if (enumValue != null) {
					result = enumValue;
				}
			} else if (declaredType.isAssignableFrom(DiagnosticGroup.class) && argType.equals(String.class)) {
				// If the arg value matches a statically defined DiagnosticGroup in DiagnosticGroups, then
				// return that.
				result = findNamedDiagnosticGroup(value.toString());
			}
		} else {
			result = value;
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, result);
		}
		return result;
	}

	/**
	 * Returns a map of Enum constant name/value pairs for the specified Enum class
	 *
	 * @param enumType
	 *            an Enum class object
	 * @return the map of Enum constant name/value pairs
	 */
	private static Map<String, Object> getEnumNamesMap(Class<? extends Enum<?>> enumType) {
		final String sourceMethod = "getEnumNameMap"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{enumType});
		}
		Map<String, Object> result = new HashMap<String, Object>();
		Enum<?>[] values = enumType.getEnumConstants();
		for (Enum<?> value : values) {
			result.put(value.name(), value);
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, result);
		}
		return result;
	}

	/**
	 * Formats the specified setter method and parameters into a string for display.
	 * <p>
	 * For example: <code>setXXX(arg1, ar2)</code>
	 *
	 * @param setterMethodName
	 *            the method name
	 * @param args
	 *            the method arguments
	 * @return the formatted string
	 */
	private static String formatForDisplay(String setterMethodName, List<Object> args) {
		StringBuilder sb = new StringBuilder(CompilerOptions.class.getName());
		sb.append(".").append(setterMethodName).append("("); //$NON-NLS-1$ //$NON-NLS-2$
		Joiner.on(",").appendTo(sb, args).append(")"); //$NON-NLS-1$ //$NON-NLS-2$
		return sb.toString();
	}

	/**
	 * Tries to match the provided name with a named Diagnostic group defined in the
	 * DiagnosticGroups class.
	 *
	 * @param name
	 *            the name to match
	 * @return The matching named DiagnosticGroup static instance or null
	 */
	private static DiagnosticGroup findNamedDiagnosticGroup(String name) {
		final String sourceMethod = "findNamedDiagnosticGroup"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{name});
		}
		DiagnosticGroup result = null;
		Class<DiagnosticGroups> clazz = DiagnosticGroups.class;
		// iterate through the public static fields looking for a match
		Field[] fields = clazz.getDeclaredFields();
		for (Field field : fields) {
			int modifier = field.getModifiers();
			if (field.getType().isAssignableFrom(DiagnosticGroup.class) &&
					(modifier & Modifier.STATIC) != 0 && (modifier & Modifier.PUBLIC) != 0 &&
					field.getName().equals(name)) {
				try {
					result = (DiagnosticGroup)field.get(null);
					break;
				} catch (Exception e) {
					// Shouldn't ever happen with public static fields
					if (log.isLoggable(Level.WARNING)) {
						log.logp(Level.WARNING, sourceClass, sourceMethod, e.getMessage(), e);
					}
				}
			}
		}
		if (isTraceLogging) {
			log.exiting(sourceMethod, sourceMethod, result);
		}
		return result;
	}
}
