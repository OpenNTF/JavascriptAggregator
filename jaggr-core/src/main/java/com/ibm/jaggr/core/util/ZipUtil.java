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
package com.ibm.jaggr.core.util;

import com.ibm.jaggr.core.impl.layer.VariableGZIPOutputStream;

import com.google.common.io.Files;

import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 *	Utilities for creating and unpacking zip files
 */
public class ZipUtil {
	private static final String sourceClass = ZipUtil.class.getName();
	private static final Logger log = Logger.getLogger(sourceClass);

	/**
	 * Extracts the specified zip file to the specified location. If {@code selector} is specified,
	 * then only the entry specified by {@code selector} (if {@code selector} is a filename) or the
	 * contents of the directory specified by {@code selector} (if {@code selector} is a directory
	 * name) will be extracted. If {@code selector} specifies a directory, then the contents of the
	 * directory in the zip file will be rooted at {@code destDir} when extracted.
	 *
	 * @param zipFile
	 *            the {@link File} object for the file to unzip
	 * @param destDir
	 *            the {@link File} object for the target directory
	 * @param selector
	 *            The name of a file or directory to extract
	 * @throws IOException
	 */
	public static void unzip(File zipFile, File destDir, String selector) throws IOException {
		final String sourceMethod = "unzip";  //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{zipFile, destDir});
		}

