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

package com.ibm.jaggr.web.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.regex.Matcher;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.eclipse.core.runtime.CoreException;

import com.ibm.jaggr.core.InitParams;
import com.ibm.jaggr.core.InitParams.InitParam;
import com.ibm.jaggr.core.NotFoundException;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.executors.IExecutors;
import com.ibm.jaggr.core.impl.AggregatorExtension;
import com.ibm.jaggr.core.impl.OverrideFoldersTreeWalker;
import com.ibm.jaggr.core.impl.deps.DependenciesImpl;
import com.ibm.jaggr.core.impl.executors.ExecutorsImpl;
import com.ibm.jaggr.core.impl.options.OptionsImpl;
import com.ibm.jaggr.core.modulebuilder.IModuleBuilder;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResourceFactory;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.web.PlatformServicesImpl;
import com.ibm.jaggr.web.impl.config.ConfigImpl;

/**
 * Implementation for IAggregator and HttpServlet interfaces.
 * 
 * Note that despite the fact that HttpServlet (which this class extends)
 * implements Serializable, attempts to serialize instances of this class will
 * fail due to the fact that not all instance data is serializable. The
 * assumption is that because instances of this class are created by the OSGi
 * Framework, and the framework itself does not support serialization, then no
 * attempts will be made to serialize instances of this class.
 */
@SuppressWarnings("serial")
public class AggregatorImpl extends com.ibm.jaggr.core.impl.AbstractAggregatorImpl {

	protected static final String RESOURCE_FACTORY = "com.ibm.jaggr.resourcefactory"; //$NON-NLS-1$
	protected static final String DEFAULT_RESOURCEFACTORIES = "com.ibm.jaggr.core.default.resourcefactories"; //$NON-NLS-1$
	protected static final String MODULE_BUILDER = "com.ibm.jaggr.modulebuilder"; //$NON-NLS-1$
	protected static final String DEFAULT_MODULEBUILDERS = "com.ibm.jaggr.core.default.modulebuilders"; //$NON-NLS-1$
	protected static final String HTTP_TRANSPORT = "com.ibm.jaggr.httptransport"; //$NON-NLS-1$
	protected static final String DEFAULT_HTTPTRANSPORT = "com.ibm.jaggr.core.dojo.httptransport"; //$NON-NLS-1$

	protected ServletContext servletContext = null;
	protected ExecutorsImpl ex = null;
	protected OptionsImpl op = null;
	private File workdir = null;
	
	@Override
	public File getWorkingDirectory() {
		return workdir;
	}

