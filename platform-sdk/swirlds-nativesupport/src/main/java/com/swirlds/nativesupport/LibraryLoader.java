/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.swirlds.nativesupport;

import com.goterl.resourceloader.ResourceLoader;
import com.sun.jna.Platform;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A simple library class which helps with loading dynamic library stored in the JAR archive.
 * Adapted from <a
 * href="https://github.com/terl/lazysodium-java/blob/master/src/main/java/com/goterl/lazysodium/utils/LibraryLoader.java">
 * https://github.com/terl/lazysodium-java/blob/master/src/main/java/com/goterl/lazysodium/utils/LibraryLoader.java
 * </a>
 *
 * @see <a
 * href="http://adamheinrich.com/blog/2012/how-to-load-native-jni-library-from-jar">http://adamheinrich
 * .com/blog/2012/how-to-load-native-jni-library-from-jar</a>
 * @see <a
 * href="https://github.com/adamheinrich/native-utils">https://github.com/adamheinrich/native-utils</a>
 */
// TODO: change for a more consistent approach
// TODO: Review javadocs, final and nonNull
public final class LibraryLoader {

    /**
     * Hidden constructor
     */
    private LibraryLoader() {}

    private static final Logger logger = LogManager.getLogger(LibraryLoader.class);

    /**
     * Loads library from the current JAR archive and registers the native methods of the provided
     * class. The library will be loaded at most once.
     *
     * <p>The file from JAR is copied into system temporary directory and then loaded. The temporary
     * file is deleted after exiting.
     */
    public static void loadBundledLibrary(final Class<?> clazz, String libraryName) {
        Path pathInJar = getLibraryPathInResources(libraryName);

        try {
            final File resourceFile = copyToTempDirectory(pathInJar, clazz);
            System.load(resourceFile.getAbsolutePath());
        } catch (final Exception e) {
            logger.error(e);
            throw new LibraryLoadingException("Failed to load library " + libraryName, e);
        }
    }

    /**
     * Copies a file into a temporary directory regardless of if it is in a JAR or not.
     *
     * @param relativePath A relative path to a file or directory, relative to the resources folder.
     * @return The file or directory you want to load.
     * @throws IOException        If at any point processing of the resource file fails.
     * @throws URISyntaxException If resource file cannot be found
     */
    private static File copyToTempDirectory(Path relativePath, Class<?> outsideClass)
            throws IOException, URISyntaxException {
        // Create a "main" temporary directory everything can be thrown in
        File tempDirectory = ResourceLoader.createMainTempDirectory();

        // Create the required directories.
        tempDirectory.mkdirs();

        // Is the user loading resources that are from inside a JAR?
        Path fullJarPathURL =
                Path.of(ResourceLoader.getThePathToTheJarWeAreIn(outsideClass).toURI());

        // Test if we are in a JAR
        if (isJarFile(fullJarPathURL)) {
            File extracted = nestedExtract(
                    tempDirectory, fullJarPathURL.resolve(relativePath).toString());

            if (extracted != null) {
                return extracted;
            }
        }

        // Try to get the file/directory straight from the file system
        return getFileFromFileSystem(relativePath, tempDirectory);
    }

    /**
     * Does the path lead to a valid JAR file?
     *
     * @param jarPath the path of the potential jar
     * @return true if the provided path is a jar file, otherwise false
     */
    private static boolean isJarFile(final Path jarPath) {
        if (jarPath == null) {
            return false;
        }

        try (JarFile jarFile = new JarFile(jarPath.toString())) {
            // Successfully opened the jar file. Check if there's a manifest
            return jarFile.getManifest() != null;
        } catch (final IOException | IllegalStateException | SecurityException e) {
            logger.debug("This is not a JAR file due to {}", e.getMessage());

            return false;
        }
    }

    /**
     * If we're not in a JAR, then we can load directly from the file system without all the
     * unzipping fiasco present in {@see #getFileFromJar}.
     *
     * @param relativePath    A relative path to a file or directory in the resources folder.
     * @param outputDirectory A directory in which to store loaded files. Preferentially a temporary
     *                        one.
     * @return The file or directory that was requested.
     * @throws IOException Could not find requested file.
     */
    private static File getFileFromFileSystem(final Path relativePath, final File outputDirectory)
            throws IOException, URISyntaxException {

        final URL url = LibraryLoader.class.getClassLoader().getResource(relativePath.toString());

        if (url == null) {
            throw new IOException("unable to get resource");
        }

        final Path file;
        if (Platform.isWindows()) {
            file = Paths.get(url.toURI());
        } else {
            file = Path.of(url.toURI());
        }

        if (!file.toFile().isFile()) {
            throw new IOException("resource to load should be a file");
        }

        File resource = new File(relativePath.toUri());
        File resourceCopiedToTempFolder = new File(outputDirectory, resource.getName());
        Files.copy(file, resourceCopiedToTempFolder.toPath());

        return resourceCopiedToTempFolder;
    }

