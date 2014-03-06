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

package com.ibm.jaggr.core.impl.layer;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.PlatformServicesException;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.transport.AbstractHttpTransport;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.layer.ILayerListener.EventType;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.modulebuilder.ModuleBuildFuture;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.RequestUtil;
import com.ibm.jaggr.core.util.TypeUtil;

import java.io.IOException;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * Layer builder class used to aggregate the output from the list of
 * {@link ModuleBuildFuture}s into the response stream.
 */
public class LayerBuilder {
	private static final Logger log = Logger.getLogger(LayerBuilder.class.getName());
	final HttpServletRequest request;
	final List<ICacheKeyGenerator> keyGens;
	final IAggregator aggr;
	final IOptions options;
	final ModuleList moduleList;
	final IHttpTransport transport;
	final List<IModule> layerListenerModuleList;
	boolean hasErrors = false;

	/**
	 * Count of modules for the current contribution type (modules or required
	 * modules)
	 */
	int count = 0;

	/**
	 * Indicates the current contribution type. True if processing required
	 * modules. Required modules (or their dependencies) are specified by the
	 * {@link AbstractHttpTransport#DEPS_REQPARAM} query arg
	 */
	boolean required = false;

	/**
	 * @param request
	 *            The servlet request object
	 * @param keyGens
	 *            The list of cache key generators for the response
	 * @param moduleList
	 *            The list of modules in the layer
	 */
	LayerBuilder(HttpServletRequest request, List<ICacheKeyGenerator> keyGens,
			ModuleList moduleList) {
		this.request = request;
		this.keyGens = keyGens;
		this.moduleList = moduleList;
		aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		options = aggr.getOptions();
		transport = aggr.getTransport();
		this.layerListenerModuleList = Collections.unmodifiableList(moduleList.getModules());
	}

	/**
	 * Aggregates the readers associated with {@code futures} together with
	 * contributions from the transport into the response.
	 *
	 * @return The built layer
	 * @throws IOException
	 */
	String build() throws IOException {

		StringBuffer sb = new StringBuffer();
		Map<String, String> moduleCacheInfo = null;
		if (request.getAttribute(LayerImpl.LAYERCACHEINFO_PROPNAME) != null) {
			moduleCacheInfo = new HashMap<String, String>();
			request.setAttribute(IModuleCache.MODULECACHEINFO_PROPNAME, moduleCacheInfo);
		}
		Set<String> dependentFeatures = new HashSet<String>();
		request.setAttribute(ILayer.DEPENDENT_FEATURES, dependentFeatures);

		if (RequestUtil.isExplodeRequires(request)) {
			DependencyList depList = (DependencyList)request.getAttribute(LayerImpl.BOOTLAYERDEPS_PROPNAME);
			if (depList != null) {
				// Output require expansion logging
				sb.append(requireExpansionLogging(depList));
			}
		}

		String prologue = notifyLayerListeners(EventType.BEGIN_LAYER, request, null);
		if (prologue != null) {
			sb.append(prologue);
		}

		List<ModuleBuildFuture> futures = collectFutures(moduleList, request);

		addTransportContribution(request, transport, sb, LayerContributionType.BEGIN_RESPONSE, null);

		if (dependentFeatures.size() > 0) {
			moduleList.getDependentFeatures().addAll(dependentFeatures);
			dependentFeatures.clear();
		}
		// For each source file, add the build output to the string buffer
		for (ModuleBuildFuture future : futures) {
			processFuture(future, sb);

			if (dependentFeatures.size() > 0) {
				moduleList.getDependentFeatures().addAll(dependentFeatures);
				dependentFeatures.clear();
			}
		}

		if (count > 0) {
			addTransportContribution(request, transport, sb,
					required ? LayerContributionType.END_LAYER_MODULES : LayerContributionType.END_MODULES,
							required ? moduleList.getRequiredModules() : null);
		}
		addTransportContribution(request, transport, sb,
				LayerContributionType.END_RESPONSE, null);

		String epilogue = notifyLayerListeners(EventType.END_LAYER, request, null);
		if (epilogue != null) {
			sb.append(epilogue);
		}

		if (dependentFeatures.size() > 0) {
			moduleList.getDependentFeatures().addAll(dependentFeatures);
			dependentFeatures.clear();
		}

		return sb.toString();

	}

