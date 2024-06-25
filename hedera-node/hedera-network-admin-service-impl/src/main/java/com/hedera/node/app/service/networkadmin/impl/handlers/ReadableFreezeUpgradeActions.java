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

package com.hedera.node.app.service.networkadmin.impl.handlers;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.utility.CommonUtils.nameToAlias;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.DISTINCT;
import static java.util.concurrent.CompletableFuture.runAsync;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.networkadmin.ReadableFreezeStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.PlatformState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Spliterators;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.StreamSupport;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides all the read-only actions that need to take place during upgrade
 */
public class ReadableFreezeUpgradeActions {
    private static final Logger log = LogManager.getLogger(ReadableFreezeUpgradeActions.class);

    private static final com.hedera.hapi.node.base.FileID UPGRADE_FILE_ID =
            com.hedera.hapi.node.base.FileID.newBuilder().fileNum(150L).build();

    private final NetworkAdminConfig adminServiceConfig;
    private final ReadableFreezeStore freezeStore;
    private final ReadableUpgradeFileStore upgradeFileStore;

    private final ReadableNodeStore nodeStore;

    private final ReadableStakingInfoStore stakingInfoStore;

    private final Executor executor;

    public static final String PREPARE_UPGRADE_DESC = "software";
    public static final String TELEMETRY_UPGRADE_DESC = "telemetry";
    public static final String MANUAL_REMEDIATION_ALERT = "Manual remediation may be necessary to avoid node ISS";

    public static final String NOW_FROZEN_MARKER = "now_frozen.mf";
    public static final String EXEC_IMMEDIATE_MARKER = "execute_immediate.mf";
    public static final String EXEC_TELEMETRY_MARKER = "execute_telemetry.mf";
    public static final String FREEZE_SCHEDULED_MARKER = "freeze_scheduled.mf";
    public static final String FREEZE_ABORTED_MARKER = "freeze_aborted.mf";

    public static final String MARK = "✓";

    public ReadableFreezeUpgradeActions(
            @NonNull final NetworkAdminConfig adminServiceConfig,
            @NonNull final ReadableFreezeStore freezeStore,
            @NonNull final Executor executor,
            @NonNull final ReadableUpgradeFileStore upgradeFileStore,
            @NonNull final ReadableNodeStore nodeStore,
            @NonNull final ReadableStakingInfoStore stakingInfoStore) {
        requireNonNull(adminServiceConfig, "Admin service config is required for freeze upgrade actions");
        requireNonNull(freezeStore, "Freeze store is required for freeze upgrade actions");
        requireNonNull(executor, "Executor is required for freeze upgrade actions");
        requireNonNull(upgradeFileStore, "Upgrade file store is required for freeze upgrade actions");
        requireNonNull(nodeStore, "Node store is required for freeze upgrade actions");
        requireNonNull(stakingInfoStore, "Staking info store is required for freeze upgrade actions");

        this.adminServiceConfig = adminServiceConfig;
        this.freezeStore = freezeStore;
        this.executor = executor;
        this.upgradeFileStore = upgradeFileStore;
        this.nodeStore = nodeStore;
        this.stakingInfoStore = stakingInfoStore;
    }

    public void externalizeFreezeIfUpgradePending() {
        log.info(
                "Externalizing freeze if upgrade pending, freezeStore: {}, updateFileHash: {}",
                freezeStore,
                freezeStore.updateFileHash());
        if (freezeStore.updateFileHash() != null) {
            writeCheckMarker(NOW_FROZEN_MARKER);
        }
    }

    protected void writeMarker(@NonNull final String file, @Nullable final Timestamp now) {
        requireNonNull(file);
        final Path artifactsDirPath = getAbsolutePath(adminServiceConfig.upgradeArtifactsPath());
        final var filePath = artifactsDirPath.resolve(file);
        try {
            if (!artifactsDirPath.toFile().exists()) {
                Files.createDirectories(artifactsDirPath);
            }
            final var contents = (now == null) ? MARK : (String.valueOf(now.seconds()));
            Files.writeString(filePath, contents);
            log.info("Wrote marker {}", filePath);
        } catch (final IOException e) {
            log.error("Failed to write NMT marker {}", filePath, e);
            log.error(MANUAL_REMEDIATION_ALERT);
        }
    }

    protected void writeCheckMarker(@NonNull final String file) {
        requireNonNull(file);
        writeMarker(file, null);
    }

    protected void writeSecondMarker(@NonNull final String file, @Nullable final Timestamp now) {
        requireNonNull(file);
        writeMarker(file, now);
    }

    public void catchUpOnMissedSideEffects(final PlatformState platformState) {
        catchUpOnMissedFreezeScheduling(platformState);
        catchUpOnMissedUpgradePrep();
    }

