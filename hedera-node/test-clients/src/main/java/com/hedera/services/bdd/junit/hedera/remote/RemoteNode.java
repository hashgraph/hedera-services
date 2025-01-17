/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.hedera.remote;

import com.hedera.services.bdd.junit.hedera.AbstractNode;
import com.hedera.services.bdd.junit.hedera.ExternalPath;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.MarkerFile;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RemoteNode extends AbstractNode implements HederaNode {
    public RemoteNode(@NonNull final NodeMetadata metadata) {
        super(metadata);
    }

    @Override
    public Path getExternalPath(@NonNull final ExternalPath path) {
        throw new UnsupportedOperationException("There is no local path to a remote node's " + path);
    }

    @Override
    public HederaNode initWorkingDir(@NonNull final String configTxt, final boolean useTestGossipFiles) {
        throw new UnsupportedOperationException("Cannot initialize a remote node's working directory");
    }

    @Override
    public HederaNode start() {
        // No-op, remote nodes must already be running
        return this;
    }

    @Override
    public CompletableFuture<Void> mfFuture(@NonNull MarkerFile markerFile) {
        throw new UnsupportedOperationException("Cannot check marker files on a remote node");
    }

    @Override
    public CompletableFuture<Void> statusFuture(
            @NonNull final PlatformStatus status, @Nullable final Consumer<NodeStatus> nodeStatusObserver) {
        // (FUTURE) Implement this via Prometheus and gRPC if it turns out to be useful
        throw new UnsupportedOperationException("Cannot check the status of a remote node");
    }

    @Override
    public CompletableFuture<Void> stopFuture() {
        // (FUTURE) Implement this via Prometheus and gRPC if it turns out to be useful
        throw new UnsupportedOperationException("Cannot stop a remote node");
    }
}
