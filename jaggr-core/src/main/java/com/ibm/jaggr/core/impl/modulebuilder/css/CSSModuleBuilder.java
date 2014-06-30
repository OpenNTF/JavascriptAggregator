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

package com.ibm.jaggr.core.impl.modulebuilder.css;

import com.ibm.jaggr.core.IAggregator;
import com.ibm.jaggr.core.IAggregatorExtension;
import com.ibm.jaggr.core.IExtensionInitializer;
import com.ibm.jaggr.core.IServiceRegistration;
import com.ibm.jaggr.core.IShutdownListener;
import com.ibm.jaggr.core.cachekeygenerator.AbstractCacheKeyGenerator;
import com.ibm.jaggr.core.cachekeygenerator.ICacheKeyGenerator;
import com.ibm.jaggr.core.config.IConfig;
import com.ibm.jaggr.core.config.IConfigListener;
import com.ibm.jaggr.core.impl.modulebuilder.text.TextModuleBuilder;
import com.ibm.jaggr.core.options.IOptions;
import com.ibm.jaggr.core.readers.CommentStrippingReader;
import com.ibm.jaggr.core.resource.IResource;
import com.ibm.jaggr.core.transport.IHttpTransport;
import com.ibm.jaggr.core.util.CopyUtil;
import com.ibm.jaggr.core.util.PathUtil;
import com.ibm.jaggr.core.util.TypeUtil;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

/**
 * This class optimizes CSS module resources that are loaded by the AMD
 * aggregator. The following optimizations are performed:
 * <ul>
 * <li>Comment removal
 * <li>Unnecessary white-space and token removal
 * <li>In-lining of &#064;imports
 * <li>In-lining of image URLs using the Data URI scheme
 * </ul>
 * This module works by extending TextModuleBuilder and processing the text stream
 * associated with the Reader that is returned by the overridden method
 * {@link #getContentReader(String, com.ibm.jaggr.core.resource.IResource, javax.servlet.http.HttpServletRequest, java.util.List)}.
 * <h2>Comment removal</h2>
 * <p>
 * Removes comments identified by /* ... *&#047; comment tags
 * <h2>Unnecessary white-space and token removal</h2>
 * <p>
 * Removes carriage returns, line-feeds, tabs and multiple spaces, replacing
 * them with a single space character where necessary. It will also remove
 * unneeded tokens like trailing semi-colons and quotes surrounding URLs.
 * <h2>In-lining of &#064;imports</h2>
 * <p>
 * &#064;import statements will be replaced with the contents of the imported
 * CSS. In order for this to work, the &#064;import URLs must be relative, and
 * the must be locatable on the server relative to the location of the importing
 * CSS. Non-relative URLs may be used in &#064;import statements but their use
 * is severely restricted by CSS rules which require that all &#064;import
 * statements be ahead of any styles in a document. Since in-lining an
 * &#064;import replaces it with the contents of the imported style-sheet, any
 * in-lined style sheets which contain style definitions will cause subsequent
 * &#064;import statements which don't get in-lined to be ignored.
 * <p>
 * Relative URLs in imported CSS files will be rewritten to make them relative
 * to the location of the top level CSS module that is being requested.
 * <p>
 * In-lining of &#64;import statements is controlled by the following property
 * specified in the server-side AMD config: <blockquote>
 * <dl>
 * <dt>{@link #INLINEIMPORTS_CONFIGPARAM}
 * <dt>
 * <dd>If true, then &#064;import statements will be inlined. The default value
 * is true.</dd>
 * </edl> </blockquote>
 * <h2>In-lining of image URLs using the Data URI scheme</h2>
 * <p>
 * <a name="foo">Foo</a> Image URLs in CSS can optionally be in-lined, replacing
 * the URL with the base64 encoded contents of the image using the <a
 * href="http://en.wikipedia.org/wiki/Data_URI_scheme">Data URI scheme</a>.
 * In-lining of image URLs by is controlled by the following properties specified
 * in the server-side AMD config:
 * <blockquote>
 * <dl>
 * <dt>{@link #SIZETHRESHOLD_CONFIGPARAM}</dt>
 * <dd>This parameter specifies the maximum size of an image that may be
 * in-lined. Images larger than this size will not be in-lined. The size is
 * specified in bytes. The default size is 0</dd>
 * <dt>{@link #IMAGETYPES_CONFIGPARAM}</dt>
 * <dd>A comma delimited list of mime types that may be in-lined. The standard
 * image types (image/gif, image/png, image/jpeg and image/tiff) do not need to
 * be specified. If you want to in-line any other image types, you can add the
 * mime type to this list.</dd>
 * <dt>{@link #EXCLUDELIST_CONFIGPARAM}</dt>
 * <dd>A comma delimited list of filespecs to be excluded from in-lining. The
 * filespec may contain the wildcard characters * and ?. If the filespec does
 * not contain the path separator character '/', then the filespec is matched
 * against the filename portion of image resource only. If the filespec does
 * contain a path separator, then it is matched against the full path of the
 * image resource on the server. Note that * and ? will match the path separator
 * so, for example, the filespec *&#047;foo/bar.css will match any path that
 * ends with /foo/bar.css. Any image resource that matches one of these
 * filespecs will not be in-lined.</dd>
 * <dt>{@link #INCLUDELIST_CONFIGPARAM}</dt>
 * <dd>A comma delimited list of filespecs to be in-lined. Resources that match
 * these filespecs will be in-lined, even if they don't meet any of the other
 * qualifications for in-lining, and no other files will be inlined (i.e. the
 * include list is a white-list). Filespec matching rules are the same as for
 * inlinedImageExcludeList.</dd>
 * </dl>
 * </blockquote>
 */
