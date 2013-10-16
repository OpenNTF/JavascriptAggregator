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

package com.ibm.jaggr.service.console.commands;

import java.util.Iterator;
import java.util.List;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;

import com.google.common.collect.Lists;
import com.ibm.jaggr.service.impl.AggregatorCommandProvider;

@Command(scope = "aggregator", name = "getdeps", description="<servlet> <module> [<feature list>] - false features begin with !")
public class GetDepsShellCommand extends AbstractOsgiCommandSupport {
	
	@Argument(index = 0, name = "servlet", description = "The servlet to find the module in", required = true, multiValued = false)
    String servlet = null;
	
	@Argument(index = 1, name = "module", description = "The module to get deps for", required = true, multiValued = false)
    String module = null;
	
	@Argument(index = 2, name = "features", description = "With has feature list.  Note: false features begin with an escaped bang (\\!).", required = false, multiValued = true)
    List<String> features = null;
	
	@Override
	protected void exec(AggregatorCommandProvider provider) throws Exception {
		List<String> args = Lists.newArrayList(servlet, module);
		if (features != null)
			args.addAll(features);
		args.add(null); // pass null back so that the CI iteration will terminate.  It cannot handle the exceptions thrown by an iterator.
		
		final Iterator<String> it = args.iterator();
		provider.doGetDepsCmd(new CommandInterpreterWrapper() {
			@Override
			public String nextArgument() {
				return it.next();
			}
		}, false);
	}
}