	/**
	 * Returns true if any of the module builds indicated an error
	 *
	 * @return true if there was an error in one of the module builds
	 */
	protected boolean hasErrors() {
		return hasErrors;
	}

	/**
	 * Adds the reader associated with the future, together with any transport
	 * contributions, to the response aggregation.
	 * <p>
	 * If the {@code ModuleBuildReader} obtained from the future specifies
	 * either before or after modules, then those modules are processed by
	 * recursively calling this function.
	 *
	 * @param future
	 *            The module build future
	 * @param sb
	 *            Output - the output buffer to write the processed future to
	 * @throws IOException
	 */
	protected void processFuture(ModuleBuildFuture future, StringBuffer sb)
			throws IOException {

		ModuleBuildReader reader;
		// get the build reader from the future
		try {
			reader = future.get();
		} catch (InterruptedException e) {
			throw new IOException(e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException)e.getCause();
			}
			throw new IOException(e.getCause());
		}
		ModuleSpecifier source = future.getModuleSpecifier();
		if (source == ModuleSpecifier.LAYER) {
			if (!required && count > 0) {
				addTransportContribution(request, transport, sb,
						LayerContributionType.END_MODULES, null);
				count = 0;
			}
			required = true;
		} else if (source == ModuleSpecifier.MODULES && required) {
			throw new IllegalStateException();
		}

