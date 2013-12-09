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

package com.ibm.jaggr.core.impl.modulebuilder.i18n;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.cachekeygenerator.I18nCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.impl.modulebuilder.javascript.JavaScriptModuleBuilder;
import com.ibm.jaggr.core.module.IModule;
import com.ibm.jaggr.core.modulebuilder.ModuleBuild;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.resource.IResourceVisitor;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.Prioritized;
import com.ibm.jaggr.core.util.TypeUtil;

/**
 * This class extends {@link JavaScriptModuleBuilder} to add support for expanding
 * the response for root level (non-locale specific) i18n requests to include the
 * local specific versions of the requested resource for the locales specified in
 * the request.
 * <p>
 * Expanding the response to include resources that were not requested requires
 * the module names be exported in the define functions of anonymous modules, so 
 * don't do this if exporting module names is disabled, or if the optimization
 * level is 'none'.
 */
public class I18nModuleBuilder 
extends JavaScriptModuleBuilder {

	public static String OPTION_DISABLE_LOCALE_EXPANSION = "disableLocaleExpansion"; //$NON-NLS-1$
	// regexp for reconstructing the master bundle name from parts of the regexp match
	// nlsRe.exec("foo/bar/baz/nls/en-ca/foo") gives:
	// ["foo/bar/baz/nls/en-ca/foo", "foo/bar/baz/nls/", "/", "/", "en-ca", "foo"]
	// nlsRe.exec("foo/bar/baz/nls/foo") gives:
	// ["foo/bar/baz/nls/foo", "foo/bar/baz/nls/", "/", "/", "foo", ""]
	// so, if match[5] is blank, it means this is the top bundle definition.
	// courtesy of http://requirejs.org and the dojo i18n plugin
	private static final Pattern re = Pattern.compile("(^.*(^|\\/)nls)(\\/|$)([^\\/]*)\\/?([^\\/]*)"); //$NON-NLS-1$

	
	private IAggregator aggregator = null;

	@Override
	public ModuleBuild build(String mid, IResource resource,
			HttpServletRequest request, List<ICacheKeyGenerator> keyGens) throws Exception {
	
		ModuleBuild result = super.build(mid, resource, request, keyGens);
		List<IModule> additionalModules = getExpandedModules(mid, resource, request, keyGens);
		if (keyGens != result.getCacheKeyGenerators()) {
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			List<ICacheKeyGenerator> newKeyGens = new ArrayList<ICacheKeyGenerator>();
			newKeyGens.addAll(result.getCacheKeyGenerators());
			/*
			 * In development mode, we want to detect new resources when they are added so we 
			 * don't remember the list of available locales when the cache key generator is 
			 * created.  We do this by specifying a null availableLocales list.  Since development
			 * mode is an aggregator option and changing options flushes cached responses, a new
			 * cache key generator will be created when development mode is turned off.
			 */
			
			String[] availableLocales = aggr.getOptions().isDevelopmentMode() ?
					null : getAvailableLocales(request, mid, resource, keyGens);
			newKeyGens.add(new CacheKeyGenerator(availableLocales, false));
			keyGens = newKeyGens;
		}
		ModuleBuild mb = new ModuleBuild(result.getBuildOutput(), keyGens, result.isError());
		if (!result.isError()) {
			for (IModule module : additionalModules) {
				mb.addBefore(module);
			}
		}
		return mb;
	}

	protected List<IModule> getExpandedModules(String mid, IResource resource,
			HttpServletRequest request, List<ICacheKeyGenerator> keyGens) throws IOException {
		
		List<IModule> result = Collections.emptyList();
		if (isExpandLocaleResources(request)) {
			Matcher m = re.matcher(mid);
			m.matches();
			Collection<String> availableLocales = Arrays.asList(getAvailableLocales(request, mid, resource, keyGens));
			result = new LinkedList<IModule>();
			Set<String> added = new HashSet<String>();
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);

			// Find the bundles that matche the requested locales.
			@SuppressWarnings("unchecked")
			Collection<String> locales = (Collection<String>)request.getAttribute(IHttpTransport.REQUESTEDLOCALES_REQATTRNAME);
			String bundleName = m.group(4);
			if (locales != null && !locales.isEmpty()) {
				for (String locale : locales) {
					processLocale(resource, result, m, availableLocales, added,
							aggr, bundleName, locale);
				}
			} else {
				// Use the first locale we can match resources for in the Accept-Language header
				locales = parseAcceptLanguageHeader(request);
				for (String locale : locales) {
					processLocale(resource, result, m, availableLocales, added,
							aggr, bundleName, locale);
					if (!result.isEmpty()) break;
				}
			}
		}
		return result;
	}

	@Override
	public String layerBeginEndNotifier(EventType type, HttpServletRequest request, List<IModule> modules, Set<String> dependentFeatures) {
		return null;
	}
	
	private void processLocale(IResource resource, List<IModule> result,
			Matcher m, Collection<String> availableLocales, Set<String> added,
			IAggregator aggr, String bundleName, String locale)
			throws IOException {
		String[] a = locale.split("-"); //$NON-NLS-1$
		String language = a[0].toLowerCase();
		String country = (a.length > 1) ? a[1].toLowerCase() : ""; //$NON-NLS-1$
		String varient = (a.length > 2) ? a[2].toLowerCase() : ""; //$NON-NLS-1$
		if (language.length() > 0 && varient.length() > 0 && country.length() > 0) {
			// Try language + country + region code first
			String tryLocale = language+"-"+country+"-"+varient; //$NON-NLS-1$ //$NON-NLS-2$
			String path = tryLocale + "/" + bundleName; //$NON-NLS-1$
			if (!added.contains(path) && tryAddModule(aggr, result, m.group(1), resource, tryLocale, bundleName, availableLocales)) {
				added.add(path);
				return;
			}
		}
		if (language.length() > 0 && country.length() > 0) {
			// Now try language + country code
			String tryLocale = language+"-"+country; //$NON-NLS-1$
			String path = tryLocale + bundleName;
			if (!added.contains(path) && tryAddModule(aggr, result, m.group(1), resource, tryLocale, bundleName, availableLocales)) {
				added.add(path);
				return;
			}
		} 
		if (language.length() > 0) {
			// Now try just language code
			String tryLocale = language;
			String path = tryLocale+"/"+bundleName; //$NON-NLS-1$
			if (!added.contains(path) && tryAddModule(aggr, result, m.group(1), resource, tryLocale, bundleName, availableLocales)) {
				added.add(path);
			}
		}
	}

	static boolean isExpandLocaleResources(HttpServletRequest request) {
		// Expanding the response to include more than what was requested requires that 
		// we export module names in the define functions of anonymous modules, so don't 
		// expand the response if module name exporting is disabled, or if the optimization
		// level is set to 'none'.
		IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggr.getOptions();
		return 
				!TypeUtil.asBoolean(options.getOption(OPTION_DISABLE_LOCALE_EXPANSION)) &&
				!TypeUtil.asBoolean(request.getAttribute(IHttpTransport.NOADDMODULES_REQATTRNAME));
	}
	
	@SuppressWarnings("unchecked")
	private static String[] getAvailableLocales(
			final HttpServletRequest request, 
			final String mid, 
			final IResource res,
			final List<ICacheKeyGenerator> keyGens) 
	throws IOException {
		String key = I18nModuleBuilder.class.getName() + "." + mid; //$NON-NLS-1$
		ConcurrentMap<String, Object> reqmap = 
			(ConcurrentMap<String, Object>)request.getAttribute(IAggregator.CONCURRENTMAP_REQATTRNAME);
		String[] availableLocales = (String[])reqmap.get(key);
		if (availableLocales != null) 
			return availableLocales;
		
		// The list of available locales isn't in the request.  Try to get it from
		// the cache key generator
		if (keyGens != null) {
			for (ICacheKeyGenerator keyGen : keyGens) {
				if (keyGen.getClass().equals(CacheKeyGenerator.class) && !keyGen.isProvisional()) {
					I18nCacheKeyGenerator i18nKeyGen = ((CacheKeyGenerator)keyGen).keyGen;
					Collection<String> locales = i18nKeyGen.getLocales();
					if (locales != null) {
						return locales.toArray(new String[locales.size()]);
					}
				}
			}
		}
		
		// Get the available locales from disk
		final Collection<String> result = new HashSet<String>();
		final URI baseUri = res.getURI().resolve(""); //$NON-NLS-1$
		final IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		String path = res.getURI().getPath();
		int idx = path.lastIndexOf("/"); //$NON-NLS-1$
		final String resourceName = idx == 0 ? path : path.substring(idx+1);
		IResource baseRes = aggr.newResource(baseUri);
		baseRes.walkTree(new IResourceVisitor() {
			@Override
			public boolean visitResource(Resource resource, String pathName) throws IOException {
				if (resource.isFolder() && !pathName.startsWith(".")) { //$NON-NLS-1$
					URI uri = baseUri.resolve(pathName + "/" + resourceName); //$NON-NLS-1$
					IResource localeRes = aggr.newResource(uri);
					if (localeRes.exists()) {
						result.add(pathName);
					}
				}
				return false;
			}
		});
		availableLocales = result.toArray(new String[result.size()]);
		reqmap.put(key, availableLocales);
		return availableLocales;
	}
	
	/**
	 * Adds the source for the locale specific i18n resource if it exists.
	 * 
	 * @param list
	 *            The list of source files to add the i18n resource to
	 * @param bundleRoot
	 *            The module path for the bundle root (e.g. 'foo/nls')
	 * @param bundleRootRes
	 *            The bundle root resource that was requested
	 * @param localePath
	 *            The path relative to the bundle root for the locale specific
	 *            resource (e.g. 'en-us/bar')
	 * @return True if the source file for for the locale specified resource was
	 *         added
	 * @throws IOException
	 */
	private boolean tryAddModule(
			IAggregator aggregator,
			List<IModule> list, 
			String bundleRoot, 
			IResource bundleRootRes, 
			String locale,
			String resource,
			Collection<String> availableLocales) 
	throws IOException {
		if (availableLocales != null && !availableLocales.contains(locale)) {
			return false;
		}
		boolean result = false;
		URI uri = bundleRootRes.getURI();
		URI testUri = uri.resolve(locale + "/" + resource + ".js"); //$NON-NLS-1$ //$NON-NLS-2$
		IResource testResource = aggregator.newResource(testUri);
		if (availableLocales != null || testResource.exists()) {
			String mid = bundleRoot+"/"+locale+"/"+resource; //$NON-NLS-1$ //$NON-NLS-2$
			IModule module = aggregator.newModule(mid, testUri); 
			list.add(module); 
			result = true;
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.modulebuilder.javascript.JavaScriptModuleBuilder#getCacheKeyGenerator()
	 */
	@Override 
	public List<ICacheKeyGenerator> getCacheKeyGenerators(IAggregator aggregator) {
		// Return a provisional cache key generator
		ArrayList<ICacheKeyGenerator> keyGens = new ArrayList<ICacheKeyGenerator>();
		keyGens.addAll(super.getCacheKeyGenerators(aggregator));
		keyGens.add(new CacheKeyGenerator(null, true));
		return keyGens;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.core.impl.modulebuilder.javascript.JavaScriptModuleBuilder#handles(java.lang.String, com.ibm.jaggr.core.resource.IResource)
	 */
	@Override
	public boolean handles(String mid, IResource resource) {
		Matcher m = re.matcher(mid);
		return super.handles(mid, resource) && 
					m.matches() && m.group(5).length() == 0;
	}
	
	protected IAggregator getAggregator() {
		return aggregator;
	}
	
	
	List<String> parseAcceptLanguageHeader(HttpServletRequest request) {
		String header = request.getHeader("Accept-Language"); //$NON-NLS-1$
		List<String> result = Collections.emptyList();
		if (header != null) {
			PriorityQueue<Prioritized<String>> queue = new PriorityQueue<Prioritized<String>>(10, Prioritized.comparator);
			String[] toks = header.split(","); //$NON-NLS-1$
			for (String tok : toks) {
				int idx = tok.indexOf(";"); //$NON-NLS-1$
				String locale = tok;
				double priority = 1.0;
				if (idx != -1) {
					locale = tok.substring(0, idx);
					String q = tok.substring(idx+1);
					if (q.startsWith("q=")) { //$NON-NLS-1$
						try {
							priority = Double.parseDouble(q.substring(2));
						} catch (NumberFormatException e) {
							continue;
						}
					} else {
						continue;	// unrecognized q value
					}
				}
				queue.offer(new Prioritized<String>(locale, priority));
			}
			result = new LinkedList<String>();
			while (!queue.isEmpty()) {
				result.add(queue.poll().value);
			}
			Collections.reverse(result);
		}
		return result;
	}
	
	/**
	 * This cache key generator is a simple composite cache key generator
	 * that wraps the {@link I18nCacheKeyGenerator} and controls whether
	 * or not the wrapped generator is expressed for a given request based
	 * on the value returned by 
	 * {@link I18nModuleBuilder#isExpandLocaleResources(HttpServletRequest)}.
	 */
	static private final class CacheKeyGenerator implements ICacheKeyGenerator {

		private static final long serialVersionUID = -3519536825171383430L;
		
		private static final String eyeCatcher = "i18nBldr"; //$NON-NLS-1$

		private final I18nCacheKeyGenerator keyGen;
		
		CacheKeyGenerator(String[] availableLocales, boolean provisional) {
			keyGen = new I18nCacheKeyGenerator(
					availableLocales != null ? Arrays.asList(availableLocales) : null,
					provisional);
		}
		
		private CacheKeyGenerator(I18nCacheKeyGenerator keyGen) {
			this.keyGen = keyGen;
		}
		
		@Override
		public String generateKey(HttpServletRequest request) {
			return isExpandLocaleResources(request) ?
					keyGen.generateKey(request) : ""; //$NON-NLS-1$
		}

		@Override
		public ICacheKeyGenerator combine(ICacheKeyGenerator otherKeyGen) {
			if (this.equals(otherKeyGen)) {
				return this;
			}
			return new CacheKeyGenerator(
					(I18nCacheKeyGenerator)keyGen.combine(((CacheKeyGenerator)otherKeyGen).keyGen)
			);
		}

		@Override
		public List<ICacheKeyGenerator> getCacheKeyGenerators(
				HttpServletRequest request) {
			if (!isExpandLocaleResources(request)) {
				return Collections.emptyList();
			}
			List<ICacheKeyGenerator> gens = keyGen.getCacheKeyGenerators(request);
			// Null means keyGen is an identity cache key generator
			return (gens != null) ? gens : Arrays.asList(new ICacheKeyGenerator[]{keyGen});
		}

		@Override
		public boolean isProvisional() {
			return keyGen.isProvisional();
		}

		@Override
		public String toString() {
			return eyeCatcher + ":(" + keyGen.toString() + ")"; //$NON-NLS-1$ //$NON-NLS-2$
		}
		
		@Override
		public boolean equals(Object other) {
			return other != null && getClass().equals(other.getClass()) && keyGen.equals(((CacheKeyGenerator)other).keyGen);
		}
		
		@Override 
		public int hashCode() {
			return getClass().hashCode() * 31 + keyGen.hashCode();
		}
	}
}
