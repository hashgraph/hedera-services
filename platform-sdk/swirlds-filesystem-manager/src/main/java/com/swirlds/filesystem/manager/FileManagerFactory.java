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

package com.swirlds.filesystem.manager;

import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Creates instances of {@link FileSystemManager}
 */
public interface FileManagerFactory {

    /**
     * Creates a {@link FileSystemManager} Relative to {@code root} path
     *
     * @param bin a {@link RecycleBin} instance
     * @return a new instance of {@link FileSystemManager}
     */
    FileSystemManager createFileSystemManager(@NonNull Path root, @NonNull RecycleBin bin);

    /**
     * Creates a {@link FileSystemManager} by searching {@code root} path in the {@link Configuration} class under a
     * property name indicated in {@code rootDirPropertyName}
     *
     * @param bin                 a {@link RecycleBin} instance
     * @param configuration       the configuration instance to retrieve properties from
     * @param rootDirPropertyName the name of the configuration property to use toto retrieve the root dir value
     * @return a new instance of {@link FileSystemManager}
     */
    default FileSystemManager createFileSystemManager(
            @NonNull final RecycleBin bin, @NonNull Configuration configuration, @NonNull String rootDirPropertyName) {
        final String value = Objects.requireNonNull(configuration.getValue(rootDirPropertyName, "swirlds-tmp"));

        return createFileSystemManager(Path.of(value), bin);
    }
}
