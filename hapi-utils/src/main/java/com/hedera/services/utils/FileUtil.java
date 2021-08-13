package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtil {
	private static final Logger log = LogManager.getLogger(FileUtil.class);
	private static final int BUFFER_SIZE = 4096;


	public static Path findNewestDirOrFileUnder(String pathName) {
		return findNewestDirOrFileUnder(Path.of(pathName));
	}

	public static Path findNewestDirOrFileUnder(Path path) {
		Path newest = null;

		File file = path.toFile();
		if(file.exists()) {
			if (file.isFile()) {
				newest = path;
			} else {
				try (Stream<Path> entries = Files.list(path)) {
					List<Path> paths = entries.collect(Collectors.toList());
					newest = getLatestFileOrDir(paths);
				} catch (IOException e) {
					log.info("Can't get newest file or directory under {}", path.toString());
					newest = path;
				}
			}
		}
		return newest;
	}

	private static Path getLatestFileOrDir(final List<Path> paths) {
		Path newestFile = null;
		Instant newestFileTime = null;
		for(Path path: paths) {
			try {
				final BasicFileAttributes basicFileAttributes = Files.readAttributes(path, BasicFileAttributes.class);
				Instant newFileTime = basicFileAttributes.creationTime().toInstant();
				if(newestFile == null || newFileTime.isAfter(newestFileTime)) {
					newestFile = path;
					newestFileTime = newFileTime;
				}
			} catch (IOException e) {
				log.warn("Can't get newest file or dir for this director");
			}
		}
		return newestFile;
	}

	public static void createZip(String srcDirName, String zipFile, String defaultScript) {
		try (FileOutputStream fos = new FileOutputStream(zipFile);
			 ZipOutputStream zos = new ZipOutputStream(fos)) {

			if (defaultScript != null) {
				Path src = Paths.get(defaultScript);
				Path dest = Paths.get(srcDirName + src.getFileName());
				Files.copy(src, dest);
			}

			File srcDir = new File(srcDirName);
			addZipEntry(zos, srcDir, srcDir);
			zos.flush();
			fos.flush();
		} catch (IOException e) {
			log.error(e);
		}
	}

	public static Path gzipDir(Path srcPath, String zipFile) {
		Path zipFilePath = null;
		try (FileOutputStream fos = new FileOutputStream(zipFile);
			 ZipOutputStream zos = new ZipOutputStream(fos)) {

			zipFilePath = Paths.get(zipFile).toAbsolutePath();

			File srcDir = new File(srcPath.toString());
			addZipEntry(zos, srcDir, srcDir);
			zos.flush();
			fos.flush();
		} catch (IOException e) {
			log.error(e);
			return  null;
		}
		return zipFilePath;

	}

	/**
	 * Compress a directory recursively to zip output stream
	 *
	 * @param zos
	 * 		zip output stream
	 * @param rootDirectory
	 * 		source directory
	 * @param currentDirectory
	 * 		current working directory
	 */
	public static void addZipEntry(ZipOutputStream zos, File rootDirectory, File currentDirectory) {
		log.info("Root = " + rootDirectory + ", current = " + currentDirectory);
		if (!rootDirectory.equals(currentDirectory)) {
			try {
				String pathDiff = currentDirectory.toString().replace(rootDirectory.toString(), "");
				//remove leading File.separator
				while (pathDiff.charAt(0) == File.separatorChar) {
					pathDiff = pathDiff.substring(1);
				}
				String dirName = pathDiff + File.separator;
				log.info("Adding dir " + dirName);
				zos.putNextEntry(new ZipEntry(dirName));
			} catch (IOException e) {
				log.error(e);
			}
		}

		log.info("Current directory " + currentDirectory.toString());
		File[] files = currentDirectory.listFiles();
		byte[] buffer = new byte[BUFFER_SIZE];

		if (files != null) {
			for (File file : files) {
				// if the file is directory, use recursion
				if (file.isDirectory()) {
					addZipEntry(zos, rootDirectory, file);
					continue;
				}

				try (FileInputStream fis = new FileInputStream(file)) {
					String name = file.getAbsolutePath().replace(rootDirectory.getAbsolutePath(), "");
					log.info("Adding file:" + name);
					zos.putNextEntry(new ZipEntry(name));
					int length;
					while ((length = fis.read(buffer)) > 0) {
						zos.write(buffer, 0, length);
					}
					zos.closeEntry();
				} catch (IOException e) {
					log.error(e);
				}
			}//for
		} else {
			log.info("Directory " + currentDirectory + " is empty");
		}

	}
}
