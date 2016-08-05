/*
 * (C) Copyright IBM Corp. 2012, 2016 All Rights Reserved.
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
package com.ibm.jaggr.core.util;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.options.IOptions;

public class AggregatorUtil {

	static public String getCacheBust(IAggregator aggregator) {
		return getCacheBust(aggregator.getConfig(), aggregator.getOptions());
	}

	static public String getCacheBust(IConfig config, IOptions options) {
		String result = config.getCacheBust();
		String optionsCb = options.getCacheBust();
		if (optionsCb != null && optionsCb.length() > 0) {
			result = (result != null && result.length() > 0) ?
					(result + "-" + optionsCb) : optionsCb; //$NON-NLS-1$
		}
		return result;
	}
}
