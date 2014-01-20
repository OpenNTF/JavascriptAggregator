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

package com.ibm.jaggr.core.readers;

import java.io.StringReader;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.TypeUtil;

public class ErrorModuleReader extends AggregationReader {
	private static final String prologueFmt = "(function(msg){console.%s(msg);(typeof define==='function')&&define('%s',[],function(){throw new Error(msg)})})(\""; //$NON-NLS-1$
	private static final String prologueFmtAnon = "(function(msg){console.%s(msg);(typeof define==='function')&&define([],function(){throw new Error(msg)})})(\""; //$NON-NLS-1$
	private static final String epilogue = "\");"; //$NON-NLS-1$
	
	public enum ConsoleMethod {
		error,
		warn,
		log
	}
	
	public ErrorModuleReader(ConsoleMethod method, String msg, String mid, HttpServletRequest request) {
		super(String.format(getFormat(request), method.name(), mid),
				new StringReader(msg), 
				epilogue);
	}
	
	public ErrorModuleReader(String msg, String mid, HttpServletRequest request) {
		this(ConsoleMethod.error, msg, mid, request); 
	}
	
	private static String getFormat(HttpServletRequest request) {
		Boolean exportMid = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME));
		return exportMid ? prologueFmt : prologueFmtAnon;
	}
}
