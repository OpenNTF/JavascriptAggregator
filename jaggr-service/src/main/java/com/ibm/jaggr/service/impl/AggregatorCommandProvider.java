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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.wink.json4j.JSONException;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.osgi.framework.console.CommandInterpreter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.ProcessingDependenciesException;
import com.ibm.jaggr.service.deps.IDependencies;
import com.ibm.jaggr.service.util.ConsoleService;
import com.ibm.jaggr.service.util.Features;

public abstract class AggregatorCommandProvider extends Plugin implements
		org.eclipse.osgi.framework.console.CommandProvider {
	
	abstract protected BundleContext getBundleContext();

	static final String LOG_DIR = "log"; //$NON-NLS-1$
	static final String LOGFILE_PREFIX = "cacheDump"; //$NON-NLS-1$
	static final String LOGFILE_SUFFIX = ".log"; //$NON-NLS-1$
	static final String EYECATCHER = "aggregator"; //$NON-NLS-1$
	static final String PARAM_CLEAN = "clean"; //$NON-NLS-1$
	static final String PARAM_CONSOLE = "con"; //$NON-NLS-1$
	static final String PARAM_FILE = "file"; //$NON-NLS-1$
	static final String CMD_HELP = "help"; //$NON-NLS-1$
	static final String CMD_LIST = "list"; //$NON-NLS-1$
	static final String CMD_RELOADCONFIG = "reloadconfig"; //$NON-NLS-1$
	static final String CMD_VALIDATEDEPS = "validatedeps"; //$NON-NLS-1$
	static final String CMD_GETDEPS = "getdeps"; //$NON-NLS-1$
	static final String CMD_CLEARCACHE = "clearcache"; //$NON-NLS-1$
	static final String CMD_DUMPCACHE = "dumpcache"; //$NON-NLS-1$
	static final String CMD_GETOPTIONS = "getoptions"; //$NON-NLS-1$
	static final String CMD_SETOPTION = "setoption"; //$NON-NLS-1$
	static final String CMD_SHOWCONFIG = "showconfig"; //$NON-NLS-1$
	
	@Override
	public String getHelp() {
		String newline = System.getProperty("line.separator"); //$NON-NLS-1$
		StringBuffer sb = new StringBuffer(Messages.CommandProvider_0).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_1, 
				new Object[]{EYECATCHER, CMD_HELP})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_2, 
				new Object[]{EYECATCHER, CMD_LIST})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_3, 
				new Object[]{EYECATCHER, CMD_RELOADCONFIG})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_4, 
				new Object[]{EYECATCHER, CMD_VALIDATEDEPS, PARAM_CLEAN})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_5, 
				new Object[]{EYECATCHER, CMD_GETDEPS})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_6, 
				new Object[]{EYECATCHER, CMD_CLEARCACHE})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_7, 
				new Object[]{EYECATCHER, CMD_DUMPCACHE, PARAM_CONSOLE, PARAM_FILE})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_8, 
				new Object[]{EYECATCHER, CMD_GETOPTIONS})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_9, 
				new Object[]{EYECATCHER, CMD_SETOPTION})).append(newline)
		  .append(MessageFormat.format(
				"\t{0} {1} <servlet> - config for the servlet",
				new Object[]{EYECATCHER, CMD_SHOWCONFIG})).append(newline);
		
		return sb.toString();
	}

	public void _aggregator(final CommandInterpreter ci) {
		// Set the command interpreter for the console service for this thread.
		ConsoleService cs = new ConsoleService(ci);
		try {
			String command = ci.nextArgument();
			if (command == null || command.equals(CMD_HELP)) {
				ci.print(getHelp());
			} else if (command.equals(CMD_LIST)) {
				doListCmd(ci);
			} else if (command.equals(CMD_RELOADCONFIG)) {
				doReloadConfigsCmd(ci);
			} else if (command.equals(CMD_VALIDATEDEPS)) {
				doReloadDepsCmd(ci);
			} else if (command.equals(CMD_GETDEPS)) {
				doGetDepsCmd(ci);
			} else if (command.equals(CMD_CLEARCACHE)){
				doClearCacheCmd(ci);
			} else if (command.equals(CMD_DUMPCACHE)) {
				doDumpCacheCmd(ci);
			} else if (command.equals(CMD_GETOPTIONS)) {
				doGetOptionsCmd(ci);
			} else if (command.equals(CMD_SETOPTION)) {
				doSetOptionCmd(ci);
			} else if (command.equals(CMD_SHOWCONFIG)) {
				doShowConfigCmd(ci);
			} else {
				ci.print(getHelp());
			}
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			ci.println(writer.toString());
		} finally {
			cs.close();
		}
	}
	
	public void doListCmd(CommandInterpreter ci) throws InvalidSyntaxException {
		// list the registered servlets
		BundleContext context = getBundleContext();
		ServiceReference[] refs = context.getServiceReferences(IAggregator.class.getName(), null);
		if (refs != null) {
			for (ServiceReference ref : refs) {
				IAggregator aggregator = (IAggregator)context.getService(ref);
				if (aggregator != null) {
					try {
						ci.println(aggregator.getName());
					} finally {
						context.ungetService(ref);
					}
				}
			}
		}
		if (refs == null || refs.length == 0) {
			ci.println(Messages.CommandProvider_19);
		}
	}
	
	public void doReloadConfigsCmd(CommandInterpreter ci) throws IOException, URISyntaxException, InvalidSyntaxException, InterruptedException {
		ServiceReference ref = getServiceRef(ci);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			try {
				aggregator.reloadConfig();
				ci.println(Messages.CommandProvider_20);
				// Call getExpandedDependencies().  It will block and not return till the 
				// dependencies have been loaded/validated.  We do this so that the 
				// command interpreter will remain valid so that console output will be
				// displayed.  If in development mode, we'll get a ProcessingDependenciesException.
				IDependencies deps = aggregator.getDependencies();
				while (true) {
					try {
						deps.getExpandedDependencies("", new Features(), new HashSet<String>(), false);
					} catch (ProcessingDependenciesException ignore) {
						Thread.sleep(1000L);
						continue;
					}
					break;
				}
				
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
	}
	
	public void doReloadDepsCmd(CommandInterpreter ci) throws MalformedURLException, IOException, URISyntaxException, InvalidSyntaxException, InterruptedException {
		ServiceReference ref = getServiceRef(ci);
		boolean clean = PARAM_CLEAN.equals(ci.nextArgument());
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			try {
				IDependencies deps = aggregator.getDependencies();
				deps.validateDeps(clean);
				// Call getExpandedDependencies().  It will block and not return till the 
				// dependencies have been loaded/validated.  We do this so that the 
				// command interpreter will remain valid so that console output will be
				// displayed.  If in development mode, we'll get a ProcessingDependenciesException.
				while (true) {
					try {
						deps.getExpandedDependencies("", new Features(), new HashSet<String>(), false);
					} catch (ProcessingDependenciesException ignore) {
						Thread.sleep(1000L);
						continue;
					}
					break;
				}
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
	}
	
	private void doGetDepsCmd(CommandInterpreter ci) throws InvalidSyntaxException, IOException {
		Map<String, String> moduleDeps = null;
		ServiceReference ref = getServiceRef(ci);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);

			// Next parameter is module name
			String moduleName = ci.nextArgument();
			if (moduleName == null) {
				ci.println(Messages.CommandProvider_22);
				return;
			}
			
			// subsequent parameters are optional feature list
			Features features = new Features();
			String feature;
			while ((feature = ci.nextArgument()) != null) {
				boolean value = true;
				if (feature.startsWith("!")) {
					feature = feature.substring(1);
					value = false;
				}
				features.put(feature, value);
			}			
			ci.println(MessageFormat.format(
					Messages.CommandProvider_23,
					new Object[]{features.toString()}));
			try {
				IDependencies deps = aggregator.getDependencies();
				if (deps != null) {
					moduleDeps = deps.getExpandedDependencies(moduleName, features, new HashSet<String>(), true);
				}
			} finally {
				getBundleContext().ungetService(ref);
			}
			if (moduleDeps != null) {
				for (Map.Entry<String, String> entry : moduleDeps.entrySet()) {
					ci.println("\"" + entry.getKey() + "\" /*" + entry.getValue() + " */"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			}
		}
	}

	public void doClearCacheCmd(CommandInterpreter ci) throws InvalidSyntaxException {
		ServiceReference ref = getServiceRef(ci);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			try {
				aggregator.getCacheManager().clearCache();
				ci.println(
						MessageFormat.format(
								Messages.CommandProvider_26, 
								new Object[]{aggregator.getName()}
						)
				);
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
	}
	
	public void doDumpCacheCmd(CommandInterpreter ci) throws InvalidSyntaxException, IOException {
		ServiceReference ref = getServiceRef(ci);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			String target = ci.nextArgument();
			Writer writer;
			File outputFile = null;
			if (PARAM_CONSOLE.equals(target)) {
				writer = new ConsoleWriter(ci);
			} else if (PARAM_FILE.equals(target)){
				File workDir = aggregator.getWorkingDirectory();
				File logDir = new File(workDir, LOG_DIR);
				if (!logDir.exists()) {
					if (!logDir.mkdir()) {
						throw new IOException(
								MessageFormat.format(
										Messages.AggregatorCommandProvider_1,
										new Object[]{logDir.getCanonicalPath()}
								)
						);
					}
				}
				outputFile = File.createTempFile(
						LOGFILE_PREFIX,
						LOGFILE_SUFFIX,
						logDir);
				writer = new FileWriter(outputFile);
			} else {
				ci.println(MessageFormat.format(Messages.CommandProvider_12, target));
				return;
			}
			String filter = ci.nextArgument();
			Pattern pattern = filter != null ? Pattern.compile(filter) : null;
			try {
				aggregator.getCacheManager().dumpCache(writer, pattern);
				writer.close();
				if (outputFile != null) {
					ci.println(
						MessageFormat.format(
							Messages.CommandProvider_13, 
							new Object[]{
								aggregator.getName(),
								outputFile.getCanonicalPath()
							}
						)
					);
				}
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
	}

	public void doGetOptionsCmd(CommandInterpreter ci) throws InvalidSyntaxException {
		ServiceReference ref = getServiceRef(ci);
		if (ref != null) {
			try {
				IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
				ci.println(aggregator.getOptions().getOptionsMap().toString());
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
	}
	
	public void doSetOptionCmd(CommandInterpreter ci) throws IOException, InvalidSyntaxException {
		ServiceReference ref = getServiceRef(ci);
		if (ref != null) {
			try {
				IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
				String name = ci.nextArgument();
				String value = ci.nextArgument();
				aggregator.getOptions().setOption(name, value);
				ci.println(
					MessageFormat.format(
						value == null ? 
								Messages.CommandProvider_15 : 
								Messages.CommandProvider_14,
						new Object[]{name, value}
					)
				);
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
	}
	
	private void doShowConfigCmd(CommandInterpreter ci) throws InvalidSyntaxException, JSONException {
		ServiceReference ref = getServiceRef(ci);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			try {
				ci.println(aggregator.getConfig().toString());
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
	}
	
	private ServiceReference getServiceRef(CommandInterpreter ci) throws InvalidSyntaxException {
		String servletName = ci.nextArgument();
		BundleContext context = getBundleContext();
		ServiceReference[] refs = context.getServiceReferences(
				IAggregator.class.getName(), 
				"(name="+servletName+")" //$NON-NLS-1$ //$NON-NLS-2$
		);
		ServiceReference result = refs != null && refs.length > 0 ? refs[0] : null;
		if (result == null) {
			ci.println(
					MessageFormat.format(
							Messages.CommandProvider_11, 
							new Object[]{servletName}
					)
			);
			ci.println(MessageFormat.format(
					Messages.CommandProvider_10,
					new Object[]{EYECATCHER, CMD_LIST}));
		}
		return result;
	}
	
}
