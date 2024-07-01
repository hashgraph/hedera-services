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

package com.hedera.services.bdd.junit.hedera.embedded;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.APPLICATION_PROPERTIES;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.GENESIS_PROPERTIES;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.STREAMS_DIR;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.UPGRADE_ARTIFACTS_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.ensureDir;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.updateUpgradeArtifactsProperty;

import com.hedera.node.app.Hedera;
import com.hedera.services.bdd.junit.hedera.AbstractLocalNode;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A node running in the same OS process as the JUnit test runner, with a direct reference
 * to a single {@link Hedera} instance shared by every node in the embedded "network".
 *
 * <p>This {@link Hedera} instance does not have a reference to an actual {@link Platform},
 * but instead a {@code FakePlatform} which orders submitted transactions exactly as they
 * are received.
 */
public class EmbeddedNode extends AbstractLocalNode<EmbeddedNode> implements HederaNode {
    public EmbeddedNode(@NonNull final NodeMetadata metadata) {
        super(metadata);
    }

    @Override
    public HederaNode start() {
        assertWorkingDirInitialized();
        // Without the normal lag of node startup, record stream assertions may check this directory too fast
        ensureDir(getExternalPath(STREAMS_DIR).normalize().toString());
        System.setProperty(
                "hedera.app.properties.path",
                getExternalPath(APPLICATION_PROPERTIES).toAbsolutePath().toString());
        System.setProperty(
                "hedera.genesis.properties.path",
                getExternalPath(GENESIS_PROPERTIES).toAbsolutePath().toString());
        System.setProperty(
                "hedera.recordStream.logDir",
                getExternalPath(STREAMS_DIR).getParent().toString());
        return this;
    }

    @Override
    public EmbeddedNode initWorkingDir(@NonNull String configTxt) {
        super.initWorkingDir(configTxt);
        updateUpgradeArtifactsProperty(getExternalPath(APPLICATION_PROPERTIES), getExternalPath(UPGRADE_ARTIFACTS_DIR));
        return this;
    }

    @Override
    public boolean stop() {
        throw new UnsupportedOperationException("Cannot stop a single node in an embedded network");
    }

    @Override
    public boolean terminate() {
        throw new UnsupportedOperationException("Cannot terminate a single node in an embedded network");
    }

    @Override
    public CompletableFuture<Void> statusFuture(
            @NonNull final PlatformStatus status, @Nullable final Consumer<NodeStatus> nodeStatusObserver) {
        throw new UnsupportedOperationException("Prefer awaiting status of the embedded network");
    }

    @Override
    public CompletableFuture<Void> stopFuture() {
        throw new UnsupportedOperationException("Cannot stop a single node in an embedded network");
    }

    @Override
    protected EmbeddedNode self() {
        return this;
    }
}
