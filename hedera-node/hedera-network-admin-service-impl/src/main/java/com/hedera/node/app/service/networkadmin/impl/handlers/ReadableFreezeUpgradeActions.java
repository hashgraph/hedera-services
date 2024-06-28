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
import static java.util.concurrent.CompletableFuture.runAsync;

import com.google.common.collect.Streams;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.addressbook.Node;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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

        final long size = archiveData.length();
        final Path artifactsLoc = getAbsolutePath(adminServiceConfig.upgradeArtifactsPath());
        requireNonNull(artifactsLoc);
        log.info("About to unzip {} bytes for {} update into {}", size, desc, artifactsLoc);
        // we spin off a separate thread to avoid blocking handleTransaction
        // if we block handle, there could be a dramatic spike in E2E latency at the time of PREPARE_UPGRADE
        return runAsync(() -> extractAndReplaceArtifacts(artifactsLoc, archiveData, size, desc, marker, now), executor);
    }

    private void extractAndReplaceArtifacts(
            Path artifactsLoc, Bytes archiveData, long size, String desc, String marker, Timestamp now) {
        try {
            FileUtils.cleanDirectory(artifactsLoc.toFile());
            UnzipUtility.unzip(archiveData.toByteArray(), artifactsLoc);
            log.info("Finished unzipping {} bytes for {} update into {}", size, desc, artifactsLoc);
            if (desc.equals(PREPARE_UPGRADE_DESC)) {
                generateConfigPem(artifactsLoc);
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

    private void generateConfigPem(@NonNull final Path artifactsLoc) {
        requireNonNull(artifactsLoc, "Cannot generate config.txt without a valid artifacts location");
        final var configTxt = artifactsLoc.resolve("config.txt");

        final var nodeIds = nodeStore.keys();
        if (nodeIds == null) {
            log.info("Node state is empty, cannot generate config.txt"); // change to log error later
            return;
        }

        try (FileWriter fw = new FileWriter(configTxt.toFile());
                BufferedWriter bw = new BufferedWriter(fw)) {
            Streams.stream(nodeIds)
                    .map(nodeId -> nodeStore.get(nodeId.number()))
                    .filter(node -> node != null && !node.deleted())
                    .sorted(Comparator.comparing(Node::nodeId))
                    .forEach(node -> writeConfigLineAndPem(node, bw, artifactsLoc));
        } catch (final IOException e) {
            log.error("Failed to generate {} with exception : {}", configTxt, e);
        }
    }

    private void writeConfigLineAndPem(
            @NonNull final Node node, @NonNull final BufferedWriter bw, @NonNull final Path pathToWrite) {
        requireNonNull(node);
        requireNonNull(bw);
        requireNonNull(pathToWrite);

        var line = new StringBuilder();
        int weight = 0;
        final var val = node.nodeId() + 1;
        final var name = "node" + val;
        final var alias = nameToAlias(name);
        final var pemFile = pathToWrite.resolve("s-public-" + alias + ".pem");
        final int INT = 0;
        final int EXT = 1;

        final var stakingNodeInfo = stakingInfoStore.get(node.nodeId());
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
