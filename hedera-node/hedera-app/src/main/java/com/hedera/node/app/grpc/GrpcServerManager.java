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

package com.hedera.node.app.grpc;

import com.hedera.node.config.data.GrpcConfig;

/**
 * Manages the lifecycle of all gRPC servers.
 *
 * <p>Our node will run at least one gRPC server on the port specified in the {@link GrpcConfig}. It will also attempt
 * to run a gRPC server on the tls-port specified in the same config. If it fails to start the tls-port, it will log
 * a warning and continue.
 */
public interface GrpcServerManager {
    /**
     * Starts the gRPC servers.
     * @throws IllegalStateException if the servers are already running
     */
    void start();

    /**
     * Stops the gRPC servers. This call is idempotent.
     */
    void stop();

    /**
     * True, if this server is started and running.
     *
     * @return {@code true} if the server is running, false otherwise.
     */
    boolean isRunning();

    /**
     * Gets the port that the non-tls gRPC server is listening on.
     *
     * @return the port of the listening server, or -1 if no server is listening on that port. Note that this value may
     *         be different from the port designation in configuration. If the special port 0 is used in config, it will
     *         denote using an ephemeral port from the OS ephemeral port range. The actual port used will be returned by
     *         this method.
     */
    int port();

    /**
     * Gets the port that the tls gRPC server is listening on.
     *
     * @return the port of the listening tls server, or -1 if no server listening on that port
     */
    int tlsPort();

    /**
     * Gets the port that the node operator gRPC server is listening on.
     *
     * @return the port of the listening server, or -1 if no server is listening on that port. Note that this value may
     *         be different from the port designation in configuration.
     */
    int nodeOperatorPort();
}
