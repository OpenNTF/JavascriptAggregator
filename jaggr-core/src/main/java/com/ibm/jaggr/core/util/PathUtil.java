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

package com.ibm.jaggr.core.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;


public class PathUtil {
	private static final Pattern hasPattern = Pattern.compile("(^|\\/)has$"); //$NON-NLS-1$

	/**
	 * Invalid characters in module/file path names.  This list of characters
	 * should be the same as the list used on the client.
	 */
	public static final Pattern invalidChars = Pattern.compile("[{},|<>*]"); //$NON-NLS-1$

	private PathUtil() {}

	/**
	 * This method normalizes an array of paths so that they do not start with
	 * or contain any ".." path segments. When called, relative paths that start
	 * with "." or ".." are relative to <code>ref</code>. In the returned array,
	 * the strings in the <code>paths</code> array have been replaced with the
	 * normalized path. Any paths that do not resolve relative to <code>ref</code> 
	 * (for example, paths that begin with '/' or try to navigate out of the
	 * top level path component specified by <code>ref</code> using '../' path
	 * components) will result in an {@link IllegalArgumentException}.
	 * 
	 * @param ref
	 *            The reference location for relative paths. May be a file or
	 *            directory.
	 * @param paths
	 *            The array of paths that are to be normalized. The paths are
	 *            normalized in-place (i.e. each array element is replaced by
	 *            the normalized path)
	 * @throws IllegalArugmentException
	 */
	public static String[] normalizePaths(String ref, String[] paths) throws IllegalArgumentException {
		List<String> result = new ArrayList<String>();
		List<String> refParts = (ref == null) ? new ArrayList<String>() : Arrays.asList(ref.split("/")); //$NON-NLS-1$
		
		for (String path : paths) {
			String plugin = ""; //$NON-NLS-1$
			int idx = path.indexOf('!');
			if (idx != -1) {
				plugin = PathUtil.normalize(refParts, path.substring(0, idx));
				path = path.substring(idx+1);
			}
			try {
				path = (hasPattern.matcher(plugin).find()) ? 
					new HasNode(path).normalize(ref).toString() :
					PathUtil.normalize(refParts, path);
			} catch(IllegalArgumentException e) {
				throw new IllegalArgumentException(path);
			}
			result.add(plugin.length() == 0 ? path : plugin + "!" + path); //$NON-NLS-1$
		}
		return result.toArray(new String[result.size()]);
	}

	private static String normalize(List<String> refParts, String path) throws IllegalArgumentException {
		List<String> normalized = null;
		
		if (path.contains("!")) { //$NON-NLS-1$
			int index = path.indexOf("!"); //$NON-NLS-1$
			return new StringBuffer(normalize(refParts, path.substring(0, index))).append('!').append(normalize(refParts, path.substring(index+1))).toString();
		}
		
		if (path.startsWith(".")) { //$NON-NLS-1$
			normalized = new ArrayList<String>(refParts);
		} else {
			normalized = new ArrayList<String>();
			if (path.startsWith("/")) { //$NON-NLS-1$
				throw new IllegalArgumentException(path);
			}
		}
		String[] pathParts = path.split("/"); //$NON-NLS-1$
		
		for (String part : pathParts) {
			if (part.equals(".") || part.equals("")) { //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			} else if (part.equals("..")) { //$NON-NLS-1$
				if (normalized.size() < 1 || normalized.size() == 1 && normalized.get(0).equals("")) { //$NON-NLS-1$
					// Illegal relative path. 
					throw new IllegalArgumentException(path);
				}
				// back up one directory
				normalized.remove(normalized.size()-1);
			} else {
				normalized.add(part);
			}
		}
		return StringUtils.join(normalized.toArray(), "/"); //$NON-NLS-1$
	}
	
	/**
	 * Returns the module name for the specified URI. This is the name part of
	 * the URI with path information removed, and with the .js extension removed
	 * if present.
	 * 
	 * @param uri
	 *            the uri to return the name for
	 * @return the module name
	 */
	public static String getModuleName(URI uri) {
		String name = uri.getPath();
		if (name.endsWith("/")) { //$NON-NLS-1$
			name = name.substring(0, name.length()-1);
		}
		int idx = name.lastIndexOf("/"); //$NON-NLS-1$
		if (idx != -1) {
			name = name.substring(idx+1);
		}
		if (name.endsWith(".js")) { //$NON-NLS-1$
			name = name.substring(0, name.length()-3);
		}
		return name;
	}
	
	/**
	 * Convenience method to convert a URL to a URI that doesn't throw a
	 * URISyntaxException if the path component for the URL contains 
	 * spaces (like {@link URL#toURI()} does).
	 * 
	 * @param url The input URL
	 * @return The URI
	 * @throws URISyntaxException
	 */
	public static URI url2uri(URL url) throws URISyntaxException {
		return new URI(
			url.getProtocol(),
			url.getAuthority(), 
			url.getPath(), 
			url.getQuery(), 
			url.getRef());
	}
	
	public static URI stripJsExtension(URI value) throws URISyntaxException {
		if (value == null) {
			return null;
		}
		return value.getPath().endsWith(".js") ?  //$NON-NLS-1$
			new URI(
				value.getScheme(),
				value.getAuthority(), 
				value.getPath().substring(0, value.getPath().length()-3), 
				value.getQuery(), 
				value.getFragment()
			) : value;
	}
	
	public static URI appendToPath(URI uri, String append) throws URISyntaxException {
		return 
			new URI(
				uri.getScheme(),
				uri.getAuthority(), 
				uri.getPath() + append, 
				uri.getQuery(), 
				uri.getFragment()
			);
	}
}
