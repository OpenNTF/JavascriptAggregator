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

package com.ibm.jaggr.service.impl;

import java.util.Properties;

import org.eclipse.core.runtime.IExtension;

import com.ibm.jaggr.core.impl.BaseAggregatorExtension;

/**
 * This class extends {@code BaseAggregatorExtension} defined in
 * {@code jaggr-core} plugin. This class provides an additional constructor to
 * initialize objects of {@code IAggregatorExtension} interface which use
 * {@code IExtension} interface. Since {@code IExtension} is an interface for
 * OSGi environment, this class is present in {@code jaggr-service} plugin.
 * 
 */
public class AggregatorExtension extends BaseAggregatorExtension {

	/**
	 * Constructs a new AggregatorExtension object from an object instance and
	 * an {@link IExtension}
	 * 
	 * @param extension
	 *            The IExtension object
	 * @param instance
	 *            The instantiated object for this extension
	 * @param attributes
	 *            The attributes for this extension
	 */
	public AggregatorExtension(IExtension extension, Object instance,
			Properties attributes) {
		super(extension, instance, attributes);
		this.extensionPointId = extension.getExtensionPointUniqueIdentifier();
		this.uniqueId = extension.getUniqueIdentifier();
		this.contributorId = extension.getContributor().getName();
		this.instance = instance;
		this.attributes = attributes;
		validate();
	}

}
