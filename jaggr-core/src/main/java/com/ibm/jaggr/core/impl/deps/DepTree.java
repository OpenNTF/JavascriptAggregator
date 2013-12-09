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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.impl.PlatformAggregatorFactory;
import com.ibm.jaggr.core.util.PathUtil;

/**
 * This class provides the service for creating dependency maps from a set of
 * javascript sources. The locations of the sources are specified in the
 * collection passed to this class's constructor. These directories are scanned
 * for javascript files. The javascript files are parsed to locate the AMD
 * {@code define(...)} function call for the AMD module and the list of module
 * dependencies specified in the define are associated with file.
 * <p>
 * The {@link #mapDependencies(Map)} function can then be called to obtain a
 * {@link DepTreeNode} which maps module references to the exploded module
 * dependencies (the dependencies declared in the module, plus nested
 * dependencies) for the module.
 */
public class DepTree implements Serializable {
	private static final long serialVersionUID = 5453343490025146049L;

	static final Logger log = Logger.getLogger(DepTree.class.getName());

	static final String TREEBUILDER_TGNAME = "treeBuilder"; //$NON-NLS-1$
	static final String JSPARSER_TGNAME = "jsParser"; //$NON-NLS-1$
	static final String THREADNAME = "{0} Thread-{1}";  //$NON-NLS-1$
	/**
	 * Map of directory names to {@link DepTreeNode} objects. Each
	 * {@link DepTreeNode} mirrors the associated file directory in terms of the
	 * directory structure and the javascript files contained therein. The file
	 * directories are orthogonal (i.e. non-overlapping). The
	 * {@link DepTreeNode} objects in this map can specify directory lists using
	 * module names with relative paths and do not contain any resolved
	 * dependency references to any other modules. They only contain the String
	 * array of dependency names for each javascript source file.
	 * <p>
	 * This object gets serialized/de-serialized to/from the location specified
	 * by the {@code basedir} constructor argument.
	 */
	protected ConcurrentMap<URI, DepTreeNode> depMap = null;
	
	protected Object rawConfig;
	
	protected long stamp;
	
	protected String cacheBust;
	
	protected boolean fromCache = false;

	private static final String DEPCACHE_DIRNAME = "deps"; //$NON-NLS-1$

	private static final String CACHE_FILE = "depmap.cache"; //$NON-NLS-1$

	/** provided for subclasses */
	protected DepTree() {}
	
