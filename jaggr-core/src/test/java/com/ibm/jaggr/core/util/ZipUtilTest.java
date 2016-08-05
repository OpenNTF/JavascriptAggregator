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
package com.ibm.jaggr.core.util;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class ZipUtilTest {
	private File tmpdir, sourceDir, targetDir;
	private static final String ROOT_FILE_CONTENTS = "root file contents";
	private static final String FILE1_CONTENTS = "file 1 contents";
	private static final String FILE2_CONTENTS = "file 2 contents";


	@Before
	public void setUp() throws Exception {
		tmpdir = Files.createTempDir();
		sourceDir = new File(tmpdir, "source");
		targetDir = new File(tmpdir, "target");
		sourceDir.mkdir();
		targetDir.mkdir();
		targetDir.setLastModified(sourceDir.lastModified());
		Files.write(ROOT_FILE_CONTENTS, new File(sourceDir, "rootFile.txt"), Charsets.UTF_8);
		File rootDir = new File(sourceDir, "rootDir");
		File subDir = new File(rootDir, "subDir");
		rootDir.mkdir();
		subDir.mkdir();
		File file1 = new File(rootDir, "file1");
		File file2 = new File(subDir, "file2");
		Files.write(FILE1_CONTENTS, file1, Charsets.UTF_8);
		Files.write(FILE2_CONTENTS, file2, Charsets.UTF_8);
		file1.setLastModified(file1.lastModified()-10000);
		subDir.setLastModified(subDir.lastModified()+120000);
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.deleteDirectory(tmpdir);
	}

	@Test
	public void testNullPackerRoot()  throws IOException {
		File zipFile = new File(tmpdir, "test.zip");
		ZipUtil.Packer packer = new ZipUtil.Packer();
		packer.open(zipFile);
		packer.packDirectory(sourceDir, null);
		packer.close();

		ZipUtil.unzip(zipFile, targetDir, null);
		assertDirectoriesSame(sourceDir, targetDir);

		FileUtils.deleteDirectory(targetDir);
		targetDir.mkdir();

		// Specify a directory selector
		ZipUtil.unzip(zipFile, targetDir, "rootDir/");
		assertDirectoriesSame(new File(sourceDir, "rootDir"), targetDir);

		FileUtils.deleteDirectory(targetDir);
		targetDir.mkdir();

		// try again with a non-root directory selector
		ZipUtil.unzip(zipFile, targetDir, "rootDir/subDir/");
		assertDirectoriesSame(new File(sourceDir, "rootDir/subDir"), targetDir);

		FileUtils.deleteDirectory(targetDir);
		targetDir.mkdir();

		// specify a file selector
		ZipUtil.unzip(zipFile, targetDir, "rootFile.txt");
		Assert.assertEquals(1, targetDir.listFiles().length);
		assertFilesSame(new File(sourceDir, "rootFile.txt"), new File(targetDir, "rootFile.txt"));

		FileUtils.deleteDirectory(targetDir);
		targetDir.mkdir();

		// try again with a non-root file selector
		ZipUtil.unzip(zipFile, targetDir, "rootDir/subDir/file2");
		Assert.assertEquals(1, targetDir.listFiles().length);
		assertFilesSame(new File(sourceDir, "rootDir/subDir/file2"), new File(targetDir, "file2"));

	}

	@Test
	public void testNonNullPackerRoot() throws Exception {
		File zipFile = new File(tmpdir, "test.zip");
		ZipUtil.Packer packer = new ZipUtil.Packer();
		packer.open(zipFile);
		packer.packDirectory(sourceDir, "foo/bar");
		packer.close();

		ZipUtil.unzip(zipFile, targetDir, null);
		assertDirectoriesSame(sourceDir, new File(targetDir, "foo/bar"));

		FileUtils.deleteDirectory(targetDir);
		targetDir.mkdir();

		// Specify a directory selector
		ZipUtil.unzip(zipFile, targetDir, "foo/bar/rootDir/");
		assertDirectoriesSame(new File(sourceDir, "rootDir"), targetDir);

		FileUtils.deleteDirectory(targetDir);
		targetDir.mkdir();

		// specify a file selector
		ZipUtil.unzip(zipFile, targetDir, "foo/bar/rootFile.txt");
		Assert.assertEquals(1, targetDir.listFiles().length);
		assertFilesSame(new File(sourceDir, "rootFile.txt"), new File(targetDir, "rootFile.txt"));
	}

	public void assertDirectoriesSame(File dir1, File dir2) throws IOException {
		File[] dir1Entries = dir1.listFiles();
		File[] dir2Entries = dir2.listFiles();

		Assert.assertEquals(dir1Entries.length, dir2Entries.length);

		for (int i = 0; i < dir1Entries.length; i++) {
			Assert.assertEquals(dir1Entries[i].getName(), dir2Entries[i].getName());
			Assert.assertTrue(Math.abs(dir1Entries[i].lastModified() - dir2Entries[i].lastModified()) < 2000);
			if (dir1Entries[i].isDirectory()) {
				assertDirectoriesSame(dir1Entries[i], dir2Entries[i]);
			} else {
				assertFilesSame(dir1Entries[i], dir2Entries[i]);
			}
		}
	}

	public void assertFilesSame(File file1, File file2) throws IOException {
		Assert.assertEquals(file1.length(), file2.length());
		Assert.assertTrue(Math.abs(file1.lastModified() - file2.lastModified()) < 2000);
		Assert.assertEquals(
				Files.toString(file1, Charsets.UTF_8),
				Files.toString(file2, Charsets.UTF_8));
	}

}
