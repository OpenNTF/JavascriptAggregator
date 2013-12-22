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

package com.ibm.jaggr.service;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.ibm.jaggr.core.deps.ModuleDepInfoTest;
import com.ibm.jaggr.core.deps.ModuleDepsTest;
import com.ibm.jaggr.core.impl.cache.CacheManagerImplTest;
import com.ibm.jaggr.core.impl.config.ConfigTests;
import com.ibm.jaggr.core.impl.deps.BooleanFormulaTest;
import com.ibm.jaggr.core.impl.deps.DepTreeNodeTests;
import com.ibm.jaggr.core.impl.deps.DepUtilsTest;
import com.ibm.jaggr.core.impl.deps.DependenciesTest;
import com.ibm.jaggr.core.impl.deps.HasNodeTests;
import com.ibm.jaggr.core.impl.layer.LayerBuilderTest;
import com.ibm.jaggr.core.impl.layer.LayerCacheTest;
import com.ibm.jaggr.core.impl.layer.LayerTest;
import com.ibm.jaggr.core.impl.module.ModuleImplTest;
import com.ibm.jaggr.core.impl.modulebuilder.css.CSSModuleBuilderTest;
import com.ibm.jaggr.core.impl.modulebuilder.i18n.I18nModuleBuilderTest;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.ExportModuleNameCompilerPassTest;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.HasFilteringCompilerPassTest;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.JavaScriptModuleBuilderTest;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.RequireExpansionCompilerPassTest;
import com.ibm.jaggr.core.impl.modulebuilder.text.TxtModuleContentProviderTest;
import com.ibm.jaggr.core.impl.resource.FileResourceTests;
import com.ibm.jaggr.core.impl.transport.AbstractHttpTransportTest;
import com.ibm.jaggr.service.impl.AggregatorImplTest;
import com.ibm.jaggr.service.impl.resource.BundleResourceFactoryTests;
import com.ibm.jaggr.service.impl.transport.DojoHttpTransportTest;

@RunWith(Suite.class)
@Suite.SuiteClasses(value = { 
	BooleanFormulaTest.class,
	ConfigTests.class,
	DependenciesTest.class,
	DepTreeNodeTests.class,
	ModuleDepInfoTest.class,
	ModuleDepsTest.class,
	DepUtilsTest.class,
	HasNodeTests.class,
	LayerTest.class,
	LayerCacheTest.class,
	LayerBuilderTest.class,
	ModuleImplTest.class,
	CSSModuleBuilderTest.class,
	I18nModuleBuilderTest.class,
	ExportModuleNameCompilerPassTest.class,
	HasFilteringCompilerPassTest.class,
	JavaScriptModuleBuilderTest.class,
	TxtModuleContentProviderTest.class,
	RequireExpansionCompilerPassTest.class,
	AbstractHttpTransportTest.class,
	DojoHttpTransportTest.class,
	AggregatorImplTest.class,
	BundleResourceFactoryTests.class,
	FileResourceTests.class,
	CacheManagerImplTest.class
})
public class EverythingSuite { }
