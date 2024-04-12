/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.config;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;

import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Settings for {@link FileSystemManager}
 *
 * @param rootPath
 * 		The directory where temporary files are created.
 * @param recycleBinMaximumFileAge   the maximum age of a file in the recycle bin before it is deleted
 * @param recycleBinCollectionPeriod the period between recycle bin collection runs
 */
@ConfigData("fileSystemManager")
public record FileSystemManagerConfig(
        @ConfigProperty(defaultValue = "swirlds-root") String rootPath,
        @ConfigProperty(defaultValue = "7d") Duration recycleBinMaximumFileAge,
        @ConfigProperty(defaultValue = "1d") Duration recycleBinCollectionPeriod) {

    /**
     * Returns the real path for the root that depends on the {@link StateCommonConfig#savedStateDirectory()}
     * property.
     *
     * @param stateConfig
     * 		the state config object
     * @return the location where temporary files are stored
     */
    @NonNull
    public Path getRootPath(final StateCommonConfig stateConfig) {
        return getAbsolutePath(stateConfig.savedStateDirectory().resolve(rootPath()));
    }

    /**
     * Returns the real path for the root of the filesystem that depends both on the {@link StateCommonConfig#savedStateDirectory()}
     * property and the {@link NodeId}
     *
     *
     * @param stateConfig the state config object
     * @param selfId      the ID of this node
     * @return the location where recycle bin files are stored
     */
    @NonNull
    public Path getRootPath(@NonNull final StateCommonConfig stateConfig, @NonNull final NodeId selfId) {
        return getAbsolutePath(stateConfig
                .savedStateDirectory()
                .resolve(Long.toString(
                        selfId.id())) // Or is the node the first parameter ?? how do we write the states files?
                .resolve(rootPath()));
    }
}
