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
 * @param port The port for plain grpc traffic. Must be non-negative. A value of 0 indicates an ephemeral port should be
 *             automatically selected by the computer. Must not be the same value as {@link #tlsPort()} unless both are
 *             0. Must be a value between 0 and 65535, inclusive.
 * @param tlsPort The port for tls-encrypted grpc traffic. Must be non-negative. A value of 0 indicates an ephemeral
 *                port should be automatically selected by the computer. Must not be the same value as {@link #port()}
 *                unless both are 0. Must be a value between 0 and 65535, inclusive.
 * @param nodeOperatorPortEnabled Whether the node operator port is enabled. If true, the node operator port will be
 *                                enabled and must be a non-negative value between 1 and 65535, inclusive. If false, the
 *                                node operator port will be disabled and the value of {@link #nodeOperatorPort()} will be
 *                                ignored.
 * @param nodeOperatorPort The port for the node operator. Must be a non-negative value between 1 and 65535, inclusive.
 * @param workflowsPort Deprecated
 * @param workflowsTlsPort Deprecated
 */
@ConfigData("grpc")
public record GrpcConfig(
        @ConfigProperty(defaultValue = "50211") @Min(0) @Max(65535) @NodeProperty int port,
        @ConfigProperty(defaultValue = "50212") @Min(0) @Max(65535) @NodeProperty int tlsPort,
        @ConfigProperty(defaultValue = "false") @NodeProperty boolean nodeOperatorPortEnabled,
        @ConfigProperty(defaultValue = "50213") @Min(1) @Max(65535) @NodeProperty int nodeOperatorPort,
        @ConfigProperty(defaultValue = "60211") @Min(0) @Max(65535) @NodeProperty int workflowsPort,
        @ConfigProperty(defaultValue = "60212") @Min(0) @Max(65535) @NodeProperty int workflowsTlsPort) {

    public GrpcConfig {
        if (port == tlsPort && port != 0) {
            throw new IllegalArgumentException("grpc.port and grpc.tlsPort must be different");
        }

        if (workflowsPort == workflowsTlsPort && workflowsPort != 0) {
            throw new IllegalArgumentException("grpc.workflowsPort and grpc.workflowsTlsPort must be different");
        }

        if (nodeOperatorPortEnabled && (nodeOperatorPort == port || nodeOperatorPort == tlsPort)) {
            throw new IllegalArgumentException(
                    "grpc.nodeOperatorPort must be different from grpc.port and grpc.tlsPort");
        }
    }
}
