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

package com.hedera.node.config.data;

import com.hedera.node.config.NodeProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;

/**
 * @param port    The port for plain grpc traffic. Must be non-negative. A value of 0 indicates an ephemeral port should be
 *                automatically selected by the computer. Must not be the same value as {@link #tlsPort()} unless both are
 *                0. Must be a value between 0 and 65535, inclusive.
 * @param tlsPort The port for tls-encrypted grpc traffic. Must be non-negative. A value of 0 indicates an ephemeral
 *                port should be automatically selected by the computer. Must not be the same value as {@link #port()}
 *                unless both are 0. Must be a value between 0 and 65535, inclusive.
 */
@ConfigData("blockNodeGrpc")
public record BlockNodeGrpcConfig(
        @ConfigProperty(defaultValue = "50601") @Min(0) @Max(65535) @NodeProperty int port,
        @ConfigProperty(defaultValue = "50602") @Min(0) @Max(65535) @NodeProperty int tlsPort) {

    public BlockNodeGrpcConfig {
        if (port == tlsPort && port != 0) {
            throw new IllegalArgumentException("grpc.port and grpc.tlsPort must be different");
        }
    }
}
