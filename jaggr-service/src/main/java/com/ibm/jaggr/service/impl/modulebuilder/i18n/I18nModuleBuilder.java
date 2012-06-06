/*
 * Copyright (c) 2004-2012, The Dojo Foundation All Rights Reserved.
 * Available via Academic Free License >= 2.1 OR the modified BSD license.
 * see: http://dojotoolkit.org/license for details
 */

package com.ibm.jaggr.service.impl.modulebuilder.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.JSSourceFile;
import com.ibm.jaggr.service.IAggregator;
import com.ibm.jaggr.service.cachekeygenerator.I18nCacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.service.cachekeygenerator.KeyGenUtil;
import com.ibm.jaggr.service.impl.modulebuilder.javascript.JavaScriptModuleBuilder;
import com.ibm.jaggr.service.modulebuilder.ModuleBuild;
import com.ibm.jaggr.service.resource.IResource;
import com.ibm.jaggr.service.resource.IResourceVisitor;
import com.ibm.jaggr.service.transport.IHttpTransport;
import com.ibm.jaggr.service.util.TypeUtil;

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
 *   
 * @author chuckd@us.ibm.com
 */
public class I18nModuleBuilder 
extends JavaScriptModuleBuilder {

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
			HttpServletRequest request, ICacheKeyGenerator[] keyGens) throws Exception {
	
		ModuleBuild result = super.build(mid, resource, request, keyGens);
		if (KeyGenUtil.isProvisional(keyGens)) {
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
			List<ICacheKeyGenerator> newKeyGens = new ArrayList<ICacheKeyGenerator>();
			newKeyGens.addAll(Arrays.asList(result.getCacheKeyGenerators()));
			/*
			 * In development mode, we want to detect new resources when they are added so we 
			 * don't remember the list of available locales when the cache key generator is 
			 * created.  We do this by specifying a null availableLocales list.  Since development
			 * mode is an aggregator option and changing options flushes cached responses, a new
			 * cache key generator will be created when development mode is turned off.
			 */
			
			Matcher m = re.matcher(mid);
			m.matches();
			String[] availableLocales = aggr.getOptions().isDevelopmentMode() ?
					null : getAvailableLocales(request, mid, resource, keyGens);
			newKeyGens.add(new CacheKeyGenerator(availableLocales, false));
			keyGens = newKeyGens.toArray(new ICacheKeyGenerator[newKeyGens.size()]);
		}
		return new ModuleBuild(result.getBuildOutput(), keyGens, result.isError());
	}
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.module.impl.javascript.JavaScriptModuleBuilder#getJSSource(com.ibm.jaggr.service.module.IModule.Source, com.ibm.jaggr.service.resource.IResource, javax.servlet.http.HttpServletRequest)
	 */
	/**
	 * Overrides the base class method to add the locale specific modules specified in the 
	 * request when a non-locale specific i18n module is requested.
	 */
	@Override
	protected List<JSSourceFile> getJSSource(String mid, IResource resource,
			HttpServletRequest request, ICacheKeyGenerator[] keyGens) throws IOException {
		
		List<JSSourceFile> result;
		if (isExpandLocaleResources(request)) {
			Matcher m = re.matcher(mid);
			m.matches();
			Collection<String> availableLocales = Arrays.asList(getAvailableLocales(request, mid, resource, keyGens));
			result = new LinkedList<JSSourceFile>();
			Set<String> added = new HashSet<String>();
			IAggregator aggr = (IAggregator)request.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);

			// Find the bundles that matche the requested locales.
			@SuppressWarnings("unchecked")
			Collection<String> locales = (Collection<String>)request.getAttribute(IHttpTransport.REQUESTEDLOCALES_REQATTRNAME);
			if (locales != null) {
				String bundleName = m.group(4);
				for (String locale : locales) {
					String[] a = locale.split("-"); //$NON-NLS-1$
					String language = a[0].toLowerCase();
					String country = (a.length > 1) ? a[1].toLowerCase() : ""; //$NON-NLS-1$
					String varient = (a.length > 2) ? a[2].toLowerCase() : ""; //$NON-NLS-1$
					if (language.length() > 0 && varient.length() > 0 && country.length() > 0) {
						// Try language + country + region code first
						String tryLocale = language+"-"+country+"-"+varient; //$NON-NLS-1$ //$NON-NLS-2$
						String path = tryLocale + "/" + bundleName; //$NON-NLS-1$
						if (!added.contains(path) && tryAddJSSourceFile(aggr, result, m.group(1), resource, tryLocale, bundleName, availableLocales)) {
							added.add(path);
							continue;
						}
					}
					if (language.length() > 0 && country.length() > 0) {
						// Now try language + country code
						String tryLocale = language+"-"+country; //$NON-NLS-1$
						String path = tryLocale + bundleName;
						if (!added.contains(path) && tryAddJSSourceFile(aggr, result, m.group(1), resource, tryLocale, bundleName, availableLocales)) {
							added.add(path);
							continue;
						}
					} 
					if (language.length() > 0) {
						// Now try just language code
						String tryLocale = language;
						String path = tryLocale+"/"+bundleName; //$NON-NLS-1$
						if (!added.contains(path) && tryAddJSSourceFile(aggr, result, m.group(1), resource, tryLocale, bundleName, availableLocales)) {
							added.add(path);
						}
					}
				}
			}
			// Add the root bundle.  Do this last so that the the dependent, language specific bundles
			// will already be defined when the root bundle is processed.  That way, the loader won't
			// try to load any of the language specific bundles that we already sent while it is
			// processing the root bundle.
			addJSSourceFile(result, m.group(0), resource);
		} else {
			result = super.getJSSource(mid, resource, request, keyGens);
		}
		return result;
	}

	static boolean isExpandLocaleResources(HttpServletRequest request) {
		// Expanding the response to include more than what was requested requires that 
		// we export module names in the define functions of anonymous modules, so don't 
		// expand the response if module name exporting is disabled, or if the optimization
		// level is set to 'none'.
        CompilationLevel level = getCompilationLevel(request);
		return (TypeUtil.asBoolean(request.getAttribute(IHttpTransport.EXPORTMODULENAMES_REQATTRNAME))
				&& level != null); 
	}
	
	@SuppressWarnings("unchecked")
	private static String[] getAvailableLocales(
			final HttpServletRequest request, 
			final String mid, 
			final IResource res,
			final ICacheKeyGenerator[] keyGens) 
	throws IOException {
		String key = I18nModuleBuilder.class.getName() + "." + mid; //$NON-NLS-1$
		ConcurrentMap<String, Object> reqmap = 
			(ConcurrentMap<String, Object>)request.getAttribute(IAggregator.CONCURRENTMAP_REQATTRNAME);
		String[] availableLocales = (String[])reqmap.get(key);
		if (availableLocales != null) 
			return availableLocales;
		
		// The list of available locales isn't in the request.  Try to get it from
		// the cache key generator
		for (ICacheKeyGenerator keyGen : keyGens) {
			if (keyGen.getClass().equals(CacheKeyGenerator.class) && !keyGen.isProvisional()) {
				I18nCacheKeyGenerator i18nKeyGen = ((CacheKeyGenerator)keyGen).keyGen;
				Collection<String> locales = i18nKeyGen.getLocales();
				if (locales != null) {
					return locales.toArray(new String[locales.size()]);
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
	 * Adds the source for the specified resource to the list
	 * 
	 * @param list
	 *            The source file list to add to
	 * @param mid
	 *            The module id for the resource
	 * @param res
	 *            The resource object
	 * @throws IOException
	 */
	private void addJSSourceFile(List<JSSourceFile> list, String mid, IResource res) throws IOException {
		InputStream in = res.getInputStream();
		JSSourceFile sf = JSSourceFile.fromInputStream(mid, in);
		sf.setOriginalPath(res.getURI().toString());
		in.close();
		list.add(sf);
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
	private boolean tryAddJSSourceFile(
			IAggregator aggregator,
			List<JSSourceFile> list, 
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
			addJSSourceFile(list, bundleRoot+"/"+locale+"/"+resource, testResource); //$NON-NLS-1$ //$NON-NLS-2$
			result = true;
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#getCacheKeyGenerator()
	 */
	@Override 
	public ICacheKeyGenerator[] getCacheKeyGenerators(IAggregator aggregator) {
		// Return a provisional cache key generator
		ArrayList<ICacheKeyGenerator> keyGens = new ArrayList<ICacheKeyGenerator>();
		keyGens.addAll(Arrays.asList(super.getCacheKeyGenerators(aggregator)));
		keyGens.add(new CacheKeyGenerator(null, true));
		return keyGens.toArray(new ICacheKeyGenerator[keyGens.size()]);
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.impl.javascript.JavaScriptModuleBuilder#handles(java.lang.String, com.ibm.jaggr.service.resource.IResource)
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
	
	/**
	 * This cache key generator is a simple composite cache key generator
	 * that wraps the {@link I18nCacheKeyGenerator} and controls whether
	 * or not the wrapped generator is expressed for a given request based
	 * on the value returned by 
	 * {@link I18nModuleBuilder#isExpandLocaleResources(HttpServletRequest)}.
	 */
	static private final class CacheKeyGenerator implements ICacheKeyGenerator {

		private static final long serialVersionUID = -3519536825171383430L;

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
			CacheKeyGenerator other = (CacheKeyGenerator)otherKeyGen;
			return new CacheKeyGenerator(
					(I18nCacheKeyGenerator)keyGen.combine(other.keyGen)
			);
		}

		@Override
		public ICacheKeyGenerator[] getCacheKeyGenerators(
				HttpServletRequest request) {
			if (!isExpandLocaleResources(request)) {
				return new ICacheKeyGenerator[0];
			}
			ICacheKeyGenerator[] gens = keyGen.getCacheKeyGenerators(request);
			// Null means keyGen is an identity cache key generator
			return (gens != null) ? gens : new ICacheKeyGenerator[]{keyGen};
		}

		@Override
		public boolean isProvisional() {
			return keyGen.isProvisional();
		}

		@Override
		public String toString() {
			return keyGen.toString();
		}
	}
}
