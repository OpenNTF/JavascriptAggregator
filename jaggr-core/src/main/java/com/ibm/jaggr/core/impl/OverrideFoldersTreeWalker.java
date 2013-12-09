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

package com.ibm.jaggr.core.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.util.PathUtil;

/**
 * Walks the directory trees rooted at the override folders specified in the
 * config and determines a last-modified date for the entire set of overrides.
 * <p>
 * If any top level override folder that existed the last time the tree 
 * walker was run no longer exist, then the last-modified time is the 
 * current time. 
 */
public class OverrideFoldersTreeWalker implements Serializable {
	private static final long serialVersionUID = -5957040347146874129L;
	
	private static final Logger log = Logger.getLogger(OverrideFoldersTreeWalker.class.getName());
	private static final String CACHED_OVERRIDES_FILE = "overrides.ser"; //$NON-NLS-1$

	public long getLastModified() {
		return lastModified;
	}

	public long getLastModifiedJS() {
		return lastModifiedJS;
	}

	private long lastModified = 0;
	private long lastModifiedJS = 0;
	private final transient IAggregator aggr;
	private final transient IConfig config;
	private Collection<URI> overrides = new HashSet<URI>();
	
	public OverrideFoldersTreeWalker(IAggregator aggr, IConfig config) {
		this.aggr = aggr;
		this.config = config;
	}
	
	public void walkTree() {
		Collection<URI> uris = new HashSet<URI>();
		for (IConfig.Location loc : config.getPaths().values()) {
			if (loc.getOverride() != null) {
				URI uri = loc.getOverride();
				uris.add(uri);
				if (!uri.getPath().endsWith("/")) { //$NON-NLS-1$
					try {
						uris.add(PathUtil.appendToPath(uri, ".js")); //$NON-NLS-1$
					} catch (URISyntaxException ignore) {
					}
				}
			}
		}
		for (IConfig.Location loc : config.getPackageLocations().values()) {
			if (loc.getOverride() != null) {
				URI uri = loc.getOverride();
				uris.add(uri);
				if (!uri.getPath().endsWith("/")) { //$NON-NLS-1$
					try {
						uris.add(PathUtil.appendToPath(uri, ".js")); //$NON-NLS-1$
					} catch (URISyntaxException ignore) {
					}
				}
			}
		}		
		// For each customization directory specified in the config, get the last-modified
		// time for the directory and its contents.
		for (URI uri : uris) {
			IResource res = aggr.newResource(uri);
			if (res.exists()) {
				overrides.add(res.getURI());
				LastModChecker checker = new LastModChecker(res.lastModified());
				try {
					res.walkTree(checker);
				} catch (IOException e) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, e.getMessage(), e);
					}
				}
				lastModified = Math.max(lastModified, checker.getLastModified());
				lastModifiedJS = Math.max(lastModifiedJS, checker.getLastModifiedJS());
			}
		}
		// De-serialize previous results to look for removed folders/resources
		OverrideFoldersTreeWalker cached = null;
		File file = new File(aggr.getWorkingDirectory(), CACHED_OVERRIDES_FILE);
    	try {
    		ObjectInputStream is = new ObjectInputStream(new FileInputStream(file));
    		try {
    			cached = (OverrideFoldersTreeWalker)is.readObject();
    		} finally {
    			try { is.close(); } catch (Exception ignore) {}
    		}
    	} catch (FileNotFoundException ignore) {
    		// Not an error
    	} catch (Exception e) {
    		if (log.isLoggable(Level.SEVERE))
				log.log(Level.SEVERE, e.getMessage(), e);
    	}
    	if (cached != null) {
    		// make sure we don't return a time earlier than previous results
    		lastModified = Math.max(lastModified, cached.lastModified);
    		lastModifiedJS = Math.max(lastModifiedJS, cached.lastModifiedJS);
    		
    		long now = new Date().getTime();
    		// Look for missing (deleted) folders/resources
    		for (URI override : cached.overrides) {
    			if (!overrides.contains(override)) {
    				// previously detected folder/resource has been removed.
    				// Set last modified times to current time
    				lastModified = lastModifiedJS = now;
    			}
    		}
    	}
    	// Serialize new results if changed
    	if (cached == null || 
    			!overrides.equals(cached.overrides) || 
    			lastModified != cached.lastModified || 
    			lastModifiedJS != cached.lastModifiedJS) {
			try {
				ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
				try {
					os.writeObject(this);
				} finally {
					try { os.close(); } catch (Exception ignore) {}
				}
			} catch(Exception e) {
				if (log.isLoggable(Level.SEVERE))
					log.log(Level.SEVERE, e.getMessage(), e);
			}
    	}
	}

	private class LastModChecker implements IResourceVisitor {
		private long lastMod = 0;
		private long lastModJS = 0;
		public LastModChecker(long initialValue) { lastMod = lastModJS = initialValue; }
		public long getLastModified() {	return lastMod; }
		public long getLastModifiedJS() { return lastModJS; }
		@Override
		public boolean visitResource(Resource resource,	String pathName) throws IOException {
			lastMod = Math.max(lastMod, resource.lastModified());
			if (resource.isFolder() || pathName.endsWith(".js")) { //$NON-NLS-1$
				lastModJS = Math.max(lastModJS, resource.lastModified());
			}
			return true;
		}
		
	}
}
