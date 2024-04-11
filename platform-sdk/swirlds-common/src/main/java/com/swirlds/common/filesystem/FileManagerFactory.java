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

package com.swirlds.common.filesystem;

import com.swirlds.common.filesystem.internal.FileManagerFactoryImpl;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Creates instances of {@link FileSystemManager}
 */
public interface FileManagerFactory {

    /**
     * Creates a {@link FileSystemManager} Relative to {@code rootLocation} path
     *
     * @param rootLocation a location to be used as rootPath
     * @param bin          a {@link RecycleBin} instance
     * @return a new instance of {@link FileSystemManager}
     * @throws IllegalArgumentException if rootLocation already exist or if the dir structure to rootLocation cannot be
     *                                  created
     */
    @NonNull
    FileSystemManager createFileSystemManager(@NonNull String rootLocation, @NonNull RecycleBin bin);

    /**
     * Creates a {@link FileSystemManager} by searching {@code root} path in the {@link Configuration} class under a
     * property name indicated in {@code rootLocationPropertyName}
     *
     * @param bin                      a {@link RecycleBin} instance
     * @param configuration            the configuration instance to retrieve properties from
     * @param rootLocationPropertyName the configuration property to retrieve the rootLocation from
     * @return a new instance of {@link FileSystemManager}
     * @throws IllegalArgumentException if {@code rootLocationPropertyName} cannot be found on configuration, if
     *                                  {@code rootLocation} already exist or if the dir structure to rootLocation
     *                                  cannot be created
     */
    @NonNull
    default FileSystemManager createFileSystemManager(
            @NonNull final RecycleBin bin,
            @NonNull final Configuration configuration,
            @NonNull final String rootLocationPropertyName) {
        final String value = configuration.getValue(rootLocationPropertyName);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Could not find property " + rootLocationPropertyName + " with the location for the rootPath");
        }
        return createFileSystemManager(value, bin);
    }

    static FileManagerFactory getInstance() {
        return FileManagerFactoryImpl.getInstance();
    }
}
