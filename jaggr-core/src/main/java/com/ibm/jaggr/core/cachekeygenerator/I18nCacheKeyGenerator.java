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

package com.ibm.jaggr.core.cachekeygenerator;

import com.ibm.jaggr.core.transport.IHttpTransport;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;

public final class I18nCacheKeyGenerator extends AbstractCollectionCacheKeyGenerator<String> {
	private static final long serialVersionUID = -8488089828754517200L;

	private static final String eyecatcher = "i18n"; //$NON-NLS-1$

	private final Collection<String> availableLocales;
	private final boolean provisional;

	/**
	 * Element constructor.
	 *
	 * @param availableLocales
	 *            Set of available locales that this cache key generator depends on.
	 *            The key output by this key generator will contain only those
	 *            locales from the request that are included in
	 *            {@code availableLocales}.  If the value is null, then all
	 *            the locales specified in the request are included in the
	 *            generated cache key.
	 * @param provisional
	 *            True if this is a provisional cache key generator.
	 */
	public I18nCacheKeyGenerator(Collection<String> availableLocales, boolean provisional) {
		this.availableLocales = availableLocales != null ?
				Collections.unmodifiableCollection(new HashSet<String>(availableLocales)) : null;
				this.provisional = provisional;
	}

	@Override
	public String generateKey(HttpServletRequest request) {
		StringBuffer sb = new StringBuffer();
		@SuppressWarnings("unchecked")
		Collection<String> requestedLocales = (Collection<String>)request.getAttribute(IHttpTransport.REQUESTEDLOCALES_REQATTRNAME);
		if (requestedLocales != null && requestedLocales.size() > 0) {
			if (requestedLocales.contains("*")) { //$NON-NLS-1$
				sb.append("*"); //$NON-NLS-1$
			} else {
				int i = 0;
				for (String locale : requestedLocales) {
					if (availableLocales == null) {
						sb.append(i++ > 0 ? "," : "").append(locale); //$NON-NLS-1$ //$NON-NLS-2$
					} else {
						String[] a = locale.split("-"); //$NON-NLS-1$
						String language = a[0].toLowerCase();
						String country = (a.length > 1) ? a[1].toLowerCase() : ""; //$NON-NLS-1$
						String varient = (a.length > 2) ? a[2].toLowerCase() : ""; //$NON-NLS-1$
						if (
								language.length() > 0 &&
								country.length() > 0 &&
								varient.length() > 0 &&
								availableLocales.contains(language+"-"+country+"-"+varient)) { //$NON-NLS-1$ //$NON-NLS-2$
							sb.append(i++ > 0 ? "," : "").append(language+"-"+country+"-"+varient); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
						} else if (
								language.length() > 0 &&
								country.length() > 0 &&
								availableLocales.contains(language+"-"+country)) { //$NON-NLS-1$
							sb.append(i++ > 0 ? "," : "").append(language+"-"+country); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						} else if (
								language.length() > 0 &&
								availableLocales.contains(language)) {
							sb.append(i++ > 0 ? "," : "").append(language); //$NON-NLS-1$ //$NON-NLS-2$
						}
					}
				}
			}
		}
		return sb.insert(0, eyecatcher+"{").append("}").toString(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public boolean isProvisional() {
		return provisional;
	}

	@Override
	public String toString() {
		// Map features into sorted set so we get predictable ordering of
		// items in the output.
		SortedSet<String> set = availableLocales == null ? null : new TreeSet<String>(availableLocales);
		StringBuffer sb = new StringBuffer(eyecatcher).append(":"); //$NON-NLS-1$
		sb.append(set == null ? "null" : set.toString()); //$NON-NLS-1$
		if (isProvisional()) {
			sb.append(":provisional"); //$NON-NLS-1$
		}
		return sb.toString();
	}

	@Override
	public List<ICacheKeyGenerator> getCacheKeyGenerators(HttpServletRequest request) {
		return null;
	}

	public Collection<String> getLocales() {
		return availableLocales;
	}

	@Override
	protected Collection<String> getCollection() {
		return getLocales();
	}

	@Override
	protected AbstractCollectionCacheKeyGenerator<String> newKeyGen(Collection<String> set,
			boolean isProvisional) {
		return new I18nCacheKeyGenerator(set, isProvisional);
	}
}