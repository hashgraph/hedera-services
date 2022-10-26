/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.network;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static java.util.concurrent.CompletableFuture.runAsync;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.swirlds.common.system.SwirldDualState;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class UpgradeActions {
    private static final Logger log = LogManager.getLogger(UpgradeActions.class);

    private static final String PREPARE_UPGRADE_DESC = "software";
    private static final String TELEMETRY_UPGRADE_DESC = "telemetry";
    private static final String MANUAL_REMEDIATION_ALERT =
            "Manual remediation may be necessary to avoid node ISS";

    public static final String NOW_FROZEN_MARKER = "now_frozen.mf";
    public static final String EXEC_IMMEDIATE_MARKER = "execute_immediate.mf";
    public static final String EXEC_TELEMETRY_MARKER = "execute_telemetry.mf";
    public static final String FREEZE_SCHEDULED_MARKER = "freeze_scheduled.mf";
    public static final String FREEZE_ABORTED_MARKER = "freeze_aborted.mf";

    public static final String MARK = "✓";

    public interface UnzipAction {
        void unzip(byte[] archiveData, String artifactsLoc) throws IOException;
    }

    private FileStringWriter fileStringWriter = Files::writeString;

    private final UnzipAction unzipAction;
    private final GlobalDynamicProperties dynamicProperties;
    private final Supplier<SwirldDualState> dualState;
    private final Supplier<MerkleSpecialFiles> specialFiles;
    private final Supplier<MerkleNetworkContext> networkCtx;

    @Inject
    public UpgradeActions(
            final UnzipAction unzipAction,
            final GlobalDynamicProperties dynamicProperties,
            final Supplier<SwirldDualState> dualState,
            final Supplier<MerkleSpecialFiles> specialFiles,
            final Supplier<MerkleNetworkContext> networkCtx) {
        this.dualState = dualState;
        this.networkCtx = networkCtx;
        this.unzipAction = unzipAction;
        this.specialFiles = specialFiles;
        this.dynamicProperties = dynamicProperties;
    }

    public void externalizeFreezeIfUpgradePending() {
        if (networkCtx.get().hasPreparedUpgrade()) {
            writeCheckMarker(NOW_FROZEN_MARKER);
        }
    }

    public CompletableFuture<Void> extractTelemetryUpgrade(
            final byte[] archiveData, final Instant now) {
        return extractNow(archiveData, TELEMETRY_UPGRADE_DESC, EXEC_TELEMETRY_MARKER, now);
    }

    public CompletableFuture<Void> extractSoftwareUpgrade(final byte[] archiveData) {
        return extractNow(archiveData, PREPARE_UPGRADE_DESC, EXEC_IMMEDIATE_MARKER, null);
    }

    public void scheduleFreezeOnlyAt(final Instant freezeTime) {
        withNonNullDualState("schedule freeze", ds -> ds.setFreezeTime(freezeTime));
    }

    public void scheduleFreezeUpgradeAt(final Instant freezeTime) {
        withNonNullDualState(
                "schedule freeze",
                ds -> {
                    ds.setFreezeTime(freezeTime);
                    writeSecondMarker(FREEZE_SCHEDULED_MARKER, freezeTime);
                });
    }

    public void abortScheduledFreeze() {
        withNonNullDualState(
                "abort freeze",
                ds -> {
                    ds.setFreezeTime(null);
                    writeCheckMarker(FREEZE_ABORTED_MARKER);
                });
    }

    public boolean isFreezeScheduled() {
        final var ans = new AtomicBoolean();
        withNonNullDualState(
                "check freeze schedule",
                ds -> {
                    final var freezeTime = ds.getFreezeTime();
                    ans.set(freezeTime != null && !freezeTime.equals(ds.getLastFrozenTime()));
                });
        return ans.get();
    }

    public void catchUpOnMissedSideEffects() {
        catchUpOnMissedFreezeScheduling();
        catchUpOnMissedUpgradePrep();
    }

    /* --- Internal methods --- */

    private CompletableFuture<Void> extractNow(
            final byte[] archiveData,
            final String desc,
            final String marker,
            @Nullable final Instant now) {
        final var size = archiveData.length;
        final var artifactsLoc = dynamicProperties.upgradeArtifactsLoc();
        log.info("About to unzip {} bytes for {} update into {}", size, desc, artifactsLoc);
        return runAsync(
                () -> {
                    try {
                        FileUtils.cleanDirectory(new File(artifactsLoc));
                        unzipAction.unzip(archiveData, artifactsLoc);
                        log.info(
                                "Finished unzipping {} bytes for {} update into {}",
                                size,
                                desc,
                                artifactsLoc);
                        writeSecondMarker(marker, now);
                    } catch (IOException e) {
                        log.error("Failed to unzip archive for NMT consumption", e);
                        log.error(MANUAL_REMEDIATION_ALERT);
                    }
                });
    }

    private void catchUpOnMissedFreezeScheduling() {
        final var isUpgradePrepared = networkCtx.get().hasPreparedUpgrade();
        if (isFreezeScheduled() && isUpgradePrepared) {
            writeMarker(FREEZE_SCHEDULED_MARKER, dualState.get().getFreezeTime());
        }
        /* If we missed a FREEZE_ABORT, we are at risk of having a problem down the road.
        But writing a "defensive" freeze_aborted.mf is itself too risky, as it will keep
        us from correctly (1) catching up on a missed PREPARE_UPGRADE; or (2) handling an
        imminent PREPARE_UPGRADE. */
    }

    private void catchUpOnMissedUpgradePrep() {
        final var curNetworkCtx = networkCtx.get();
        if (!curNetworkCtx.hasPreparedUpgrade()) {
            return;
        }

        final var curSpecialFiles = specialFiles.get();
        final var upgradeFileNum = curNetworkCtx.getPreparedUpdateFileNum();
        final var upgradeFileId = STATIC_PROPERTIES.scopedFileWith(upgradeFileNum);
        if (!curNetworkCtx.isPreparedFileHashValidGiven(curSpecialFiles)) {
            log.error(
                    "Cannot redo NMT upgrade prep, file {} changed since FREEZE_UPGRADE",
                    () -> readableId(upgradeFileId));
            log.error(MANUAL_REMEDIATION_ALERT);
            return;
        }

        final var archiveData = curSpecialFiles.get(upgradeFileId);
        extractSoftwareUpgrade(archiveData).join();
    }

    private void withNonNullDualState(String actionDesc, Consumer<SwirldDualState> action) {
        final var curDualState = dualState.get();
        Objects.requireNonNull(
                curDualState, "Cannot " + actionDesc + " without access to the dual state");
        action.accept(curDualState);
    }

    private void writeCheckMarker(final String file) {
        writeMarker(file, null);
    }

    private void writeSecondMarker(final String file, final Instant now) {
        writeMarker(file, now);
    }

    private void writeMarker(final String file, @Nullable final Instant now) {
        final var path = Paths.get(dynamicProperties.upgradeArtifactsLoc(), file);
        try {
            final var artifactsDirPath = Paths.get(dynamicProperties.upgradeArtifactsLoc());
            if (!artifactsDirPath.toFile().exists()) {
                Files.createDirectories(artifactsDirPath);
            }
            final var contents = (now == null) ? MARK : ("" + now.getEpochSecond());
            fileStringWriter.writeString(path, contents);
            log.info("Wrote marker {}", path);
        } catch (IOException e) {
            log.error("Failed to write NMT marker {}", path, e);
            log.error(MANUAL_REMEDIATION_ALERT);
        }
    }

    @FunctionalInterface
    interface FileStringWriter {
        Path writeString(Path path, CharSequence csq, OpenOption... options) throws IOException;
    }

    /* --- Only used by unit tests --- */
    void setFileStringWriter(FileStringWriter fileStringWriter) {
        this.fileStringWriter = fileStringWriter;
    }
}
