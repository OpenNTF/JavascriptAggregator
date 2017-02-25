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
import java.util.ArrayList;
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
public class JarResourceFactory implements IResourceFactory {

	static public Map<String, List<String>> jarResourceMap = new HashMap<String, List<String>>();

	@Override
	public IResource newResource(URI uri) {
		IResource result = null;
		try {
			String scheme = uri.getScheme();
			if ("jar".equals(scheme)) { //$NON-NLS-1$
				String filePath = null;
				int startIndex = uri.toString().indexOf("file:"); //$NON-NLS-1$
				filePath = uri.toString().substring(startIndex);

				URL classPathUrl = null;
				ZipEntry entry = null;
				String classPathName = null;
				String zipFileEntry = null;
				List<String> resourceNames = new ArrayList<String>();


					if (filePath.startsWith("file:") && filePath.contains("!")) { //$NON-NLS-1$ //$NON-NLS-2$
						String[] split = filePath.split("!"); //$NON-NLS-1$
						classPathUrl = new URL(split[0]);
						classPathName = split[0].substring(split[0].lastIndexOf("/") + 1); //$NON-NLS-1$
						zipFileEntry = split[1];
						if(!jarResourceMap.containsKey(classPathName)){
							ZipInputStream zip = new ZipInputStream(classPathUrl.openStream());
							while ((entry = zip.getNextEntry()) != null) {
								resourceNames.add(entry.getName());
							}
							jarResourceMap.put(classPathName, resourceNames);
						}
						String resourceName = zipFileEntry.startsWith("/")?zipFileEntry.substring(1):zipFileEntry; //$NON-NLS-1$
						if(jarResourceMap.get(classPathName).contains(resourceName)){
						result = new JarResource(filePath, jarResourceMap.get(classPathName));
						}else{
							result = new NotFoundResource(uri);
						}
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
		return ("jar".equals(uri.getScheme())); //$NON-NLS-1$
	}
}
