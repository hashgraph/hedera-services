/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.service.networkadmin.impl.WritableFreezeStore;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private final NetworkAdminConfig adminServiceConfig;
    private final WritableFreezeStore freezeStore;

    private final Executor executor;

    public FreezeUpgradeActions(
            @NonNull final NetworkAdminConfig adminServiceConfig,
            @NonNull final WritableFreezeStore freezeStore,
            @NonNull final Executor executor) {
        requireNonNull(adminServiceConfig);
        requireNonNull(freezeStore);
        requireNonNull(executor);

        this.adminServiceConfig = adminServiceConfig;
        this.freezeStore = freezeStore;
        this.executor = executor;
    }

    public void externalizeFreezeIfUpgradePending() {
        // @todo('Issue #8660') this code is not currently triggered anywhere
        if (freezeStore.updateFileHash() != null) {
            writeCheckMarker(NOW_FROZEN_MARKER);
        }
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

    public void scheduleFreezeOnlyAt(@NonNull final Timestamp freezeTime) {
        requireNonNull(freezeTime);
        requireNonNull(freezeStore, "Cannot schedule freeze without access to the dual state");
        freezeStore.freezeTime(freezeTime);
    }

    public void scheduleFreezeUpgradeAt(@NonNull final Timestamp freezeTime) {
        requireNonNull(freezeTime);
        requireNonNull(freezeStore, "Cannot schedule freeze without access to the dual state");
        freezeStore.freezeTime(freezeTime);
        writeSecondMarker(FREEZE_SCHEDULED_MARKER, freezeTime);
    }

    public void abortScheduledFreeze() {
        requireNonNull(freezeStore, "Cannot abort freeze without access to the dual state");
        freezeStore.freezeTime(null);
        writeCheckMarker(FREEZE_ABORTED_MARKER);
    }

    public boolean isFreezeScheduled() {
        final var ans = new AtomicBoolean();
        requireNonNull(freezeStore, "Cannot check freeze schedule without access to the dual state");
        final var freezeTime = freezeStore.freezeTime();
        ans.set(freezeTime != null && !freezeTime.equals(freezeStore.lastFrozenTime()));
        return ans.get();
    }

    /* --- Internal methods --- */

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
            try (Stream<Path> paths = Files.walk(Paths.get(artifactsLoc))) {
                // delete any existing files in the artifacts directory
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } catch (final IOException e) {
            // above is a best-effort delete
            // if it fails, we log the error and continue
            log.error("Failed to delete existing files in {}", artifactsLoc, e);
        }
        try {
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

    private void writeCheckMarker(@NonNull final String file) {
        requireNonNull(file);
        writeMarker(file, null);
    }

    private void writeSecondMarker(@NonNull final String file, @Nullable final Timestamp now) {
        requireNonNull(file);
        writeMarker(file, now);
    }

    private void writeMarker(@NonNull final String file, @Nullable final Timestamp now) {
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
}
