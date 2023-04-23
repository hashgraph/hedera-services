/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.admin.impl.test.handlers;

import static com.hedera.node.app.service.admin.impl.handlers.FreezeUpgradeActions.EXEC_IMMEDIATE_MARKER;
import static com.hedera.node.app.service.admin.impl.handlers.FreezeUpgradeActions.EXEC_TELEMETRY_MARKER;
import static com.hedera.node.app.service.admin.impl.handlers.FreezeUpgradeActions.FREEZE_ABORTED_MARKER;
import static com.hedera.node.app.service.admin.impl.handlers.FreezeUpgradeActions.FREEZE_SCHEDULED_MARKER;
import static com.hedera.node.app.service.admin.impl.handlers.FreezeUpgradeActions.MARK;
import static com.hedera.node.app.service.admin.impl.handlers.FreezeUpgradeActions.NOW_FROZEN_MARKER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.admin.impl.WritableSpecialFileStore;
import com.hedera.node.app.service.admin.impl.config.AdminServiceConfig;
import com.hedera.node.app.service.admin.impl.handlers.FreezeUpgradeActions;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.fixtures.util.TestFileUtils;
import com.swirlds.platform.state.DualStateImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class FreezeUpgradeActionsTest {
    private static final Instant then = Instant.ofEpochSecond(1_234_567L, 890);
    private String markerFilesLoc;
    private String noiseDirLoc;
    private String noiseFileLoc;
    private String noiseSubFileLoc;
    private static final byte[] PRETEND_ARCHIVE =
            "This is missing something. Hard to put a finger on what...".getBytes(StandardCharsets.UTF_8);

    private static final String REAL_ARCHIVE_LOC = "src/test/resources/testfiles/updateFeature/update.zip";

    @TempDir
    private File tempDir;

    @Mock
    private DualStateImpl dualState;

    @Mock
    private WritableSpecialFileStore specialFiles;

    @Mock
    private AdminServiceConfig adminServiceConfig;

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private FreezeUpgradeActions subject;

    @BeforeEach
    void setUp() {
        markerFilesLoc = TestFileUtils.toPath(tempDir, "outdated");
        noiseDirLoc = TestFileUtils.toPath(markerFilesLoc, "old-config.txt");
        noiseFileLoc = TestFileUtils.toPath(markerFilesLoc, "forgotten.cfg");
        noiseSubFileLoc = TestFileUtils.toPath(markerFilesLoc, "edargpu");

        subject = new FreezeUpgradeActions(adminServiceConfig, dualState, specialFiles);
    }

    @AfterEach
    void tearDown() {
        final File testLoc = new File(markerFilesLoc);
        testLoc.delete();
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
    void freezeCatchUpWritesNoMarkersIfJustUnfrozen() {
        rmIfPresent(FREEZE_ABORTED_MARKER);
        rmIfPresent(FREEZE_SCHEDULED_MARKER);

        given(dualState.getFreezeTime()).willReturn(then);
        given(dualState.getLastFrozenTime()).willReturn(then);

        subject.catchUpOnMissedSideEffects();

        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_ABORTED_MARKER).toFile().exists(),
                "Should not create " + FREEZE_ABORTED_MARKER + " if dual last frozen time is freeze time");
        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_SCHEDULED_MARKER).toFile().exists(),
                "Should not create " + FREEZE_SCHEDULED_MARKER + " if dual last frozen time is freeze time");
    }

    @Test
    void doesntCatchUpOnFreezeAbortIfUpgradeIsPrepared() {
        rmIfPresent(FREEZE_ABORTED_MARKER);

        subject.catchUpOnMissedSideEffects();

        assertFalse(
                Paths.get(markerFilesLoc, FREEZE_ABORTED_MARKER).toFile().exists(),
                "Should not create defensive " + FREEZE_ABORTED_MARKER + " if upgrade is prepared");
    }

    @Test
    void complainsLoudlyWhenUnableToUnzipArchive() throws IOException {
        rmIfPresent(EXEC_IMMEDIATE_MARKER);
        assertTrue(new File(markerFilesLoc).mkdirs());

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        subject.extractSoftwareUpgrade(PRETEND_ARCHIVE).join();

        assertThat(
                logCaptor.errorLogs(),
                contains(
                        Matchers.startsWith("Failed to unzip archive for NMT consumption java.io.IOException:" + " "),
                        Matchers.equalTo("Manual remediation may be necessary to avoid node ISS")));
        assertFalse(
                Paths.get(markerFilesLoc, EXEC_IMMEDIATE_MARKER).toFile().exists(),
                "Should not create " + EXEC_IMMEDIATE_MARKER + " if unzip failed");
    }

    @Test
    void preparesForUpgrade() throws IOException {
        assertTrue(new File(markerFilesLoc).mkdirs());
        setupNoiseFiles();
        rmIfPresent(EXEC_IMMEDIATE_MARKER);

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        byte[] realArchive = Files.readAllBytes(Paths.get(REAL_ARCHIVE_LOC));
        subject.extractSoftwareUpgrade(realArchive).join();

        assertMarkerCreated(EXEC_IMMEDIATE_MARKER, null);
        assertNoiseFilesAreGone();
    }

    @Test
    void upgradesTelemetry() throws IOException {
        rmIfPresent(EXEC_TELEMETRY_MARKER);
        assertTrue(new File(markerFilesLoc).mkdirs());

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        byte[] realArchive = Files.readAllBytes(Paths.get(REAL_ARCHIVE_LOC));
        subject.extractTelemetryUpgrade(realArchive, then).join();

        assertMarkerCreated(EXEC_TELEMETRY_MARKER, then);
    }

    @Test
    void externalizesFreeze() throws IOException {
        rmIfPresent(NOW_FROZEN_MARKER);

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        subject.externalizeFreezeIfUpgradePending();

        assertMarkerCreated(NOW_FROZEN_MARKER, null);
    }

    @Test
    void setsExpectedFreezeAndWritesMarkerForFreezeUpgrade() throws IOException {
        rmIfPresent(FREEZE_SCHEDULED_MARKER);

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

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

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        subject.abortScheduledFreeze();

        verify(dualState).setFreezeTime(null);

        assertMarkerCreated(FREEZE_ABORTED_MARKER, null);
    }

    @Test
    void canStillWriteMarkersEvenIfDirDoesntExist() throws IOException {
        final String otherMarkerFilesLoc = noiseSubFileLoc;
        rmIfPresent(otherMarkerFilesLoc, FREEZE_ABORTED_MARKER);
        final var d = Paths.get(otherMarkerFilesLoc).toFile();
        if (d.exists()) {
            assertTrue(d.delete());
        }

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(otherMarkerFilesLoc);

        subject.abortScheduledFreeze();

        verify(dualState).setFreezeTime(null);

        assertMarkerCreated(FREEZE_ABORTED_MARKER, null, otherMarkerFilesLoc);

        rmIfPresent(otherMarkerFilesLoc, FREEZE_ABORTED_MARKER);
        if (d.exists()) {
            assertTrue(d.delete());
        }
    }

    @Test
    void complainsLoudlyWhenUnableToWriteMarker() throws IOException {
        final var p = Paths.get(markerFilesLoc, FREEZE_ABORTED_MARKER);
        final var throwingWriter = mock(FreezeUpgradeActions.FileStringWriter.class);

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);
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

    private void rmIfPresent(final String file) {
        rmIfPresent(markerFilesLoc, file);
    }

    private static void rmIfPresent(final String baseDir, final String file) {
        final var p = Paths.get(baseDir, file);
        final var f = p.toFile();
        if (f.exists()) {
            assertTrue(f.delete());
        }
    }

    private void assertMarkerCreated(final String file, final @NonNull Instant when) throws IOException {
        assertMarkerCreated(file, when, markerFilesLoc);
    }

    private void assertMarkerCreated(final String file, final @NonNull Instant when, final String baseDir)
            throws IOException {
        final var p = Paths.get(baseDir, file);
        final var f = p.toFile();
        assertTrue(f.exists(), file + " should have been created, but wasn't");
        final var contents = Files.readString(p);
        assertTrue(f.delete());
        if (file.equals(EXEC_IMMEDIATE_MARKER)) {
            assertThat(
                    logCaptor.infoLogs(),
                    contains(
                            Matchers.stringContainsInOrder(
                                    Arrays.asList("About to unzip ", " bytes for software update into " + baseDir)),
                            Matchers.stringContainsInOrder(
                                    Arrays.asList("Finished unzipping ", " bytes for software update into " + baseDir)),
                            Matchers.equalTo("Wrote marker " + p)));
        } else if (file.equals(EXEC_TELEMETRY_MARKER)) {
            assertThat(
                    logCaptor.infoLogs(),
                    contains(
                            Matchers.stringContainsInOrder(
                                    "About to unzip ", " bytes for telemetry update into " + baseDir),
                            Matchers.stringContainsInOrder(
                                    "Finished unzipping ", " bytes for telemetry update into " + baseDir),
                            Matchers.equalTo("Wrote marker " + p)));
        } else {
            assertThat(logCaptor.infoLogs(), contains(Matchers.equalTo("Wrote marker " + p)));
        }
        if (when != null) {
            final var writtenEpochSecond = Long.parseLong(contents);
            assertEquals(when.getEpochSecond(), writtenEpochSecond);
        } else {
            assertEquals(FreezeUpgradeActions.MARK, contents);
        }
    }

    private void setupNoiseFiles() throws IOException {
        Files.write(
                Paths.get(noiseFileLoc),
                List.of("There, the eyes are", "Sunlight on a broken column", "There, is a tree swinging"));
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