    public boolean isPreparedFileHashValidGiven(final byte[] curSpecialFilesHash, final byte[] hashFromTxnBody) {
        return Arrays.equals(curSpecialFilesHash, hashFromTxnBody);
    }

    public CompletableFuture<Void> extractTelemetryUpgrade(
            @NonNull final Bytes archiveData, @Nullable final Timestamp now) {
        requireNonNull(archiveData);
        return extractNow(archiveData, TELEMETRY_UPGRADE_DESC, EXEC_TELEMETRY_MARKER, now);
    }

    public CompletableFuture<Void> extractSoftwareUpgrade(@NonNull final Bytes archiveData) {
        requireNonNull(archiveData);
        return extractNow(archiveData, PREPARE_UPGRADE_DESC, EXEC_IMMEDIATE_MARKER, null);
    }

    public boolean isFreezeScheduled(final PlatformState platformState) {
        requireNonNull(platformState, "Cannot check freeze schedule without access to the dual state");
        final var freezeTime = platformState.getFreezeTime();
        return freezeTime != null && !freezeTime.equals(platformState.getLastFrozenTime());
    }

    /* -------- Internal Methods */
    private CompletableFuture<Void> extractNow(
            @NonNull final Bytes archiveData,
            @NonNull final String desc,
            @NonNull final String marker,
            @Nullable final Timestamp now) {
        requireNonNull(archiveData);
        requireNonNull(desc);
        requireNonNull(marker);

        final Path artifactsLoc = getAbsolutePath(adminServiceConfig.upgradeArtifactsPath());
        requireNonNull(artifactsLoc);
        final long size = archiveData.length();
        log.info("About to unzip {} bytes for {} update into {}", size, desc, artifactsLoc);
        // we spin off a separate thread to avoid blocking handleTransaction
        // if we block handle, there could be a dramatic spike in E2E latency at the time of PREPARE_UPGRADE
        final var activeNodes = desc.equals(PREPARE_UPGRADE_DESC) ? allActiveNodes() : null;
        return runAsync(
                () -> extractAndReplaceArtifacts(artifactsLoc, archiveData, size, desc, marker, now, activeNodes),
                executor);
    }

    private record ActiveNode(@NonNull Node node, @Nullable StakingNodeInfo stakingInfo) {}

    private List<ActiveNode> allActiveNodes() {
        return StreamSupport.stream(
                        Spliterators.spliterator(nodeStore.keys(), nodeStore.sizeOfState(), DISTINCT), false)
                .mapToLong(EntityNumber::number)
                .mapToObj(nodeStore::get)
                .filter(node -> node != null && !node.deleted())
                .sorted(Comparator.comparing(Node::nodeId))
                .map(node -> new ActiveNode(node, stakingInfoStore.get(node.nodeId())))
                .toList();
    }

    private void extractAndReplaceArtifacts(
            @NonNull final Path artifactsLoc,
            @NonNull final Bytes archiveData,
            final long size,
            @NonNull final String desc,
            @NonNull final String marker,
            @Nullable final Timestamp now,
            @Nullable List<ActiveNode> nodes) {
        try {
            FileUtils.cleanDirectory(artifactsLoc.toFile());
            UnzipUtility.unzip(archiveData.toByteArray(), artifactsLoc);
            log.info("Finished unzipping {} bytes for {} update into {}", size, desc, artifactsLoc);
            if (desc.equals(PREPARE_UPGRADE_DESC)) {
                requireNonNull(nodes, "Cannot generate config.txt without a valid list of active nodes");
                generateConfigPem(artifactsLoc, nodes);
                log.info("Finished generating config.txt and pem files into {}", artifactsLoc);
            }
            writeSecondMarker(marker, now);
        } catch (final IOException e) {
            // catch and log instead of throwing because upgrade process looks at the presence or absence
            // of marker files to determine whether to proceed with the upgrade
            // if second marker is present, that means the zip file was successfully extracted
            log.error("Failed to unzip archive for NMT consumption", e);
            log.error(MANUAL_REMEDIATION_ALERT);
        }
    }

    private void generateConfigPem(@NonNull final Path artifactsLoc, @NonNull final List<ActiveNode> activeNodes) {
        requireNonNull(artifactsLoc, "Cannot generate config.txt without a valid artifacts location");
        requireNonNull(activeNodes, "Cannot generate config.txt without a valid list of active nodes");
        final var configTxt = artifactsLoc.resolve("config.txt");

        if (activeNodes.isEmpty()) {
            log.info("Node state is empty, cannot generate config.txt"); // change to log error later
            return;
        }

        try (final var fw = new FileWriter(configTxt.toFile());
                final var bw = new BufferedWriter(fw)) {
            activeNodes.forEach(node -> writeConfigLineAndPem(node, bw, artifactsLoc));
            writeNextNodeId(activeNodes, bw);
        } catch (final IOException e) {
            log.error("Failed to generate {} with exception : {}", configTxt, e);
        }
    }