		boolean selectorIsFolder = selector != null && selector.charAt(selector.length()-1) == '/';
		ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFile));
		try {
			ZipEntry entry = zipIn.getNextEntry();
			// iterates over entries in the zip file
			while (entry != null) {
				String entryName = entry.getName();
				if (selector == null ||
					!selectorIsFolder && entryName.equals(selector) ||
					selectorIsFolder && entryName.startsWith(selector) && entryName.length() != selector.length())
				{
					if (selector != null) {
						if (selectorIsFolder) {
							// selector is a directory.  Strip selected path
							entryName = entryName.substring(selector.length());
						} else {
							// selector is a filename.  Extract the filename portion of the path
							int idx = entryName.lastIndexOf("/"); //$NON-NLS-1$
							if (idx != -1) {
								entryName = entryName.substring(idx+1);
							}
						}
					}
					File file = new File(destDir, entryName.replace("/", File.separator)); //$NON-NLS-1$
					if (!entry.isDirectory()) {
						// if the entry is a file, extract it
						extractFile(entry, zipIn, file);
					} else {
						// if the entry is a directory, make the directory
						extractDirectory(entry, file);
					}
					zipIn.closeEntry();
				}
				entry = zipIn.getNextEntry();
			}
		} finally {
			zipIn.close();
		}

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	/**
	 * Extracts the file entry to the location specified by {@code file}
	 *
	 * @param entry
	 *            the {@link ZipEntry} object for the directory being extracted
	 * @param zipIn
	 *            the zip input stream to read from
	 * @param file
	 *            the {@link File} object for the target file
	 * @throws IOException
	 */
	private static void extractFile(ZipEntry entry, ZipInputStream zipIn, File file) throws IOException {
		final String sourceMethod = "extractFile"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{entry, zipIn, file});
		}

		Files.createParentDirs(file);
		BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
		try {
			IOUtils.copy(zipIn, bos);
		} finally {
			bos.close();
		}
		if (!file.setLastModified(entry.getTime())) {
			throw new IOException("Failed to set last modified time for " + file.getAbsolutePath()); //$NON-NLS-1$
		}

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	/**
	 * Extracts the directory entry to the location specified by {@code dir}
	 *
	 * @param entry
	 *            the {@link ZipEntry} object for the directory being extracted
	 * @param dir
	 *            the {@link File} object for the target directory
	 *
	 * @throws IOException
	 */
	private static void extractDirectory(ZipEntry entry, File dir) throws IOException {
		final String sourceMethod = "extractFile"; //$NON-NLS-1$
		final boolean isTraceLogging = log.isLoggable(Level.FINER);
		if (isTraceLogging) {
			log.entering(sourceClass, sourceMethod, new Object[]{entry, dir});
		}

		dir.mkdir();	// May fail if the directory has already been created
		if (!dir.setLastModified(entry.getTime())) {
			throw new IOException("Failed to set last modified time for " + dir.getAbsolutePath()); //$NON-NLS-1$
		}

		if (isTraceLogging) {
			log.exiting(sourceClass, sourceMethod);
		}
	}

	public static class Packer {

		private ZipOutputStream zos;

		public void open(File outputFile) throws IOException {
			FileOutputStream fos = new FileOutputStream(outputFile);
			zos = new ZipOutputStream(fos);
		}

		public void close() throws IOException {
			zos.close();
		}

		/**
		 * Creates a zip file for the contents of the specified directory
		 *
		 * @param dir
		 *            the {@link File} object for the directory to pack
		 * @param rootPath
		 *            the root path of the content within the bundle
		 * @throws IOException
		 */
		public void packDirectory(File dir, String rootPath) throws IOException {
			final String sourceMethod = "packDirectory"; //$NON-NLS-1$
			final boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(sourceClass, sourceMethod, new Object[]{dir, rootPath});
			}

			// iterate directory structure recursively and add zip entries
			packDirContents(dir, "", rootPath != null ? rootPath : ""); //$NON-NLS-1$ //$NON-NLS-2$

			// Close the streams
			zos.closeEntry();

			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod);
			}
		}

		/**
		 * Adds the contents of the specified directory to the zip output stream
		 *
		 * @param root
		 *            the {@link File} object for the root directory
		 * @param dirPath
		 *            the path of the directory being added (relative to {@code root})
		 * @param zipPath
		 *            the zip path entry, or null if the same as {@code dirPath}
		 * @throws IOException
		 */
		private void packDirContents(File root, String dirPath, String zipPath) throws IOException {
			final String sourceMethod = "packDirContents"; //$NON-NLS-1$
			final boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(sourceClass, sourceMethod, new Object[]{root, dirPath});
			}

			// Iterate through the directory entries
			for (File dirElement : new File(root, dirPath).listFiles()) {
				String dirEntryName = joinPaths(dirPath, dirElement.getName());
				String zipEntryName = joinPaths(zipPath, dirElement.getName());
				// Construct each element full path
				// For directories - go down the directory tree recursively
				if (dirElement.isDirectory()) {
					packDirContents(root, dirEntryName, zipEntryName);
					packDirectory(zipEntryName, dirElement.lastModified());

				} else {
					// For files add the a ZIP entry
					packFile(zipEntryName, dirElement);
				}
			}

			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod);
			}
		}

		/**
		 * Adds the specified directory to the zip output stream
		 *
		 * @param name
		 *            the zip entry name
		 * @param dir
		 *            the {@link File} object for the directory being added
		 * @param lastModified
		 *            the lastModified time of the directory
		 * @throws IOException
		 */
		private void packDirectory(String name, long lastModified) throws IOException {
			final String sourceMethod = "packDirectory"; //$NON-NLS-1$
			final boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(sourceClass, sourceMethod, new Object[]{name, lastModified});
			}

			if (!name.endsWith("/")) { //$NON-NLS-1$
				name = name + "/"; //$NON-NLS-1$
			}
			ZipEntry ze = new ZipEntry(name);
			ze.setTime(lastModified);
			zos.putNextEntry(ze);
			zos.closeEntry();

			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod);
			}
		}

		/**
		 * Adds the specified file to the zip output stream
		 *
		 * @param name
		 *            the zip entry name of the file
		 * @param file
		 *            the {@link File} object for the file being added
		 * @throws IOException
		 */
		private void packFile(String name, File file) throws IOException {
			final String sourceMethod = "packFile"; //$NON-NLS-1$
			final boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(sourceClass, sourceMethod, new Object[]{name, file});
			}
			FileInputStream fis = new FileInputStream(file);
			try {
				packEntryFromStream(name, fis, file.lastModified());
			} finally {
				fis.close();
			}
			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod);
			}
		}

		/**
		 * Adds the content from the input stream to the specified file entry in the zip file
		 *
		 * @param name
		 *            the zip entry name of the file
		 * @param is
		 *            the input stream for the file contents
		 * @param lastModified
		 *            the last modified time of the entry
		 * @throws IOException
		 */
		public void packEntryFromStream(String name, InputStream is, long lastModified) throws IOException {
			final String sourceMethod = "packEntryFromStream"; //$NON-NLS-1$
			final boolean isTraceLogging = log.isLoggable(Level.FINER);
			if (isTraceLogging) {
				log.entering(sourceClass, sourceMethod, new Object[]{name, is});
			}
			ZipEntry ze = new ZipEntry(name);
			ze.setTime(lastModified);
			zos.putNextEntry(ze);
			IOUtils.copy(is, zos);
			if (isTraceLogging) {
				log.exiting(sourceClass, sourceMethod);
			}
		}
	}

	/**
	 * Returns a byte array containing the gzipped contents of the input stream
	 *
	 * @param in
	 *            the input stream to zip
	 * @return the gzipped contents in a byte array
	 * @throws IOException
	 */
	static public byte[] zip(InputStream in) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		VariableGZIPOutputStream compress = new VariableGZIPOutputStream(bos, 10240);  // is 10k too big?
		compress.setLevel(Deflater.BEST_COMPRESSION);
		Writer writer = new OutputStreamWriter(compress, "UTF-8"); //$NON-NLS-1$
		// Copy the data from the input stream to the output, compressing as we go.
		CopyUtil.copy(in, writer);
		return bos.toByteArray();
	}

	/**
	 * Returns the unzipped contents of the zipped input stream in a byte array
	 *
	 * @param in
	 *            the input stream to unzip
	 * @return the unzipped content in a byte array
	 * @throws IOException
	 */
	static public byte[] unzip(InputStream in) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		CopyUtil.copy(new GZIPInputStream(in), bos);
		return bos.toByteArray();
	}
	private static String joinPaths(String left, String right) {
		// make sure left ends with a '/' unless it's empty
		if (left.length() > 0 && left.charAt(left.length()-1) != '/') {
			left = left + '/';
		}
		// make sure right doesn't start with a '/'
		while (right.charAt(0) == '/') {
			right = right.substring(1);
		}
		return left+right;
	}
}
