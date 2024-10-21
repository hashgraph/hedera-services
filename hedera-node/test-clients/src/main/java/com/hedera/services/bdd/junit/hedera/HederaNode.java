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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus;
import com.hedera.services.bdd.spec.HapiSpec;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface HederaNode {
    /**
     * Gets the hostname of the node.
     *
     * @return the hostname of the node
     */
    String getHost();

    /**
     * Gets the port number of the gRPC service.
     *
     * @return the port number of the gRPC service
     */
    int getGrpcPort();

    /**
     * Gets the port number of the node operator gRPC service.
     *
     * @return the port number of the node operator gRPC service
     */
    int getGrpcNodeOperatorPort();

    /**
     * Gets the node ID, such as 0, 1, 2, or 3.
     * @return the node ID
     */
    long getNodeId();

    /**
     * The name of the node, such as "Alice" or "Bob".
     * @return the node name
     */
    String getName();

    /**
     * Gets the node Account ID
     * @return the node account ID
     */
    AccountID getAccountId();

    /**
     * Gets the path to a external file or directory used by the node.
     *
     * @param path the external path to get
     * @return the requested external path
     */
    Path getExternalPath(@NonNull ExternalPath path);

    /**
     * Initializes the working directory for the node. Must be called before the node is started.
     *
     * @param configTxt the address book the node should start with
     * @return this
     */
    HederaNode initWorkingDir(String configTxt);

    /**
     * Starts the node software.
     *
     * @throws IllegalStateException if the working directory was not initialized
     * @return this
     */
    HederaNode start();

    /**
     * Returns a future that resolves when the node has the given status.
     *
     * @param status the status to wait for
     * @param nodeStatusObserver if non-null, an observer that will receive the node's status each time it is checked
     * @return a future that resolves when the node has the given status
     */
    CompletableFuture<Void> statusFuture(
            @NonNull PlatformStatus status, @Nullable Consumer<NodeStatus> nodeStatusObserver);

    /**
     * Returns a future that resolves when the node has written the specified <i>.mf</i> file.
     *
     * @param markerFile the marker file to wait for
     * @return a future that resolves when the node has written the specified <i>.mf</i> file
     */
    CompletableFuture<Void> mfFuture(@NonNull MarkerFile markerFile);

    /**
     * Begins stopping the node, returning a future that resolves when this is done.
     *
     * @return a future that resolves when the node has stopped
     */
    CompletableFuture<Void> stopFuture();

    /**
     * Returns the string that would summarize this node as a target
     * server in a {@link HapiSpec} that is submitting transactions
     * and sending queries via gRPC.
     *
     * <p><b>(FUTURE)</b> Remove this method once {@link HapiSpec} is
     * refactored to be agnostic about how a target node should
     * receive transactions and queries. (E.g. an embedded node
     * can have its workflows directly invoked.)
     *
     * @return this node's HAPI spec identifier
     */
    default String hapiSpecInfo() {
        return getHost() + ":" + getGrpcPort() + ":0.0." + getAccountId().accountNumOrThrow();
    }

    /**
     * Returns the metadata for this node.
     * @return the metadata for this node
     */
    NodeMetadata metadata();
}