    private void writeNextNodeId(final List<ActiveNode> activeNodes, final BufferedWriter bw) {
        requireNonNull(activeNodes);
        requireNonNull(bw);
        // find max nodeId of all nodes and write nextNodeId as maxNodeId + 1
        final var maxNodeId = activeNodes.stream()
                .map(ActiveNode::node)
                .map(Node::nodeId)
                .max(Long::compareTo)
                .orElse(-1L);
        try {
            bw.write("nextNodeId, " + maxNodeId + 1);
        } catch (IOException e) {
            log.error("Failed to write nextNodeId {} with exception : {}", maxNodeId, e);
        }
    }

    private void writeConfigLineAndPem(
            @NonNull final ActiveNode activeNode, @NonNull final BufferedWriter bw, @NonNull final Path pathToWrite) {
        requireNonNull(activeNode);
        requireNonNull(bw);
        requireNonNull(pathToWrite);

        var line = new StringBuilder();
        int weight = 0;
        final var node = activeNode.node();
        final var name = "node" + node.nodeId();
        final var alias = nameToAlias(name);
        final var pemFile = pathToWrite.resolve("s-public-" + alias + ".pem");
        final int INT = 0;
        final int EXT = 1;

        final var stakingNodeInfo = activeNode.stakingInfo();
        if (stakingNodeInfo != null) {
            weight = stakingNodeInfo.weight();
        }

        final var gossipEndpoints = node.gossipEndpoint();
        if (gossipEndpoints.size() > 1) {
            line.append("address, ")
                    .append(node.nodeId())
                    .append(", ")
                    .append(node.nodeId()) // nodeId as nickname
                    .append(", ")
                    .append(name)
                    .append(", ")
                    .append(weight)
                    .append(", ")
                    .append(gossipEndpoints.get(INT).ipAddressV4().asUtf8String())
                    .append(", ")
                    .append(gossipEndpoints.get(INT).port())
                    .append(", ")
                    .append(gossipEndpoints.get(EXT).ipAddressV4().asUtf8String())
                    .append(", ")
                    .append(gossipEndpoints.get(EXT).port())
                    .append(", ")
                    .append(node.accountId().shardNum() + "." + node.accountId().realmNum() + "."
                            + node.accountId().accountNum())
                    .append("\n");
            try {
                bw.write(line.toString());
            } catch (IOException e) {
                log.error("Failed to write line {} with exception : {}", line, e);
            }
            try {
                Files.write(pemFile, node.gossipCaCertificate().toByteArray());
            } catch (IOException e) {
                log.error("Failed to write to {} with exception : {}", pemFile, e);
            }
        } else {
            log.error("Node has {} gossip endpoints, expected greater than 1", gossipEndpoints.size());
        }
    }

    private void catchUpOnMissedFreezeScheduling(final PlatformState platformState) {
        final var isUpgradePrepared = freezeStore.updateFileHash() != null;
        if (isFreezeScheduled(platformState) && isUpgradePrepared) {
            final var freezeTime = requireNonNull(platformState.getFreezeTime());
            writeMarker(
                    FREEZE_SCHEDULED_MARKER,
                    Timestamp.newBuilder()
                            .nanos(freezeTime.getNano())
                            .seconds(freezeTime.getEpochSecond())
                            .build());
        }
        /* If we missed a FREEZE_ABORT, we are at risk of having a problem down the road.
        But writing a "defensive" freeze_aborted.mf is itself too risky, as it will keep
        us from correctly (1) catching up on a missed PREPARE_UPGRADE; or (2) handling an
        imminent PREPARE_UPGRADE. */
    }

    private void catchUpOnMissedUpgradePrep() {
        if (freezeStore.updateFileHash() == null) {
            return;
        }

        try {
            final var curSpecialFileContents = upgradeFileStore.getFull(UPGRADE_FILE_ID);
            if (!isPreparedFileHashValidGiven(
                    noThrowSha384HashOf(curSpecialFileContents.toByteArray()),
                    freezeStore.updateFileHash().toByteArray())) {
                log.error(
                        "Cannot redo NMT upgrade prep, file 0.0.{} changed since FREEZE_UPGRADE",
                        UPGRADE_FILE_ID.fileNum());
                log.error(MANUAL_REMEDIATION_ALERT);
                return;
            }
            extractSoftwareUpgrade(curSpecialFileContents).join();
        } catch (final IOException e) {
            log.error(
                    "Cannot redo NMT upgrade prep, file 0.0.{} changed since FREEZE_UPGRADE",
                    UPGRADE_FILE_ID.fileNum(),
                    e);
            log.error(MANUAL_REMEDIATION_ALERT);
        }
    }
}
