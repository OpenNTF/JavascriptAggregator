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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
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

import javax.servlet.http.HttpServletRequest;


import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.impl.PlatformAggregatorFactory;
import com.ibm.jaggr.core.layer.ILayerListener;
import com.ibm.jaggr.core.layer.ILayerListener.EventType;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.module.IModuleCache;
import com.ibm.jaggr.core.module.ModuleSpecifier;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.BuildListReader;
import com.ibm.jaggr.core.readers.ModuleBuildReader;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.transport.IHttpTransport.LayerContributionType;
import com.ibm.jaggr.core.util.TypeUtil;

/**
 * Layer builder class used to aggregate the output from the list of
 * {@link ModuleBuildFuture}s into the {@link BuildListReader} for the response
 */
public class LayerBuilder {
	final HttpServletRequest request;
	final List<ICacheKeyGenerator> keyGens;
	final IAggregator aggr;
	final IOptions options;
	final ModuleList moduleList;
	final IHttpTransport transport;
	final List<IModule> layerListenerModuleList;

	/**
	 * Count of modules for the current contribution type (modules or required
	 * modules)
	 */
	int count = 0;
	
	/**
	 * Indicates the current contribution type. True if processing required
	 * modules. Required modules (or their dependencies) specified by the
	 * {@link IHttpTransport#REQUIRED_REQATTRNAME} query arg
	 */
	boolean required = false;	
	