public class CSSModuleBuilder extends TextModuleBuilder implements  IExtensionInitializer, IShutdownListener, IConfigListener {

	static final Logger log = Logger.getLogger(CSSModuleBuilder.class.getName());

	// Custom server-side AMD config param names
	static public final String INLINEIMPORTS_CONFIGPARAM = "inlineCSSImports"; //$NON-NLS-1$
	static public final String INCLUDELIST_CONFIGPARAM = "inlinedImageIncludeList"; //$NON-NLS-1$
	static public final String EXCLUDELIST_CONFIGPARAM = "inlinedImageExcludeList"; //$NON-NLS-1$
	static public final String IMAGETYPES_CONFIGPARAM = "inlineableImageTypes"; //$NON-NLS-1$
	static public final String SIZETHRESHOLD_CONFIGPARAM = "inlinedImageSizeThreshold";  //$NON-NLS-1$

	// Custom server-side AMD config param default values
	static public final boolean INLINEIMPORTS_DEFAULT_VALUE = true;
	static public final int SIZETHRESHOLD_DEFAULT_VALUE = 0;

	static public final String INLINEIMPORTS_REQPARAM_NAME = "inlineImports"; //$NON-NLS-1$
	static public final String INLINEIMAGES_REQPARAM_NAME = "inlineImages"; //$NON-NLS-1$

	static final protected Pattern urlPattern = Pattern.compile("url\\(\\s*([^\\)]+)\\s*\\)?"); //$NON-NLS-1$
	static final protected Pattern protocolPattern = Pattern.compile("^[a-zA-Z]*:"); //$NON-NLS-1$

	static final protected Collection<String> s_inlineableImageTypes;

	static {
		s_inlineableImageTypes = new ArrayList<String>();
		s_inlineableImageTypes.add("image/gif"); //$NON-NLS-1$
		s_inlineableImageTypes.add("image/png"); //$NON-NLS-1$
		s_inlineableImageTypes.add("image/jpeg"); //$NON-NLS-1$
		s_inlineableImageTypes.add("image/tiff"); //$NON-NLS-1$
	};

