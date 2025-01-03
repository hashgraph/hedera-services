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

package com.hedera.services.bdd.junit.hedera.embedded;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.APPLICATION_PROPERTIES;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.BLOCK_STREAMS_DIR;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.DATA_CONFIG_DIR;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.GENESIS_PROPERTIES;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.LOG4J2_XML;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.NODE_ADMIN_KEYS_JSON;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.RECORD_STREAMS_DIR;
import static com.hedera.services.bdd.junit.hedera.ExternalPath.UPGRADE_ARTIFACTS_DIR;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork.CONCURRENT_WORKING_DIR;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork.REPEATABLE_WORKING_DIR;
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
import org.apache.logging.log4j.core.config.Configurator;

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
        ensureDir(getExternalPath(RECORD_STREAMS_DIR).normalize().toString());
        System.setProperty(
                "hedera.app.properties.path",
                getExternalPath(APPLICATION_PROPERTIES).toAbsolutePath().toString());
        System.setProperty(
                "hedera.genesis.properties.path",
                getExternalPath(GENESIS_PROPERTIES).toAbsolutePath().toString());
        System.setProperty(
                "hedera.recordStream.logDir",
                getExternalPath(RECORD_STREAMS_DIR).getParent().toString());
        System.setProperty(
                "blockStream.blockFileDir",
                getExternalPath(BLOCK_STREAMS_DIR).getParent().toString());
        System.setProperty(
                "networkAdmin.upgradeSysFilesLoc",
                getExternalPath(DATA_CONFIG_DIR).toAbsolutePath().toString());
        System.setProperty(
                "bootstrap.nodeAdminKeys.path",
                getExternalPath(NODE_ADMIN_KEYS_JSON).toAbsolutePath().toString());
        System.setProperty("hedera.profiles.active", "DEV");
        final var log4j2ConfigLoc = getExternalPath(LOG4J2_XML).toString();
        if (isForShared(log4j2ConfigLoc)) {
            System.setProperty("log4j.configurationFile", log4j2ConfigLoc);
            try (var ignored = Configurator.initialize(null, "")) {
                // Only initialize logging for the shared embedded network
            }
        }
        return this;
    }

    @Override
    public EmbeddedNode initWorkingDir(@NonNull final String configTxt) {
        super.initWorkingDir(configTxt);
        updateUpgradeArtifactsProperty(getExternalPath(APPLICATION_PROPERTIES), getExternalPath(UPGRADE_ARTIFACTS_DIR));
        return this;
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

    private boolean isForShared(@NonNull final String log4jConfigLoc) {
        return log4jConfigLoc.contains(CONCURRENT_WORKING_DIR) || log4jConfigLoc.contains(REPEATABLE_WORKING_DIR);
    }
}
