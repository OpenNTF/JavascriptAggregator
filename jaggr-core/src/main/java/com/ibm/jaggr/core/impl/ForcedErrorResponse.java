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
package com.ibm.jaggr.core.impl;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class encapsulates the forced error response processing for the Aggregator
 */
public class ForcedErrorResponse {
	static Pattern pat = Pattern.compile("^(\\d*)\\s*(\\d*)?\\s*(\\d*)?\\s*(.*)$"); //$NON-NLS-1$

	private int status = 0;
	private AtomicInteger skip = new AtomicInteger(0);
	private AtomicInteger count = new AtomicInteger(1);
	private URI responseBody = null;

	/**
	 * Constructor
	 *
	 * @param info
	 *            String of the form {@code <status> <count> <skip> <response>}
	 *            </br></br> where {@code <status>} is required and is a number specifying
	 *            the HTTP response code that should be returned for the next request.
	 *            {@code <count>} is optional and specifies
	 *            the number of times that the specified response status should be returned. If it
	 *            is not specified, then the response status will be returned only once.
	 *            {@code skip} is optional and specifies the number of requests to process normally
	 *            before returning the specified response status.
	 *            {@code <response text>} is optional and specifies the text that should be returned
	 *            in the response body. If it is not specified, then no response text is include.
	 *
	 * @throws URISyntaxException
	 * @throws NumberFormatException
	 * @throws IllegalArgumentException
	 */
	public ForcedErrorResponse(String info) throws URISyntaxException {
		Matcher m = pat.matcher(info);
		if (m.find()) {
			status = Integer.parseInt(m.group(1));
			String s = m.group(2);
			if (s != null && s.length() > 0) {
				count.set(Integer.parseInt(s));
			}
			s = m.group(3);
			if (s != null && s.length() > 0) {
				skip.set(Integer.parseInt(s));
			}
			String rest = m.group(4);
			if (rest != null && rest.length() > 0) {
				responseBody = new URI(rest);
			}
			if (count.get() <= 0) {
				status = 0;
			}
		} else {
			throw new IllegalArgumentException(info);
		}
	}
	/**
	 * Returns the error response status that should be returned for the current
	 * response.  If the value is zero, then the normal response should be returned
	 * and this method should be called again for the next response.  If the value
	 * is less than 0, then this error response status object is spent and may be
	 * discarded.
	 *
	 * @return the error response status
	 */
	public int getStatus() {
		int result = status;
		if (status > 0) {
			if (skip.getAndDecrement() <= 0) {
				if (count.getAndDecrement() <= 0) {
					result = status = -1;
				}
			} else {
				result = 0;
			}
		}
		return result;
	}

	int peekStatus() {
		return status;
	}

	int getCount() {
		return count.get();
	}

	int getSkip() {
		return skip.get();
	}

	/**
	 * @return the error response URI
	 */
	public URI getResponseBody() {
		return responseBody;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("status:").append(status); //$NON-NLS-1$
		sb.append(", count:").append(count); //$NON-NLS-1$
		sb.append(", skip:").append(skip); //$NON-NLS-1$
		if (responseBody != null) {
			sb.append(", response:").append(responseBody); //$NON-NLS-1$
		}
		return sb.toString();
	}
}
