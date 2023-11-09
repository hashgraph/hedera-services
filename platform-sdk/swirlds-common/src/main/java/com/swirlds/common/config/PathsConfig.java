/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.config;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.nio.file.Path;

/**
 * Configurations related to paths.
 * @param settingsUsedDir
 *     the directory where the settings used file will be created on startup if and only if settings.txt exists
 * @param keysDirPath
 *     path to data/keys/
 * @param appsDirPath
 *     path to data/apps/
 * @param logPath
 * 	   path to log4j2.xml (which might not exist)
 */
@ConfigData("paths")
public record PathsConfig(
        @ConfigProperty(defaultValue = ".") String settingsUsedDir,
        @ConfigProperty(defaultValue = "data/keys") String keysDirPath,
        @ConfigProperty(defaultValue = "data/apps") String appsDirPath,
        @ConfigProperty(defaultValue = "log4j2.xml") String logPath) {

    /**
     * the directory where the settings used file will be created on startup if and only if settings.txt exists
     *
     * @return absolute path to settings directory
     */
    public Path getSettingsUsedDir() {
        return getAbsolutePath(settingsUsedDir);
    }

    /**
     * path to data/keys/
     *
     * @return absolute path to data/keys/
     */
    public Path getKeysDirPath() {
        return getAbsolutePath(keysDirPath);
    }

    /**
     * path to data/apps/
     *
     * @return absolute path to data/apps/
     */
    public Path getAppsDirPath() {
        return getAbsolutePath(appsDirPath);
    }

    /**
     * path to log4j2.xml (which might not exist)
     *
     * @return absolute path to log4j2.xml
     */
    public Path getLogPath() {
        return rethrowIO(() -> getAbsolutePath(logPath));
    }
}
