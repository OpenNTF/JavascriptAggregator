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

package com.ibm.jaggr.service.impl.modulebuilder.javascript;


import com.ibm.jaggr.core.DependencyVerificationException;
import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDepInfo;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.Messages;
import com.ibm.jaggr.core.util.BooleanTerm;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.NodeUtil;
import com.ibm.jaggr.core.util.PathUtil;

import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom Compiler pass for Google Closure compiler to do require list explosion.
 * Scans the AST for require calls and explodes any require lists it finds to
 * include nested dependencies.  Nested dependencies for modules specified in
 * the require list are obtained from the {@link IDepTreeNode} object passed
 * in the constructor.
 */
public class RequireExpansionCompilerPass implements CompilerPass {

	/**
	 * The {link IAggregator} object
	 */
	private IAggregator aggregator;

	/**
	 * The features specified in the request
	 */
	private Features hasFeatures;

	/**
	 * Collection of features that this compiled build depends on.
	 * Use for generating cache keys for cached builds
	 */
	private Set<String> dependentFeatures;

	/**
	 * The list of {@link ModuleDeps} which specify the expanded dependencies.
	 * There is one entry in the list for each require call in the module.
	 * The position in the list corresponds to index value specified in the
	 * place holder element in the require list.
	 */
	private List<ModuleDeps> expandedDepsList;

	/**
	 * Output for browser console
	 */
	private List<List<String>> consoleDebugOutput;

	/**
	 * The name of the config var on the client.  This is used to
	 * locate the deps property.
	 */
	private String configVarName;

	/**
	 * True if console logging is enabled
	 */
	private boolean logDebug;

