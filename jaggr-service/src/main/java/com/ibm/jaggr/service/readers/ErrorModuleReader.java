/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.readers;

import java.io.StringReader;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.TypeUtil;

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
