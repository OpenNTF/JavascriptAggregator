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

package com.ibm.jaggr.blueprint;

import java.util.Arrays;

import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.eclipse.osgi.framework.console.CommandProvider;

import com.ibm.jaggr.service.impl.AggregatorCommandProvider;

@Command(scope = AggregatorCommandProvider.EYECATCHER, name = AggregatorCommandProvider.CMD_DUMPCACHE)
public class DumpCacheShellCommand extends AbstractOsgiCommandSupport {

	@Argument(index = 0, name = "servlet", required = true, multiValued = false)
    String servlet = null;

	@Argument(index = 1, name = "target", required = true, multiValued = false)
	String target = null;

	@Argument(index = 2, name = "regexp", required = false, multiValued = false)
	String regexp = null;

	@Override
	protected String exec(CommandProvider provider) throws Exception {
		return invoke(provider, new CommandInterpreterWrapper(Arrays.asList(AggregatorCommandProvider.CMD_DUMPCACHE, servlet, target, regexp)));
	}
}
