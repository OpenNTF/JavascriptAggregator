/*
help * (C) Copyright 2012, IBM Corporation
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

import com.ibm.jaggr.core.util.ConsoleService;
import com.ibm.jaggr.service.util.CSConsoleWriter;

import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Descriptor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;

public class AggregatorCommandProviderGogo extends AggregatorCommandProvider {

	public AggregatorCommandProviderGogo(BundleContext context) {
		super(context);
	}

	@Override
	public String getHelp() {
		return getHelp(":"); //$NON-NLS-1$
	}

	@Descriptor("shows aggregator help")
	protected String help() {
		return getHelp(":"); //$NON-NLS-1$
	}

	@Descriptor("lists registered aggregation servlets")
	public String list() throws InvalidSyntaxException {
		return super.list();
	}

	@Descriptor("reloads the config script")
	public String reloadconfig(@Descriptor("<servlet>")String servlet)
			throws IOException, URISyntaxException, InvalidSyntaxException, InterruptedException {
		return super.reloadconfig(new String[]{servlet});
	}

	@Descriptor("validates the dependency graph")
	public String validatedeps(CommandSession cs,
			@Descriptor("<servlet>")String servlet
			) throws MalformedURLException, IOException, URISyntaxException, InvalidSyntaxException, InterruptedException {
		new ConsoleService(new CSConsoleWriter(cs));    // Saves the command session so it can be accessed by async thread
		return super.validatedeps(new String[]{servlet});
	}

	@Descriptor("discards cached data and rebuilds the dependency graph")
	public String validatedeps(CommandSession cs,
			@Descriptor("<servlet>")String servlet,
			@Descriptor("clean (clears cached data)")String clean
			) throws MalformedURLException, IOException, URISyntaxException, InvalidSyntaxException, InterruptedException {
		new ConsoleService(new CSConsoleWriter(cs));		// Saves the command session so it can be accessed by async thread
		return super.validatedeps(new String[]{servlet, clean});
	}

	@Descriptor("displays dependencies for the specified module")
	public String getdeps(
			@Descriptor("<servlet>")String servlet,
			@Descriptor("<modle>")String module
			) throws InvalidSyntaxException, IOException {
		return super.getdeps(makeArgumentArray(servlet, module));
	}

	@Descriptor("displays dependencies for the specified module with the specified features defined")
	public String getdeps(
			@Descriptor("<servlet>")String servlet,
			@Descriptor("<modle>")String module,
			@Descriptor("<feature list> (false features begin with !)")String[] featureList
			) throws InvalidSyntaxException, IOException {
		return super.getdeps(makeArgumentArray(servlet, module, featureList));
	}

	@Descriptor("clears the cache for the specified servlet")
	public String clearcache(CommandSession cs,
			@Descriptor("<servlet>")String servlet
			) throws InvalidSyntaxException {
		new ConsoleService(new CSConsoleWriter(cs));	// Saves the command session so it can be accessed by async thread
		return super.clearcache(new String[]{servlet});
	}

	@Descriptor("outputs the cache metadata for the specified servlet")
	public String dumpcache(
			@Descriptor("<servlet>")String servlet,
			@Descriptor("(con | file)")String target
			) throws InvalidSyntaxException, IOException {
		return super.dumpcache(new String[]{servlet, target});
	}

	@Descriptor("outputs the cache metadata for the specified servlet, filtered using the specified regular expression")
	public String dumpcache(
			@Descriptor("<servlet>")String servlet,
			@Descriptor("(con | file)")String target,
			@Descriptor("<filter> (regular expression to filter output by module name)")String filter
			) throws InvalidSyntaxException, IOException {
		return super.dumpcache(new String[]{servlet, target, filter});
	}

	@Descriptor("displays the current options and their values for the specified servlet")
	public String getoptions(
			@Descriptor("<servlet>")String servlet
			) throws InvalidSyntaxException {
		return super.getoptions(new String[]{servlet});
	}

	@Descriptor("resets the specified option to the default value")
	public String setoption(
			@Descriptor("<servlet>")String servlet,
			@Descriptor("<name>")String name
			) throws IOException, InvalidSyntaxException {
		return super.setoption(new String[]{servlet, name});
	}

	@Descriptor("sets the specified option to the specified value")
	public String setoption(
			@Descriptor("<servlet>")String servlet,
			@Descriptor("<name>")String name,
			@Descriptor("<value>")String value
			) throws IOException, InvalidSyntaxException {
		return super.setoption(new String[]{servlet, name, value});
	}

	@Descriptor("displays the config for the servlet")
	public String showconfig(
			@Descriptor("<servlet>")String servlet
			) throws InvalidSyntaxException {
		return super.showconfig(new String[]{servlet});
	}

	private String[] makeArgumentArray(Object... args) {
		ArrayList<String> result = new ArrayList<String>();
		for (Object arg : args) {
			if (arg instanceof String[]) {
				result.addAll(Arrays.asList((String[])arg));
			} else {
				result.add(arg.toString());
			}
		}
		return result.toArray(new String[result.size()]);
	}
}
