/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.utility;

import static com.swirlds.common.io.utility.TemporaryFileBuilder.buildTemporaryDirectory;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STATE_TO_DISK;
import static java.nio.file.Files.exists;

import com.swirlds.common.io.streams.MerkleDataOutputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility methods for file operations.
 */
public final class FileUtils {

    private static final Logger logger = LogManager.getLogger(FileUtils.class);

    private FileUtils() {}

    /**
     * Syntactic sugar. Runs an operation and rethrows any {@link IOException}s as {@link UncheckedIOException}s.
     *
     * @param runnable
     * 		an operation to run
     */
    public static void rethrowIO(final IORunnable runnable) {
        try {
            runnable.run();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Syntactic sugar. Runs an operation and rethrows any {@link IOException}s as {@link UncheckedIOException}s.
     *
     * @param supplier
     * 		an operation that supplies a value
     */
    public static <T> T rethrowIO(final IOSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Get an absolute path to the current working directory, i.e. ".".
     */
    public static Path getAbsolutePath() {
        return getAbsolutePath(".");
    }

    /**
     * Get an absolute path to a particular location described by a string, starting in the current working directory.
     * For example, if the current execution directory is "/user/home" and this method is invoked with "foo", then a
     * {@link Path} at "/user/home/foo" is returned. Resolves "~".
     *
     * @param pathDescription
     * 		a description of the path, e.g. "foo", "/foobar", "foo/bar"
     * @return an absolute Path to the requested location
     */
    public static Path getAbsolutePath(final String pathDescription) {
        final String expandedPath = pathDescription.replaceFirst("^~", System.getProperty("user.home"));
        return FileSystems.getDefault().getPath(expandedPath).toAbsolutePath().normalize();
    }

    /**
     * Get an absolute path to a particular location described by a string, starting in the current working directory.
     * For example, if the current execution directory is "/user/home" and this method is invoked with "foo", then a
     * {@link Path} at "/user/home/foo" is returned. Resolves "~".
     *
     * @param path
     * 		a non-absolute path
     * @return an absolute Path to the requested location
     */
    public static Path getAbsolutePath(final Path path) {
        return getAbsolutePath(path.toString());
    }

    /**
     * Recursively delete a directory and all files contained in this directory. If there is a problem deleting a
     * file or subdirectory, no more files or directories are attempted to be deleted.
     *
     * @param directoryToBeDeleted
     * 		the directory to be deleted
     */
    @SuppressWarnings("resource")
    public static void deleteDirectory(final Path directoryToBeDeleted) throws IOException {
        if (Files.isDirectory(directoryToBeDeleted)) {
            final Iterator<Path> children = Files.list(directoryToBeDeleted).iterator();
            while (children.hasNext()) {
                deleteDirectory(children.next());
            }
            Files.delete(directoryToBeDeleted);
        } else {
            Files.deleteIfExists(directoryToBeDeleted);
        }
    }

    /**
     * Similar to {@link #deleteDirectory(Path)} but with additional logging. If there is a problem deleting a file
     * or subdirectory, no more files or directories are attempted to be deleted.
     *
     * @param directoryToBeDeleted
     * 		the directory to be deleted
     */
    public static void deleteDirectoryAndLog(final Path directoryToBeDeleted) throws IOException {
        logger.info(STATE_TO_DISK.getMarker(), "deleting directory {}", directoryToBeDeleted);

        try {
            deleteDirectory(directoryToBeDeleted);
            logger.info(STATE_TO_DISK.getMarker(), "successfully deleted directory {}", directoryToBeDeleted);
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "failed to delete directory {}", directoryToBeDeleted);
            throw e;
        }
    }

    /**
     * Create a shallow copy of a directory structure using hard links. Resulting directory structure will contain the
     * same files. Modifying files in the new directory will also modify the corresponding files in the original
     * directory. Deleting files in the new directory has no effect on the old directory. Adding files to the new
     * directory has no effect on the old directory.
     *
     * @param source
     * 		the directory structure to copy, is legal for this argument to be a regular file
     * @param destination
     * 		the location where the directory structure will be placed, assumed that no file or directory
     * 		already exists in this location. Note that the root of the copied directory structure will be placed at this
     * 		destination, and not in this destination.
     * @throws UncheckedIOException
     * 		if there is a problem hard linking the tree
     */
    public static void hardLinkTree(final Path source, final Path destination) throws IOException {
        if (!exists(source)) {
            throw new IOException(source + " does not exist or can not be accessed");
        }

        if (exists(destination)) {
            throw new IOException(destination + " already exists");
        }

        // Recreate the directory tree
        try (final Stream<Path> files = Files.walk(source)) {
            files.filter(Files::isDirectory).forEach((final Path originalDirectoryPath) -> {
                final Path relativeDirectoryPath = source.relativize(originalDirectoryPath);
                final Path newDirectoryPath = destination.resolve(relativeDirectoryPath);
                try {
                    Files.createDirectories(newDirectoryPath);
                } catch (final IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }

        // Hard link files
        try (final Stream<Path> files = Files.walk(source)) {
            files.filter(Files::isRegularFile).forEach((final Path originalFilePath) -> {
                final Path relativeFilePath = source.relativize(originalFilePath);
                final Path newFilePath = destination.resolve(relativeFilePath);
                try {
                    Files.createLink(newFilePath, originalFilePath);
                } catch (final IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }
    }

    /**
     * Throw an exception if any of an array of files already exists.
     *
     * @param files
     * 		an array of files
     * @throws IOException
     * 		if any of the files exist
     */
    public static void throwIfFileExists(final Path... files) throws IOException {
        if (files == null) {
            return;
        }
        for (final Path file : files) {
            if (exists(file)) {
                throw new FileAlreadyExistsException("File " + file + " already exists");
            }
        }
    }

    /**
     * Execute an operation that writes to a directory. When the operation is complete, rename the directory.
     * Useful for file operations that need to be atomic.
     *
     * @param directory
     * 		the name of directory after it is renamed
     * @param operation
     * 		an operation that writes to a directory
     */
    public static void executeAndRename(final Path directory, final IOConsumer<Path> operation) throws IOException {
        executeAndRename(directory, buildTemporaryDirectory(), operation);
    }

    /**
     * Execute an operation that writes to a directory. When the operation is complete, rename the directory.
     * Useful for file operations that need to be atomic.
     *
     * @param directory
     * 		the name of directory after it is renamed
     * @param tmpDirectory
     * 		the name of the temporary directory, if it does not exist then it is created
     * @param operation
     * 		an operation that writes to a directory
     */
    public static void executeAndRename(final Path directory, final Path tmpDirectory, final IOConsumer<Path> operation)
            throws IOException {

        try {
            throwIfFileExists(directory);
            if (!exists(tmpDirectory)) {
                Files.createDirectories(tmpDirectory);
            }
            if (!Files.exists(directory.getParent())) {
                Files.createDirectories(directory.getParent());
            }

            operation.accept(tmpDirectory);

            // Move needs to be atomic to guarantee that the folder only exists when its contents are complete.
            // Otherwise, it's possible another thread will see a half-completed directory.
            Files.move(tmpDirectory, directory, StandardCopyOption.ATOMIC_MOVE);
        } catch (final Throwable ex) {
            logger.info(STATE_TO_DISK.getMarker(), "deleting temporary file due to exception");
            throw ex;
        } finally {
            try {
                deleteDirectory(tmpDirectory);
            } catch (final Throwable t) {
                logger.warn(
                        STATE_TO_DISK.getMarker(),
                        "unable to delete temporary directory {} due to {}",
                        tmpDirectory,
                        t);
            }
        }
    }

    /**
     * Write to a new file, and make sure it's flushed to disk before returning.
     *
     * @param file
     * 		the file to be written to, should not exist prior to this method being called
     * @param writeMethod
     * 		the method that writes
     */
    public static void writeAndFlush(final Path file, final IOConsumer<MerkleDataOutputStream> writeMethod)
            throws IOException {

        throwIfFileExists(file);

        try (final FileOutputStream fileOut = new FileOutputStream(file.toFile());
                final BufferedOutputStream bufOut = new BufferedOutputStream(fileOut);
                final MerkleDataOutputStream out = new MerkleDataOutputStream(bufOut)) {

            writeMethod.accept(out);

            // flush all the data to the file stream
            out.flush();
            // make sure the data is actually written to disk
            fileOut.getFD().sync();
        }
    }
}
