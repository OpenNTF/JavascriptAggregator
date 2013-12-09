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

package com.ibm.jaggr.core;

import java.util.Properties;

import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.resource.IResourceFactory;

/**
 * Initialization interface for aggregator extensions. Extensions implementing
 * this interface will be called in the
 * {@link #initialize(IAggregator, IAggregatorExtension, IExtensionRegistrar)} 
 * initialize method immediately after the extension has been created.
 */
public interface IExtensionInitializer {

	/**
	 * Called immediately after the extension is created to set the aggregator
	 * instance that this extension object was created for.
	 * 
	 * @param aggregator
	 *            The aggregator that this extension was created for.
	 * @param extension
	 *            The extension object
	 * @param registrar
	 *            A registrar object that can be used to register additional
	 *            aggregator extendables
	 */
	void initialize(IAggregator aggregator, IAggregatorExtension extension, IExtensionRegistrar registrar);

	/**
	 * Interface that can be used by Aggregator extensions to register
	 * additional {@link IResourceFactory} or {@link IModuleBuilder} extensions.
	 * For a list of currently registered extensions, call
	 * {@link IAggregator#getResourceFactoryExtensions()} or 
	 * {@link IAggregator#getModuleBuilderExtensions()};
	 */
	public interface IExtensionRegistrar {

		/**
		 * Registers the specified extension, with the specified attributes and
		 * the specified unique id. See the schemas for the resourcefactory and
		 * modulebuilder extension points for the names and descriptions of
		 * attributes. Extensions registered through this method do not need to
		 * be in the eclipse extension registry.
		 * 
		 * @param impl
		 *            an object which implements {@link IResourceFactory} or
		 *            {@link IModuleBuilder}.
		 * @param attributes
		 *            the extension attributes.
		 * @param uniqueId
		 *            a unique identifier for the extension
		 * @param before
		 *            a reference to an already registered extension before
		 *            which {@code extension} should be placed in the iteration
		 *            order. If not specified, {@code extension} is added to the
		 *            end of the iteration order.
		 * @throws IllegalStateException
		 *             If there are missing required attributes for the
		 *             extension being registered.
		 */
		public void registerExtension(Object impl, Properties attributes,
				String extensionPointId, String uniqueId, IAggregatorExtension before)
				throws IllegalStateException;
	}

}
