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

import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Settings for {@link FileSystemManager}
 *
 * @param rootPath The directory where temporary files are created.
 * @param recycleBinMaximumFileAge   the maximum age of a file in the recycle bin before it is deleted
 * @param recycleBinCollectionPeriod the period between recycle bin collection runs
 */
@ConfigData("fileSystemManager")
public record FileSystemManagerConfig(
        @ConfigProperty(defaultValue = "data")
                String rootPath, // On purpose matches root of {@link StateCommonConfig#savedStateDirectory()}
        @ConfigProperty(defaultValue = DEFAULT_DATA_DIR_NAME) String userDataDir,
        @ConfigProperty(defaultValue = DEFAULT_TMP_DIR_NAME) String tmpDir,
        @ConfigProperty(defaultValue = DEFAULT_RECYCLE_BIN_DIR_NAME) String recycleBinDir,
        @ConfigProperty(defaultValue = "7d") Duration recycleBinMaximumFileAge,
        @ConfigProperty(defaultValue = "1d") Duration recycleBinCollectionPeriod) {

    public static final String DEFAULT_TMP_DIR_NAME = "tmp";
    public static final String DEFAULT_DATA_DIR_NAME = "saved";
    public static final String DEFAULT_RECYCLE_BIN_DIR_NAME = "saved/swirlds-recycle-bin";
}
