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

package com.ibm.jaggr.service.impl.layer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.module.ModuleSpecifier;
import com.ibm.jaggr.service.options.IOptions;
import com.ibm.jaggr.service.readers.BuildListReader;
import com.ibm.jaggr.service.readers.ModuleBuildReader;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.transport.IHttpTransport.LayerContributionType;
import com.ibm.jaggr.service.util.TypeUtil;

/**
 * Layer builder class used to aggregate the output from the list of
 * {@link ModuleBuildFuture}s into the {@link BuildListReader} for the response
 */
public class LayerBuilder {
    public static final String PREAMBLEFMT = "\n/*-------- %s --------*/\n"; //$NON-NLS-1$

	final HttpServletRequest request;
	final List<ICacheKeyGenerator> keyGens;
	final IAggregator aggr;
	final IOptions options;
	final Set<String> requiredModules;
	final IHttpTransport transport;

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
			Set<String> requiredModules) {
		this.request = request;
		this.keyGens = keyGens;
		this.requiredModules = requiredModules;
		aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		options = aggr.getOptions();
		transport = aggr.getTransport();
	}
	
	/**
	 * Aggregates the readers associated with {@code futures} together with
	 * contributions from the transport into the response.
	 * 
	 * @return The response reader
	 * @throws IOException
	 */
	BuildListReader build(List<ModuleBuildFuture> futures) throws IOException {
		
		List<ModuleBuildReader> readerList = new LinkedList<ModuleBuildReader>();
        
		// Add the application specified notice to the beginning of the response
        String notice = aggr.getConfig().getNotice();
        if (notice != null) {
			readerList.add(
				new ModuleBuildReader(notice + "\r\n") //$NON-NLS-1$
			);
        }
        // If development mode is enabled, say so
		if (options.isDevelopmentMode() || options.isDebugMode()) {
			readerList.add(
				new ModuleBuildReader("/* " + //$NON-NLS-1$
						(options.isDevelopmentMode() ? Messages.LayerImpl_1 : Messages.LayerImpl_2) +
						" */\r\n") //$NON-NLS-1$ 
			);
		}
		
		addTransportContribution(request, transport, readerList, LayerContributionType.BEGIN_RESPONSE, null);
		// For each source file, add a Future<IModule.ModuleReader> to the list 
		for (ModuleBuildFuture future : futures) {
			processFuture(future, readerList);
		}
		if (count > 0) {
			if (required) {
				addTransportContribution(request, transport, readerList, 
					LayerContributionType.END_REQUIRED_MODULES,
					requiredModules);
			} else {
				addTransportContribution(request, transport, readerList, 
						LayerContributionType.END_MODULES, null);
			}
		}
		addTransportContribution(request, transport, readerList, 
				LayerContributionType.END_RESPONSE, null);
		
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
		
		// Include the filename preamble if requested.
		if ((options.isDebugMode() || options.isDevelopmentMode()) && TypeUtil.asBoolean(request.getAttribute(IHttpTransport.SHOWFILENAMES_REQATTRNAME))) {
			readerList.add(
				new ModuleBuildReader(String.format(PREAMBLEFMT, future.getResource().getURI().toString()))
			);
		}
		// Get the layer contribution from the transport
		// Note that we depend on the behavior that all non-required
		// modules in the module list appear before all required 
		// modules in the iteration.
		if (!required) {
			if (count == 0) {
				addTransportContribution(request, transport, readerList, 
						LayerContributionType.BEGIN_MODULES, null);
				addTransportContribution(request, transport, readerList, 
						LayerContributionType.BEFORE_FIRST_MODULE, 
						future.getModuleId());
			} else {	        			
				addTransportContribution(request, transport, readerList, 
						LayerContributionType.BEFORE_SUBSEQUENT_MODULE, 
						future.getModuleId());
			}
			count++;
		} else {
			if (count == 0) {
				addTransportContribution(request, transport, readerList, 
						LayerContributionType.BEGIN_REQUIRED_MODULES,
						requiredModules);
				addTransportContribution(request, transport, readerList, 
						LayerContributionType.BEFORE_FIRST_REQUIRED_MODULE, 
						future.getModuleId());
			} else {	        			
				addTransportContribution(request, transport, readerList, 
						LayerContributionType.BEFORE_SUBSEQUENT_REQUIRED_MODULE, 
						future.getModuleId());
			}
			count++;
		}
		// Add the reader to the result
		readerList.add(reader);
		
		// Add post-module transport contribution
		if (source == ModuleSpecifier.MODULES || source == ModuleSpecifier.BUILD_ADDED && !required) {
			addTransportContribution(request, transport, readerList, 
					LayerContributionType.AFTER_MODULE, future.getModuleId());
		} else if (source == ModuleSpecifier.REQUIRED || source == ModuleSpecifier.BUILD_ADDED && required) {
			addTransportContribution(request, transport, readerList, 
					LayerContributionType.AFTER_REQUIRED_MODULE, future.getModuleId()); 
		}
		
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
	
}
