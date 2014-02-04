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

package com.ibm.jaggr.core.impl.resource;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation for {@link IResourceFactory} that currently supports
 * only file resources.
 */
public class FileResourceFactory implements IResourceFactory {
	private static final String CLAZZ = FileResourceFactory.class.getName();
	private static final Logger log = Logger.getLogger(CLAZZ);
	private static final String WARN_MESSAGE = "Abandoning attempt to use nio."; //$NON-NLS-1$

	private boolean tryNIO = true;
	private Class<?> nioFileResourceClass = null; // access only through the getter
	private Constructor<?> nioFileResourceConstructor = null; // access only through the getter

	@Override
	public IResource newResource(URI uri) {
		IResource result = null;
		String scheme = uri.getScheme();
		if ("file".equals(scheme) || scheme == null) { //$NON-NLS-1$
			Constructor<?> constructor = getNIOFileResourceConstructor(URI.class);
			try {
				result = (IResource)getInstance(constructor, uri);
			} catch (Throwable t) {
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, t.getMessage(), t);
				}
			}

			if (result == null)
				result = new FileResource(uri);

		} else {
			throw new UnsupportedOperationException(uri.getScheme());
		}
		return result;
	}

	@Override
	public boolean handles(URI uri) {
		return "file".equals(uri.getScheme()); //$NON-NLS-1$
	}

	/**
	 * Utility method for acquiring a reference to the NIOFileResource class without
	 * asking the class loader every single time after we know it's not there.
	 */
	protected Class<?> getNIOFileResourceClass() {
		final String method = "getNIOFileResourceClass"; //$NON-NLS-1$

		if (tryNIO && nioFileResourceClass == null) {
			try {
				nioFileResourceClass = FileResourceFactory.class.getClassLoader()
						.loadClass("com.ibm.jaggr.core.impl.resource.NIOFileResource"); //$NON-NLS-1$
			} catch (ClassNotFoundException e) {
				tryNIO = false; // Don't try this again.
				if (log.isLoggable(Level.WARNING)) {
					log.logp(Level.WARNING, CLAZZ, method, e.getMessage());
					log.logp(Level.WARNING, CLAZZ, method, WARN_MESSAGE);
				}
			}
		}
		return nioFileResourceClass;
	}

	/**
	 * Utility method for acquiring a reference to the NIOFileResource class constructor
	 * without asking the class loader every single time after we know it's not there.
	 */
	protected Constructor<?> getNIOFileResourceConstructor(Class<?>... args) {
		final String method = "getNIOFileResourceConstructor"; //$NON-NLS-1$

		if (tryNIO && nioFileResourceConstructor == null) {
			try {
				Class<?> clazz = getNIOFileResourceClass();
				if (clazz != null)
					nioFileResourceConstructor = clazz.getConstructor(args);
			} catch (NoSuchMethodException e) {
				tryNIO = false; // Don't try this again.
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
				if (log.isLoggable(Level.WARNING))
					log.logp(Level.WARNING, CLAZZ, method, WARN_MESSAGE);
			} catch (SecurityException e) {
				tryNIO = false; // Don't try this again.
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
				if (log.isLoggable(Level.WARNING))
					log.logp(Level.WARNING, CLAZZ, method, WARN_MESSAGE);
			}
		}
		return nioFileResourceConstructor;
	}

	/**
	 * Utility method to catch class loading issues and prevent repeated attempts to use reflection.
	 *
	 * @param constructor The constructor to invoke
	 * @param args The args to pass the constructor
	 * @return The instance.
	 *
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	protected Object getInstance(Constructor<?> constructor, Object... args) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		final String method = "getInstance"; //$NON-NLS-1$

		Object ret = null;
		if (tryNIO) {
			if (constructor != null) {
				try {
					ret = (IResource)constructor.newInstance(args);
				} catch (NoClassDefFoundError e) {
					if (log.isLoggable(Level.WARNING)) {
						log.logp(Level.WARNING, CLAZZ, method, e.getMessage());
						log.logp(Level.WARNING, CLAZZ, method, WARN_MESSAGE);
					}
					tryNIO = false;
				} catch (UnsupportedClassVersionError e) {
					if (log.isLoggable(Level.WARNING)) {
						log.logp(Level.WARNING, CLAZZ, method, e.getMessage());
						log.logp(Level.WARNING, CLAZZ, method, WARN_MESSAGE);
					}
					tryNIO = false;
				}
			}
		}
		return ret;
	}
}
