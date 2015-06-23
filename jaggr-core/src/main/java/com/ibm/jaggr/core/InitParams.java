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

package com.ibm.jaggr.core;

import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.options.IOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Wrapper class for aggregator init-params.
 * <p>
 * Init params are specified with the init-param attribute in the servlet
 * element of the {@code org.eclipse.equinox.http.registry.servlets}
 * extension point.
 */
public class InitParams implements Iterable<InitParam>{

	/**
	 * Name of the servlet init-param that specifies the name of the
	 * httptransport plugin extension used by this servlet.
	 *
	 * @see com.ibm.jaggr.core.transport.IHttpTransport
	 */
	public static final String TRANSPORT_INITPARAM = "httptransport"; //$NON-NLS-1$
	/**
	 * Name of the servlet init-param that specifies the name of the
	 * modulebuilder plugin extension(s) used by this servlet.  Multiple
	 * extensions may be specified using multiple init-params.
	 */
	public static final String MODULEBUILDERS_INITPARAM = "modulebuilders"; //$NON-NLS-1$
	/**
	 * Name of the servlet init-param that specifies the name of the
	 * resourcefactory plugin extension(s) used by this servlet.  Multiple
	 * extensions may be specified using multiple init-params.
	 */
	public static final String RESOURCEFACTORIES_INITPARAM = "resourcefactories"; //$NON-NLS-1$
	/**
	 * Name of the servlet init-param that specifies the name of the
	 * resourceconverter plugin extension(s) used by this servlet.  Multiple
	 * extensions may be specified using multiple init-params.
	 */
	public static final String RESOURCECONVERTERS_INITPARAM = "resourceconverters"; //$NON-NLS-1$
	/**
	 * Name of the servlet init-param that specifies the name of the service provider extension(s)
	 * used by this servlet. Multiple extensions may be specified using multple init-params. The
	 * extensions will be initialized in the order that they are specified.
	 * <p>
	 * Service provider extensions are generally used to register OSGi services such as config
	 * variable resolvers, etc.
	 */
	public static final String SERVICEPROVIDERS_INITPARAM = "serviceproviders"; //$NON-NLS-1$
	/**
	 * Name of the servlet init-param that specifies the URI to the server side
	 * AMD config JSON. If the config JSON resides in the same bundle as the
	 * defining servlet, then the config URI may specify a relative path
	 */
	public static final String CONFIG_INITPARAM = "config"; //$NON-NLS-1$
	/**
	 * Name of the servlet init-param that specifies the filename of the java properties
	 * file containing the aggregator options (see {@link IOptions}).  If not
	 * specified, then the aggregator will look for options properties in the file
	 * aggregator.properties in the home directory of the user that started the
	 * aggregator.
	 */
	public static final String OPTIONS_INITPARAM = "options"; //$NON-NLS-1$

	/**
	 * Name of the servlet init-param that specifies the maximum capacity of
	 * the layer cache in megabytes.
	 */
	public static final String MAXLAYERCACHECAPACITY_MB_INITPARAM = "maxlayercachecapacity_mb"; //$NON-NLS-1$

	/**
	 * Name of the servlet init-param that specifies aggregator aliases within the servlet
	 */
	public static final String ALIAS_INITPARAM = "alias"; //$NON-NLS-1$

	/**
	 * Name of the servlet init-param that specifies resource ids.  Resource ids are used to
	 * specify resource alias and base-name values using init-param names of the form
	 * <resource id>:alias and <resource id>:base-name.  For example, the following init-params
	 * would specify a resource with alias foo and base-name foopath:
	 * <pre>
	 * &lt;init-param name="resource-id" value="foores"/&gt;
	 * &lt;init-param name="foores:alias" value="foo"/&gt;
	 * &lt;init-param name="foores:base-name" value="foopath"/&gt;
	 * </pre>
	 */
	public static final String RESOURCEID_INITPARAM = "resource-id";  //$NON-NLS-1$

	/**
	 * The init-params
	 */
	private List<InitParam> initParams;
	/**
	 * Init params are name/value pairs
	 */
	public static class InitParam {
		private final String name;
		private final String value;
		private final IAggregator aggregator;
		public InitParam(String name, String value, IAggregator aggregator) {
			if (name.length() == 0 || value.length() == 0) {
				// disallow null or empty values
				throw new IllegalArgumentException();
			}
			this.name = name;
			this.value = value;
			this.aggregator = aggregator;
		}
		public InitParam(String name, String value) { this(name, value, null); }
		public String getName() { return name; }
		public String getValue() { return aggregator != null ? aggregator.substituteProps(value) : value; }
	}

	/**
	 * Constructor from a {@code List}
	 *
	 * @param initParams
	 */
	public InitParams(List<InitParam> initParams) {
		this.initParams = initParams;
	}

	/**
	 * Returns a list of the values for the init-param(s) with the
	 * specified name, or an empty collection if there are no init-params with
	 * the specified name.
	 *
	 * @param name
	 *            the init-param name
	 * @return the collection of values for the init-params with the specified
	 *         name
	 */
	public List<String> getValues(String name) {
		List<String> result = new LinkedList<String>();
		for (InitParam initParam : initParams) {
			if (initParam.getName().equals(name)) {
				result.add(initParam.getValue());
			}
		}
		return result;
	}

	/**
	 * Returns the value of the first instance of the named init-param, or
	 * null if the init-param is not found.
	 *
	 * @param name
	 *            the init-param name
	 * @return the value of the first init-param with the specified name or null
	 */
	public String getValue(String name) {
		String result = null;
		for (InitParam initParam : initParams) {
			if (initParam.getName().equals(name)) {
				result = initParam.getValue();
				break;
			}
		}
		return result;
	}

	/**
	 * Returns a collection of the init-param names, or an empty collection
	 * if there are no init-params
	 *
	 * @return The collection of init-param names
	 */
	public Collection<String> getNames() {
		Collection<String> names = new HashSet<String>();
		for (InitParam initParam : initParams) {
			names.add(initParam.getName());
		}
		return names;
	}

	/* (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<InitParam> iterator() {
		return initParams.iterator();
	}

	@Override
	public String toString() {
		Map<String, List<String>> map = new HashMap<String, List<String>>();
		for (InitParam initParam : initParams) {
			if (map.containsKey(initParam.name)) {
				map.get(initParam.name).add(initParam.value);
			} else {
				List<String> value = new ArrayList<String>();
				value.add(initParam.value);
				map.put(initParam.name, value);
			}
		}
		return map.toString();
	}
}
