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

package com.swirlds.filesystem.manager.internal;

import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.filesystem.manager.FileSystemManager;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

public class FileSystemManagerImpl implements FileSystemManager {

    private final Path rootPath;
    private final RecycleBin bin;
    private static final AtomicLong FILE_ID = new AtomicLong(0);

    FileSystemManagerImpl(@NonNull final Path rootPath, @NonNull final RecycleBin bin) {
        this.rootPath = rootPath;
        this.bin = bin;
    }

    /**
     * {@inheritDoc}
     * This implementation is nominal.
     */
    @NonNull
    @Override
    public Path resolve(@NonNull final Path relativePath) {
        return subPathFromTempLocation(relativePath);
    }

    /**
     * Creates a temporary directory relative to the root directory of the file system manager.
     *
     * A class level index is used in order to assure uniqueness of the files in the directory.
     *
     * @param name the name to use as suffix for the directory
     * @return the resolved path
     * @throws IllegalArgumentException if the path cannot be relative to or scape the root directory
     */
    @NonNull
    @Override
    public Path createTemporaryPath(@NonNull final String name) {
        final String fileName = FILE_ID.getAndIncrement() + "-" + name;
        return subPathFromTempLocation(Paths.get(fileName));
    }

    /**
     * {@inheritDoc}
     * This implementation is nominal.
     */
    @Override
    public void recycle(@NonNull final Path relativePath) throws IOException {
        bin.recycle(subPathFromTempLocation(relativePath));
    }

    private Path subPathFromTempLocation(@NonNull final Path subpath) {
        final Path normalizedSubpath = // Remove redundant name parts
                requireNotSameAsTempLocation(rootPath.resolve(subpath).normalize());
        final Path relativePath = rootPath.relativize(normalizedSubpath);
        // Check if relativization is within temporaryFileLocation
        if (relativePath.startsWith("..")) {
            throw new IllegalArgumentException("Requested path scape from temporary path '" + subpath + ".");
        }
        return normalizedSubpath;
    }

    private Path requireNotSameAsTempLocation(final @NonNull Path path) {
        try {
            if (Files.isSameFile(path, rootPath)) {
                throw new IllegalArgumentException(
                        "Requested subPath resolves to the root temporary path '" + path + ".");
            }
            return path;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot assert if provided path against temporaryFileLocation", e);
        }
    }
}
