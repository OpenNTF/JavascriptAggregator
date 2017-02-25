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

import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceFactory;

/**
 * Default implementation for {@link IResourceFactory} that currently supports
 * only file resources.
 */
public class ClassPathResourceFactory implements IResourceFactory {

	static public Map<String, List<String>> classPathResourceMap = new HashMap<String, List<String>>();

	@Override
	public IResource newResource(URI uri) {
		IResource result = null;
		try {
			String scheme = uri.getScheme();
			String extractedResourceString = "";   //$NON-NLS-1$
			if ("classPath".equals(scheme)) { //$NON-NLS-1$
				ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
				if(uri.toString().indexOf("!") == -1){ //$NON-NLS-1$
					String uriPath = uri.getPath();
					extractedResourceString = uriPath.startsWith("/") ? uriPath.substring(1) : uriPath; //$NON-NLS-1$
				} else {
					extractedResourceString = uri.toString().substring(uri.toString().indexOf("!") + 2); //$NON-NLS-1$
				}
				Enumeration<URL> resources = classLoader.getResources(extractedResourceString);
				String filePath = null;
				while (resources.hasMoreElements()) {
					URL resource = resources.nextElement();
					filePath = URLDecoder.decode(resource.getFile(), "UTF-8"); //$NON-NLS-1$
				}


				ZipEntry entry = null;
				String classPathName = null;
				List<String> resourceNames = new ArrayList<String>();

				if(filePath != null){
					if (filePath.startsWith("file:") && filePath.contains("!")) { //$NON-NLS-1$ //$NON-NLS-2$
						String[] split = filePath.split("!"); //$NON-NLS-1$
						URL classPathUrl = new URL(split[0]);
						classPathName = split[0].substring(split[0].lastIndexOf("/") + 1); //$NON-NLS-1$
						if(!classPathResourceMap.containsKey(classPathName)){
							ZipInputStream zip = new ZipInputStream(classPathUrl.openStream());
							while ((entry = zip.getNextEntry()) != null) {
								resourceNames.add(entry.getName());
							}
							classPathResourceMap.put(classPathName, resourceNames);
						}
						result = new ClassPathResource(filePath, classPathResourceMap.get(classPathName));
					}
				} else{
					result = new NotFoundResource(uri);
				}
			} else {
				throw new UnsupportedOperationException(uri.getScheme());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public boolean handles(URI uri) {
		return ("classPath".equals(uri.getScheme())); //$NON-NLS-1$
	}
}
