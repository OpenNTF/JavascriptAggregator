/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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
package com.ibm.jaggr.service.util;

import com.ibm.jaggr.core.util.CopyUtil;

import com.google.common.io.Files;

import org.osgi.framework.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BundleUtil {
	private static final String sourceClass = BundleUtil.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);

	/**
	 * Returns true if the specified name is for a folder (ends with '/')
	 *
	 * @param name
	 *            the name to test
	 * @return true if name is for a folder
	 */
	private static boolean isFolder(String name) {
		return name.length() == 0 || name.charAt(name.length()-1) == '/';
	}

	/**
	 * Extracts the entries in the specified bundle to the specified location. If {@code selector}
	 * is specified, then only the entry specified by {@code selector} (if {@code selector} is a
	 * filename) or the contents of the directory specified by {@code selector} (if {@code selector}
	 * is a directory name) will be extracted. If {@code selector} specifies a directory, then the
	 * contents of the directory in the bundle file will be rooted at {@code destDir} when
	 * extracted.
	 *
	 * @param bundle
	 *            the source bundle
	 * @param destDir
	 *            the {@link File} object for the target directory
	 * @param selector
	 *            The name of a file or directory to extract
	 * @throws IOException
	 */
	public static void extract(Bundle bundle, File destDir, String selector) throws IOException {
		final String sourceMethod = "extract";  //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{bundle, destDir});
		}

		if (selector == null) selector = ""; //$NON-NLS-1$
		if (isFolder(selector)) {
			// Selector specifies a folder path.  Get the contents of the folder
			Enumeration<?> urls = bundle.findEntries(selector, "*", true); //$NON-NLS-1$
			// iterates over entries in the folder
			while (urls.hasMoreElements()) {
				URL url = (URL)urls.nextElement();
				String path = url.getPath();
				// Make sure path doesn't start with '/'
				if (path.charAt(0) == '/') {
					path = path.substring(1);
				}
				// Make sure target path is rooted at the folder specified by selector
				String targetName = path.substring(selector.length());
				File file = new File(destDir, targetName.replace("/", File.separator)); //$NON-NLS-1$
				if (isFolder(path)) {
					extractDirectory(url, file);
				} else {
					extractFile(url, file);
				}
			}
		} else {
			// Selector specifies a file
			URL entry = bundle.getEntry(selector);
			String pathName = entry.getPath();
			// target name is just the filename
			int idx = pathName.lastIndexOf('/');
			String targetName = (idx == -1) ? pathName : pathName.substring(idx+1);
			File file = new File(destDir, targetName);
			extractFile(entry, file);
		}

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	private static void extractDirectory(URL url, File dir) throws IOException {
		final String sourceMethod = "extractDirectory";  //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{url, dir});
		}

		dir.mkdirs();
		URLConnection connection = url.openConnection();
		dir.setLastModified(connection.getLastModified());

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	private static void extractFile(URL url, File file) throws IOException {
		final String sourceMethod = "extractFile";  //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{url, file});
		}

		Files.createParentDirs(file);
		URLConnection connection = url.openConnection();
		long lastModified = connection.getLastModified();
		InputStream is = connection.getInputStream();
		FileOutputStream fos = new FileOutputStream(file);
		CopyUtil.copy(is, fos);		// closes the streams
		file.setLastModified(lastModified);

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
}
}