		// Process any before modules by recursively calling this method
		if (!TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOADDMODULES_REQATTRNAME))) {
			for (ModuleBuildFuture beforeFuture : reader.getBefore()) {
				processFuture(beforeFuture, sb);
			}
		}
		// Add the cache key generator list to the result list
		List<ICacheKeyGenerator> keyGenList = reader.getCacheKeyGenerators();
		if (keyGenList != null) {
			keyGens.addAll(keyGenList);
		}

		if (count == 0) {
			addTransportContribution(request, transport, sb,
					required ? LayerContributionType.BEGIN_LAYER_MODULES : LayerContributionType.BEGIN_MODULES,
							required ? moduleList.getRequiredModules() : null);
		}
		String str = notifyLayerListeners(EventType.BEGIN_MODULE, request, future.getModule());
		if (str != null) {
			sb.append(str);
		}

		// Get the layer contribution from the transport
		// Note that we depend on the behavior that all non-required
		// modules in the module list appear before all required
		// modules in the iteration.
		LayerContributionType type;
		if (count == 0) {
			type = required ? LayerContributionType.BEFORE_FIRST_LAYER_MODULE : LayerContributionType.BEFORE_FIRST_MODULE;
		} else {
			type = required ? LayerContributionType.BEFORE_SUBSEQUENT_LAYER_MODULE : LayerContributionType.BEFORE_SUBSEQUENT_MODULE;
		}
		addTransportContribution(request, transport, sb, type, future.getModule().getModuleId());

		count++;
		// Add the reader to the result
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		sb.append(writer.toString());
		hasErrors |= reader.isError();

		// Add post-module transport contribution
		type = (source == ModuleSpecifier.LAYER || source == ModuleSpecifier.BUILD_ADDED && required) ?
				LayerContributionType.AFTER_LAYER_MODULE : LayerContributionType.AFTER_MODULE;
		addTransportContribution(request, transport, sb, type, future.getModule().getModuleId());

		// Process any after modules by recursively calling this method
		if (!TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOADDMODULES_REQATTRNAME))) {
			for (ModuleBuildFuture afterFuture : reader.getAfter()) {
				processFuture(afterFuture, sb);
			}
		}
	}

	/**
	 * Appends the layer contribution specified by {@code type}
	 * (contributed by the transport) to the string buffer.
	 *
	 * @param request
	 *            The http request object
	 * @param transport
	 *            The transport object
	 * @param sb
	 *            The string buffer to append to
	 * @param type
	 *            The layer contribution type
	 * @param arg
	 *            The argument value (see
	 *            {@link IHttpTransport#contributeLoaderExtensionJavaScript(String)}
	 */
	protected void addTransportContribution(
			HttpServletRequest request,
			IHttpTransport transport,
			StringBuffer sb,
			LayerContributionType type,
			Object arg) {

		String transportContrib = transport.getLayerContribution(
				request,
				type,
				arg
				);
		if (transportContrib != null) {
			sb.append(transportContrib);
		}
	}

	/**
	 * Dispatch the modules specified in the request to the module builders and
	 * collect the build futures returned by the builders into the returned
	 * list.
	 * @param moduleList
	 *            The list of modules in the layer
	 * @param request
	 *            The request object
	 * @return The list of {@link ModuleBuildFuture} objects.
	 * @throws IOException
	 */
	protected List<ModuleBuildFuture> collectFutures(ModuleList moduleList, HttpServletRequest request)
			throws IOException {

		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		List<ModuleBuildFuture> futures = new LinkedList<ModuleBuildFuture>();

		IModuleCache moduleCache = aggr.getCacheManager().getCache().getModules();

		// For each source file, add a Future<IModule.ModuleReader> to the list
		for(ModuleList.ModuleListEntry moduleListEntry : moduleList) {
			IModule module = moduleListEntry.getModule();
			Future<ModuleBuildReader> future = moduleCache.getBuild(request, module);
			futures.add(new ModuleBuildFuture(
					moduleListEntry.getModule(),
					future,
					moduleListEntry.getSource()
					));
		}
		return futures;
	}

	/**
	 * Calls the registered layer listeners.
	 *
	 * @param type
	 *            The type of notification (begin or end)
	 * @param request
	 *            The request object.
	 * @param module
	 *            the associated module reference, depending on the value of {@code type}.
	 * @return The string to include in the layer output
	 * @throws IOException
	 */
	protected String notifyLayerListeners(ILayerListener.EventType type, HttpServletRequest request, IModule module) throws IOException {
		StringBuffer sb = new StringBuffer();
		// notify any listeners that the config has been updated

		Object[] refs = null;
		if(aggr.getPlatformServices() != null){
			try {
				refs = aggr.getPlatformServices().getServiceReferences(ILayerListener.class.getName(),  "(name="+aggr.getName()+")"); //$NON-NLS-1$ //$NON-NLS-2$
			} catch (PlatformServicesException e) {
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
			}
			if (refs != null) {
				for (Object ref : refs) {
					ILayerListener listener = (ILayerListener)aggr.getPlatformServices().getService(ref);
					try {
						Set<String> dependentFeatures = new HashSet<String>();
						String str = listener.layerBeginEndNotifier(type, request,
								type == ILayerListener.EventType.BEGIN_MODULE ?
										Arrays.asList(new IModule[]{module}) : layerListenerModuleList,
										dependentFeatures);
						if (dependentFeatures.size() != 0) {
							moduleList.getDependentFeatures().addAll(dependentFeatures);
						}
						if (str != null) {
							sb.append(str);
						}
					} finally {
						aggr.getPlatformServices().ungetService(ref);
					}
				}
			}
		}
		return sb.length() != 0 ? sb.toString() : null;
	}

	protected String requireExpansionLogging(DependencyList depList) throws IOException {
		StringBuffer sb = new StringBuffer();
		sb.append("console.log(\"%c") //$NON-NLS-1$
		.append(MessageFormat.format(Messages.LayerImpl_6, new Object[]{request.getRequestURI()+"?"+request.getQueryString()})) //$NON-NLS-1$
		.append("\", \"color:blue;background-color:yellow\");"); //$NON-NLS-1$
		sb.append("console.log(\"%c") //$NON-NLS-1$
		.append(Messages.LayerImpl_4)
		.append("\", \"color:blue\");") //$NON-NLS-1$
		.append("console.log(\"%c"); //$NON-NLS-1$
		for (Map.Entry<String, String> entry : new ModuleDeps(depList.getExplicitDeps()).getModuleIdsWithComments().entrySet()) {
			sb.append("\t" + entry.getKey() + " (" + entry.getValue() + ")\\r\\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		sb.append("\", \"font-size:x-small\");"); //$NON-NLS-1$
		sb.append("console.log(\"%c") //$NON-NLS-1$
		.append(Messages.LayerImpl_5)
		.append("\", \"color:blue\");") //$NON-NLS-1$
		.append("console.log(\"%c"); //$NON-NLS-1$
		for (Map.Entry<String, String> entry : new ModuleDeps(depList.getExpandedDeps()).getModuleIdsWithComments().entrySet()) {
			sb.append("\t" + entry.getKey() + " (" + entry.getValue() + ")\\r\\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		sb.append("\", \"font-size:x-small\");"); //$NON-NLS-1$
		return sb.toString();
	}
}
