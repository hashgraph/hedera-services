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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.io.config.FileSystemManagerConfig;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.filesystem.FileSystemManagerFactory;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

public class FileSystemManagerFactoryImpl implements FileSystemManagerFactory {

    private static class InstanceHolder {
        private static final FileSystemManagerFactoryImpl INSTANCE = new FileSystemManagerFactoryImpl();
    }

    /**
     * Creates a {@link FileSystemManager} by searching {@code root} path in the {@link Configuration} class under a
     * property name indicated in {@code rootLocationPropertyName}
     *
     * @param configuration the configuration instance to retrieve properties from
     * @param metrics metrics instance
     * @return a new instance of {@link FileSystemManager}
     * @throws IllegalArgumentException if {@code rootLocationPropertyName} cannot be found on configuration, if
     *                                  {@code rootLocation} already exist or if the dir structure to rootLocation
     *                                  cannot be created
     */
    @NonNull
    @Override
    public FileSystemManager createFileSystemManager(
            @NonNull final Configuration configuration, @NonNull final Metrics metrics) {

        final FileSystemManagerConfig fileSystemManagerConfig =
                ConfigurationHolder.getConfigData(FileSystemManagerConfig.class);

        return new FileSystemManagerImpl(
                fileSystemManagerConfig.rootPath(),
                fileSystemManagerConfig.userDataDir(),
                fileSystemManagerConfig.tmpDir(),
                fileSystemManagerConfig.recycleBinDir(),
                path -> new RecycleBinImpl(
                        metrics,
                        getStaticThreadManager(),
                        Time.getCurrent(),
                        path,
                        fileSystemManagerConfig.recycleBinMaximumFileAge(),
                        fileSystemManagerConfig.recycleBinCollectionPeriod()));
    }

    /**
     * Retrieves the default FileSystemManagerFactory instance
     */
    @NonNull
    public static FileSystemManagerFactory getInstance() {
        return InstanceHolder.INSTANCE;
    }
}
