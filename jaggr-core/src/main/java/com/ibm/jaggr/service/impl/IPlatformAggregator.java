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

import java.net.URL;
import java.util.Dictionary;

public interface IPlatformAggregator {
	
	public Object registerService(String clazz, Object service, Dictionary<String, String> properties);
	
	public void unRegisterService(Object service);
	
	public Object[] getServiceReferences(String clazz, String filter);

	public Object getService(Object serviceReference);
	
	// TODO: Check if we can optimise this
	public boolean unGetService(Object serviceReference, String clazz);

	public URL getResource(String resourceName);
	
	public Dictionary<String, String> getHeaders();
	
	public void println(String msg);
	
	public boolean initiateShutdown();	
	
}
