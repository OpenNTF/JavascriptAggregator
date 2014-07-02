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

import java.net.URI;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of {@link IResource} for Jar resources
 */
public class JarResource extends ClasspathResource {
	static final Logger log = Logger.getLogger(JarResource.class.getName());

	/**
	 * Public constructor used by factory
	 *
	 *@param entry
	 *            the entry within the jar
	 * @param entries
	 * 			  list of all entries within the jar
	 */
	public JarResource(String entry, ArrayList<String> entries) {
		super(entry, entries);
	}

	@Override
	public String getPath() {
		return "jar:///" + zipFileEntry; //$NON-NLS-1$
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.ibm.jaggr.service.resource.IResource#resolve(java.net.URI)
	 */
	@Override
	public IResource resolve(String relative) {
		String jarUriString, finaljarString = null;
		URI fileUri, finalFileUri = null;
		JarResource jarResource = null;
		try {
			jarUriString = getURI().toString();
			if (jarUriString.startsWith("jar:")) { //$NON-NLS-1$
				jarUriString = jarUriString.substring(4);
			}

			fileUri = new URI(jarUriString).resolve(relative);
			finaljarString = "jar:/" + fileUri; //$NON-NLS-1$
			finalFileUri = new URI(finaljarString);

		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
		}
		if (finalFileUri == null)
			jarResource = newInstance(getURI().resolve(relative));
		else
			jarResource = newInstance(finalFileUri);
		return jarResource;
	}

	protected JarResource newInstance(URI uri) {
		return (JarResource) new ClasspathResourceFactory().newResource(uri);
	}
}
