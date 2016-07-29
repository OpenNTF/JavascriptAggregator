/*
 * (C) Copyright IBM Corp. 2012, 2016
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

import com.ibm.jaggr.core.impl.deps.DepUtils.ParseResult;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.util.CompilerUtil;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.JSError;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.rhino.Node;

import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements the {@link Callable} interface to parse an AMD module
 * and extract the dependency information from the AMD module's define()
 * function. The dependency information thus obtained is used to update the
 * dependency array in the provided {@link DepTreeNode} and the node's
 * last-modified times are updated as well.
 */
final class DepParser implements Callable<URI> {
	static final Logger log = Logger.getLogger(DepParser.class.getName());
	static {
		Compiler.setLoggingLevel(Level.WARNING);
	}

	private final DepTreeNode treeNode;
	private final IResource resource;
	private final Map<AccessibleObject, List<Object>> compilerOptionsMap;

	/**
	 * Object constructor
	 *
	 * @param treeNode
	 *            The node to be populated with the dependency list obtained
	 *            from jsFile
	 * @param resource
	 *            The resource to be parsed for dependencies
	 * @param compilerOptionsMap
	 *            Compiler options from config
	 */
	DepParser(DepTreeNode treeNode, IResource resource, Map<AccessibleObject, List<Object>> compilerOptionsMap) {
		this.treeNode = treeNode;
		this.resource = resource;
		this.compilerOptionsMap = compilerOptionsMap;

	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Callable#call()
	 */
	public URI call() throws Exception {
		// Save original time stamp for dependency list
		long lastModifiedDep = treeNode.lastModifiedDep();
		long lastModified = resource.lastModified();
		// Parse the javascript code
		Compiler compiler = new Compiler();
		CompilerOptions options = CompilerUtil.getDefaultOptions();
		if (compilerOptionsMap != null) {
			CompilerUtil.applyCompilerOptionsFromMap(options, compilerOptionsMap);
		}
		options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
				CheckLevel.OFF);

		compiler.initOptions(options);
		InputStream in = resource.getInputStream();
		Node node = null;
		try {
			node = compiler.parse(JSSourceFile.fromInputStream(resource.getURI().toString(), in));
			if (compiler.hasErrors()) {
				if (log.isLoggable(Level.WARNING)) {
					JSError[] errors = compiler.getErrors();
					for (JSError error : errors) {
						log.log(Level.WARNING, error.toString());
					}
				}
			}
		} catch (Throwable e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Error occurred parsing " + resource.getURI().toString() + ": " + e.getMessage(), e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} finally {
			in.close();
		}
		if (node != null) {
			// walk the AST for the node looking for define calls
			// and pull out the required dependency list.
			Set<String> features = new HashSet<String>();
			ParseResult parseResult = DepUtils.parseDependencies(node, features);
			Collection<String> defineDeps = parseResult.getDefineDependencies();
			Collection<String> requireDeps = parseResult.getRequireDependencies();
			String[] defineDepArray = (defineDeps == null) ?
					new String[0] : defineDeps.toArray(new String[defineDeps.size()]);
			String[] requireDepArray = (requireDeps == null) ?
					new String[0] : requireDeps.toArray(new String[requireDeps.size()]);
			String[] featureArray = (features == null) ?
					new String[0] : features.toArray(new String[features.size()]);
			/*
			 * Determine if the dependency list has changed.  We keep track of
			 * dependency list changes separate from code changes in general
			 * because a dependency list change necessitates invalidating all
			 * cached responses for the configs that reference this file, and
			 * we want to do this only when necessary.
			 */
			if (lastModifiedDep ==  -1 ||
					!Arrays.equals(defineDepArray, treeNode.getDefineDepArray()) ||
					!Arrays.equals(requireDepArray, treeNode.getRequireDepArray()) ||
					!Arrays.equals(featureArray, treeNode.getDependentFeatures())) {
				lastModifiedDep = lastModified;
			}
			// update the dependency info in the node
			treeNode.setDependencies(defineDepArray, requireDepArray, featureArray,
					lastModified, lastModifiedDep);
		}
		return resource.getURI();
	}
}
