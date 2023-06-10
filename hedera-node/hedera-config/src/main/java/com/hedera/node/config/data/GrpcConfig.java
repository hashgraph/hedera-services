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

package com.hedera.node.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * @param port The port for plain grpc traffic. Must be non-negative. A value of 0 indicates an ephemeral port should be
 *             automatically selected by the computer. Must not be the same value as {@link #tlsPort()} unless both are
 *             0. Must be a value between 0 and 65535, inclusive.
 * @param tlsPort The port for tls-encrypted grpc traffic. Must be non-negative. A value of 0 indicates an ephemeral
 *                port should be automatically selected by the computer. Must not be the same value as {@link #port()}
 *                unless both are 0. Must be a value between 0 and 65535, inclusive.
 * @param workflowsPort Deprecated
 * @param workflowsTlsPort Deprecated
 */
@ConfigData("grpc")
public record GrpcConfig(
        @ConfigProperty(defaultValue = "50211") int port,
        @ConfigProperty(defaultValue = "50212") int tlsPort,
        @ConfigProperty(defaultValue = "60211") int workflowsPort,
        @ConfigProperty(defaultValue = "60212") int workflowsTlsPort) {

    public GrpcConfig {
        if (port == tlsPort && port != 0) {
            throw new IllegalArgumentException("grpc.port and grpc.tlsPort must be different");
        }

        if (invalidPort(port)) {
            throw new IllegalArgumentException("grpc.port must be between 0 and 65535");
        }

        if (invalidPort(tlsPort)) {
            throw new IllegalArgumentException("grpc.tlsPort must be between 0 and 65535");
        }

        if (workflowsPort == workflowsTlsPort) {
            throw new IllegalArgumentException("grpc.workflowsPort and grpc.workflowsTlsPort must be different");
        }

        if (invalidPort(workflowsPort)) {
            throw new IllegalArgumentException("grpc.workflowsPort must be between 0 and 65535");
        }

        if (invalidPort(workflowsTlsPort)) {
            throw new IllegalArgumentException("grpc.workflowsTlsPort must be between 0 and 65535");
        }
    }

    private boolean invalidPort(final int port) {
        return port < 0 || port > 65535;
    }
}
