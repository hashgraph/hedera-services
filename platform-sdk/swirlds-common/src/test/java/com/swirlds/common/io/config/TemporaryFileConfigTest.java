/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TemporaryFileConfigTest {

    @Test
    public void testDefaultValuesValid() {
        // given
        final ConfigurationBuilder builder =
                ConfigurationBuilder.create().withConfigDataType(TemporaryFileConfig.class);

        // then
        Assertions.assertDoesNotThrow(
                () -> builder.build(), "All default values of TemporaryFileConfig should be valid");
    }

    @Test
    public void testGetRealTempPath() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConfigDataType(TemporaryFileConfig.class)
                .withConfigDataType(StateConfig.class)
                .build();

        // when
        final TemporaryFileConfig temporaryFileConfig = configuration.getConfigData(TemporaryFileConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final String temporaryFilePath = temporaryFileConfig.getTemporaryFilePath(stateConfig);

        // then
        Assertions.assertNotNull(temporaryFilePath, "The path should never be null");
        Assertions.assertTrue(
                temporaryFilePath.endsWith(temporaryFileConfig.temporaryFilePath()),
                "The path should be the real path of the configured folder.");
    }
}
