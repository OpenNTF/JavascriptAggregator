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

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Default implementation for {@link IResourceFactory} that currently supports resources having scheme as classpath and jar
 */
public class ClasspathResourceFactory implements IResourceFactory {

	static final Logger log = Logger.getLogger(ClasspathResourceFactory.class.getName());
	public Map<String, ArrayList<String>> resourceMap = new HashMap<String, ArrayList<String>>();

	@Override
	public IResource newResource(URI uri) {
		IResource result = null;
		String filePath = null;
		try {
			String scheme = uri.getScheme();
			if ("classpath".equals(scheme) || "jar".equals(scheme)) {//$NON-NLS-1$ //$NON-NLS-2$
				if ("classpath".equals(scheme)) {//$NON-NLS-1$
					filePath = getFilePathForClassPath(uri);
				} else if ("jar".equals(scheme)) {//$NON-NLS-1$
					filePath = getFilePathForJar(uri);
				}
				ZipEntry entry = null;
				String classpathName = null;
				String zipFileEntry = null;
				ArrayList<String> resourceNames = new ArrayList<String>();

				if (filePath != null) {
					if (filePath.startsWith("file:") && filePath.contains("!")) { //$NON-NLS-1$ //$NON-NLS-2$
						String[] split = filePath.split("!"); //$NON-NLS-1$
						URL classpathUrl = new URL(split[0]);
						if(split[0].lastIndexOf("/") != -1) { //$NON-NLS-1$
							classpathName = split[0].substring(split[0].lastIndexOf("/") + 1); //$NON-NLS-1$
						} else {
							classpathName = split[0];
						}
						zipFileEntry = split[1];
						if (!resourceMap.containsKey(classpathName)) {
							ZipInputStream zip = new ZipInputStream(classpathUrl.openStream());
							while ((entry = zip.getNextEntry()) != null) {
								resourceNames.add(entry.getName());
							}
							resourceMap.put(classpathName, resourceNames);
						}
						String resourceName = zipFileEntry.startsWith("/") ? zipFileEntry.substring(1) : zipFileEntry; //$NON-NLS-1$
						if (resourceMap.get(classpathName).contains(resourceName)) {
							if ("classpath".equals(scheme)) {//$NON-NLS-1$
								result = new ClasspathResource(filePath, resourceMap.get(classpathName), "classpath"); //$NON-NLS-1$
							} else if ("jar".equals(scheme)) {//$NON-NLS-1$
								result = new ClasspathResource(filePath, resourceMap.get(classpathName), "jar"); //$NON-NLS-1$
							}
						} else {
							result = new NotFoundResource(uri);
						}
						// result = new ClasspathResource(filePath, resourceMap.get(classpathName));
					}
				} else {
					result = new NotFoundResource(uri);
				}
			} else {
				throw new UnsupportedOperationException(uri.getScheme());
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		return result;
	}

	@Override
	public boolean handles(URI uri) {
		return ("classpath".equals(uri.getScheme()) || "jar".equals(uri.getScheme())); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private String getFilePathForClassPath(URI uri) {
		String filePath = null;
		try {
			String extractedResourceString = ""; //$NON-NLS-1$
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			if (uri.toString().indexOf("!") == -1) { //$NON-NLS-1$
				String uriPath = uri.getPath();
				extractedResourceString = uriPath.startsWith("/") ? uriPath.substring(1) : uriPath; //$NON-NLS-1$
			} else {
				extractedResourceString = uri.toString().substring(uri.toString().indexOf("!") + 2); //$NON-NLS-1$
			}
			Enumeration<URL> resources = classLoader.getResources(extractedResourceString);

			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				filePath = URLDecoder.decode(resource.getFile(), "UTF-8"); //$NON-NLS-1$
			}

		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		return filePath;
	}

	private String getFilePathForJar(URI uri) {
		int startIndex = uri.toString().indexOf("file:"); //$NON-NLS-1$
		String filePath = uri.toString().substring(startIndex);
		return filePath;
	}



}