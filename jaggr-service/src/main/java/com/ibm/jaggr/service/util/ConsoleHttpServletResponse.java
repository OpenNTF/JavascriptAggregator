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
package com.ibm.jaggr.service.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * Implementation of the HttpServletRequest interface used for processing request URLs
 * specified using the processRequestUrl console command.  This console command is used
 * primarily for cache priming purposes by automation scripts.
 */
public class ConsoleHttpServletResponse implements HttpServletResponse {
	private String charEncoding = ""; //$NON-NLS-1$
	private String contentType = ""; //$NON-NLS-1$
	private Map<String, Collection<String>> headers = new HashMap<String, Collection<String>>();
	private Locale locale = null;
	private int responseCode = 200;
	private final ServletOutputStream os;

	/**
	 * Constructs an http servlet response with the specified output stream
	 * @param ostream the output stream
	 */
	public ConsoleHttpServletResponse(final OutputStream ostream) {
		os = new ServletOutputStream() {
			@Override
			public void write(int b) throws IOException {
				ostream.write(b);
			}
		};
	}

	@Override
	public String getCharacterEncoding() {
		return charEncoding;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return os;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		throw new UnsupportedOperationException("Not implemented"); //$NON-NLS-1$
	}

	@Override
	public void setCharacterEncoding(String charset) {
		charEncoding = charset;
	}

	@Override
	public void setContentLength(int len) {
		setHeader("Content-Length", Integer.toBinaryString(len)); //$NON-NLS-1$
	}

	@Override
	public void setContentType(String type) {
		contentType = type;
	}

	@Override
	public void setBufferSize(int size) {
		throw new UnsupportedOperationException("Not implemented"); //$NON-NLS-1$
	}

	@Override
	public int getBufferSize() {
		throw new UnsupportedOperationException("Not implemented"); //$NON-NLS-1$
	}

	@Override
	public void flushBuffer() throws IOException {
		throw new UnsupportedOperationException("Not implemented"); //$NON-NLS-1$
	}

	@Override
	public void resetBuffer() {
		throw new UnsupportedOperationException("Not implemented"); //$NON-NLS-1$
	}

	@Override
	public boolean isCommitted() {
		return false;
	}

	@Override
	public void reset() {
		throw new UnsupportedOperationException("Not implemented"); //$NON-NLS-1$
	}

	@Override
	public void setLocale(Locale loc) {
		locale = loc;
	}

	@Override
	public Locale getLocale() {
		return locale;
	}

	@Override
	public void addCookie(Cookie cookie) {
		addHeader("Set-Cookie", cookie.toString()); //$NON-NLS-1$
	}

	@Override
	public boolean containsHeader(String name) {
		return headers.containsKey(name);
	}

	@Override
	public String encodeURL(String url) {
		return url;
	}

	@Override
	public String encodeRedirectURL(String url) {
		return url;
	}

	@Deprecated
	@Override
	public String encodeUrl(String url) {
		return url;
	}

	@Deprecated
	@Override
	public String encodeRedirectUrl(String url) {
		return url;
	}

	@Override
	public void sendError(int sc, String msg) throws IOException {
		responseCode = sc;
		os.println(msg);
		os.close();
	}

	@Override
	public void sendError(int sc) throws IOException {
		responseCode = sc;
		os.close();
	}

	@Override
	public void sendRedirect(String location) throws IOException {
		throw new UnsupportedOperationException("Not implemented"); //$NON-NLS-1$
	}

	@Override
	public void setDateHeader(String name, long date) {
	    setHeader("Date", formatDate(date)); //$NON-NLS-1$
	}

	@Override
	public void addDateHeader(String name, long date) {
	    addHeader("Date", formatDate(date)); //$NON-NLS-1$
	}

	@Override
	public void setHeader(String name, String value) {
		headers.put(name, Arrays.asList(new String[]{value}));
	}

	@Override
	public void addHeader(String name, String value) {
		Collection<String> values = headers.get(name);
		if (values == null) {
			values = Arrays.asList(new String[]{value});
		} else {
			values.add(value);
		}
		headers.put(name, values);
	}

	@Override
	public void setIntHeader(String name, int value) {
		setHeader(name, Integer.toString(value));
	}

	@Override
	public void addIntHeader(String name, int value) {
		addHeader(name, Integer.toString(value));
	}

	@Override
	public void setStatus(int sc) {
		responseCode = sc;
	}

	@Deprecated
	@Override
	public void setStatus(int sc, String sm) {
		throw new UnsupportedOperationException("Not implemented"); //$NON-NLS-1$
	}

	@Override
	public int getStatus() {
		return responseCode;
	}

	@Override
	public String getHeader(String name) {
		Collection<String> values = headers.get(name);
		return (values == null) ? null : values.iterator().next();

	}

	@Override
	public Collection<String> getHeaders(String name) {
		return headers.get(name);
	}

	@Override
	public Collection<String> getHeaderNames() {
		return headers.keySet();
	}

	private static final String dateFormatString = "EEE, dd MMM yyyy HH:mm:ss z"; //$NON-NLS-1$

	private String formatDate(long date) {
		Calendar calendar = Calendar.getInstance();
	    SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatString, Locale.US);
	    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //$NON-NLS-1$
	    return dateFormat.format(calendar.getTime());
	}
}