	/**
	 * Constructs a instance of this class for a specific module that is being compiled.
	 *
	 * @param moduleName The name of the module
	 * @param deps
	 * @param config
	 * @param features The set of features for the request
	 * @param dependentFeatures
	 * @param configDeps
	 * @param logDebug
	 */
	public RequireExpansionCompilerPass(
			IAggregator aggregator,
			Features features,
			Set<String> dependentFeatures,
			List<ModuleDeps> expandedDepsList,
			String configVarName,
			boolean logDebug) {

		this.aggregator = aggregator;
		this.hasFeatures = features;
		this.dependentFeatures = dependentFeatures;
		this.expandedDepsList = expandedDepsList;
		this.configVarName = configVarName;
		this.consoleDebugOutput = logDebug ? new LinkedList<List<String>>() : null;
		this.logDebug = logDebug;

		if (configVarName == null || configVarName.length() == 0) {
			this.configVarName = "require"; //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see com.google.javascript.jscomp.CompilerPass#process(com.google.javascript.rhino.Node, com.google.javascript.rhino.Node)
	 */
	@Override
	public void process(Node externs, Node root) {
		this.expandedDepsList.clear();
		List<DependencyList> enclosingDependencies = new LinkedList<DependencyList>();
		try {
			processChildren(root, enclosingDependencies);
		} catch (ProcessingDependenciesException e) {
			throw new RuntimeProcessingDependenciesException(e);
		} catch (DependencyVerificationException e) {
			throw new RuntimeDependencyVerificationException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		/*
		 * Emit code to call console.log on the client
		 */
		if (logDebug && consoleDebugOutput.size() > 0) {
			Node node = root;
			if (node.getType() == Token.BLOCK) {
				node = node.getFirstChild();
			}
			if (node.getType() == Token.SCRIPT) {
				Node firstNode = node.getFirstChild();
				for (List<String> entry : consoleDebugOutput) {
					Node call = new Node(Token.CALL,
							new Node(Token.GETPROP,
									Node.newString(Token.NAME, "console"),  //$NON-NLS-1$
									Node.newString(Token.STRING, "log") //$NON-NLS-1$
									)
							);
					for (String str : entry) {
						call.addChildToBack(Node.newString(str));
					}
					node.addChildBefore(new Node(Token.EXPR_RESULT, call), firstNode);
				}
			}
		}
	}

	/**
	 * Recursively called to process AST nodes looking for require calls. If a
	 * require call is found, then the dependency list is expanded to include
	 * nested dependencies. The set of nested dependencies is obtained from the
	 * config object and is trimmed so as not to include enclosing dependencies
	 * (dependencies specified in the enclosing define or any enclosing require
	 * calls.
	 *
	 * @param node
	 *            The node being processed
	 * @param enclosingDependencies
	 *            The set of dependencies specified by enclosing define or
	 *            require calls.
	 */
	public void processChildren(Node node, List<DependencyList> enclosingDependencies)
			throws IOException
			{
		for (Node cursor = node.getFirstChild(); cursor != null; cursor = cursor.getNext()) {
			Node dependencies = null;
			if ((dependencies = NodeUtil.moduleDepsFromRequire(cursor)) != null) {
				enclosingDependencies = new LinkedList<DependencyList>(enclosingDependencies);
				expandRequireList(
						dependencies,
						enclosingDependencies,
						logDebug ? MessageFormat.format(
								Messages.RequireExpansionCompilerPass_0,
								new Object[]{cursor.getLineno()})
								: null);
			} else if ((dependencies = NodeUtil.moduleDepsFromConfigDeps(cursor, configVarName)) != null) {
				expandRequireList(
						dependencies,
						new LinkedList<DependencyList>(),
						logDebug ?
								MessageFormat.format(
										Messages.RequireExpansionCompilerPass_2,
										new Object[]{cursor.getLineno()}
										) : null);
			} else if ((dependencies = NodeUtil.moduleDepsFromDefine(cursor)) != null) {
				String moduleName = cursor.getFirstChild().getProp(Node.SOURCENAME_PROP).toString();

				if (aggregator.getOptions().isDevelopmentMode() &&
						aggregator.getOptions().isVerifyDeps()) {
					// Validate dependencies for this module by comparing the
					// declared dependencies against the dependencies that were
					// used to calculate the dependency graph.
					Node strNode = dependencies.getFirstChild();
					List<String> deps = new ArrayList<String>();
					while (strNode != null) {
						if (strNode.getType() == Token.STRING) {
							String mid = strNode.getString();
							if (!PathUtil.invalidChars.matcher(mid).find()) {
								// ignore names with invalid characters
								deps.add(strNode.getString());
							}
						}
						strNode = strNode.getNext();
					}
					int idx = moduleName.lastIndexOf("/"); //$NON-NLS-1$
					String ref = (idx == -1) ? "" : moduleName.substring(0, idx); //$NON-NLS-1$
					List<String> normalized = Arrays.asList(PathUtil.normalizePaths(ref, deps.toArray(new String[deps.size()])));

					// Run the list through a linked hash set to remove duplicate entries, yet keep list ordering
					Set<String> temp = new LinkedHashSet<String>(normalized);
					normalized = Arrays.asList(temp.toArray(new String[temp.size()]));

					List<String> processedDeps = aggregator.getDependencies().getDelcaredDependencies(moduleName);
					if (processedDeps != null && !processedDeps.equals(normalized)) {
						// The dependency list for this module has changed since the dependencies
						// were last created/validated.  Throw an exception.
						throw new DependencyVerificationException(moduleName);
					}
				}
				// Add the expanded dependencies to the set of enclosing dependencies for
				// the module.
				List<String> moduleDeps = aggregator.getDependencies().getDelcaredDependencies(moduleName);
				if (moduleDeps != null) {
					enclosingDependencies = new LinkedList<DependencyList>(enclosingDependencies);
					DependencyList depList = new DependencyList(
							moduleDeps,
							aggregator,
							hasFeatures,
							true,	// resolveAliases
							logDebug);
					depList.setLabel(MessageFormat.format(
							Messages.RequireExpansionCompilerPass_1,
							new Object[] {cursor.getLineno()}
							));
					enclosingDependencies.add(depList);
				}
			}
			// Recursively call this method to process the child nodes
			if (cursor.hasChildren())
				processChildren(cursor, enclosingDependencies);
		}
			}

	private void expandRequireList(Node array, List<DependencyList> enclosingDependencies, String detail)
			throws IOException {

		Node strNode = array.getFirstChild();
		List<String> names = new LinkedList<String>();
		while (strNode != null) {
			if (strNode.getType() == Token.STRING) {
				names.add(strNode.getString());
			}
			strNode = strNode.getNext();
		}
		if (names.size() == 0) {
			return;
		}
		List<String> msg = new LinkedList<String>();
		String moduleName = array.getParent().getProp(Node.SOURCENAME_PROP).toString();
		if (logDebug) {
			msg.add("%c" + MessageFormat.format( //$NON-NLS-1$
					Messages.RequireExpansionCompilerPass_6,
					new Object[]{detail, moduleName}
					));
			msg.add("color:blue;background-color:yellow"); //$NON-NLS-1$
			consoleDebugOutput.add(msg);
		}

		// normalize the module names in the dependency list
		int idx = moduleName.lastIndexOf("/"); //$NON-NLS-1$
		String ref = (idx == -1) ? "" : moduleName.substring(0, idx); //$NON-NLS-1$
		String[] normalizedNames = PathUtil.normalizePaths(ref, names.toArray(new String[names.size()]));
		DependencyList depList = new DependencyList(
				Arrays.asList(normalizedNames),
				aggregator,
				hasFeatures,
				true,	// resolveAliases
				logDebug);
		depList.setLabel(detail);

		if (logDebug) {
			msg = new LinkedList<String>();
			msg.add("%c" + Messages.RequireExpansionCompilerPass_7); //$NON-NLS-1$
			msg.add("color:blue"); //$NON-NLS-1$
			consoleDebugOutput.add(msg);
			StringBuffer sb = new StringBuffer();
			for (Map.Entry<String, String> entry : new ModuleDeps(depList.getExplicitDeps()).getModuleIdsWithComments().entrySet()) {
				sb.append("\t" + entry.getKey() + " (" + entry.getValue() + ")\r\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			msg = new LinkedList<String>();
			msg.add("%c" + sb.toString()); //$NON-NLS-1$
			msg.add("font-size:x-small"); //$NON-NLS-1$
			consoleDebugOutput.add(msg);
		}
		ModuleDeps filter = new ModuleDeps(depList.getExplicitDeps());
		for (String exclude : IDependencies.excludes) {
			filter.add(exclude, new ModuleDepInfo(null, (BooleanTerm)null, null));
		}
		int i = 0;
		for (DependencyList encDep : enclosingDependencies) {
			if (logDebug) {
				msg = new LinkedList<String>();
				msg.add("%c" + MessageFormat.format( //$NON-NLS-1$
						(i++ == 0) ?
								Messages.RequireExpansionCompilerPass_8 :
									Messages.RequireExpansionCompilerPass_9,
									new Object[] {encDep.getLabel()}
						));
				msg.add("color:blue"); //$NON-NLS-1$
				consoleDebugOutput.add(msg);
				ModuleDeps depMap = new ModuleDeps(encDep.getExplicitDeps());
				depMap.addAll(encDep.getExpandedDeps());
				depMap.subtractAll(filter);
				StringBuffer sb = new StringBuffer();
				for (Map.Entry<String, String> entry : new ModuleDeps(depMap).getModuleIdsWithComments().entrySet()) {
					sb.append("\t" + entry.getKey() + " (" + entry.getValue() + ")\r\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
				msg = new LinkedList<String>();
				msg.add("%c" + sb.toString()); //$NON-NLS-1$
				msg.add("font-size:x-small"); //$NON-NLS-1$
				consoleDebugOutput.add(msg);
			}
			filter.addAll(encDep.getExplicitDeps());
			filter.addAll(encDep.getExpandedDeps());
			if (dependentFeatures != null) {
				dependentFeatures.addAll(encDep.getDependentFeatures());
			}
		}

		ModuleDeps expandedDeps = new ModuleDeps(depList.getExpandedDeps());

		if (logDebug) {
			msg = new LinkedList<String>();
			msg.add("%c" + Messages.RequireExpansionCompilerPass_10); //$NON-NLS-1$
			msg.add("color:blue"); //$NON-NLS-1$
			consoleDebugOutput.add(msg);
			StringBuffer sb = new StringBuffer();
			for (Map.Entry<String, String> entry : new ModuleDeps(expandedDeps).getModuleIdsWithComments().entrySet()) {
				sb.append("\t" + entry.getKey() + " (" + entry.getValue() + ")\r\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			}
			msg = new LinkedList<String>();
			msg.add("%c" + sb.toString()); //$NON-NLS-1$
			msg.add("font-size:x-small"); //$NON-NLS-1$
			consoleDebugOutput.add(msg);
		}
		// The list of dependencies to add to the require list is the items in
		// expanded deps that are not included in enclosingDependencies.
		if (dependentFeatures != null) {
			dependentFeatures.addAll(depList.getDependentFeatures());
		}

		/*
		 * Use LinkedHashMap to maintain ordering of expanded dependencies
		 * as some types of modules (i.e. css) are sensitive to the order
		 * that modules are required relative to one another.
		 */
		expandedDeps.subtractAll(filter);

		expandedDepsList.add(expandedDeps);
		if (expandedDeps.getModuleIds().size() > 0) {
			int listIndex = expandedDepsList.size()-1;
			if (logDebug) {
				msg = new LinkedList<String>();
				msg.add("%c" + Messages.RequireExpansionCompilerPass_11); //$NON-NLS-1$
				msg.add("color:blue"); //$NON-NLS-1$
				consoleDebugOutput.add(msg);
				StringBuffer sb = new StringBuffer("\t"); //$NON-NLS-1$
				sb.append(String.format(JavaScriptBuildRenderer.REQUIRE_EXPANSION_LOG_PLACEHOLDER_FMT, listIndex));
				sb.append("\r\n\r\n\r\n"); //$NON-NLS-1$
				msg = new LinkedList<String>();
				msg.add("%c" + sb.toString()); //$NON-NLS-1$
				msg.add("font-size:x-small"); //$NON-NLS-1$
				consoleDebugOutput.add(msg);
			}
			array.addChildrenToBack(Node.newString(String.format(JavaScriptBuildRenderer.REQUIRE_EXPANSION_PLACEHOLDER_FMT, listIndex)));
		}
		// Add the expanded dependencies we found in this require call
		// to the set of enclosing dependencies for all the child node
		enclosingDependencies.add(depList);
	}
}