    /**
     * A method that keeps extracting JAR files from within each other. This method only allows a
     * maximum nested depth of 20.
     *
     * @param extractTo Where shall we initially extract files to.
     * @param fullPath  The full path to the initial
     * @return The final extracted file.
     * @throws IOException
     * @throws URISyntaxException
     */
    private static File nestedExtract(File extractTo, String fullPath) throws IOException {
        final String JAR = ".jar";

        // After this line we have something like
        // file:C/app, some/lazysodium, file.txt
        String[] split = fullPath.split("(\\.jar/)");

        if (split.length > 20) {
            // What monster would put a JAR in a JAR 20 times?
            throw new StackOverflowError("We cannot extract a file 21 or more layers deep.");
        }

        // We have no ".jar/" so we go straight
        // to extraction.
        if (split.length == 1) {
            logger.debug("Extracted {} to {}", fullPath, extractTo.getAbsolutePath());
            return extractFilesOrFoldersFromJar(extractTo, new URL(fullPath), "");
        }

        String currentExtractionPath = "";
        File extracted = null;
        File nestedExtractTo = extractTo;
        for (int i = 0; i < split.length - 1; i++) {
            // Remember part = "file:C/app". But we need to know
            // where to extract these files. So we have
            // to prefix it with the current extraction path. We can't
            // just dump everything in the temp directory all the time.
            // Of course, we also suffix it with a ".jar". So at the end,
            // we get something like "file:C:/temp/app.jar"
            String part = currentExtractionPath + split[i] + JAR;
            // If we don't add this then when we pass it into
            // a URL() object then the URL object will complain
            if (!part.startsWith("file:")) {
                part = "file:" + part;
            }

            // Now, we need to "look ahead" and determine
            // the next part. We'd get something like
            // this: "/lazysodium".
            String nextPart = "/" + split[i + 1];

            // Now check if it's the last iteration of this for-loop.
            // If it isn't then add a ".jar" to nextPart, resulting
            // in something like "/lazysodium.jar"
            boolean isLastIteration = (i == (split.length - 2));
            if (!isLastIteration) {
                nextPart = nextPart + JAR;
            }

            // Now perform the extraction.
            logger.debug("Extracting {} from {}", nextPart, part);
            extracted = extractFilesOrFoldersFromJar(nestedExtractTo, new URL(part), nextPart);
            logger.debug("Extracted: {}", extracted.getAbsolutePath());

            // Note down the parent folder's location of the file we extracted to.
            // This will be used at the start of the for-loop as the
            // new destination to extract to.
            currentExtractionPath = nestedExtractTo.getAbsolutePath() + "/";
            nestedExtractTo = extracted.getParentFile();
        }
        return extracted;
    }

    /**
     * Extracts a file/directory from a JAR. A JAR is simply a zip file. We can unzip it and get our
     * file successfully.
     *
     * @param jarUrl    A JAR's URL.
     * @param outputDir A directory of where to store our extracted files.
     * @param pathInJar A relative path to a file that is in our resources folder.
     * @return The file or directory that we requested.
     * @throws URISyntaxException If we could not ascertain our location.
     * @throws IOException        If whilst unzipping we had some problems.
     */
    private static File extractFilesOrFoldersFromJar(File outputDir, URL jarUrl, String pathInJar) throws IOException {
        File jar = ResourceLoader.urlToFile(jarUrl);
        unzip(jar.getAbsolutePath(), outputDir.getAbsolutePath());
        String filePath = outputDir.getAbsolutePath() + pathInJar;
        return new File(filePath);
    }

    /**
     * From https://www.javadevjournal.com/java/zipping-and-unzipping-in-java/
     *
     * @param zipFilePath   An absolute path to a zip file
     * @param unzipLocation Where to unzip the zip file
     * @throws IOException If could not unzip.
     */
    private static void unzip(final String zipFilePath, final String unzipLocation) throws IOException {
        if (!(Files.exists(Paths.get(unzipLocation)))) {
            Files.createDirectories(Paths.get(unzipLocation));
        }
        try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipInputStream.getNextEntry();
            while (entry != null) {
                Path filePath = Paths.get(unzipLocation, entry.getName());
                if (!entry.isDirectory()) {
                    filePath.getParent().toFile().mkdirs();
                    unzipFiles(zipInputStream, filePath);
                } else {
                    Files.createDirectories(filePath);
                }

                zipInputStream.closeEntry();
                entry = zipInputStream.getNextEntry();
            }
        }
    }

    private static void unzipFiles(final ZipInputStream zipInputStream, final Path unzipFilePath) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(unzipFilePath.toAbsolutePath().toString()))) {
            byte[] bytesIn = new byte[1024];
            int read;
            while ((read = zipInputStream.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }

    /**
     * Returns the absolute path to library inside a JAR (beginning with '/')
     *
     * @return The path to the library binary
     * @param libraryName
     */
    private static Path getLibraryPathInResources(String libraryName) {
        final String x64 = "x86_64";
        final String i686 = "i686";
        final String aarch64 = "aarch64";

        if (Platform.isWindows()) {
            final String platformFolder = "windows";
            final String fileName = libraryName.substring(3).concat(".dll");

            if (Platform.is64Bit()) {
                return Path.of(platformFolder, x64, fileName);
            } else {
                return Path.of(platformFolder, i686, fileName);
            }
        }
        if (Platform.isMac()) {
            final String platformFolder = "darwin";
            final String fileName = libraryName + ".dylib";

            if (Platform.isARM()) {
                return Path.of(platformFolder, aarch64, fileName);
            } else {
                return Path.of(platformFolder, x64, fileName);
            }
        }
        if (Platform.isLinux()) {
            final String platformFolder = "linux";
            final String fileName = libraryName + ".so";

            if (Platform.is64Bit()) {
                if (Platform.isARM()) {
                    return Path.of(platformFolder, aarch64, fileName);
                }
                return Path.of(platformFolder, x64, fileName);
            } else {
                return Path.of(platformFolder, i686, fileName);
            }
        }

        String message = String.format(
                "Unsupported platform: %s/%s", System.getProperty("os.name"), System.getProperty("os.arch"));

        throw new LibraryLoadingException(message);
    }
}
