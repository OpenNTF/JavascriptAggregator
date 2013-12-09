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

package com.ibm.jaggr.core.impl.deps;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;

/**
 * This class implements the {@link Callable} interface to build/validate a
 * dependecy tree rooted at the specified path. The provided
 * {@link CompletionService} is used to launch the parser threads that parse the
 * javascript files and extract the module dependencies from the module's
 * define() function. Separate threads are used for the parser in order to try
 * and achieve a measure of parallel processing so that the builder is not so
 * I/O bound.
 */
final class DepTreeBuilder implements Callable<DepTreeBuilder.Result> {

	static final Logger log = Logger.getLogger(DepTreeBuilder.class.getName());
	
	private final IAggregator aggregator;
	/**
	 * The {@link CompletionService} used to lauch parser threads
	 */
	private final CompletionService<URI> parserCs;
	
	/**
	 * URI to the folder resource containing the javascript modules
	 * to be parsed for dependencies.  Corresponds to {@link #root}.
	 */
	private final URI uri;
	
	/**
	 * The root node corresponding to {@link #uri}.  Any existing child
	 * nodes will be validated based on the last modified time stamps.
	 */
	private final DepTreeNode root;
	
	private final DepTreeNode cached;
	
	/**
	 * Counter to keep track of the number of parser threads started
	 */
	private final AtomicInteger parserCount = new AtomicInteger(0);

	/**
	 * Result type returned by the {@link DepTreeBuilder#call()} method.
	 */
	public static class Result {
		public final String dirName;
		public final int parseCount;

		Result(String dirName, int parseCount) {
			this.dirName = dirName;
			this.parseCount = parseCount;
		}
	}

	/**
	 * Object constructor
	 * 
	 * @param parserCs
	 *            The {@link CompletionService} to use to start parser threads
	 * @param path
	 *            The root path containing the javascrpt modules to be parsed
	 * @param node
	 *            The {@link DepTreeNode} corresponding to {@code path}.
	 */
	DepTreeBuilder(IAggregator aggregator, CompletionService<URI> parserCs,
			URI path, DepTreeNode node, DepTreeNode cached) {
		this.aggregator = aggregator;
		this.parserCs = parserCs;
		this.uri = path;
		this.root = node;
		this.cached = cached;
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	public Result call() throws Exception {

		IResourceVisitor visitor = new IResourceVisitor() {
			/* (non-Javadoc)
			 * @see com.ibm.jaggr.core.modules.ResourceVisitor#visitResource(com.ibm.jaggr.core.modules.Resource)
			 */
			@Override
			public boolean visitResource(Resource resource, String pathname) throws IOException {
				if (resource.isFolder()) {
					return true;
				}
				if (!resource.getURI().getPath().endsWith(".js")) { //$NON-NLS-1$
					return false;
				}
				if (pathname == null) {
					pathname = ""; //$NON-NLS-1$
				}
				// strip off the .js extension
				if (pathname.endsWith(".js")) {  //$NON-NLS-1$
					pathname = pathname.substring(0, pathname.length()-3);
				}
				DepTreeNode node = (pathname.length() > 0) ? root.createOrGet(pathname) : root;
				DepTreeNode cachedNode = null;
				if (cached != null) {
					cachedNode = (pathname.length() > 0) ? cached.getDescendent(pathname) : cached;
				}
				if (cachedNode != null) {
					node.setDependencies(cachedNode.getDepArray(), 
							cachedNode.lastModified(), cachedNode.lastModifiedDep());
				}
				/*
				 * The path is for a javascript module. Check the timestamp for the
				 * node against the timestamp for the file object to see if the file
				 * has changed and we need to re-parse it.
				 */
				if (node.lastModified() != resource.lastModified()) {
					// File has changed, or is new. Submit an async parser job.
					parserCs.submit(new DepParser(node, resource));
					parserCount.incrementAndGet();
				}
				return true;
			}
		};
		
		/*
		 * Process the path. The treeWalker method will queue files
		 * to the parser completion service to parse javascript
		 * files in order to read the require list from the AMD
		 * define() function, and increments parserCount for each
		 * file queued.
		 */
		IResource resource = aggregator.newResource(uri);
		if (resource.exists()) {
			resource.walkTree(visitor);
		}
		/*
		 * Call treeWalker again, this time to add the javascript
		 * module with the same pathname, if it exists.
		 */
		String name = uri.getPath();
		if (!name.endsWith(".js")) { //$NON-NLS-1$
			if (name.endsWith("/")) { //$NON-NLS-1$
				name = name.substring(0, name.length()-1);
			}
			int idx = name.lastIndexOf("/"); //$NON-NLS-1$
			if (idx != -1) {
				name = name.substring(idx + 1);
			}
			name += ".js"; //$NON-NLS-1$
			
			resource = aggregator.newResource(
					uri.resolve((uri.getPath().endsWith("/") ? "../" : "./") + name)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if (resource.exists()) {
				visitor.visitResource(resource.asVisitorResource(), ""); //$NON-NLS-1$
			}
		}
		// Record the count of files queue
		int totalCount = parserCount.get();
		
		// Pull the completed parser tasks from the completion queue
		// until all files have been parsed
		while (parserCount.decrementAndGet() >= 0) {
			try {
				parserCs.take().get();
			} catch (Exception e) {
				e.printStackTrace();
				if (log.isLoggable(Level.SEVERE))
					log.log(Level.SEVERE, e.getMessage(), e);
			}
		}

		// Return the buildReader
		return new Result(uri.toString(), totalCount);
	}
}
