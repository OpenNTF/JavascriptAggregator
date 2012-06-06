/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.ibm.jaggr.service.impl.config.ConfigTests;
import com.ibm.jaggr.service.impl.deps.DepTreeNodeTests;
import com.ibm.jaggr.service.impl.deps.DepUtilsTest;
import com.ibm.jaggr.service.impl.deps.DependenciesTest;
import com.ibm.jaggr.service.impl.deps.HasNodeTests;
import com.ibm.jaggr.service.impl.layer.LayerTest;
import com.ibm.jaggr.service.impl.modulebuilder.css.CSSModuleBuilderTest;
import com.ibm.jaggr.service.impl.modulebuilder.i18n.I18nModuleBuilderTest;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.ExportModuleNameCompilerPassTest;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.HasFilteringCompilerPassTest;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.JsModuleContentProviderTest;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.RequireExpansionCompilerPassTest;
import com.ibm.jaggr.service.impl.modulebuilder.text.TxtModuleContentProviderTest;
import com.ibm.jaggr.service.impl.transport.DojoHttpTransportTest;

@RunWith(Suite.class)
@Suite.SuiteClasses(value = { 
	ConfigTests.class,
	DependenciesTest.class,
	DepTreeNodeTests.class,
	DepUtilsTest.class,
	HasNodeTests.class,
	LayerTest.class,
	CSSModuleBuilderTest.class,
	I18nModuleBuilderTest.class,
	ExportModuleNameCompilerPassTest.class,
	HasFilteringCompilerPassTest.class,
	JsModuleContentProviderTest.class,
	TxtModuleContentProviderTest.class,
	RequireExpansionCompilerPassTest.class,
	DojoHttpTransportTest.class
})
public class EverythingSuite {

}