	/**
	 * @param futures
	 *            List of {@link ModuleBuildFutures}
	 * @param requiredModules
	 *            Set of modules specified by the required query arg
	 * @param request
	 *            The servlet request object
	 * @param keyGens
	 *            The list of cache key generators for the response
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
	 * @return The response reader
	 * @throws IOException
	 */
	BuildListReader build() throws IOException {
		
        Map<String, String> moduleCacheInfo = null;
        if (request.getAttribute(LayerImpl.LAYERCACHEINFO_PROPNAME) != null) {
        	moduleCacheInfo = new HashMap<String, String>();
        	request.setAttribute(IModuleCache.MODULECACHEINFO_PROPNAME, moduleCacheInfo);
        }

        List<ModuleBuildReader> readerList = new LinkedList<ModuleBuildReader>();
		
        String prologue = notifyLayerListeners(EventType.BEGIN_LAYER, request, null);
		if (prologue != null) {
			readerList.add(new ModuleBuildReader(prologue));
		}
        
		List<ModuleBuildFuture> futures = collectFutures(moduleList, request);

		addTransportContribution(request, transport, readerList, LayerContributionType.BEGIN_RESPONSE, null);

		// For each source file, add a Future<IModule.ModuleReader> to the list 
		for (ModuleBuildFuture future : futures) {
			processFuture(future, readerList);
		}
		
		if (count > 0) {
			addTransportContribution(request, transport, readerList, 
					required ? LayerContributionType.END_REQUIRED_MODULES : LayerContributionType.END_MODULES,
					required ? moduleList.getRequiredModules() : null);
		}
		addTransportContribution(request, transport, readerList, 
				LayerContributionType.END_RESPONSE, null);
		
		/*
		 * Add an epilogue reader.  Note that we can't call the layer listeners here
		 * because not all the module builders have necessarily completed.  Instead,
		 * we add an epilogue reader that will call the listeners once the build output
		 * of all the builders have been read.
		 */
		readerList.add(new ModuleBuildReader(new EpilogueReader()));
		
		return new BuildListReader(readerList);
		
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
	 * @throws IOException
	 */
	private void processFuture(ModuleBuildFuture future, List<ModuleBuildReader> readerList)
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
		if (source == ModuleSpecifier.REQUIRED) {
			if (!required && count > 0) {
    			addTransportContribution(request, transport, readerList, 
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
				processFuture(beforeFuture, readerList);
			}
		}		
		// Add the cache key generator list to the result list
		List<ICacheKeyGenerator> keyGenList = reader.getCacheKeyGenerators();
		if (keyGenList != null) {
	    	keyGens.addAll(keyGenList);
		}
		
		if (count == 0) {
			addTransportContribution(request, transport, readerList, 
					required ? LayerContributionType.BEGIN_REQUIRED_MODULES : LayerContributionType.BEGIN_MODULES, 
					required ? moduleList.getRequiredModules() : null);
		}
		
        String interlude = notifyLayerListeners(EventType.BEGIN_MODULE, request, future.getModule());
		if (interlude != null) {
			readerList.add(new ModuleBuildReader(interlude));
		}
		
		// Get the layer contribution from the transport
		// Note that we depend on the behavior that all non-required
		// modules in the module list appear before all required 
		// modules in the iteration.
		LayerContributionType type;
		if (count == 0) {
			type = required ? LayerContributionType.BEFORE_FIRST_REQUIRED_MODULE : LayerContributionType.BEFORE_FIRST_MODULE;
		} else {
			type = required ? LayerContributionType.BEFORE_SUBSEQUENT_REQUIRED_MODULE : LayerContributionType.BEFORE_SUBSEQUENT_MODULE;
		}
		addTransportContribution(request, transport, readerList, type, future.getModule().getModuleId());
		
		count++;
		// Add the reader to the result
		readerList.add(reader);
		
		// Add post-module transport contribution
		type = (source == ModuleSpecifier.REQUIRED || source == ModuleSpecifier.BUILD_ADDED && required) ? 
				LayerContributionType.AFTER_REQUIRED_MODULE : LayerContributionType.AFTER_MODULE;
		addTransportContribution(request, transport, readerList, type, future.getModule().getModuleId());
		
		// Process any after modules by recursively calling this method
		if (!TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOADDMODULES_REQATTRNAME))) {
			for (ModuleBuildFuture afterFuture : reader.getAfter()) {
				processFuture(afterFuture, readerList);
			}
		}
	}

	/**
	 * Appends the reader for the layer contribution specified by {@code type}
	 * (contributed by the transport) to the end of {@code readerList}.
	 * 
	 * @param request
	 *            The http request object
	 * @param transport
	 *            The transport object
	 * @param readerList
	 *            The reader list to append to
	 * @param type
	 *            The layer contribution type
	 * @param arg
	 *            The argument value (see
	 *            {@link IHttpTransport#contributeLoaderExtensionJavaScript(String)}
	 */
	protected void addTransportContribution(
			HttpServletRequest request,
			IHttpTransport transport, 
			List<ModuleBuildReader> readerList, 
			LayerContributionType type,
			Object arg) {

		String transportContrib = transport.getLayerContribution(
				request, 
				type, 
				arg
		);
		if (transportContrib != null) {
    		readerList.add(
   				new ModuleBuildReader(transportContrib)
        	);
		}
	}
	
	/**
	 * Dispatch the modules specified in the request to the module builders and
	 * collect the build futures returned by the builders into the returned
	 * list.
	 * 
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
	 * @param type The type of notification (begin or end)
	 * @param req The request object.
	 * @throws IOException
	 */
	protected String notifyLayerListeners(ILayerListener.EventType type, HttpServletRequest request, IModule module) throws IOException {		
		StringBuffer sb = new StringBuffer();
		// notify any listeners that the config has been updated
		
		Object[] refs;		
		refs = PlatformAggregatorFactory.getPlatformAggregator().getServiceReferences(ILayerListener.class.getName(),  "(name="+aggr.getName()+")");
		
		if (refs != null) {
			for (Object ref : refs) {
				ILayerListener listener = (ILayerListener)PlatformAggregatorFactory.getPlatformAggregator().getService(ref);
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
					PlatformAggregatorFactory.getPlatformAggregator().ungetService(ref);
				}
			}
		}
		return sb.length() != 0 ? sb.toString() : null;
	}
	
	private class EpilogueReader extends Reader {
		private boolean initialized = false;
		private StringReader reader = null;
		
		private void initialize() throws IOException {
			if (!initialized) {
				initialized = true;
				String epilogue = notifyLayerListeners(EventType.END_LAYER, request, null);
				reader = new StringReader(epilogue != null ? epilogue : ""); //$NON-NLS-1$
			}
		}
		
		@Override
		public int read(char[] cbuf, int off, int len) throws IOException {
			initialize();
			return reader.read(cbuf, off, len);
		}

		@Override
		public void close() throws IOException {
			initialize();
			reader.close();
		}
	}
}
