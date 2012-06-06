/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import com.ibm.jaggr.service.InitParams.InitParam;

/**
 * Wrapper class for aggregator init-params.
 * <p>
 * Init params are specified with the init-param attribute in the servlet
 * element of the {@code org.eclipse.equinox.http.registry.servlets}
 * extension point.
 * 
 * @author chuckd@us.ibm.com
 */
public class InitParams implements Iterable<InitParam>{

	/**
	 * Name of the servlet init-param that specifies the name of the
	 * httptransport plugin extension used by this servlet.
	 * 
	 * @see com.ibm.jaggr.service.transport.IHttpTransport
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
	 * Name of the servlet init-param that specifies the URI to the server side
	 * AMD config JSON. If the config JSON resides in the same bundle as the
	 * defining servlet, then the config URI may specify a relative path
	 */
	public static final String CONFIG_INITPARAM = "config"; //$NON-NLS-1$
	
	/**
	 * The init-params 
	 */
	private Iterable<InitParam> initParams;
	/**
	 * Init params are name/value pairs 
	 */
	public static class InitParam {
		private final String name;
		private final String value;
		public InitParam(String name, String value) {
			if (name.length() == 0 || value.length() == 0) {
				// disallow null or empty values
				throw new IllegalArgumentException();
			}
			this.name = name;
			this.value = value;
		}
		public String getName() { return name; }
		public String getValue() { return value; }
	}
	
	/**
	 * Constructor from an {@code Iterable}
	 * 
	 * @param initParams
	 */
	public InitParams(Iterable<InitParam> initParams) {
		this.initParams = initParams;
	}
	
	/**
	 * Returns a collection of the values for the init-param(s) with the
	 * specified name, or an empty collection if there are no init-params with
	 * the specified name.
	 * 
	 * @param name
	 *            the init-param name
	 * @return the collection of values for the init-params with the specified
	 *         name
	 */
	public Collection<String> getValues(String name) {
		Collection<String> result = new LinkedList<String>();
		for (InitParam initParam : initParams) {
			if (initParam.getName().equals(name)) {
				result.add(initParam.getValue());
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
}
