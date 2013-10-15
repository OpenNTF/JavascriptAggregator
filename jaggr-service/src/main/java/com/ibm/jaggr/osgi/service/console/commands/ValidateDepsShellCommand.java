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

package com.ibm.jaggr.osgi.service.console.commands;

import java.util.Iterator;
import java.util.List;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;

import com.google.common.collect.Lists;
import com.ibm.jaggr.osgi.service.impl.AggregatorCommandProvider;

@Command(scope = "aggregator", name = "validatedeps", description="<servlet> [clean] - reloads the dependency graph")
public class ValidateDepsShellCommand extends AbstractOsgiCommandSupport {
	
	@Argument(index = 0, name = "servlet", description = "The servlet to validate deps for", required = true, multiValued = false)
    String servlet = null;
	
	@Argument(index = 1, name = "clean", description = "Dependency graph is re-generated from scratch", required = false, multiValued = false)
    String clean = null;
	
	@Override
	protected void exec(AggregatorCommandProvider provider) throws Exception {
		List<String> args = Lists.newArrayList(servlet, clean);
		final Iterator<String> it = args.iterator();
		provider.doReloadDepsCmd(new CommandInterpreterWrapper() {
			@Override
			public String nextArgument() {
				return it.next();
			}
		});
	}
}
