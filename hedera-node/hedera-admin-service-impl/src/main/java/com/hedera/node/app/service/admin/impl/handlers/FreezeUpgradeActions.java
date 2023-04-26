/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.admin.impl.handlers;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.admin.impl.WritableSpecialFileStore;
import com.hedera.node.app.service.admin.impl.config.AdminServiceConfig;
import com.swirlds.common.system.SwirldDualState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class FreezeUpgradeActions {
    private static final Logger log = LogManager.getLogger(FreezeUpgradeActions.class);

    private static final String PREPARE_UPGRADE_DESC = "software";
    private static final String TELEMETRY_UPGRADE_DESC = "telemetry";
    private static final String MANUAL_REMEDIATION_ALERT = "Manual remediation may be necessary to avoid node ISS";

    public static final String NOW_FROZEN_MARKER = "now_frozen.mf";
    public static final String EXEC_IMMEDIATE_MARKER = "execute_immediate.mf";
    public static final String EXEC_TELEMETRY_MARKER = "execute_telemetry.mf";
    public static final String FREEZE_SCHEDULED_MARKER = "freeze_scheduled.mf";
    public static final String FREEZE_ABORTED_MARKER = "freeze_aborted.mf";

    public static final String MARK = "✓";

    private FileStringWriter fileStringWriter = Files::writeString;

    private final AdminServiceConfig adminServiceConfig;
    private final SwirldDualState dualState;
    private final WritableSpecialFileStore specialFiles;

    @Inject
    public FreezeUpgradeActions(
            @NonNull final AdminServiceConfig adminServiceConfig,
            final SwirldDualState dualState,
            @NonNull final WritableSpecialFileStore specialFiles) {
        requireNonNull(adminServiceConfig);
        requireNonNull(specialFiles);

        this.adminServiceConfig = adminServiceConfig;
        this.dualState = dualState;
        this.specialFiles = specialFiles;
        // TODO: need to also pass in network context
        // TODO: requireNonNull(dualState);
        //  for the time being, dualState will always be null because we are not using it
    }

    public void externalizeFreezeIfUpgradePending() {
        // TODO: check whether networkCtx.hasPreparedUpgrade() once networkContext is available
        final var isUpgradePrepared = true; // TODO: isUpgradePrepared = networkCtx.hasPreparedUpgrade();

        if (isUpgradePrepared) {
            writeCheckMarker(NOW_FROZEN_MARKER);
        }
    }

    public CompletableFuture<Void> extractTelemetryUpgrade(
            @NonNull final byte[] archiveData, @NonNull final Instant now) {
        return extractNow(archiveData, TELEMETRY_UPGRADE_DESC, EXEC_TELEMETRY_MARKER, now);
    }

    public CompletableFuture<Void> extractSoftwareUpgrade(@NonNull final byte[] archiveData) {
        return extractNow(archiveData, PREPARE_UPGRADE_DESC, EXEC_IMMEDIATE_MARKER, null);
    }

    public void scheduleFreezeOnlyAt(@NonNull final Instant freezeTime) {
        requireNonNull(freezeTime);
        withNonNullDualState("schedule freeze", ds -> ds.setFreezeTime(freezeTime));
    }

    public void scheduleFreezeUpgradeAt(@NonNull final Instant freezeTime) {
        requireNonNull(freezeTime);
        withNonNullDualState("schedule freeze", ds -> {
            ds.setFreezeTime(freezeTime);
            writeSecondMarker(FREEZE_SCHEDULED_MARKER, freezeTime);
        });
    }

    public void abortScheduledFreeze() {
        withNonNullDualState("abort freeze", ds -> {
            ds.setFreezeTime(null);
            writeCheckMarker(FREEZE_ABORTED_MARKER);
        });
    }

    public boolean isFreezeScheduled() {
        final var ans = new AtomicBoolean();
        withNonNullDualState("check freeze schedule", ds -> {
            final var freezeTime = ds.getFreezeTime();
            ans.set(freezeTime != null && !freezeTime.equals(ds.getLastFrozenTime()));
        });
        return ans.get();
    }

    public void catchUpOnMissedSideEffects() {
        catchUpOnMissedFreezeScheduling();
        // TODO: fix and then reenable this once networkContext is available
        //  catchUpOnMissedUpgradePrep();
    }

    /* --- Internal methods --- */

    private CompletableFuture<Void> extractNow(
            @NonNull final byte[] archiveData,
            @NonNull final String desc,
            @NonNull final String marker,
            @Nullable final Instant now) {
        requireNonNull(archiveData);
        requireNonNull(desc);
        requireNonNull(marker);

        final var size = archiveData.length;
        final var artifactsLoc = adminServiceConfig.upgradeArtifactsPath();
        requireNonNull(artifactsLoc);
        log.info("About to unzip {} bytes for {} update into {}", size, desc, artifactsLoc);
        // we spin off a separate thread to avoid blocking handleTransaction
        // if we block handle, there could be a dramatic spike in E2E latency at the time of PREPARE_UPGRADE
        return runAsync(() -> {
            try {
                FileUtils.cleanDirectory(new File(artifactsLoc));
                boolean result = UnzipUtility.unzip(archiveData, artifactsLoc);
                if (!result) throw new IOException("Unzip failed");
                log.info("Finished unzipping {} bytes for {} update into {}", size, desc, artifactsLoc);
                writeSecondMarker(marker, now);
            } catch (final IOException e) {
                // catch and log instead of throwing because upgrade process looks at the presence or absence
                // of marker files to determine whether to proceed with the upgrade
                // if second marker is present, that means the zip file was successfully extracted
                log.error("Failed to unzip archive for NMT consumption", e);
                log.error(MANUAL_REMEDIATION_ALERT);
            }
        });
    }

    private void catchUpOnMissedFreezeScheduling() {
        // TODO: check whether networkCtx.hasPreparedUpgrade() once networkContext is available
        final var isUpgradePrepared = false; // TODO: isUpgradePrepared = networkCtx.hasPreparedUpgrade();

        if (isFreezeScheduled() && isUpgradePrepared) {
            writeMarker(FREEZE_SCHEDULED_MARKER, dualState.getFreezeTime());
        }
        /* If we missed a FREEZE_ABORT, we are at risk of having a problem down the road.
        But writing a "defensive" freeze_aborted.mf is itself too risky, as it will keep
        us from correctly (1) catching up on a missed PREPARE_UPGRADE; or (2) handling an
        imminent PREPARE_UPGRADE. */
    }

    private void withNonNullDualState(
            @NonNull final String actionDesc, @NonNull final Consumer<SwirldDualState> action) {
        requireNonNull(dualState, "Cannot " + actionDesc + " without access to the dual state");
        action.accept(dualState);
    }

    private void writeCheckMarker(@NonNull final String file) {
        writeMarker(file, null);
    }

    private void writeSecondMarker(@NonNull final String file, @NonNull final Instant now) {
        writeMarker(file, now);
    }

    private void writeMarker(final String file, @Nullable final Instant now) {
        requireNonNull(file);
        final var path = Paths.get(adminServiceConfig.upgradeArtifactsPath(), file);
        try {
            final var artifactsDirPath = Paths.get(adminServiceConfig.upgradeArtifactsPath());
            if (!artifactsDirPath.toFile().exists()) {
                Files.createDirectories(artifactsDirPath);
            }
            final var contents = (now == null) ? MARK : ("" + now.getEpochSecond());
            fileStringWriter.writeString(path, contents);
            log.info("Wrote marker {}", path);
        } catch (final IOException e) {
            log.error("Failed to write NMT marker {}", path, e);
            log.error(MANUAL_REMEDIATION_ALERT);
        }
    }

    @FunctionalInterface
    @VisibleForTesting
    public interface FileStringWriter {
        Path writeString(Path path, CharSequence csq, OpenOption... options) throws IOException;
    }

    /* --- Only used by unit tests --- */
    @VisibleForTesting
    public void setFileStringWriter(final FileStringWriter fileStringWriter) {
        this.fileStringWriter = fileStringWriter;
    }
}