	public void setInitializationData() {

		try {	
			
			servletContext = getServletContext();
			name = getAggregatorName();
			initParams = getInitParamsFromServletConfig();
			op = new OptionsImpl(true, this);
			ex = new ExecutorsImpl(op);
			workdir = initWorkingDirectory();
			initExtensions();
			IConfig config = newConfig();

			// Check last-modified times of resources in the overrides folders.
			// These resources
			// are considered to be dynamic in a production environment and we
			// want to
			// detect new/changed resources in these folders on startup so that
			// we can clear
			// caches, etc.
			OverrideFoldersTreeWalker walker = new OverrideFoldersTreeWalker(
					this, config);
			walker.walkTree();
			deps = newDependencies(walker.getLastModifiedJS());			
			cacheMgr = newCacheManager(walker.getLastModified());
			this.config = config;
			// Notify listeners
			notifyConfigListeners(1);

			// bundleContext.addBundleListener(this);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the name for this aggregator
	 * <p>
	 * This method is called during aggregator intialization. Subclasses may
	 * override this method to initialize the aggregator using a different name.
	 * Use the public {@link AggregatorImpl#getName()} method to get the name of
	 * an initialized aggregator.
	 * 
	 * @param configElem
	 *            The configuration element.
	 * @return The aggregator name
	 */

	public String getAggregatorName() {
		// trim leading and trailing '/'
		String alias = getInitParameter("alias");//$NON-NLS-1$
		while (alias.charAt(0) == '/')
			alias = alias.substring(1);
		while (alias.charAt(alias.length() - 1) == '/')
			alias = alias.substring(0, alias.length() - 1);
		return alias;
	}

	/**
	 * Returns the init params for this aggregator
	 * <p>
	 * This method is called during aggregator intialization. Subclasses may
	 * override this method to initialize the aggregator using different init
	 * params. Use the public {@link AggregatorImpl#getInitParams()} method to
	 * get the init params for an initialized aggregator.
	 * 
	 * @param configElem
	 *            The configuration element.
	 * @return The init params
	 */

	public InitParams getInitParamsFromServletConfig() {

		List<InitParam> initParams = new LinkedList<InitParam>();
		Enumeration<String> strs = getInitParameterNames();
		while (strs.hasMoreElements()) {
			String name = strs.nextElement(); 
			String value = getInitParameter(name); 
			if (!(name.equals("alias"))) { //$NON-NLS-1$
				initParams.add(new InitParam(name, value));
			}
		}
		return new InitParams(initParams);
	}

	/**
	 * Loads and initializes the resource factory, module builder and http
	 * transport extensions specified in the configuration element for this
	 * aggregator
	 * 
	 * @param configElem
	 *            The configuration element
	 * @throws CoreException
	 * @throws NotFoundException
	 */
	protected void initExtensions() throws NotFoundException {
		/*
		 * Init the resource factory extensions
		 */
		initResourceFactoryExtension();
		initModuleBuilderExtension();
		initHttpTransportExtension();

		ExtensionRegistrar reg = new ExtensionRegistrar();
		callExtensionInitializers(getExtensions(null), reg);
		reg.closeRegistration();
	}

	@Override
	public IOptions getOptions() {
		return op;
	}

	@Override
	public IExecutors getExecutors() {
		return ex;
	}
	
	
	public ServletContext getAggregatorServletContext(){
		return servletContext;
	}
	
	@Override
	protected IDependencies newDependencies(long stamp) {
		return new DependenciesImpl(this, stamp);
	}
	
	@Override
    public IDependencies getDependencies() {
    	return (IDependencies) deps;
    }

	@Override
	public String substituteProps(String str,
			SubstitutionTransformer transformer) {
		if (str == null) {
			return null;
		}
		StringBuffer buf = new StringBuffer();
		Matcher matcher = pattern.matcher(str);
		while (matcher.find()) {
			String propName = matcher.group(1);
			String propValue = null;
			propValue = System.getProperty(propName);
			if (propValue != null) {
				if (transformer != null) {
					propValue = transformer.transform(propName, propValue);
				}
				matcher.appendReplacement(buf, propValue.replace("\\", "\\\\") //$NON-NLS-1$ //$NON-NLS-2$
						.replace("$", "\\$") //$NON-NLS-1$ //$NON-NLS-2$
				);
			} else {
				matcher.appendReplacement(buf, "\\${" + propName + "}"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		matcher.appendTail(buf);
		return buf.toString();
	}

	/**
	 * Returns the working directory for this aggregator.
	 * <p>
	 * This method is called during aggregator intialization. Subclasses may
	 * override this method to initialize the aggregator using a different
	 * working directory. Use the public {@link #getWorkingDirectory()} method
	 * to get the working directory from an initialized aggregator.
	 * 
	 * @param configElem
	 *            The configuration element. Not used by this class but provided
	 *            for use by subclasses.
	 * @return The {@code File} object for the working directory
	 * @throws FileNotFoundException
	 */
	protected File initWorkingDirectory() {	
		File tmpDir = (File) getServletContext().getAttribute(
				"javax.servlet.context.tempdir"); //$NON-NLS-1$
		System.out.println("cache dir " + tmpDir); //$NON-NLS-1$
		return tmpDir;
		
		
	}

	public void initResourceFactoryExtension() throws NotFoundException {		
		ServiceLoader<IResourceFactory> resourceFactoryLoader = ServiceLoader.load(IResourceFactory.class);		
		for (IResourceFactory resourceFactory : resourceFactoryLoader) {
			Properties props = getExtensionPointProperties(resourceFactory);
			registerExtension(new AggregatorExtension(resourceFactory,
					props, "com.ibm.jaggr.service.resourcefactory", //$NON-NLS-1$
					DEFAULT_RESOURCEFACTORIES), null);
		}
	}

	public void initModuleBuilderExtension() throws NotFoundException {	
		ServiceLoader<IModuleBuilder> moduleBuilderLoader = ServiceLoader.load(IModuleBuilder.class);		
		for (IModuleBuilder moduleBuilder : moduleBuilderLoader) {
			Properties props = getExtensionPointProperties(moduleBuilder);
			registerExtension(new AggregatorExtension(moduleBuilder,
					props, "com.ibm.jaggr.service.modulebuilder", //$NON-NLS-1$
					DEFAULT_MODULEBUILDERS), null);
		}
	}
		
		
		

	public void initHttpTransportExtension() throws NotFoundException {		
		ServiceLoader<IHttpTransport> httpTransportLoader = ServiceLoader.load(IHttpTransport.class);		
		for (IHttpTransport httpTransport : httpTransportLoader) {
			Properties props = getExtensionPointProperties(httpTransport);
			registerExtension(new AggregatorExtension(httpTransport,
					props, "com.ibm.jaggr.service.httptransport", //$NON-NLS-1$
					DEFAULT_HTTPTRANSPORT), null);
		}
		
	}
	
	public Properties getExtensionPointProperties(Object object){
		Properties prop = new Properties();
		try {
			URL propUrl = this.getAggregatorServletContext().getResource("/WEB-INF/extensionPointsConf/" + object.getClass().getName() + ".properties"); //$NON-NLS-1$ //$NON-NLS-2$
			prop.load(propUrl.openStream());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return prop;
				
	}

	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
		super.init(servletConfig);
		platformServices = new PlatformServicesImpl();		
		setInitializationData();
	}

	protected IConfig newConfig() throws IOException {
		return new ConfigImpl(this);		
	}

}
