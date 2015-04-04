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
import com.ibm.jaggr.core.IServiceReference;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.PlatformServicesException;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.module.NotFoundModule;
import com.ibm.jaggr.core.layer.ILayer;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.layer.ILayerListener.EventType;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.modulebuilder.ModuleBuildFuture;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType;
import com.ibm.jaggr.core.transport.IHttpTransport.ModuleInfo;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.RequestUtil;

import java.io.IOException;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

/**
 * Layer builder class used to aggregate the output from the list of
 * {@link ModuleBuildFuture}s into the response stream.
 */
public class LayerBuilder {
	private static final String sourceClass = LayerBuilder.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);
	final HttpServletRequest request;
	final List<ICacheKeyGenerator> keyGens;
	final IAggregator aggr;
	final IOptions options;
	final ModuleList moduleList;
	final IHttpTransport transport;
	final List<IModule> layerListenerModuleList;
	final Set<String> dependentFeatures;

	boolean hasErrors = false;

	boolean built = false;

	/**
	 * List of error message from build errors
	 */
	final List<String> errorMessages;

	final List<String> nonErrorMessages;

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
		errorMessages = new ArrayList<String>();
		nonErrorMessages = new ArrayList<String>();
		aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		options = aggr.getOptions();
		transport = aggr.getTransport();
		this.layerListenerModuleList = Collections.unmodifiableList(moduleList.getModules());
		dependentFeatures = new HashSet<String>();
	}

	/**
	 * Aggregates the readers associated with {@code futures} together with
	 * contributions from the transport into the response.
	 *
	 * @return The built layer
	 * @throws IOException
	 */
	String build() throws IOException {

		if (built) {
			// Can call build only once per instance
			throw new IllegalStateException();
		}
		built = true;

		StringBuffer sb = new StringBuffer();
		Map<String, String> moduleCacheInfo = null;
		if (request.getAttribute(LayerImpl.LAYERCACHEINFO_PROPNAME) != null) {
			moduleCacheInfo = new HashMap<String, String>();
			request.setAttribute(IModuleCache.MODULECACHEINFO_PROPNAME, moduleCacheInfo);
		}

		if (RequestUtil.isRequireExpLogging(request)) {
			DependencyList depList = (DependencyList)request.getAttribute(LayerImpl.EXCLUDEDEPS_PROPNAME);
			if (depList != null) {
				// Output require expansion logging
				sb.append(requireExpansionLogging(depList));
			}
		}

		SortedReaders sorted = new SortedReaders(collectFutures(moduleList, request), request);

		/*
		 * Set layer dependent features attribute.  The build readers add the layer dependent features
		 * to this collection as they are read.
		 */
		request.setAttribute(ILayer.DEPENDENT_FEATURES, dependentFeatures);

		sb.append(notifyLayerListeners(EventType.BEGIN_LAYER, request, null));
		addTransportContribution(sb, LayerContributionType.BEGIN_RESPONSE, null);

		// Add script files to the layer first first.  Scripts have no transport contribution
		for (ModuleBuildReader reader : sorted.getScripts().values()) {
			processReader(reader, sb);
		}

		sb.append(notifyLayerListeners(EventType.BEGIN_AMD, request, null));

		// Now add the loader cache entries.
		if (sorted.getCacheEntries().size() > 0) {
			addTransportContribution(sb, LayerContributionType.BEGIN_LAYER_MODULES, moduleList.getRequiredModules());
			int i = 0;
			for (Map.Entry<IModule, ModuleBuildReader> entry : sorted.getCacheEntries().entrySet()) {
				sb.append(notifyLayerListeners(EventType.BEGIN_MODULE, request, entry.getKey()));
				ModuleInfo info = new ModuleInfo(entry.getKey().getModuleId(), entry.getValue().isScript());
				LayerContributionType type = (i++ == 0) ? LayerContributionType.BEFORE_FIRST_LAYER_MODULE : LayerContributionType.BEFORE_SUBSEQUENT_LAYER_MODULE;
				addTransportContribution(sb, type, info);
				processReader(entry.getValue(), sb);
				addTransportContribution(sb, LayerContributionType.AFTER_LAYER_MODULE, info);
			}
			addTransportContribution(sb, LayerContributionType.END_LAYER_MODULES, moduleList.getRequiredModules());
		}

		// Now add the loader requested modules
		if (sorted.getModules().size() > 0) {
			addTransportContribution(sb, LayerContributionType.BEGIN_MODULES, null);
			int i = 0;
			for (Map.Entry<IModule, ModuleBuildReader> entry : sorted.getModules().entrySet()) {
				sb.append(notifyLayerListeners(EventType.BEGIN_MODULE, request, entry.getKey()));
				ModuleInfo info = new ModuleInfo(entry.getKey().getModuleId(), entry.getValue().isScript());
				LayerContributionType type = (i++ == 0) ? LayerContributionType.BEFORE_FIRST_MODULE : LayerContributionType.BEFORE_SUBSEQUENT_MODULE;
				addTransportContribution(sb, type, info);
				processReader(entry.getValue(), sb);
				addTransportContribution(sb, LayerContributionType.AFTER_MODULE, info);
			}
			addTransportContribution(sb, LayerContributionType.END_MODULES, null);
		}
 		sb.append(notifyLayerListeners(EventType.END_LAYER, request, null));
		addTransportContribution(sb, LayerContributionType.END_RESPONSE, null);

		moduleList.getDependentFeatures().addAll(dependentFeatures);

		// Output any messages to the console if debug mode is enabled
		if (options.isDebugMode() || options.isDevelopmentMode()) {
			for (String errorMsg : errorMessages) {
				sb.append("\r\nconsole.error(\"" + errorMsg + "\");"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			for (String msg : nonErrorMessages) {
				sb.append("\r\nconsole.warn(\"" + msg + "\");"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		return sb.toString();
	}

	/**
	 * Returns true if any of the module builds indicated an error
	 *
	 * @return true if there was an error in one of the module builds
	 */
	protected boolean hasErrors() {
		return !errorMessages.isEmpty();
	}

	/**
	 * Adds the content from {@code reader}, together with any transport contributions,
	 * to the response aggregation.
	 *
	 * @param reader
	 *            The module build reader
	 * @param sb
	 *            Output - the output buffer to accept the content from {@code reader}
	 * @throws IOException
	 */
	protected void processReader(ModuleBuildReader reader, StringBuffer sb) throws IOException {
		// Add the cache key generator list to the result list
		List<ICacheKeyGenerator> keyGenList = reader.getCacheKeyGenerators();
		if (keyGenList != null) {
			keyGens.addAll(keyGenList);
		}

		// Add the reader contents to the result
		StringWriter writer = new StringWriter();
		CopyUtil.copy(reader, writer);
		sb.append(writer.toString());
		if (reader.isError()) {
			errorMessages.add(reader.getErrorMessage());
		}
	}

	/**
	 * Appends the layer contribution specified by {@code type}
	 * (contributed by the transport) to the string buffer.
	 *
	 * @param sb
	 *            The string buffer to append to
	 * @param type
	 *            The layer contribution type
	 * @param arg
	 *            The argument value (see
	 *            {@link IHttpTransport#contributeLoaderExtensionJavaScript(String)}
	 */
	protected void addTransportContribution(
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

		final String sourceMethod = "collectFutures"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{moduleList, request});
		}
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		List<ModuleBuildFuture> futures = new LinkedList<ModuleBuildFuture>();

		IModuleCache moduleCache = aggr.getCacheManager().getCache().getModules();

		// For each source file, add a Future<IModule.ModuleReader> to the list
		for(ModuleList.ModuleListEntry moduleListEntry : moduleList) {
			IModule module = moduleListEntry.getModule();
			Future<ModuleBuildReader> future = null;
			try {
				future = moduleCache.getBuild(request, module);
			} catch (NotFoundException e) {
				if (log.isLoggable(Level.FINER)) {
					log.logp(Level.FINER, sourceClass, sourceMethod, moduleListEntry.getModule().getModuleId() + " not found."); //$NON-NLS-1$
				}
				future = new NotFoundModule(module.getModuleId(), module.getURI()).getBuild(request);
				if (!moduleListEntry.isServerExpanded()) {
					// Treat as error.  Return error response, or if debug mode, then include
					// the NotFoundModule in the response.
					if (options.isDevelopmentMode() || options.isDebugMode()) {
						// Don't cache responses containing error modules.
						request.setAttribute(ILayer.NOCACHE_RESPONSE_REQATTRNAME, Boolean.TRUE);
					} else {
						// Rethrow the exception to return an error response
						throw e;
					}
				} else {
					// Not an error if we're doing server-side expansion, but if debug mode
					// then get the error message from the completed future so that we can output
					// a console warning in the browser.
					if (options.isDevelopmentMode() || options.isDebugMode()) {
						try {
							nonErrorMessages.add(future.get().getErrorMessage());
						} catch (Exception ex) {
							// Sholdn't ever happen as this is a CompletedFuture
							throw new RuntimeException(ex);
						}
					}
					if (isTraceLogging) {
						log.logp(Level.FINER, sourceClass, sourceMethod, "Ignoring exception for server expanded module " + e.getMessage(), e); //$NON-NLS-1$
					}
					// Don't add the future for the error module to the response.
					continue;
				}
			} catch (UnsupportedOperationException ex) {
				// Missing resource factory or module builder for this resource
				if (moduleListEntry.isServerExpanded()) {
					// ignore the error if it's a server expanded module
					if (isTraceLogging) {
						log.logp(Level.FINER, sourceClass, sourceMethod, "Ignoring exception for server expanded module " + ex.getMessage(), ex); //$NON-NLS-1$
					}
					continue;
				} else {
					// re-throw the exception
					throw ex;
				}
			}
			futures.add(new ModuleBuildFuture(
					module,
					future,
					moduleListEntry.getSource()
			));
		}
		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod, futures);
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

		List<IServiceReference> refs = null;
		if(aggr.getPlatformServices() != null){
			try {
				IServiceReference[] ary = aggr.getPlatformServices().getServiceReferences(ILayerListener.class.getName(),  "(name="+aggr.getName()+")"); //$NON-NLS-1$ //$NON-NLS-2$
				if (ary != null) refs = Arrays.asList(ary);
			} catch (PlatformServicesException e) {
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, e.getMessage(), e);
				}
			}
			if (refs != null) {
				if (type == EventType.END_LAYER) {
					// for END_LAYER, invoke the listeners in reverse order
					refs = new ArrayList<IServiceReference>(refs);
					Collections.reverse(refs);
				}
				for (IServiceReference ref : refs) {
					ILayerListener listener = (ILayerListener)aggr.getPlatformServices().getService(ref);
					try {
						Set<String> depFeatures = new HashSet<String>();
						String str = listener.layerBeginEndNotifier(type, request,
								type == ILayerListener.EventType.BEGIN_MODULE ?
										Arrays.asList(new IModule[]{module}) : layerListenerModuleList,
										depFeatures);
						dependentFeatures.addAll(depFeatures);
						if (str != null) {
							sb.append(str);
						}
					} finally {
						aggr.getPlatformServices().ungetService(ref);
					}
				}
			}
		}
		return sb.toString();
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

	// For unit testing
	Set<String> getDepenedentFeatures() {
		return dependentFeatures;
	}
}
