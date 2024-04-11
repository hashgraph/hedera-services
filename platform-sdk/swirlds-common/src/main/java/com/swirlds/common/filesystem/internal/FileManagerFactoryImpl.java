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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.filesystem.FileManagerFactory;
import com.swirlds.common.filesystem.FileSystemManager;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;

public class FileManagerFactoryImpl implements FileManagerFactory {

    private static class InstanceHolder {
        private static final FileManagerFactoryImpl INSTANCE = new FileManagerFactoryImpl();
    }

    /**
     * Creates a {@link FileSystemManager} by searching {@code root} path in the {@link Configuration} class under a
     * property name indicated in {@code rootLocationPropertyName}
     *
     * @param configuration the configuration instance to retrieve properties from
     * @param metrics
     * @return a new instance of {@link FileSystemManager}
     * @throws IllegalArgumentException if {@code rootLocationPropertyName} cannot be found on configuration, if
     *                                  {@code rootLocation} already exist or if the dir structure to rootLocation
     *                                  cannot be created
     */
    @NonNull
    @Override
    public FileSystemManager createFileSystemManager(
            @NonNull final Configuration configuration, @NonNull final Metrics metrics, @NonNull final NodeId selfId) {

        final TemporaryFileConfig config = ConfigurationHolder.getConfigData(TemporaryFileConfig.class);
        final StateCommonConfig stateConfig = ConfigurationHolder.getConfigData(StateCommonConfig.class);
        final String rootFileLocation = config.temporaryFilePath().replace("{nodeId}", selfId.toString());
        final String rootLocation =
                stateConfig.savedStateDirectory().resolve(rootFileLocation).toString();

        return new FileSystemManagerImpl(
                rootLocation,
                path -> new RecycleBinImpl(configuration, metrics, getStaticThreadManager(), Time.getCurrent(), path));
    }

    /**
     * Retrieves the default FileSystemManagerFactory instance
     */
    @NonNull
    public static FileManagerFactory getInstance() {
        return InstanceHolder.INSTANCE;
    }
}
