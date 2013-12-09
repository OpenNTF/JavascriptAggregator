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
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.wink.json4j.JSONException;
import org.eclipse.osgi.framework.console.CommandInterpreter;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.ProcessingDependenciesException;
import com.ibm.jaggr.core.deps.IDependencies;
import com.ibm.jaggr.core.deps.ModuleDeps;
import com.ibm.jaggr.core.impl.Messages;
import com.ibm.jaggr.core.util.Features;
import com.ibm.jaggr.core.util.StringBufferWriter;
import com.ibm.jaggr.service.util.CIConsoleWriter;
import com.ibm.jaggr.service.util.ConsoleService;

public class AggregatorCommandProvider implements
		org.eclipse.osgi.framework.console.CommandProvider {
	
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
	static final String CMD_GETDEPSWITHHASBRANCHING = "getdepswithhasbranching"; //$NON-NLS-1$
	static final String NEWLINE = "\r\n"; //$NON-NLS-1$
	
	public static final String[] COMMANDS = new String[] {
	  CMD_HELP,
	  CMD_LIST,
	  CMD_RELOADCONFIG,
	  CMD_VALIDATEDEPS,
	  CMD_GETDEPS,
	  CMD_CLEARCACHE,
	  CMD_DUMPCACHE,
	  CMD_GETOPTIONS,
	  CMD_SETOPTION,
	  CMD_SHOWCONFIG,
	  CMD_GETDEPSWITHHASBRANCHING
	};
	
	private final BundleContext context;
	
	public AggregatorCommandProvider(BundleContext context) {
		this.context = context;
	}
	
	protected BundleContext getBundleContext() {
		return context;
	}
	
	@Override
	public String getHelp() {
		return getHelp(" "); //$NON-NLS-1$
	}
	
	protected String getHelp(String scopeSep) {
		String newline = System.getProperty("line.separator"); //$NON-NLS-1$
		StringBuffer sb = new StringBuffer(Messages.CommandProvider_0).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_1, 
				new Object[]{EYECATCHER, scopeSep, CMD_HELP})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_2, 
				new Object[]{EYECATCHER, scopeSep, CMD_LIST})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_3, 
				new Object[]{EYECATCHER, scopeSep, CMD_RELOADCONFIG})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_4, 
				new Object[]{EYECATCHER, scopeSep, CMD_VALIDATEDEPS, PARAM_CLEAN})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_5, 
				new Object[]{EYECATCHER, scopeSep, CMD_GETDEPS})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_16, 
				new Object[]{EYECATCHER, scopeSep, CMD_GETDEPSWITHHASBRANCHING, CMD_GETDEPS})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_6, 
				new Object[]{EYECATCHER, scopeSep, CMD_CLEARCACHE})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_7, 
				new Object[]{EYECATCHER, scopeSep, CMD_DUMPCACHE, PARAM_CONSOLE, PARAM_FILE})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_8, 
				new Object[]{EYECATCHER, scopeSep, CMD_GETOPTIONS})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_9, 
				new Object[]{EYECATCHER, scopeSep, CMD_SETOPTION})).append(newline)
		  .append(MessageFormat.format(
				Messages.CommandProvider_17,
				new Object[]{EYECATCHER, scopeSep, CMD_SHOWCONFIG})).append(newline);
		
		return sb.toString();
	}

	public void _aggregator(final CommandInterpreter ci) {
		// Set the command interpreter for the console service for this thread.
		ConsoleService cs = new ConsoleService(new CIConsoleWriter(ci));
		try {
			String command = ci.nextArgument();

			// Marshal the arguments into a string array
			List<String> argList = new ArrayList<String>();
			String arg = ci.nextArgument();
			while (arg != null) {
				argList.add(arg);
				arg = ci.nextArgument();
			}
			String[] args = argList.toArray(new String[argList.size()]);

			if (command == null || command.equals(CMD_HELP)) {
				ci.print(getHelp(" ")); //$NON-NLS-1$
			} else if (command.equals(CMD_LIST)) {
				ci.print(list());
			} else if (command.equals(CMD_RELOADCONFIG)) {
				ci.print(reloadconfig(args));
			} else if (command.equals(CMD_VALIDATEDEPS)) {
				ci.println(validatedeps(args));
			} else if (command.equals(CMD_GETDEPS)) {
				ci.println(getdeps(args, false));
			} else if (command.equals(CMD_GETDEPSWITHHASBRANCHING)) {
				ci.println(getdeps(args, true));
			} else if (command.equals(CMD_CLEARCACHE)){
				ci.println(clearcache(args));
			} else if (command.equals(CMD_DUMPCACHE)) {
				ci.println(dumpcache(args));
			} else if (command.equals(CMD_GETOPTIONS)) {
			  ci.println(getoptions(args));
			} else if (command.equals(CMD_SETOPTION)) {
				ci.println(setoption(args));
			} else if (command.equals(CMD_SHOWCONFIG)) {
				ci.println(showconfig(args));
			} else {
				ci.print(getHelp());
			}
		} catch (Exception e) {
			ci.println(e.getMessage());
		} finally {
			cs.close();
		}
	}
	
	protected String list() throws InvalidSyntaxException {
		// list the registered servlets
		BundleContext context = getBundleContext();
		StringBuffer sb = new StringBuffer();
		ServiceReference[] refs = context.getServiceReferences(IAggregator.class.getName(), null);
		if (refs != null) {
			for (ServiceReference ref : refs) {
				IAggregator aggregator = (IAggregator)context.getService(ref);
				if (aggregator != null) {
					try {
						sb.append(aggregator.getName()).append(NEWLINE);
					} finally {
						context.ungetService(ref);
					}
				}
			}
		}
		if (refs == null || refs.length == 0) {
			return Messages.CommandProvider_19;
		}
		return sb.toString();
	}
	
	protected String reloadconfig(String[] args) throws IOException, URISyntaxException, InvalidSyntaxException, InterruptedException {
	  StringBuffer sb = new StringBuffer();
		ServiceReference ref = getServiceRef(args, sb);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			try {
				aggregator.reloadConfig();
				sb.append(Messages.CommandProvider_20);
				// Call getExpandedDependencies().  It will block and not return till the 
				// dependencies have been loaded/validated.  We do this so that the 
				// command interpreter will remain valid so that console output will be
				// displayed.  If in development mode, we'll get a ProcessingDependenciesException.
				IDependencies deps = aggregator.getDependencies();
				while (true) {
					try {
						deps.getExpandedDependencies("", new Features(), new HashSet<String>(), false, false); //$NON-NLS-1$
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
		return sb.toString();
	}
	
	protected String validatedeps(String[] args) throws MalformedURLException, IOException, URISyntaxException, InvalidSyntaxException, InterruptedException {
		StringBuffer sb = new StringBuffer();
		ServiceReference ref = getServiceRef(args, sb);
		boolean clean = PARAM_CLEAN.equals(args.length > 1 ? args[1] : null);
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
						deps.getExpandedDependencies("", new Features(), new HashSet<String>(), false, false); //$NON-NLS-1$
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
    return sb.toString();
	}
	
	protected String getdeps(String[] args, boolean performHasPluginBranching) throws InvalidSyntaxException, IOException {
		ModuleDeps moduleDeps = null;
		StringBuffer sb = new StringBuffer();
		ServiceReference ref = getServiceRef(args, sb);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);

			// Next parameter is module name
			String moduleName = args.length > 1 ? args[1] : null;
			if (moduleName == null) {
				return Messages.CommandProvider_22;
			}
			
			// subsequent parameters are optional feature list
			Features features = new Features();
			String feature;
			for(int i = 2; i < args.length; i++) {
			  feature = args[i];
				boolean value = true;
				if (feature.startsWith("!")) { //$NON-NLS-1$
					feature = feature.substring(1);
					value = false;
				}
				features.put(feature, value);
			}			
			sb.append(MessageFormat.format(
					Messages.CommandProvider_23,
					new Object[]{features.toString()})).append(NEWLINE);
			try {
				IDependencies deps = aggregator.getDependencies();
				if (deps != null) {
					moduleDeps = deps.getExpandedDependencies(
							moduleName, 
							features, 
							new HashSet<String>(), 
							true, 
							performHasPluginBranching);
				}
			} finally {
				getBundleContext().ungetService(ref);
			}
			if (moduleDeps != null) {
				moduleDeps.simplify();
				for (Map.Entry<String, String> entry : moduleDeps.getModuleIdsWithComments().entrySet()) {
					sb.append("\"" + entry.getKey() + "\" /*" + entry.getValue() + " */").append(NEWLINE); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
			}
		}
		return sb.toString();
	}

	protected String clearcache(String[] args) throws InvalidSyntaxException {
		StringBuffer sb = new StringBuffer();
		ServiceReference ref = getServiceRef(args, sb);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			try {
				aggregator.getCacheManager().clearCache();
				return
						MessageFormat.format(
								Messages.CommandProvider_26, 
								new Object[]{aggregator.getName()}
						);
			} finally {
				getBundleContext().ungetService(ref);
			}
		} else {
		  return sb.toString();
		}
	}
	
	protected String dumpcache(String[] args) throws InvalidSyntaxException, IOException {
		StringBuffer sb = new StringBuffer();
		ServiceReference ref = getServiceRef(args, sb);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			String target = args.length > 1 ? args[1] : null;
			Writer writer;
			File outputFile = null;
			if (PARAM_CONSOLE.equals(target)) {
				writer = new StringBufferWriter(sb);
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
				return MessageFormat.format(Messages.CommandProvider_12, target);
			}
			String filter = args.length > 2 ? args[2] : null;
			Pattern pattern = filter != null ? Pattern.compile(filter) : null;
			try {
				aggregator.getCacheManager().dumpCache(writer, pattern);
				writer.close();
				if (outputFile != null) {
					sb.append(
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
		return sb.toString();
	}

	protected String getoptions(String[] args) throws InvalidSyntaxException {
		StringBuffer sb = new StringBuffer();
		ServiceReference ref = getServiceRef(args, sb);
		if (ref != null) {
			try {
				IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
				sb.append(aggregator.getOptions().getOptionsMap().toString());
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
		return sb.toString();
	}
	
	protected String setoption(String args[]) throws IOException, InvalidSyntaxException {
		StringBuffer sb = new StringBuffer();
		ServiceReference ref = getServiceRef(args, sb);
		if (ref != null) {
			try {
				IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
				String name = args.length > 1 ? args[1] : null;
				String value = args.length > 2 ? args[2] : null;
				aggregator.getOptions().setOption(name, value);
				sb.append(
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
		return sb.toString();
	}
	
	protected String showconfig(String[] args) throws InvalidSyntaxException, JSONException {
		StringBuffer sb = new StringBuffer();
		ServiceReference ref = getServiceRef(args, sb);
		if (ref != null) {
			IAggregator aggregator = (IAggregator)getBundleContext().getService(ref);
			try {
				sb.append(aggregator.getConfig().toString());
			} finally {
				getBundleContext().ungetService(ref);
			}
		}
		return sb.toString();
	}
	
	protected ServiceReference getServiceRef(String[] args, StringBuffer sb) throws InvalidSyntaxException {
		if (args.length == 0) {
			throw new InvalidSyntaxException("servlet name not specified", null); //$NON-NLS-1$
		}
		String servletName = args[0];
		BundleContext context = getBundleContext();
		ServiceReference[] refs = context.getServiceReferences(
				IAggregator.class.getName(), 
				"(name="+servletName+")" //$NON-NLS-1$ //$NON-NLS-2$
		);
		ServiceReference result = refs != null && refs.length > 0 ? refs[0] : null;
		if (result == null) {
			sb.append(
					MessageFormat.format(
							Messages.CommandProvider_11, 
							new Object[]{servletName}
					)
			).append(NEWLINE);
			sb.append(MessageFormat.format(
					Messages.CommandProvider_10,
					new Object[]{EYECATCHER, CMD_LIST}));
		}
		return result;
	}
	
}
