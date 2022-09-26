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

import static com.hedera.services.txns.network.UpgradeActions.EXEC_IMMEDIATE_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.EXEC_TELEMETRY_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.FREEZE_ABORTED_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.FREEZE_SCHEDULED_MARKER;
import static com.hedera.services.txns.network.UpgradeActions.MARK;
import static com.hedera.services.txns.network.UpgradeActions.NOW_FROZEN_MARKER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.swirlds.platform.state.DualStateImpl;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class UpgradeActionsTest {
    private static final Instant then = Instant.ofEpochSecond(1_234_567L, 890);
    private static final String markerFilesLoc = "src/test/resources/upgrade";
    private static final String noiseDirLoc = markerFilesLoc + "/outdated";
    private static final String noiseFileLoc = markerFilesLoc + "/old-config.txt";
    private static final String noiseSubFileLoc = noiseDirLoc + "/forgotten.cfg";
    private static final String otherMarkerFilesLoc = "src/test/resources/upgrade/edargpu";
    private static final byte[] PRETEND_ARCHIVE =
            "This is missing something. Hard to put a finger on what..."
                    .getBytes(StandardCharsets.UTF_8);

    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private DualStateImpl dualState;
    @Mock private UpgradeActions.UnzipAction unzipAction;
    @Mock private MerkleSpecialFiles specialFiles;
    @Mock private MerkleNetworkContext networkCtx;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private UpgradeActions subject;

    @BeforeEach
    void setUp() {
        subject =
                new UpgradeActions(
                        unzipAction,
                        dynamicProperties,
                        () -> dualState,
                        () -> specialFiles,
                        () -> networkCtx);
    }

    @AfterAll
    static void cleanup() throws IOException {
        FileUtils.deleteDirectory(new File(markerFilesLoc));
    }

    @Test
    void complainsLoudlyIfUpgradeHashDoesntMatch() {
        rmIfPresent(EXEC_IMMEDIATE_MARKER);

        given(networkCtx.hasPreparedUpgrade()).willReturn(true);
        given(networkCtx.getPreparedUpdateFileNum()).willReturn(150L);

        subject.catchUpOnMissedSideEffects();

        assertThat(
                logCaptor.errorLogs(),
                contains(
                        Matchers.startsWith(
                                "Cannot redo NMT upgrade prep, file 0.0.150 changed since"
                                        + " FREEZE_UPGRADE"),
                        Matchers.equalTo("Manual remediation may be necessary to avoid node ISS")));
        assertFalse(
                Paths.get(markerFilesLoc, EXEC_IMMEDIATE_MARKER).toFile().exists(),
                "Should not create "
                        + EXEC_IMMEDIATE_MARKER
                        + " if prepared file hash doesn't match");
    }

    @Test
    void catchesUpOnUpgradePreparationIfInContext() throws IOException {
        rmIfPresent(EXEC_IMMEDIATE_MARKER);

        given(networkCtx.hasPreparedUpgrade()).willReturn(true);
        given(networkCtx.isPreparedFileHashValidGiven(specialFiles)).willReturn(true);
        given(networkCtx.getPreparedUpdateFileNum()).willReturn(150L);
        given(specialFiles.get(IdUtils.asFile("0.0.150"))).willReturn(PRETEND_ARCHIVE);
        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);
        given(dualState.getFreezeTime()).willReturn(then);
        given(dualState.getLastFrozenTime()).willReturn(then);

        subject.catchUpOnMissedSideEffects();

        verify(unzipAction).unzip(PRETEND_ARCHIVE, markerFilesLoc);
        assertMarkerCreated(EXEC_IMMEDIATE_MARKER, null);
    }

    @Test
    void catchUpIsNoopWithNothingToDo() {
        rmIfPresent(FREEZE_SCHEDULED_MARKER);
        rmIfPresent(EXEC_IMMEDIATE_MARKER);

        subject.catchUpOnMissedSideEffects();

        assertFalse(
                Paths.get(markerFilesLoc, EXEC_IMMEDIATE_MARKER).toFile().exists(),
                "Should not create " + EXEC_IMMEDIATE_MARKER + " if no prepared upgrade in state");
        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_SCHEDULED_MARKER).toFile().exists(),
                "Should not create " + FREEZE_SCHEDULED_MARKER + " if dual freeze time is null");
    }

    @Test
    void doesntCatchUpOnFreezeScheduleIfInDualAndNoUpgradeIsPrepared() {
        rmIfPresent(FREEZE_SCHEDULED_MARKER);

        given(dualState.getFreezeTime()).willReturn(then);

        subject.catchUpOnMissedSideEffects();

        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_SCHEDULED_MARKER).toFile().exists(),
                "Should not create " + FREEZE_SCHEDULED_MARKER + " if no upgrade is prepared");
    }

    @Test
    void catchesUpOnFreezeScheduleIfInDualAndUpgradeIsPrepared() throws IOException {
        rmIfPresent(FREEZE_SCHEDULED_MARKER);

        given(dualState.getFreezeTime()).willReturn(then);
        given(networkCtx.hasPreparedUpgrade()).willReturn(true);
        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

        subject.catchUpOnMissedSideEffects();

        assertMarkerCreated(FREEZE_SCHEDULED_MARKER, then);
    }

    @Test
    void freezeCatchUpWritesNoMarkersIfJustUnfrozen() {
        rmIfPresent(FREEZE_ABORTED_MARKER);
        rmIfPresent(FREEZE_SCHEDULED_MARKER);

        given(dualState.getFreezeTime()).willReturn(then);
        given(dualState.getLastFrozenTime()).willReturn(then);

        subject.catchUpOnMissedSideEffects();

        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_ABORTED_MARKER).toFile().exists(),
                "Should not create "
                        + FREEZE_ABORTED_MARKER
                        + " if dual last frozen time is freeze time");
        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_SCHEDULED_MARKER).toFile().exists(),
                "Should not create "
                        + FREEZE_SCHEDULED_MARKER
                        + " if dual last frozen time is freeze time");
    }

    @Test
    void doesntCatchUpOnFreezeAbortIfUpgradeIsPrepared() {
        rmIfPresent(FREEZE_ABORTED_MARKER);

        given(networkCtx.hasPreparedUpgrade()).willReturn(true);

        subject.catchUpOnMissedSideEffects();

        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_ABORTED_MARKER).toFile().exists(),
                "Should not create defensive " + FREEZE_ABORTED_MARKER + " if upgrade is prepared");
    }

    @Test
    void complainsLoudlyWhenUnableToUnzipArchive() throws IOException {
        new File(markerFilesLoc).mkdirs();
        rmIfPresent(EXEC_IMMEDIATE_MARKER);

        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);
        willThrow(IOException.class).given(unzipAction).unzip(PRETEND_ARCHIVE, markerFilesLoc);

        subject.extractSoftwareUpgrade(PRETEND_ARCHIVE).join();

        assertThat(
                logCaptor.errorLogs(),
                contains(
                        Matchers.startsWith(
                                "Failed to unzip archive for NMT consumption java.io.IOException:"
                                        + " "),
                        Matchers.equalTo("Manual remediation may be necessary to avoid node ISS")));
        assertFalse(
                Paths.get(markerFilesLoc, EXEC_IMMEDIATE_MARKER).toFile().exists(),
                "Should not create " + EXEC_IMMEDIATE_MARKER + " if unzip failed");
    }

    @Test
    void preparesForUpgrade() throws IOException {
        setupNoiseFiles();
        rmIfPresent(EXEC_IMMEDIATE_MARKER);

        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

        subject.extractSoftwareUpgrade(PRETEND_ARCHIVE).join();

        verify(unzipAction).unzip(PRETEND_ARCHIVE, markerFilesLoc);
        assertMarkerCreated(EXEC_IMMEDIATE_MARKER, null);
        assertNoiseFilesAreGone();
    }

    @Test
    void upgradesTelemetry() throws IOException {
        rmIfPresent(EXEC_TELEMETRY_MARKER);

        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

        subject.extractTelemetryUpgrade(PRETEND_ARCHIVE, then).join();

        verify(unzipAction).unzip(PRETEND_ARCHIVE, markerFilesLoc);
        assertMarkerCreated(EXEC_TELEMETRY_MARKER, then);
    }

    @Test
    void externalizesFreeze() throws IOException {
        rmIfPresent(NOW_FROZEN_MARKER);

        given(networkCtx.hasPreparedUpgrade()).willReturn(true);
        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

        subject.externalizeFreezeIfUpgradePending();

        assertMarkerCreated(NOW_FROZEN_MARKER, null);
    }

    @Test
    void doesntExternalizeFreezeIfNoUpgradeIsPrepared() {
        rmIfPresent(NOW_FROZEN_MARKER);

        subject.externalizeFreezeIfUpgradePending();

        assertFalse(
                Paths.get(markerFilesLoc, NOW_FROZEN_MARKER).toFile().exists(),
                "Should not create " + NOW_FROZEN_MARKER + " if no upgrade is prepared");
    }

    @Test
    void setsExpectedFreezeAndWritesMarkerForFreezeUpgrade() throws IOException {
        rmIfPresent(FREEZE_SCHEDULED_MARKER);

        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

        subject.scheduleFreezeUpgradeAt(then);

        verify(dualState).setFreezeTime(then);

        assertMarkerCreated(FREEZE_SCHEDULED_MARKER, then);
    }

    @Test
    void setsExpectedFreezeOnlyForFreezeOnly() {
        rmIfPresent(FREEZE_SCHEDULED_MARKER);

        subject.scheduleFreezeOnlyAt(then);

        verify(dualState).setFreezeTime(then);

        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_SCHEDULED_MARKER).toFile().exists(),
                "Should not create " + FREEZE_SCHEDULED_MARKER + " for FREEZE_ONLY");
    }

    @Test
    void nullsOutDualOnAborting() throws IOException {
        rmIfPresent(FREEZE_ABORTED_MARKER);

        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);

        subject.abortScheduledFreeze();

        verify(dualState).setFreezeTime(null);

        assertMarkerCreated(FREEZE_ABORTED_MARKER, null);
    }

    @Test
    void canStillWriteMarkersEvenIfDirDoesntExist() throws IOException {
        rmIfPresent(otherMarkerFilesLoc, FREEZE_ABORTED_MARKER);
        final var d = Paths.get(otherMarkerFilesLoc).toFile();
        if (d.exists()) {
            d.delete();
        }

        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(otherMarkerFilesLoc);

        subject.abortScheduledFreeze();

        verify(dualState).setFreezeTime(null);

        assertMarkerCreated(FREEZE_ABORTED_MARKER, null, otherMarkerFilesLoc);

        rmIfPresent(otherMarkerFilesLoc, FREEZE_ABORTED_MARKER);
        if (d.exists()) {
            d.delete();
        }
    }

    @Test
    void complainsLoudlyWhenUnableToWriteMarker() throws IOException {
        final var p = Paths.get(markerFilesLoc, FREEZE_ABORTED_MARKER);
        final var throwingWriter = mock(UpgradeActions.FileStringWriter.class);

        given(dynamicProperties.upgradeArtifactsLoc()).willReturn(markerFilesLoc);
        given(throwingWriter.writeString(p, MARK)).willThrow(IOException.class);
        subject.setFileStringWriter(throwingWriter);

        subject.abortScheduledFreeze();

        verify(dualState).setFreezeTime(null);

        assertThat(
                logCaptor.errorLogs(),
                contains(
                        Matchers.startsWith("Failed to write NMT marker " + p),
                        Matchers.equalTo("Manual remediation may be necessary to avoid node ISS")));
    }

    @Test
    void determinesIfFreezeIsScheduled() {
        assertFalse(subject.isFreezeScheduled());

        given(dualState.getFreezeTime()).willReturn(then);

        assertTrue(subject.isFreezeScheduled());
    }

    private static void rmIfPresent(String file) {
        rmIfPresent(markerFilesLoc, file);
    }

    private static void rmIfPresent(final String baseDir, final String file) {
        final var p = Paths.get(baseDir, file);
        final var f = p.toFile();
        if (f.exists()) {
            f.delete();
        }
    }

    private void assertMarkerCreated(final String file, final @Nullable Instant when)
            throws IOException {
        assertMarkerCreated(file, when, markerFilesLoc);
    }

    private void assertMarkerCreated(
            final String file, final @Nullable Instant when, final String baseDir)
            throws IOException {
        final var p = Paths.get(baseDir, file);
        final var f = p.toFile();
        assertTrue(f.exists(), file + " should have been created, but wasn't");
        final var contents = Files.readString(p);
        f.delete();
        if (file.equals(EXEC_IMMEDIATE_MARKER)) {
            assertThat(
                    logCaptor.infoLogs(),
                    contains(
                            Matchers.equalTo(
                                    "About to unzip 58 bytes for software update into " + baseDir),
                            Matchers.equalTo(
                                    "Finished unzipping 58 bytes for software update into "
                                            + baseDir),
                            Matchers.equalTo("Wrote marker " + p)));
        } else if (file.equals(EXEC_TELEMETRY_MARKER)) {
            assertThat(
                    logCaptor.infoLogs(),
                    contains(
                            Matchers.equalTo(
                                    "About to unzip 58 bytes for telemetry update into " + baseDir),
                            Matchers.equalTo(
                                    "Finished unzipping 58 bytes for telemetry update into "
                                            + baseDir),
                            Matchers.equalTo("Wrote marker " + p)));
        } else {
            assertThat(logCaptor.infoLogs(), contains(Matchers.equalTo("Wrote marker " + p)));
        }
        if (when != null) {
            final var writtenEpochSecond = Long.parseLong(contents);
            assertEquals(when.getEpochSecond(), writtenEpochSecond);
        } else {
            assertEquals(UpgradeActions.MARK, contents);
        }
    }

    private void setupNoiseFiles() throws IOException {
        final var noiseDir = new File(noiseDirLoc);
        noiseDir.mkdirs();
        Files.write(
                Paths.get(noiseFileLoc),
                List.of(
                        "There, the eyes are",
                        "Sunlight on a broken column",
                        "There, is a tree swinging"));
        Files.write(
                Paths.get(noiseSubFileLoc),
                List.of(
                        "And voices are",
                        "In the wind's singing",
                        "More distant and more solemn",
                        "Than a fading star"));
    }

    private void assertNoiseFilesAreGone() {
        assertFalse(new File(noiseDirLoc).exists());
        assertFalse(new File(noiseFileLoc).exists());
        assertFalse(new File(noiseSubFileLoc).exists());
    }
}
