/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.config.StateConfig;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Settings for temporary files
 *
 * @param temporaryFilePath
 * 		The directory where temporary files are created.
 */
@ConfigData("temporaryFiles")
public record TemporaryFileConfig(@ConfigProperty(defaultValue = "swirlds-tmp") String temporaryFilePath) {

    /**
     * Returns the real path to the temporary files that depends on the {@link StateConfig#savedStateDirectory()}
     * property.
     *
     * @param stateConfig
     * 		the state config object
     * @return the location where temporary files are stored
     */
    public String getTemporaryFilePath(final StateConfig stateConfig) {
        return stateConfig.savedStateDirectory().resolve(temporaryFilePath()).toString();
    }
}
