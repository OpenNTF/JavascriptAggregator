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
package com.ibm.jaggr.service.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
/**
 * Implementation of the HttpServletRequest interface used for processing request URLs
 * specified using the processRequestUrl console command.  This console command is used
 * primarily for cache priming purposes by automation scripts.
 */
public class ConsoleHttpServletRequest implements HttpServletRequest {

	private static final String dateFormatString = "EEE, dd MMM yyyy HH:mm:ss z"; //$NON-NLS-1$

	private Map<String, Object> requestAttributes = new HashMap<String, Object>();
	private Map<String, String[]> requestParams = new HashMap<String, String[]>();
	private Map<String, String> headers = new HashMap<String, String>();
	private String charEncoding = null;
	private final ServletContext context;
	private final String queryString;

	/**
	 * Constructs a request object from the specified servlet context and request url.  The
	 * content of the request URL preceding the query args is ignored.
	 *
	 * @param context the servlet context
	 * @param requestUrl the request url
	 * @throws IOException
	 */
	public ConsoleHttpServletRequest(ServletContext context, String requestUrl) throws IOException{
		this.context = context;

		int idx = requestUrl.indexOf("?"); //$NON-NLS-1$
		this.queryString = idx == -1 ? requestUrl : requestUrl.substring(idx+1);

		// set Date header
		Calendar calendar = Calendar.getInstance();
	    SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatString, Locale.US);
	    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
	    headers.put("Date", dateFormat.format(calendar.getTime())); //$NON-NLS-1$

	    // Set request parameters
	    String[] parts = queryString.split("[?&]"); //$NON-NLS-1$
	    for (String part : parts) {
	    	idx = part.indexOf("="); //$NON-NLS-1$
	    	String name = idx == -1 ? part : part.substring(0, idx);
	    	String value = ""; //$NON-NLS-1$
	    	if (idx != -1) {
	    		value = URLDecoder.decode(part.substring(idx+1), "UTF-8"); //$NON-NLS-1$
	    	}
	    	String[] values = requestParams.get(name);
	    	if (values == null) {
	    		values = new String[]{value};
	    	} else {
	    		Set<String> valueSet = new HashSet<String>(Arrays.asList(values));
	    		valueSet.add(value);
	    		values = valueSet.toArray(new String[valueSet.size()]);
	    	}
	    	requestParams.put(name, values);
	    }

	}


	@Override
	public Object getAttribute(String name) {
		return requestAttributes.get(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		return Collections.enumeration(requestAttributes.keySet());
	}

	@Override
	public String getCharacterEncoding() {
		return charEncoding;
	}

	@Override
	public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
		charEncoding = env;
	}

	@Override
	public int getContentLength() {
		return 0;
	}

	@Override
	public String getContentType() {
		return null;
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		return null;
	}

	@Override
	public String getParameter(String name) {
		String[] values = getParameterValues(name);
		return values == null ? null : values[0];
	}

	@Override
	public Enumeration<String> getParameterNames() {
		return Collections.enumeration(requestParams.keySet());
	}

	@Override
	public String[] getParameterValues(String name) {
		return requestParams.get(name);
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		return Collections.unmodifiableMap(requestParams);
	}

	@Override
	public String getProtocol() {
		return "HTTP/1.1"; //$NON-NLS-1$
	}

	@Override
	public String getScheme() {
		return "http"; //$NON-NLS-1$
	}

	@Override
	public String getServerName() {
		return "osgi.console"; //$NON-NLS-1$
	}

	@Override
	public int getServerPort() {
		return 0;
	}

	@Override
	public BufferedReader getReader() throws IOException {
		return null;
	}

	@Override
	public String getRemoteAddr() {
		return null;
	}

	@Override
	public String getRemoteHost() {
		return null;
	}

	@Override
	public void setAttribute(String name, Object o) {
		requestAttributes.put(name, o);
	}

	@Override
	public void removeAttribute(String name) {
		requestAttributes.remove(name);
	}

	@Override
	public Locale getLocale() {
		return null;
	}

	@Override
	public Enumeration<Locale> getLocales() {
		return null;
	}

	@Override
	public boolean isSecure() {
		return false;
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		return null;
	}

	@Deprecated
	@Override
	public String getRealPath(String path) {
		return null;
	}

	@Override
	public int getRemotePort() {
		return 0;
	}

	@Override
	public String getLocalName() {
		return null;
	}

	@Override
	public String getLocalAddr() {
		return null;
	}

	@Override
	public int getLocalPort() {
		return 0;
	}

	@Override
	public ServletContext getServletContext() {
		return context;
	}

	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		return null;
	}

	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
			throws IllegalStateException {
		return null;
	}

	@Override
	public boolean isAsyncStarted() {
		return false;
	}

	@Override
	public boolean isAsyncSupported() {
		return false;
	}

	@Override
	public AsyncContext getAsyncContext() {
		return null;
	}

	@Override
	public DispatcherType getDispatcherType() {
		return null;
	}

	@Override
	public String getAuthType() {
		return null;
	}

	@Override
	public Cookie[] getCookies() {
		return new Cookie[]{};
	}

	@Override
	public long getDateHeader(String name) {
		return 0;
	}

	@Override
	public String getHeader(String name) {
		return headers.get(name);
	}

	@Override
	public Enumeration<String> getHeaders(String name) {
		return Collections.enumeration(Arrays.asList(new String[] {getHeader(name)}));
	}

	@Override
	public Enumeration<String> getHeaderNames() {
		return Collections.enumeration(headers.keySet());
	}

	@Override
	public int getIntHeader(String name) {
		return Integer.parseInt(getHeader(name));
	}

	@Override
	public String getMethod() {
		return "GET"; //$NON-NLS-1$
	}

	@Override
	public String getPathInfo() {
		return null;
	}

	@Override
	public String getPathTranslated() {
		return null;
	}

	@Override
	public String getContextPath() {
		return ""; //$NON-NLS-1$
	}

	@Override
	public String getQueryString() {
		return queryString;
	}

	@Override
	public String getRemoteUser() {
		return null;
	}

	@Override
	public boolean isUserInRole(String role) {
		return false;
	}

	@Override
	public Principal getUserPrincipal() {
		return null;
	}

	@Override
	public String getRequestedSessionId() {
		return null;
	}

	@Override
	public String getRequestURI() {
		return getPathInfo() + "?" + getQueryString(); //$NON-NLS-1$
	}

	@Override
	public StringBuffer getRequestURL() {
		return new StringBuffer(getRequestURI());
	}

	@Override
	public String getServletPath() {
		return ""; //$NON-NLS-1$
	}

	@Override
	public HttpSession getSession(boolean create) {
		return null;
	}

	@Override
	public HttpSession getSession() {
		return null;
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		return false;
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		return false;
	}

	@Deprecated
	@Override
	public boolean isRequestedSessionIdFromUrl() {
		return false;
	}

	@Override
	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
		return false;
	}

	@Override
	public void login(String username, String password) throws ServletException {
	}

	@Override
	public void logout() throws ServletException {
	}

	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		return null;
	}

	@Override
	public Part getPart(String name) throws IOException, ServletException {
		return null;
	}

}