	@SuppressWarnings("serial")
	static private final AbstractCacheKeyGenerator s_cacheKeyGenerator = new AbstractCacheKeyGenerator() {
		// This is a singleton, so default equals() is sufficient
		private final String eyecatcher = "css"; //$NON-NLS-1$
		@Override
		public String generateKey(HttpServletRequest request) {
			boolean inlineImports = TypeUtil.asBoolean(request.getParameter(CSSModuleBuilder.INLINEIMPORTS_REQPARAM_NAME));
			boolean inlineImages = TypeUtil.asBoolean(request.getParameter(CSSModuleBuilder.INLINEIMAGES_REQPARAM_NAME));
			boolean showFilenames = TypeUtil.asBoolean(request.getAttribute(IHttpTransport.SHOWFILENAMES_REQATTRNAME));
			StringBuffer sb = new StringBuffer(eyecatcher)
			.append(inlineImports ? ":1" : ":0") //$NON-NLS-1$ //$NON-NLS-2$
			.append(inlineImages ? ":1" : ":0") //$NON-NLS-1$ //$NON-NLS-2$
			.append(showFilenames ? ":1" : ":0"); //$NON-NLS-1$ //$NON-NLS-2$
			return sb.toString();
		}
		@Override
		public String toString() {
			return eyecatcher;
		}
	};

	static protected final List<ICacheKeyGenerator> s_cacheKeyGenerators;

	static {
		List<ICacheKeyGenerator> keyGens = new ArrayList<ICacheKeyGenerator>(TextModuleBuilder.s_cacheKeyGenerators.size());
		keyGens.addAll(TextModuleBuilder.s_cacheKeyGenerators);
		keyGens.add(s_cacheKeyGenerator);
		s_cacheKeyGenerators = Collections.unmodifiableList(keyGens);
	}

	//private List<ServiceRegistration> registrations = new LinkedList<ServiceRegistration>();
	private List<IServiceRegistration> registrations = new LinkedList<IServiceRegistration>();
	public int imageSizeThreshold = 0;
	public boolean inlineImports = false;
	private Collection<String> inlineableImageTypes = new ArrayList<String>(s_inlineableImageTypes);
	private Map<String, String> inlineableImageTypeMap = new HashMap<String, String>();
	private Collection<Pattern> inlinedImageIncludeList = Collections.emptyList();
	public Collection<Pattern> inlinedImageExcludeList = Collections.emptyList();

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.impl.text.TextModuleBuilder#getContentReader(java.lang.String, com.ibm.jaggr.service.resource.IResource, javax.servlet.http.HttpServletRequest, com.ibm.jaggr.service.module.ICacheKeyGenerator)
	 */
	@Override
	protected Reader getContentReader(
			String mid,
			IResource resource,
			HttpServletRequest request,
			List<ICacheKeyGenerator> keyGens)
			throws IOException {

		String css = readToString(new CommentStrippingReader(resource.getReader()));
		// in-line @imports
		if (inlineImports) {
			css = inlineImports(request, css, resource, ""); //$NON-NLS-1$
		}
		return processCss(resource, request, css);
	}

	/**
	 * Runs CSS through minification and image inlining.
	 * @param resource The resource representing the CSS file.
	 * @param request The request for the CSS file.
	 * @param css The actual CSS {@link java.lang.String} to process.
	 * @return A {@link java.io.StringReader} for the resulting CSS.
	 * @throws IOException
	 */
	protected Reader processCss(IResource resource, HttpServletRequest request, String css) throws IOException {
		// whitespace
		css = minify(css, resource);

		// Inline images
		css = inlineImageUrls(request, css, resource);

		return new StringReader(css);
	}

	/**
	 * Copies the contents of the specified {@link Reader} to a String.
	 *
	 * @param in The input Reader
	 * @return The contents of the Reader as a String
	 * @throws IOException
	 */
	protected String readToString(Reader in) throws IOException {
		StringWriter out = new StringWriter();
		CopyUtil.copy(in, out);
		return out.toString();

	}

	private static final Pattern quotedStringPattern = Pattern.compile("\\\"[^\\\"]*\\\"|'[^']*'|url\\(([^)]+)\\)"); //$NON-NLS-1$
	private static final Pattern whitespacePattern = Pattern.compile("\\s+", Pattern.MULTILINE); //$NON-NLS-1$
	private static final Pattern endsPattern = Pattern.compile("^\\s|\\s$"); //$NON-NLS-1$
	private static final Pattern closeBracePattern = Pattern.compile("[;\\s]+\\}"); //$NON-NLS-1$
	private static final Pattern delimitersPattern = Pattern.compile("(\\s?[;:,{]\\s?)"); //$NON-NLS-1$
	private static final Pattern forwardSlashPattern = Pattern.compile("\\\\"); //$NON-NLS-1$

