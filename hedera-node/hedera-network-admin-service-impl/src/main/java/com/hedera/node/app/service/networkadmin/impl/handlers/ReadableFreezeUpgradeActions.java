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
import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.readableId;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.networkadmin.ReadableFreezeStore;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.PlatformState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
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
    private final NetworkAdminConfig adminServiceConfig;
    private final ReadableFreezeStore freezeStore;
    private final ReadableUpgradeFileStore upgradeFileStore;

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
            @NonNull final ReadableUpgradeFileStore upgradeFileStore) {
        requireNonNull(adminServiceConfig, "Admin service config is required for freeze upgrade actions");
        requireNonNull(freezeStore, "Freeze store is required for freeze upgrade actions");
        requireNonNull(executor, "Executor is required for freeze upgrade actions");
        requireNonNull(upgradeFileStore, "Upgrade file store is required for freeze upgrade actions");

        this.adminServiceConfig = adminServiceConfig;
        this.freezeStore = freezeStore;
        this.executor = executor;
        this.upgradeFileStore = upgradeFileStore;
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
        final Path artifactsDirPath = Paths.get(adminServiceConfig.upgradeArtifactsPath());
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

    private void catchUpOnMissedFreezeScheduling(final PlatformState platformState) {
        final var isUpgradePrepared = freezeStore.updateFileHash() != null;
        if (isFreezeScheduled(platformState) && isUpgradePrepared) {
            final var freezeTime = platformState.getFreezeTime();
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

        final var upgradeFileId = STATIC_PROPERTIES.scopedFileWith(150);
        try {
            final var curSpecialFileContents = upgradeFileStore.getFull(toPbj(upgradeFileId));
            if (!isPreparedFileHashValidGiven(
                    noThrowSha384HashOf(curSpecialFileContents.toByteArray()),
                    freezeStore.updateFileHash().toByteArray())) {
                log.error(
                        "Cannot redo NMT upgrade prep, file {} changed since FREEZE_UPGRADE",
                        () -> readableId(upgradeFileId));
                log.error(MANUAL_REMEDIATION_ALERT);
                return;
            }
            extractSoftwareUpgrade(curSpecialFileContents).join();
        } catch (final IOException e) {
            log.error(
                    "Cannot redo NMT upgrade prep, file {} changed since FREEZE_UPGRADE", readableId(upgradeFileId), e);
            log.error(MANUAL_REMEDIATION_ALERT);
        }
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
        final String artifactsLoc = adminServiceConfig.upgradeArtifactsPath();
        requireNonNull(artifactsLoc);
        log.info("About to unzip {} bytes for {} update into {}", size, desc, artifactsLoc);
        // we spin off a separate thread to avoid blocking handleTransaction
        // if we block handle, there could be a dramatic spike in E2E latency at the time of PREPARE_UPGRADE
        return runAsync(() -> extractAndReplaceArtifacts(artifactsLoc, archiveData, size, desc, marker, now), executor);
    }

    private void extractAndReplaceArtifacts(
            String artifactsLoc, Bytes archiveData, long size, String desc, String marker, Timestamp now) {
        try {
            FileUtils.cleanDirectory(new File(artifactsLoc));
            UnzipUtility.unzip(archiveData.toByteArray(), Paths.get(artifactsLoc));
            log.info("Finished unzipping {} bytes for {} update into {}", size, desc, artifactsLoc);
            writeSecondMarker(marker, now);
        } catch (final IOException e) {
            // catch and log instead of throwing because upgrade process looks at the presence or absence
            // of marker files to determine whether to proceed with the upgrade
            // if second marker is present, that means the zip file was successfully extracted
            log.error("Failed to unzip archive for NMT consumption", e);
            log.error(MANUAL_REMEDIATION_ALERT);
        }
    }
}
