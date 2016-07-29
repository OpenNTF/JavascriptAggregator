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
package com.ibm.jaggr.core;

import com.ibm.jaggr.core.IExtensionInitializer.IExtensionRegistrar;

/**
 * Aggregator extensions implementing this interface will be treated as singletons when the
 * extensions are created declaratively (e.g. from plugin.xml). This means that only one instance of
 * the class will be created regardless of how many extension points use the class.
 * <p>
 * Note that the
 * {@link IExtensionInitializer#initialize(IAggregator, IAggregatorExtension, IExtensionRegistrar)}
 * method will still be called once for each extension point that is initialized (although, not
 * concurrently) so extensions implementing this interface need to take steps to avoid duplicating
 * work in their initializers.
 */
public interface IExtensionSingleton {}
