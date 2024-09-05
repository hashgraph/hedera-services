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

package com.hedera.services.bdd.junit.hedera;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;

public record NodeMetadata(
        long nodeId,
        String name,
        AccountID accountId,
        String host,
        int grpcPort,
        int gossipPort,
        int gossipTlsPort,
        int prometheusPort,
        @Nullable Path workingDir) {
    public static final int UNKNOWN_PORT = -1;

    /**
     * Create a new instance with the same values as this instance, but different ports.
     *
     * @param grpcPort the new grpc port
     * @param gossipPort the new gossip port
     * @param tlsGossipPort the new tls gossip port
     * @param prometheusPort the new prometheus port
     * @return a new instance with the same values as this instance, but different ports
     */
    public NodeMetadata withNewPorts(
            final int grpcPort, final int gossipPort, final int tlsGossipPort, final int prometheusPort) {
        return new NodeMetadata(
                nodeId, name, accountId, host, grpcPort, gossipPort, tlsGossipPort, prometheusPort, workingDir);
    }

    /**
     * Returns the working directory for this node, or throws an exception if the working directory is null.
     *
     * @return the working directory for this node
     */
    public @NonNull Path workingDirOrThrow() {
        return requireNonNull(workingDir);
    }
}
