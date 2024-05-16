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

package com.swirlds.common.io.filesystem;

import com.swirlds.common.io.filesystem.internal.FileSystemManagerFactoryImpl;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Creates instances of {@link FileSystemManager}
 */
public interface FileSystemManagerFactory {

    /**
     * Creates a {@link FileSystemManager} by searching {@code root} path in the {@link Configuration} class under a
     * property name indicated in {@code rootLocationPropertyName}
     *
     * @param configuration the configuration instance to retrieve properties from
     * @param recycleBin    the recycleBin instance to use
     * @return a new instance of {@link FileSystemManager}
     */
    @NonNull
    FileSystemManager createFileSystemManager(@NonNull Configuration configuration, @NonNull RecycleBin recycleBin);

    /**
     * Creates a {@link FileSystemManager} by searching {@code root} path in the {@link Configuration} class under a
     * property name indicated in {@code rootLocationPropertyName}
     *
     * @param configuration the configuration instance to retrieve properties from
     * @param metrics       metrics instance of the platform
     * @param nodeId        id of the ode configuring this instance
     * @return a new instance of {@link FileSystemManager}
     */
    @NonNull
    FileSystemManager createFileSystemManager(
            @NonNull Configuration configuration, @NonNull Metrics metrics, @NonNull NodeId nodeId);

    /**
     * Retrieves the default FileSystemManagerFactory instance
     */
    @NonNull
    static FileSystemManagerFactory getInstance() {
        return FileSystemManagerFactoryImpl.getInstance();
    }
}
