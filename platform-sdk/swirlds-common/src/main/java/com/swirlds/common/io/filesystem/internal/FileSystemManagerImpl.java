/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.filesystem.internal;

import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static java.nio.file.Files.exists;

import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBin;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of {@link FileSystemManager} It organizes the file creation in the following structure: root
 * usr tmp recycle-bin
 * <p>
 * If root directory already exist, deletes its content and recreates it. All {@link Path}s provided by this class are
 * handled in the same filesystem as indicated in rootLocation parameter.
 *
 * @implNote Creates and manages a temp dir when creating an instance of this class using
 * {@link Files#createTempDirectory(Path, String, FileAttribute[])} located under {@code rootPath} where all temporary
 * files are located.
 */
public class FileSystemManagerImpl implements FileSystemManager {

    private static final Logger logger = LogManager.getLogger(FileSystemManagerImpl.class);
    private static final String TMP = "tmp";
    private static final String USER = "usr";
    private static final String BIN = "recycle-bin";
    private final Path rootPath;
    private final Path tempPath;
    private final Path userPath;
    private final Path recycleBinPath;
    private final RecycleBin bin;
    private static final AtomicLong TMP_FIELD_INDEX = new AtomicLong(0);

    /**
     * Creates an instance of {@link FileSystemManager}
     * If root directory already exist, deletes its content and recreates it.
     *
     * @param rootLocation the location to be used as root path. It should not exist.
     * @param binSupplier  for building the recycle bin.
     * @throws UncheckedIOException if the dir structure to rootLocation cannot be
     *                              created
     */
    FileSystemManagerImpl(@NonNull final String rootLocation, @NonNull final Function<Path, RecycleBin> binSupplier) {
        this.rootPath = Path.of(rootLocation).normalize();
        if (exists(rootPath)) {
            logger.info(STARTUP.getMarker(), "Deleting rootPath {}", rootPath);
            rethrowIO(() -> FileUtils.deleteDirectory(rootPath));
        }

        this.tempPath = rootPath.resolve(TMP);
        this.userPath = rootPath.resolve(USER);
        this.recycleBinPath = rootPath.resolve(BIN);

        rethrowIO(() -> Files.createDirectories(rootPath));
        rethrowIO(() -> Files.createDirectory(userPath));
        rethrowIO(() -> Files.createDirectory(tempPath));
        rethrowIO(() -> Files.createDirectory(recycleBinPath));

        this.bin = binSupplier.apply(recycleBinPath);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Path resolve(@NonNull final Path relativePath) {
        return requireValidSubPathOf(userPath, userPath.resolve(relativePath));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Path resolveNewTemp(@Nullable final String tag) {
        final StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(System.currentTimeMillis());
        nameBuilder.append(TMP_FIELD_INDEX.getAndIncrement());
        if (tag != null) {
            nameBuilder.append("-");
            nameBuilder.append(tag);
        }

        return requireValidSubPathOf(tempPath, tempPath.resolve(nameBuilder.toString()));
    }

    /**
     * Remove the file or directory tree at the specified absolute path. A best effort attempt is made to relocate the
     * file or directory tree to a temporary location where it may persist for an amount of time. No guarantee on the
     * amount of time the file or directory tree will persist is provided. {@code absolutePath} should be under
     * {@code rootPath} or {@link IllegalArgumentException} is thrown
     *
     * @param absolutePath the path to recycle
     * @throws IllegalArgumentException if the path cannot be relative to or scape the root directory
     */
    @Override
    public void recycle(@NonNull final Path absolutePath) throws IOException {
        final Path path = absolutePath.startsWith(rootPath) ? absolutePath : rootPath.resolve(absolutePath);
        bin.recycle(requireValidSubPathOf(rootPath, path));
    }

    /**
     * Checks that the specified {@code path} reference is relative to {@code parent} and is not {@code parent} itself.
     * throws IllegalArgumentException if this condition is not true.
     *
     * @param parent the path to check against.
     * @param path   the path to check if is
     * @return {@code path} if it represents a valid path inside {@code parent}
     * @throws IllegalArgumentException if the reference is not relative to root or is root itself
     */
    @NonNull
    private static Path requireValidSubPathOf(@NonNull final Path parent, @NonNull final Path path) {
        final Path relativePath = parent.relativize(path);
        // Check if path is not parent itself and if is contained in parent
        if (relativePath.startsWith("") || relativePath.startsWith("..")) {
            throw new IllegalArgumentException(
                    "Requested path is cannot be converted to valid relative path inside of:" + parent);
        }
        return path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        bin.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        bin.start();
    }
}
