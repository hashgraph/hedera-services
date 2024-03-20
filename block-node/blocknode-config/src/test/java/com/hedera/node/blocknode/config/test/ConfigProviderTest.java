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

package com.hedera.node.blocknode.config.test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.config.data.BlockNodeGrpcConfig;
import org.junit.jupiter.api.Test;

class ConfigProviderTest {

    @Test
    void testInitialConfig() {
        // given
        final var configProvider = new ConfigProvider();

        // when
        final var configuration = configProvider.configuration;

        // then
        assertNotNull(configuration);
        assertNotEquals(configuration.getConfigData(BlockNodeGrpcConfig.class).port(), 0);
        assertNotEquals(configuration.getConfigData(BlockNodeGrpcConfig.class).tlsPort(), 0);
    }
}
