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
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Creates instances of {@link FileSystemManager}
 */
public interface FileManagerFactory {

    /**
     * Creates a {@link FileSystemManager} by searching {@code root} path in the {@link Configuration} class under a
     * property name indicated in {@code rootLocationPropertyName}
     *
     * @param configuration            the configuration instance to retrieve properties from
     * @return a new instance of {@link FileSystemManager}
     */
    @NonNull
    FileSystemManager createFileSystemManager(
            @NonNull final Configuration configuration, @NonNull final Metrics metrics, @NonNull final NodeId selfId);

    /**
     * Retrieves the default FileSystemManagerFactory instance
     */
    @NonNull
    static FileManagerFactory getInstance() {
        return FileManagerFactoryImpl.getInstance();
    }
}
