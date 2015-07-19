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
import com.ibm.jaggr.core.modulebuilder.SourceMap;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType;
import com.ibm.jaggr.core.transport.IHttpTransport.ModuleInfo;
import com.ibm.jaggr.core.transport.IRequestedModuleNames;
import com.ibm.jaggr.core.util.DependencyList;
import com.ibm.jaggr.core.util.RequestUtil;

import com.google.debugging.sourcemap.SourceMapGeneratorV3;
import com.google.debugging.sourcemap.SourceMapParseException;

import org.apache.commons.io.IOUtils;
import org.apache.wink.json4j.JSONArray;
import org.apache.wink.json4j.JSONException;
import org.apache.wink.json4j.JSONObject;

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
	private final HttpServletRequest request;
	private final List<ICacheKeyGenerator> keyGens;
	private final IAggregator aggr;
	private final IOptions options;
	private final ModuleList moduleList;
	private final IHttpTransport transport;
	private final List<IModule> layerListenerModuleList;
	private final Set<String> dependentFeatures;
	private final SourceMapGeneratorV3 smGen;
	private final StringWriter writer;
	private boolean built = false;
	private int sectionCount = 0;
	private Map<String, String> sourcesMap;
	private String sourceMap = null;

	/**
	 * List of error message from build errors
	 */
	private final List<String> errorMessages;

	private final List<String> nonErrorMessages;

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

		// Source maps
		boolean isSourceMapsEnabled = RequestUtil.isSourceMapsEnabled(request);
		sourcesMap = isSourceMapsEnabled ? new HashMap<String, String>() : null;
		smGen = isSourceMapsEnabled ? new SourceMapGeneratorV3() : null;
		writer = isSourceMapsEnabled ? new LineCountingStringWriter() : new StringWriter();
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

		Map<String, String> moduleCacheInfo = null;
		if (request.getAttribute(LayerImpl.LAYERCACHEINFO_PROPNAME) != null) {
			moduleCacheInfo = new HashMap<String, String>();
			request.setAttribute(IModuleCache.MODULECACHEINFO_PROPNAME, moduleCacheInfo);
		}

		if (RequestUtil.isDependencyExpansionLogging(request)) {
			DependencyList depList = (DependencyList)request.getAttribute(LayerImpl.EXPANDEDDEPS_PROPNAME);
			if (depList != null) {
				// Output dependency expansion logging
				writer.append(dependencyExpansionLogging(depList));
			}
		}

		SortedReaders sorted = new SortedReaders(collectFutures(moduleList, request), request);

		/*
		 * Set layer dependent features attribute.  The build readers add the layer dependent features
		 * to this collection as they are read.
		 */
		request.setAttribute(ILayer.DEPENDENT_FEATURES, dependentFeatures);

		writer.append(notifyLayerListeners(EventType.BEGIN_LAYER, request, null));
		addTransportContribution(LayerContributionType.BEGIN_RESPONSE, null);

		// Add script files to the layer first.  Scripts have no transport contribution
		for (Map.Entry<IModule, ModuleBuildReader> entry : sorted.getScripts().entrySet()) {
			processReader(entry.getKey(), entry.getValue());
		}
		if (sorted.getCacheEntries().size() > 0 || sorted.getModules().size() > 0) {
			writer.append(notifyLayerListeners(EventType.BEGIN_AMD, request, null));

			// Now add the loader cache entries.
			if (sorted.getCacheEntries().size() > 0) {
				addTransportContribution(LayerContributionType.BEGIN_LAYER_MODULES, moduleList.getRequiredModules());
				int i = 0;
				for (Map.Entry<IModule, ModuleBuildReader> entry : sorted.getCacheEntries().entrySet()) {
					writer.append(notifyLayerListeners(EventType.BEGIN_MODULE, request, entry.getKey()));
					ModuleInfo info = new ModuleInfo(entry.getKey().getModuleId(), entry.getValue().isScript());
					LayerContributionType type = (i++ == 0) ? LayerContributionType.BEFORE_FIRST_LAYER_MODULE : LayerContributionType.BEFORE_SUBSEQUENT_LAYER_MODULE;
					addTransportContribution(type, info);
					processReader(entry.getKey(), entry.getValue());
					addTransportContribution(LayerContributionType.AFTER_LAYER_MODULE, info);
				}
				addTransportContribution(LayerContributionType.END_LAYER_MODULES, moduleList.getRequiredModules());
			}

			// Now add the loader requested modules
			if (sorted.getModules().size() > 0) {
				addTransportContribution(LayerContributionType.BEGIN_MODULES, null);
				int i = 0;
				for (Map.Entry<IModule, ModuleBuildReader> entry : sorted.getModules().entrySet()) {
					writer.append(notifyLayerListeners(EventType.BEGIN_MODULE, request, entry.getKey()));
					ModuleInfo info = new ModuleInfo(entry.getKey().getModuleId(), entry.getValue().isScript());
					LayerContributionType type = (i++ == 0) ? LayerContributionType.BEFORE_FIRST_MODULE : LayerContributionType.BEFORE_SUBSEQUENT_MODULE;
					addTransportContribution(type, info);
					processReader(entry.getKey(), entry.getValue());
					addTransportContribution(LayerContributionType.AFTER_MODULE, info);
				}
				addTransportContribution(LayerContributionType.END_MODULES, null);
			}
		}
 		writer.append(notifyLayerListeners(EventType.END_LAYER, request, null));
		addTransportContribution(LayerContributionType.END_RESPONSE, null);

		moduleList.getDependentFeatures().addAll(dependentFeatures);

		// Output any messages to the console if debug mode is enabled
		if (options.isDebugMode() || options.isDevelopmentMode()) {
			for (String errorMsg : errorMessages) {
				writer.append("\r\nconsole.error(\"" + errorMsg + "\");"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			for (String msg : nonErrorMessages) {
				writer.append("\r\nconsole.warn(\"" + msg + "\");"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}

		if (sectionCount > 0) {
			// If any of the modules in the layer contained source map info, then
			// create a source map for the layer and include the source mapping URL
			// in the response.
			writer.append("\n").append(getSourcesMappingEpilogue()); //$NON-NLS-1$
			addSourcesContentToSourceMap();
		}

		return writer.toString();
	}

	/**
	 * Returns the source mapping epilogue for the layer that gets added to the end of the response.
	 *
	 * @see <a
	 *      href="https://docs.google.com/document/d/1U1RGAehQwRypUTovF1KRlpiOFze0b-_2gc6fAH0KY0k/edit#heading=h.lmz475t4mvbx">Source
	 *      Map Revision 3 Proposal - Linking generated code to source maps</a>
	 *
	 * @return the source mapping eplilogue
	 */
	protected String getSourcesMappingEpilogue() {
		String root = ""; //$NON-NLS-1$
		String contextPath = request.getRequestURI();
		if (contextPath != null) {
			// We may be re-building a layer for a source map request where the layer
			// has been flushed from the cache.  If that's the case, then the context
			// path will already include the source map path component, so just remove it.
			if (contextPath.endsWith("/" + ILayer.SOURCEMAP_RESOURSE_PATHCOMP)) { //$NON-NLS-1$
				contextPath = contextPath.substring(0, contextPath.length()-(ILayer.SOURCEMAP_RESOURSE_PATHCOMP.length()+1));
			}
			// Because we're specifying a relative URL that is relative to the request for the
			// layer and aggregator paths are assumed to NOT include a trailing '/', then we
			// need to start the relative path with the last path component of the context
			// path so that the relative URL will be resolved correctly by the browser.  For
			// more details, see refer to the behavior of URI.resolve();
			int idx = contextPath.lastIndexOf("/"); //$NON-NLS-1$
			root = contextPath.substring(idx!=-1 ? idx+1 : 0);
			if (root.length() > 0 && !root.endsWith("/")) { //$NON-NLS-1$
				root += "/"; //$NON-NLS-1$
			}
		}
		StringBuffer sb = new StringBuffer();
		sb.append("//# sourceMappingURL=") //$NON-NLS-1$
		  .append(root)
	      .append(ILayer.SOURCEMAP_RESOURSE_PATHCOMP);
		String queryString = request.getQueryString();
		if (queryString != null) {
			sb.append("?").append(queryString); //$NON-NLS-1$
		}
		return sb.toString();
	}

	/**
	 * Add the sources content to the source map.  We need to do this because
	 * {@link SourceMapGeneratorV3#mergeMapSection(int, int, String)} does not
	 * preserve sourcesContent from the map being merged in the output map.
	 *
	 * @throws IOException
	 */
	private void addSourcesContentToSourceMap() throws IOException {
		StringWriter writer = new StringWriter();
		smGen.appendTo(writer, "" /* file name */); //$NON-NLS-1$
		sourceMap = writer.toString();
		// now add the sourcesContent field
		try {
			JSONObject obj = new JSONObject(sourceMap);
			JSONArray sources = (JSONArray)obj.get("sources"); //$NON-NLS-1$
			JSONArray sourcesContent = new JSONArray();
			for (int i = 0; i < sources.length(); i++) {
				String content = sourcesMap.get(sources.get(i));
				sourcesContent.add(content != null ? content : ""); //$NON-NLS-1$
			}
			obj.put("sourcesContent", sourcesContent); //$NON-NLS-1$
			sourceMap = obj.toString();
		} catch (JSONException e) {
			throw new IOException(e);
		}
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
	 * @param module
	 *            The module id
	 * @param reader
	 *            The module build reader
	 * @throws IOException
	 */
	protected void processReader(IModule module, ModuleBuildReader reader) throws IOException {
		// Add the cache key generator list to the result list
		List<ICacheKeyGenerator> keyGenList = reader.getCacheKeyGenerators();
		if (keyGenList != null) {
			keyGens.addAll(keyGenList);
		}
		if (smGen != null) {
			// If we're generating a source map, then merge the source map for the module
			// into the layer source map.
			SourceMap moduleSourceMap = reader.getSourceMap();
			if (moduleSourceMap != null && writer instanceof LineCountingStringWriter) {
				sectionCount++;
				LineCountingStringWriter lcWriter = (LineCountingStringWriter)writer;
				try {
					smGen.mergeMapSection(lcWriter.getLine(), lcWriter.getColumn(), moduleSourceMap.map);
				} catch (SourceMapParseException e) {
					throw new IOException(e);
				}
				// Save the sources content, indexed by the module name, so that we can
				// add the 'sourcesContent' property to the layer map after the map
				// has been generated.  We need to do this because SourceMapGeneratorV3
				// does not merge the sourcesContent properties from module source maps into
				// the merged layer source map.
				sourcesMap.put(moduleSourceMap.name, moduleSourceMap.source);
			}
		}
		// Add the reader contents to the result
		try {
			IOUtils.copy(reader, writer);
		} finally {
			IOUtils.closeQuietly(reader);
		}
		if (reader.isError()) {
			errorMessages.add(reader.getErrorMessage());
		}
	}

	/**
	 * Appends the layer contribution specified by {@code type}
	 * (contributed by the transport) to the string buffer.
	 *
	 * @param type
	 *            The layer contribution type
	 * @param arg
	 *            The argument value (see
	 *            {@link IHttpTransport#contributeLoaderExtensionJavaScript(String)}
	 * @throws IOException
	 */
	protected void addTransportContribution(
			LayerContributionType type,
			Object arg) throws IOException {

		String transportContrib = transport.getLayerContribution(
				request,
				type,
				arg
				);
		if (transportContrib != null) {
			writer.append(transportContrib);
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

	protected String dependencyExpansionLogging(DependencyList depList) throws IOException {

		StringBuffer sb = new StringBuffer();
		sb.append("console.log(\"%c") //$NON-NLS-1$
		.append(MessageFormat.format(Messages.LayerImpl_6, new Object[]{request.getRequestURI()+"?"+request.getQueryString()})) //$NON-NLS-1$
		.append("\", \"color:blue;background-color:yellow\");"); //$NON-NLS-1$
		IRequestedModuleNames reqNames = (IRequestedModuleNames)request.getAttribute(IHttpTransport.REQUESTEDMODULENAMES_REQATTRNAME);
		if (reqNames != null) {
			sb.append("console.log(\"%c") //$NON-NLS-1$
			.append(reqNames.toString(true))
			.append("\", \"color:blue;background-color:yellow\");"); //$NON-NLS-1$
		}
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

	String getSourceMap() {
		return sourceMap;
	}
}