	private static final String QUOTED_STRING_MARKER = "__qUoTeDsTrInG"; //$NON-NLS-1$
	private static final Pattern QUOTED_STRING_MARKER_PAT = Pattern.compile("%%" + QUOTED_STRING_MARKER + "([0-9]*)__%%"); //$NON-NLS-1$ //$NON-NLS-2$
	/**
	 * Minifies a CSS string by removing comments and excess white-space, as well as
	 * some unneeded tokens.
	 *
	 * @param css The contents of a CSS file as a String
	 * @param res The resource for the CSS file
	 * @return the minified css
	 */
	protected String minify(String css, IResource res) {

		// replace all quoted strings and url(...) patterns with unique ids so that
		// they won't be affected by whitespace removal.
		LinkedList<String> quotedStringReplacements = new LinkedList<String>();
		Matcher m = quotedStringPattern.matcher(css);
		StringBuffer sb = new StringBuffer();
		int i = 0;
		while (m.find()) {
			String text = (m.group(1) != null) ?
					("url(" + StringUtils.trim(m.group(1)) + ")") :   //$NON-NLS-1$ //$NON-NLS-2$
						m.group(0);
					quotedStringReplacements.add(i, text);
					String replacement = "%%" + QUOTED_STRING_MARKER + (i++) + "__%%"; //$NON-NLS-1$ //$NON-NLS-2$
					m.appendReplacement(sb, ""); //$NON-NLS-1$
					sb.append(replacement);
		}
		m.appendTail(sb);
		css = sb.toString();

		// Get rid of extra whitespace
		css = whitespacePattern.matcher(css).replaceAll(" "); //$NON-NLS-1$
		css = endsPattern.matcher(css).replaceAll(""); //$NON-NLS-1$
		css = closeBracePattern.matcher(css).replaceAll("}"); //$NON-NLS-1$
		m = delimitersPattern.matcher(css);
		sb = new StringBuffer();
		while (m.find()) {
			String text = m.group(1);
			m.appendReplacement(sb, ""); //$NON-NLS-1$
			sb.append(text.length() == 1 ? text : text.replace(" ", "")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		m.appendTail(sb);
		css = sb.toString();

		// restore quoted strings and url(...) patterns
		m = QUOTED_STRING_MARKER_PAT.matcher(css);
		sb = new StringBuffer();
		while (m.find()) {
			i = Integer.parseInt(m.group(1));
			m.appendReplacement(sb, ""); //$NON-NLS-1$
			sb.append(quotedStringReplacements.get(i));
		}
		m.appendTail(sb);
		css = sb.toString();

		return css.toString();
	}

	static final Pattern importPattern = Pattern.compile("\\@import\\s+(?:\\(less\\))?\\s*(url\\()?\\s*([^);]+)\\s*(\\))?([\\w, ]*)(;)?", Pattern.MULTILINE); //$NON-NLS-1$
	/**
	 * Processes the input CSS to replace &#064;import statements with the
	 * contents of the imported CSS.  The imported CSS is minified, image
	 * URLs in-lined, and this method recursively called to in-line nested
	 * &#064;imports.
	 *
	 * @param req
	 *            The request associated with the call.
	 * @param css
	 *            The current CSS containing &#064;import statements to be
	 *            processed
	 * @param res
	 *            The resource for the CSS file.
	 * @param path
	 *            The path, as specified in the &#064;import statement used to
	 *            import the current CSS, or null if this is the top level CSS.
	 *
	 * @return The input CSS with &#064;import statements replaced with the
	 *         contents of the imported files.
	 *
	 * @throws IOException
	 */
	protected String inlineImports(HttpServletRequest req, String css, IResource res, String path) throws IOException {

		// In-lining of imports can be disabled by request parameter for debugging
		if (!TypeUtil.asBoolean(req.getParameter(INLINEIMPORTS_REQPARAM_NAME), true)) {
			return css;
		}

		StringBuffer buf = new StringBuffer();
		IAggregator aggregator = (IAggregator)req.getAttribute(IAggregator.AGGREGATOR_REQATTRNAME);
		IOptions options = aggregator.getOptions();
		/*
		 * True if we should include the name of imported CSS files in a comment at
		 * the beginning of the file.
		 */
		boolean includePreamble
		= TypeUtil.asBoolean(req.getAttribute(IHttpTransport.SHOWFILENAMES_REQATTRNAME))
		&& (options.isDebugMode() || options.isDevelopmentMode());
		if (includePreamble && path != null && path.length() > 0) {
			buf.append("/* @import "  + path + " */\r\n"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		Matcher m = importPattern.matcher(css);
		while (m.find()) {
			String fullMatch = m.group(0);
			String importNameMatch = m.group(2);
			String mediaTypes = m.group(4);

			/*
			 * CSS rules require that all @import statements appear before any
			 * style definitions within a document. Most browsers simply ignore
			 * @import statements which appear following any styles definitions.
			 * This means that once we've inlined an @import, then we can't not
			 * inline any subsequent @imports. The implication is that all
			 * @imports which cannot be inlined (i.e. non-relative url or device
			 * specific media types) MUST appear before any @import that is
			 * inlined. For this reason, we throw an error if we encounter an
			 * @import which we cannot inline if we have already inlined a
			 * previous @import.
			 */

			//Only process media type "all" or empty media type rules.
			if(mediaTypes.length() > 0 && !"all".equals(StringUtils.trim(mediaTypes))){ //$NON-NLS-1$
				m.appendReplacement(buf, ""); //$NON-NLS-1$
				buf.append(fullMatch);
				continue;
			}
			// remove quotes.
			importNameMatch = dequote(importNameMatch);
			importNameMatch = forwardSlashPattern.matcher(importNameMatch).replaceAll("/"); //$NON-NLS-1$

			// if name is not relative, then bail
			if (importNameMatch.startsWith("/") || protocolPattern.matcher(importNameMatch).find()) { //$NON-NLS-1$
				m.appendReplacement(buf, ""); //$NON-NLS-1$
				buf.append(fullMatch);
				continue;
			}

			IResource importRes = res.resolve(importNameMatch);
			String importCss = null;
			importCss = readToString(
					new CommentStrippingReader(
							new InputStreamReader(
									importRes.getURI().toURL().openStream(),
									"UTF-8" //$NON-NLS-1$
									)
							)
					);
			importCss = minify(importCss, importRes);
			// Inline images
			importCss = inlineImageUrls(req, importCss, importRes);

			if (inlineImports) {
				importCss = inlineImports(req, importCss, importRes, importNameMatch);
			}
			m.appendReplacement(buf, ""); //$NON-NLS-1$
			buf.append(importCss);
		}
		m.appendTail(buf);

		css = buf.toString();
		/*
		 * Now re-write all relative URLs in url(...) statements to make them relative
		 * to the importing CSS
		 */
		if (path != null && path.length() > 0) {
			int idx = path.lastIndexOf("/"); //$NON-NLS-1$
			//Make a file path based on the last slash.
			//If no slash, so must be just a file name. Use empty string then.
			path = (idx != -1) ? path.substring(0, idx + 1) : ""; //$NON-NLS-1$
			buf = new StringBuffer();
			m = urlPattern.matcher(css);
			while (m.find()) {
				String fullMatch = m.group(0);
				String urlMatch = m.group(1);

				urlMatch = StringUtils.trim(urlMatch.replace("\\", "/")); //$NON-NLS-1$ //$NON-NLS-2$
				String quoted = ""; //$NON-NLS-1$
				if (urlMatch.charAt(0) == '"' && urlMatch.charAt(urlMatch.length()-1) == '"') {
					quoted = "\""; //$NON-NLS-1$
					urlMatch = urlMatch.substring(1, urlMatch.length()-1);
				} else if (urlMatch.charAt(0) == '\'' && urlMatch.charAt(urlMatch.length()-1) == '\'') {
					quoted = "'"; //$NON-NLS-1$
					urlMatch = urlMatch.substring(1, urlMatch.length()-1);
				}

				// Don't modify non-relative URLs
				if (urlMatch.startsWith("/") || urlMatch.startsWith("#") || protocolPattern.matcher(urlMatch).find()) { //$NON-NLS-1$ //$NON-NLS-2$
					m.appendReplacement(buf, ""); //$NON-NLS-1$
					buf.append(fullMatch);
					continue;
				}

				String fixedUrl = path + ((path.endsWith("/") || path.length() == 0) ? "" : "/") + urlMatch; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				//Collapse '..' and '.'
				String[] parts = fixedUrl.split("/"); //$NON-NLS-1$
				for(int i = parts.length - 1; i > 0; i--){
					if(".".equals(parts[i])){ //$NON-NLS-1$
						parts = (String[]) ArrayUtils.remove(parts, i);
					}else if("..".equals(parts[i])){ //$NON-NLS-1$
						if(i != 0 && !"..".equals(parts[i - 1])){ //$NON-NLS-1$
							parts = (String[]) ArrayUtils.remove(parts, i-1);
							parts = (String[]) ArrayUtils.remove(parts, i-1);
						}
					}
				}
				m.appendReplacement(buf, ""); //$NON-NLS-1$
				buf.append("url(") //$NON-NLS-1$
				.append(quoted)
				.append(StringUtils.join(parts, "/")) //$NON-NLS-1$
				.append(quoted)
				.append(")"); //$NON-NLS-1$
			}
			m.appendTail(buf);
			css = buf.toString();
		}
		return css;
	}

	/**
	 * Replace <code>url(&lt;<i>relative-path</i>&gt;)</code> references in the
	 * input CSS with
	 * <code>url(data:&lt;<i>mime-type</i>&gt;;&lt;<i>base64-encoded-data</i>&gt;</code>
	 * ). The conversion is controlled by option settings as described in
	 * {@link CSSModuleBuilder}.
	 *
	 * @param req
	 *            The request associated with the call.
	 * @param css
	 *            The input CSS
	 * @param res
	 *            The resource for the CSS file
	 * @return The transformed CSS with images in-lined as determined by option
	 *         settings.
	 */
	protected String inlineImageUrls(HttpServletRequest req, String css, IResource res) {
		if (imageSizeThreshold == 0 && inlinedImageIncludeList.size() == 0) {
			// nothing to do
			return css;
		}

		// In-lining of imports can be disabled by request parameter for debugging
		if (!TypeUtil.asBoolean(req.getParameter(INLINEIMAGES_REQPARAM_NAME), true)) {
			return css;
		}

		StringBuffer buf = new StringBuffer();
		Matcher m = urlPattern.matcher(css);
		while (m.find()) {
			String fullMatch = m.group(0);
			String urlMatch = m.group(1);

			// remove quotes.
			urlMatch = dequote(urlMatch);
			urlMatch = forwardSlashPattern.matcher(urlMatch).replaceAll("/"); //$NON-NLS-1$

			// Don't do anything with non-relative URLs
			if (urlMatch.startsWith("/") || urlMatch.startsWith("#") || protocolPattern.matcher(urlMatch).find()) { //$NON-NLS-1$ //$NON-NLS-2$
				m.appendReplacement(buf, ""); //$NON-NLS-1$
				buf.append(fullMatch);
				continue;
			}

			URI imageUri = res.resolve(urlMatch).getURI();
			boolean exclude = false, include = false;

			// Determine if this image is in the include list
			for (Pattern regex : inlinedImageIncludeList) {
				if (regex.matcher(imageUri.getPath()).find()) {
					include = true;
					break;
				}
			}

			// Determine if this image is in the exclude list
			for (Pattern regex : inlinedImageExcludeList) {
				if (regex.matcher(imageUri.getPath()).find()) {
					exclude = true;
					break;
				}
			}
			// If there's an include list, then only the files in the include list
			// will be inlined
			if (inlinedImageIncludeList.size() > 0 && !include || exclude) {
				m.appendReplacement(buf, ""); //$NON-NLS-1$
				buf.append(fullMatch);
				continue;
			}

			boolean imageInlined = false;
			String type = URLConnection.getFileNameMap().getContentTypeFor(imageUri.getPath());
			String extension = PathUtil.getExtension(imageUri.getPath());
			if (type == null) {
				type = inlineableImageTypeMap.get(extension);
			}
			if (type == null) {
				type = "content/unknown"; //$NON-NLS-1$
			}
			if (include || inlineableImageTypes.contains(type) || inlineableImageTypeMap.containsKey(extension)) {
				InputStream in = null;
				try {
					// In-line the image.
					URLConnection connection = imageUri.toURL().openConnection();

					if (include || connection.getContentLength() <= imageSizeThreshold) {
						in = connection.getInputStream();
						String base64 = getBase64(connection);
						m.appendReplacement(buf, ""); //$NON-NLS-1$
						buf.append("url('data:" + type + //$NON-NLS-1$
								";base64," + base64 + "')"); //$NON-NLS-1$ //$NON-NLS-2$
						imageInlined = true;
					}
				} catch (IOException ex) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(
								Level.WARNING,
								MessageFormat.format(
										Messages.CSSModuleBuilder_0,
										new Object[]{imageUri}
										),
										ex
								);
					}
				} finally {
					if (in != null) {
						try {in.close();} catch (IOException ignore) {}
					}
				}
			}
			if (!imageInlined) {
				// Image not in-lined.  Write the original URL
				m.appendReplacement(buf, ""); //$NON-NLS-1$
				buf.append(fullMatch);
			}
		}
		m.appendTail(buf);
		return buf.toString();
	}

	/**
	 * Returns a base64 encoded string representation of the contents of the
	 * resource associated with the {@link URLConnection}.
	 *
	 * @param connection
	 *            The URLConnection object for the resource
	 * @return The base64 encoded string representation of the resource
	 * @throws IOException
	 */
	protected String getBase64(URLConnection connection) throws IOException {
		InputStream in = connection.getInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		CopyUtil.copy(in, out);
		return new String(Base64.encodeBase64(out.toByteArray()), "UTF-8"); //$NON-NLS-1$
	}

	private static final Pattern escaper = Pattern.compile("([\\\\.*?+\\[{|()^$])"); //$NON-NLS-1$

	/**
	 * Returns a regular expression for a filepath that can include standard
	 * file system wildcard characters (e.g. * and ?)
	 *
	 * @param filespec A filespec that can contain wildcards
	 * @return A regular expression to match paths specified by <code>filespec</code>
	 */
	protected Pattern toRegexp(String filespec) {
		Matcher m = escaper.matcher(filespec);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String matched = m.group(0);
			if (matched.equals("*")) {  //$NON-NLS-1$
				m.appendReplacement(sb, "\\[^/]*?"); //$NON-NLS-1$
			} else if (matched.equals("?")) {  //$NON-NLS-1$
				m.appendReplacement(sb, "[^/]"); //$NON-NLS-1$
			} else if (matched.equals("$")) { //$NON-NLS-1$
				m.appendReplacement(sb, "\\\\\\$"); //$NON-NLS-1$
			} else if (matched.equals("\\")) { //$NON-NLS-1$
				m.appendReplacement(sb, "\\\\\\\\"); //$NON-NLS-1$
			} else {
				m.appendReplacement(sb, "\\\\" + matched); //$NON-NLS-1$
			}
		}
		m.appendTail(sb);
		String patStr = sb.toString();
		return Pattern.compile((patStr.startsWith("/") ? "" : "(^|/)") + //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				patStr + "$", Pattern.CASE_INSENSITIVE); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.IModuleBuilder#getCacheKeyGenerator(com.ibm.jaggr.service.IAggregator)
	 */
	@Override	public final List<ICacheKeyGenerator> getCacheKeyGenerators(IAggregator aggregator) {
		return s_cacheKeyGenerators;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.modulebuilder.IModuleBuilder#handles(java.lang.String, com.ibm.jaggr.service.resource.IResource)
	 */
	@Override
	public boolean handles(String mid, IResource resource) {
		return mid.endsWith(".css"); //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IShutdownListener#shutdown(com.ibm.jaggr.service.IAggregator)
	 */
	@Override
	public void shutdown(IAggregator aggregator) {
		for (IServiceRegistration reg : registrations) {
			reg.unregister();
		}
		registrations.clear();
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.IExtensionInitializer#initialize(com.ibm.jaggr.service.IAggregator, com.ibm.jaggr.service.IAggregatorExtension, com.ibm.jaggr.service.IExtensionInitializer.IExtensionRegistrar)
	 */
	@Override
	public void initialize(IAggregator aggregator,
			IAggregatorExtension extension, IExtensionRegistrar registrar) {
		Hashtable<String, String> props;
		props = new Hashtable<String, String>();
		props.put("name", aggregator.getName()); //$NON-NLS-1$
		registrations.add(aggregator.getPlatformServices().registerService(IConfigListener.class.getName(), this, props));
		props = new Hashtable<String, String>();
		props.put("name", aggregator.getName()); //$NON-NLS-1$
		registrations.add(aggregator.getPlatformServices().registerService(IShutdownListener.class.getName(), this, props));
		IConfig config = aggregator.getConfig();
		if (config != null) {
			configLoaded(config, 1);
		}
	}

	/* (non-Javadoc)
	 * @see com.ibm.jaggr.service.config.IConfigListener#configLoaded(com.ibm.jaggr.service.config.IConfig, long)
	 */
	@Override
	public void configLoaded(IConfig conf, long sequence) {
		/** Maximum size of image that can be in-lined */
		Object obj = conf.getProperty(SIZETHRESHOLD_CONFIGPARAM, null);
		imageSizeThreshold = TypeUtil.asInt(obj, SIZETHRESHOLD_DEFAULT_VALUE);

		/** True if &#064;import statements should be inlined */
		obj = conf.getProperty(INLINEIMPORTS_CONFIGPARAM, null);
		inlineImports = TypeUtil.asBoolean(obj, INLINEIMPORTS_DEFAULT_VALUE);

		Collection<String> types = new ArrayList<String>(s_inlineableImageTypes);
		Object oImageTypes = conf.getProperty(IMAGETYPES_CONFIGPARAM, null);
		if (oImageTypes != IConfig.NOT_FOUND && oImageTypes != null) {
			// property can be either a comma delimited string, or a property map
			if (oImageTypes instanceof String) {
				String[] aTypes = oImageTypes.toString().split(","); //$NON-NLS-1$
				for (String type : aTypes) {
					types.add(type);
				}
			} else {
				@SuppressWarnings("unchecked")
				Map<String, String> map = (Map<String, String>)conf.getProperty(IMAGETYPES_CONFIGPARAM, Map.class);
				inlineableImageTypeMap.putAll(map);
			}
		}
		inlineableImageTypes = types;

		/** List of files that should be in-lined */
		Collection<Pattern> list = Collections.emptyList();
		Object oIncludeList = conf.getProperty(INCLUDELIST_CONFIGPARAM, null);
		if (oIncludeList != IConfig.NOT_FOUND && oIncludeList != null) {
			list = new ArrayList<Pattern>();
			for (String s : oIncludeList.toString().split(",")) { //$NON-NLS-1$
				list.add(toRegexp(s));
			}
		}
		inlinedImageIncludeList = list;

		/** List of files that should NOT be in-lined */
		list = Collections.emptyList();
		Object oExcludeList = conf.getProperty(EXCLUDELIST_CONFIGPARAM, null);
		if (oExcludeList != IConfig.NOT_FOUND && oExcludeList != null) {
			list = new ArrayList<Pattern>();
			for (String s : oExcludeList.toString().split(",")) { //$NON-NLS-1$
				list.add(toRegexp(s));
			}
		}
		inlinedImageExcludeList = list;
	}

	/**
	 * Removes single or double quotes from a quoted string. The entire string
	 * is expected to be quoted, with possible leading or trailing whitespace.
	 * If the string is not quoted, then it is returned unmodified.
	 *
	 * @param in
	 *            The possibly quoted string
	 * @return The string with quotes removed
	 */
	public String dequote(String in) {
		String result = in.trim();
		if (result.charAt(0) == '"' && result.charAt(result.length()-1) == '"') {
			return result.substring(1, result.length()-1);
		}
		if (result.charAt(0) == '\'' && result.charAt(result.length()-1) == '\'') {
			return result.substring(1, result.length()-1);
		}
		return result;
	}
}
