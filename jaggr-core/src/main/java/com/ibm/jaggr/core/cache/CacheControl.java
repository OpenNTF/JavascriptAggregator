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
package com.ibm.jaggr.core.cache;

import java.io.Serializable;
import java.util.Map;

public class CacheControl implements Serializable {
	private static final long serialVersionUID = 1276701428723406198L;

	public static final String CONTROL_SERIALIZATION_FILENAME = "control.ser"; //$NON-NLS-1$

	private volatile String rawConfig = null;
	private volatile Map<String, String> optionsMap = null;
	private volatile long depsLastMod = -1;
	private long initStamp = -1;

	public String getRawConfig() {
		return rawConfig;
	}
	public void setRawConfig(String rawConfig) {
		this.rawConfig = rawConfig;
	}
	public Map<String, String> getOptionsMap() {
		return optionsMap;
	}
	public void setOptionsMap(Map<String, String> optionsMap) {
		this.optionsMap = optionsMap;
	}
	public long getDepsLastMod() {
		return depsLastMod;
	}
	public void setDepsLastMod(long depsLastMod) {
		this.depsLastMod = depsLastMod;
	}
	public long getInitStamp() {
		return initStamp;
	}
	public void setInitStamp(long initStamp) {
		this.initStamp = initStamp;
	}
	public static long getSerialversionuid() {
		return serialVersionUID;
	}
}
