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

package com.swirlds.common.filesystem.internal;

import static java.nio.file.Files.exists;

import com.swirlds.common.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.RecycleBin;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemManagerImpl implements FileSystemManager {

    private final Path rootPath;
    private final Path tempPath;
    private final RecycleBin bin;

    /**
     * Creates an instance of {@link FileSystemManager}
     *
     * @param rootLocation the location to be used as root path. It should not exist.
     * @param bin          the recycle bin.
     * @throws IllegalArgumentException if rootLocation already exist or if the dir structure to rootLocation cannot be
     *                                  created
     */
    FileSystemManagerImpl(@NonNull final String rootLocation, @NonNull final RecycleBin bin) {
        this.rootPath = Path.of(rootLocation);
        this.bin = bin;
        if (exists(rootPath)) {
            throw new IllegalArgumentException("rootLocation already exists: " + rootLocation);
        }
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not create rootPath:" + rootPath, e);
        }
        try {
            this.tempPath = Files.createTempDirectory(rootPath, "tmp");
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not create a tmp directory under rootPath:" + rootPath, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Path resolve(@NonNull final Path relativePath) {
        return requireValidSubPathOf(rootPath, rootPath.resolve(relativePath));
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Path createTemporaryPath(@Nullable final String tag) {
        try {
            return Files.createTempFile(tempPath, null, tag);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not create a tmp file with tag:" + tag + " under " + tempPath, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle(@NonNull final Path relativePath) throws IOException {
        final Path path = relativePath.startsWith(rootPath) ? relativePath : rootPath.resolve(relativePath);
        bin.recycle(requireValidSubPathOf(rootPath, path));
    }

    /**
     * Checks that the specified {@code path} reference is relative to {@code parent} and is not {@code parent} itself.
     * throws a customized IllegalArgumentException if this condition is not true.
     *
     * @param parent the path to check against.
     * @param path   the path to check if is
     * @return {@code path} if it represents a valid path inside {@code parent}
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
}