	/**
	 * Object constructor. Attempts to de-serialize the cached dependency lists
	 * from disk and then validates the dependency lists based on last-modified
	 * dates, looking for any new or removed files. If the cached dependency
	 * list data cannot be de-serialized, new lists are constructed. Once the
	 * dependency lists have been validated, the list data is serialized back
	 * out to disk.
	 * 
	 * @param paths
	 *            Collection of URIs which specify the target resources
	 *            to be scanned for javascript files.
	 * @param aggregator
	 *            The servlet instance for this object
	 * @param stamp
	 *            timestamp associated with external override/customization 
	 *            resources that are check on every server restart                     
	 * @param clean
	 *            If true, then the dependency lists are generated from scratch
	 *            rather than by de-serializing and then validating the cached
	 *            dependency lists.
	 * @param validateDeps
	 *            If true, then validate existing cached dependencies using
	 *            file last-modified times.
	 * @throws IOException
	 */
	public DepTree(Collection<URI> paths, IAggregator aggregator, long stamp, boolean clean, boolean validateDeps) 
	throws IOException {
		this.stamp = stamp;
		IConfig config = aggregator.getConfig();
		rawConfig = config.toString();

		File cacheDir = new File(aggregator.getWorkingDirectory(), DEPCACHE_DIRNAME);
		File cacheFile = new File(cacheDir, CACHE_FILE);

		/*
		 * The de-serialized dependency map. If we have a cached dependency map,
		 * then it will be validated against the last-modified dates of the
		 * current files and only the files that have changed will need to be
		 * re-parsed to update the dependency lists.
		 */
		DepTree cached = null;

		if (!clean) {
			// If we're not starting clean, try to de-serialize the map from
			// cache
			try {
				ObjectInputStream is = new ObjectInputStream(
						new FileInputStream(cacheFile));
				try {
					cached = (DepTree) is.readObject();
				} finally {
					try { is.close(); } catch (Exception ignore) {}
				}
			} catch (FileNotFoundException e) {
				/*
				 * Not an error. Just means that the cache file hasn't been
				 * written yet or else it's been deleted.
				 */
				if (log.isLoggable(Level.INFO))
					log.log(Level.INFO, Messages.DepTree_1);
			} catch (Exception e) {
				if (log.isLoggable(Level.SEVERE))
					log.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		
		// If the cacheBust config param has changed, then do a clean build
		// of the dependencies.
		if (cached != null) {
			if (stamp == 0) {
				// no init stamp provided.  Preserve the cached one.
				stamp = cached.stamp;
			}
			if (stamp > cached.stamp) {
				// init stamp has been updated.  Validate dependencies.
				validateDeps = true;
			}
			cacheBust = aggregator.getOptions().getCacheBust();
			if (!StringUtils.equals(cacheBust, cached.cacheBust)) {
				if (log.isLoggable(Level.INFO)) {
					log.info(Messages.DepTree_2);
				}
				cached = null;
			}
		}

		/*
		 * If we de-serialized a previously saved dependency map, then go with
		 * that.
		 */
		if (cached != null && 
				rawConfig.equals(cached.rawConfig) &&
				!validateDeps && !clean) {
			depMap = cached.depMap;
			fromCache = true;
			return;
		}

		// Initialize the dependency map
		depMap = new ConcurrentHashMap<URI, DepTreeNode>();
		

		// This can take a while, so print something to the console
		String msg = MessageFormat.format(
				Messages.DepTree_3,
				new Object[]{aggregator.getName()});
		
		if(PlatformAggregatorFactory.getPlatformAggregator() != null){
			PlatformAggregatorFactory.getPlatformAggregator().println(msg);
		}
		if (log.isLoggable(Level.INFO)) {
			log.info(msg);
		}
		// Make sure that all the paths are unique and orthogonal
		paths = DepUtils.removeRedundantPaths(paths);

		/*
		 * Create the thread pools, one for the tree builders and one for the
		 * parsers. Since a tree builder thread will wait for all the outstanding
		 * parser threads started by that builder to complete, we need to use two
		 * independent thread pools to guard against the possibility of deadlock
		 * caused by all the threads in the pool being consumed by tree builders
		 * and leaving none available to service the parsers.
		 */
		final ThreadGroup 
			treeBuilderTG = new ThreadGroup(TREEBUILDER_TGNAME),
			parserTG = new ThreadGroup(JSPARSER_TGNAME);
		ExecutorService 
			treeBuilderExc = Executors.newFixedThreadPool(10, new ThreadFactory() {
				public Thread newThread(Runnable r) {
					return new Thread(treeBuilderTG, r,
						MessageFormat.format(THREADNAME,
							new Object[]{
									treeBuilderTG.getName(),
									treeBuilderTG.activeCount()
							}
						)
					);
				}
			}), 
			parserExc = Executors.newFixedThreadPool(20, new ThreadFactory() {
				public Thread newThread(Runnable r) {
					return new Thread(parserTG, r,
						MessageFormat.format(THREADNAME,
							new Object[]{
									parserTG.getName(),
									parserTG.activeCount()
							}
						)
					);
				}
			});

		// Counter to keep track of number of tree builder threads started
		AtomicInteger treeBuilderCount = new AtomicInteger(0);

		// The completion services for the thread pools
		final CompletionService<URI> parserCs = new ExecutorCompletionService<URI>(
				parserExc);
		CompletionService<DepTreeBuilder.Result> treeBuilderCs = new ExecutorCompletionService<DepTreeBuilder.Result>(
				treeBuilderExc);

		// Start the tree builder threads to process the paths
		for (final URI path : paths) {
			/*
			 * Create or get from cache the root node for this path and
			 * add it to the new map.
			 */
			DepTreeNode root = new DepTreeNode(PathUtil.getModuleName(path));
			DepTreeNode cachedNode = null;
			if (cached != null) {
				cachedNode = cached.depMap.get(path);
				if (log.isLoggable(Level.INFO)) {
					log.info(
						MessageFormat.format(
							Messages.DepTree_4,
							new Object[]{path}
						)
					);
				}
			} else {
				if (log.isLoggable(Level.INFO)) {
					log.info(
						MessageFormat.format(
							Messages.DepTree_5,
							new Object[]{path}
						)
					);
				}
			}
			depMap.put(path, root);

			treeBuilderCount.incrementAndGet();
			treeBuilderCs.submit(new DepTreeBuilder(aggregator, parserCs, path, root, cachedNode));
		}

		// List of parser exceptions
		LinkedList<Exception> parserExceptions = new LinkedList<Exception>();
		
		/*
		 * Pull the completed tree builder tasks from the completion queue until
		 * all the paths have been processed
		 */
		while (treeBuilderCount.decrementAndGet() >= 0) {
			try {
				DepTreeBuilder.Result result = treeBuilderCs.take().get();
				if (log.isLoggable(Level.INFO)) {
					log.info(
						MessageFormat.format(
							Messages.DepTree_6,
							new Object[] {
								result.parseCount,
								result.dirName
							}
						)
					);
				}
			} catch (Exception e) {
				if (log.isLoggable(Level.SEVERE))
					log.log(Level.SEVERE, e.getMessage(), e);
				parserExceptions.add(e);
			}
		}

		// shutdown the thread pools now that we're done with them
		parserExc.shutdown();
		treeBuilderExc.shutdown();
		
		// If parser exceptions occurred, then rethrow the first one 
		if (parserExceptions.size() > 0) {
			throw new RuntimeException(parserExceptions.get(0));
		}

		// Prune dead nodes (nodes with no children or dependency lists)
		for (Map.Entry<URI, DepTreeNode> entry : depMap.entrySet()) {
			entry.getValue().prune();
		}

		/*
		 * Make sure the cache directory exists before we try to serialize the
		 * dependency map.
		 */
		if (!cacheDir.exists())
			if (!cacheDir.mkdirs()) {
				throw new IOException(MessageFormat.format(
					Messages.DepTree_0,
					new Object[]{cacheDir.getAbsolutePath()}
				));
			}

		// Serialize the map to the cache directory
		ObjectOutputStream os;
		os = new ObjectOutputStream(new FileOutputStream(cacheFile));
		try { 
			os.writeObject(this);
		} finally {
			try { os.close(); } catch (Exception ignore) {}
		}
		msg = MessageFormat.format(
				Messages.DepTree_7,
				new Object[]{aggregator.getName()}
		);

		// Output that we're done.
		if(PlatformAggregatorFactory.getPlatformAggregator() != null){
			PlatformAggregatorFactory.getPlatformAggregator().println(msg);
		}
		if (log.isLoggable(Level.INFO)) {
			log.info(msg);
		}
	}
	
	/**
	 * @return true if the dependencies were loaded from cache
	 */
	public boolean isFromCache() {
		return fromCache;
	}
	
	/**
	 * Returns a new tree with an unnamed {@link DepTreeNode} object at the root
	 * of the tree. Each of the keys specified in the map are children of the
	 * returned node and those node's children are the children of the nodes 
	 * corresponding to the resource URIs specified by the map values.
	 * The map values must be the same as, or a subset of, the paths that
	 * were used to create this instance.
	 * 
	 * @param map
	 *            A map of module names to resource URIs
	 * 
	 * @return The root {@link DepTreeNode} for the new tree
	 */
	public DepTreeRoot mapDependencies(DepTreeRoot root, Object context, Map<String, URI> map, IConfig config) {
		// For each config path entry...
		for (Entry<String, URI> configPathEntry : map.entrySet()) {
			String name = configPathEntry.getKey();
			// make sure name is valid
			if (name.startsWith("/")) { //$NON-NLS-1$
				log.severe(
					MessageFormat.format(
						Messages.DepTree_8,
						new Object[]{name}
					)
				);
				throw new IllegalArgumentException(name);
			}
			
			// make sure no relative path components (./ ore ../).
			for (String part : name.split("/")) { //$NON-NLS-1$
				if (part.startsWith(".")) { //$NON-NLS-1$
					log.severe(
						MessageFormat.format(
							Messages.DepTree_9,
							new Object[]{name}
						)
					);
					throw new IllegalArgumentException(name);
				}
			}
			
			// Create the child node for this entry's package/path name
			DepTreeNode target = root.createOrGet(name);
			URI filePath = configPathEntry.getValue();
			
			/*
			 * Get the root node corresponding to the entry's file path from the
			 * map. This node does not have any resolved references and the
			 * module names in the dependency lists may contain relative paths.
			 * Note that the node may be null if the config specifies a path
			 * that is not found.
			 */
			DepTreeNode source = DepUtils.getNodeForResource(filePath, depMap);
			if (source != null) {
				/*
				 * Clone the tree and copy the cloned node's children to the 
				 * target node.
				 */
				DepTreeNode temp = null;
				try {
					temp = source.clone();
				} catch (CloneNotSupportedException e) {
					// won't happen, but the language requires us to handle it.
					e.printStackTrace();
				}
				target.overlay(temp);
			} else {
				throw new IllegalStateException("Missing required resource: " + filePath); //$NON-NLS-1$
			}
		}

		return root;
	}
}
