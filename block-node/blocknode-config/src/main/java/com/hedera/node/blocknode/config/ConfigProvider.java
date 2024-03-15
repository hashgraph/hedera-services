/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.blocknode.config;

import com.hedera.node.blocknode.config.data.BlockNodeConfig;
import com.hedera.node.blocknode.config.data.BlockNodeGrpcConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;

/**
 * Constructs and returns a {@link Configuration} instance that contains only those configs used at startup during
 * the bootstrapping phase.
 */
public class ConfigProvider {
    /**
     * The bootstrap configuration.
     */
    public final Configuration configuration;

    public ConfigProvider() {
        final var builder = ConfigurationBuilder.create()
                .withConfigDataType(BlockNodeGrpcConfig.class)
                .withConfigDataType(BlockNodeConfig.class);
        this.configuration = builder.build();
    }
}
