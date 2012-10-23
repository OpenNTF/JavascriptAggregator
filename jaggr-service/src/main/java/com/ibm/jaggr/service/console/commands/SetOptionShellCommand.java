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

@Command(scope = "aggregator", name = "setoption", description="<servlet> <name> [<value>] - sets the specified option to the specified value or removes the option if value is not specified")
public class SetOptionShellCommand extends AbstractOsgiCommandSupport {
	
	@Argument(index = 0, name = "servlet", description = "The servlet to set option for", required = true, multiValued = false)
    String servlet = null;
	
	@Argument(index = 1, name = "name", description = "Name of the option to set", required = true, multiValued = false)
    String name = null;
	
	@Argument(index = 2, name = "value", description = "Value of the option to set, omit to delete", required = false, multiValued = false)
    String value = null;
	
	@Override
	protected void exec(AggregatorCommandProvider provider) throws Exception {
		List<String> args = Lists.newArrayList(servlet, name, value);
		final Iterator<String> it = args.iterator();
		provider.doSetOptionCmd(new CommandInterpreterWrapper() {
			@Override
			public String nextArgument() {
				return it.next();
			}
		});
	}
}
