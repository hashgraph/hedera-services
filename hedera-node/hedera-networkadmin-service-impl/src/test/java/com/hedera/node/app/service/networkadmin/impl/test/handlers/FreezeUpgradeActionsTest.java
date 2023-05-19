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

package com.hedera.node.app.service.networkadmin.impl.test.handlers;

import static com.hedera.node.app.service.networkadmin.impl.handlers.FreezeUpgradeActions.EXEC_IMMEDIATE_MARKER;
import static com.hedera.node.app.service.networkadmin.impl.handlers.FreezeUpgradeActions.EXEC_TELEMETRY_MARKER;
import static com.hedera.node.app.service.networkadmin.impl.handlers.FreezeUpgradeActions.FREEZE_ABORTED_MARKER;
import static com.hedera.node.app.service.networkadmin.impl.handlers.FreezeUpgradeActions.FREEZE_SCHEDULED_MARKER;
import static com.hedera.node.app.service.networkadmin.impl.handlers.FreezeUpgradeActions.NOW_FROZEN_MARKER;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.networkadmin.impl.config.NetworkAdminServiceConfig;
import com.hedera.node.app.service.networkadmin.impl.handlers.FreezeUpgradeActions;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.state.WritableFreezeStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
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
    private static final byte[] PRETEND_ARCHIVE = "Not a valid zip archive".getBytes(StandardCharsets.UTF_8);

    private static final String REAL_ARCHIVE_LOC = "src/test/resources/testfiles/updateFeature/valid.zip";

    @TempDir
    private File tempDir;

    @Mock
    private WritableFreezeStore freezeStore;

    @Mock
    private NetworkAdminServiceConfig adminServiceConfig;

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private FreezeUpgradeActions subject;

    @BeforeEach
    void setUp() {
        markerFilesLoc = toPath(tempDir, "outdated");
        noiseDirLoc = toPath(markerFilesLoc, "old-config.txt");
        noiseFileLoc = toPath(markerFilesLoc, "forgotten.cfg");
        noiseSubFileLoc = toPath(markerFilesLoc, "edargpu");

        subject = new FreezeUpgradeActions(adminServiceConfig, freezeStore);
    }

    /** Convenience method for adding the OS file separator to a path */
    private static String toPath(@NonNull final File dir, @NonNull final String filename) {
        requireNonNull(dir);
        requireNonNull(filename);
        final var withSeparator = filename.startsWith(File.separator) ? filename : File.separator + filename;
        return dir.getPath() + withSeparator;
    }

    private static String toPath(@NonNull final String dir, @NonNull final String filename) {
        requireNonNull(dir);
        requireNonNull(filename);
        final var withSeparator = filename.startsWith(File.separator) ? filename : File.separator + filename;
        return new File(dir).getPath() + withSeparator;
    }

    @AfterEach
    void tearDown() {
        final File testLoc = new File(markerFilesLoc);
        testLoc.delete();
    }

    @Test
    void complainsLoudlyWhenUnableToUnzipArchive() {
        rmIfPresent(EXEC_IMMEDIATE_MARKER);
        assertThat(new File(markerFilesLoc).mkdirs()).isTrue();

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        subject.extractSoftwareUpgrade(PRETEND_ARCHIVE).join();

        assertThat(logCaptor.errorLogs())
                .anyMatch(l -> l.startsWith("Failed to unzip archive for NMT consumption java.io.IOException:" + " "));
        assertThat(logCaptor.errorLogs())
                .anyMatch(l -> l.equals("Manual remediation may be necessary to avoid node ISS"));

        assertThat(Paths.get(markerFilesLoc, EXEC_IMMEDIATE_MARKER).toFile()).doesNotExist();
    }

    @Test
    void preparesForUpgrade() throws IOException {
        assertThat(new File(markerFilesLoc).mkdirs()).isTrue();
        setupNoiseFiles();
        rmIfPresent(EXEC_IMMEDIATE_MARKER);

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        final byte[] realArchive = Files.readAllBytes(Paths.get(REAL_ARCHIVE_LOC));
        subject.extractSoftwareUpgrade(realArchive).join();

        assertMarkerCreated(EXEC_IMMEDIATE_MARKER, null);
        assertNoiseFilesAreGone();
    }

    @Test
    void upgradesTelemetry() throws IOException {
        rmIfPresent(EXEC_TELEMETRY_MARKER);
        assertThat(new File(markerFilesLoc).mkdirs()).isTrue();

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        final byte[] realArchive = Files.readAllBytes(Paths.get(REAL_ARCHIVE_LOC));
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

        verify(freezeStore).freezeTime(then);

        assertMarkerCreated(FREEZE_SCHEDULED_MARKER, then);
    }

    @Test
    void setsExpectedFreezeOnlyForFreezeOnly() {
        rmIfPresent(FREEZE_SCHEDULED_MARKER);

        subject.scheduleFreezeOnlyAt(then);

        verify(freezeStore).freezeTime(then);

        assertThat(Paths.get(markerFilesLoc, FREEZE_SCHEDULED_MARKER).toFile()).doesNotExist();
    }

    @Test
    void nullsOutDualOnAborting() throws IOException {
        rmIfPresent(FREEZE_ABORTED_MARKER);

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(markerFilesLoc);

        subject.abortScheduledFreeze();

        verify(freezeStore).freezeTime(null);

        assertMarkerCreated(FREEZE_ABORTED_MARKER, null);
    }

    @Test
    void canStillWriteMarkersEvenIfDirDoesntExist() throws IOException {
        final String otherMarkerFilesLoc = noiseSubFileLoc;
        rmIfPresent(otherMarkerFilesLoc, FREEZE_ABORTED_MARKER);
        final var d = Paths.get(otherMarkerFilesLoc).toFile();
        if (d.exists()) {
            assertThat(d.delete()).isTrue();
        }

        given(adminServiceConfig.upgradeArtifactsPath()).willReturn(otherMarkerFilesLoc);

        subject.abortScheduledFreeze();

        verify(freezeStore).freezeTime(null);

        assertMarkerCreated(FREEZE_ABORTED_MARKER, null, otherMarkerFilesLoc);

        rmIfPresent(otherMarkerFilesLoc, FREEZE_ABORTED_MARKER);
        if (d.exists()) {
            assertThat(d.delete()).isTrue();
        }
    }

    @Test
    void determinesIfFreezeIsScheduled() {
        assertThat(subject.isFreezeScheduled()).isFalse();

        given(freezeStore.freezeTime()).willReturn(then);

        assertThat(subject.isFreezeScheduled()).isTrue();
    }

    private void rmIfPresent(final String file) {
        rmIfPresent(markerFilesLoc, file);
    }

    private static void rmIfPresent(final String baseDir, final String file) {
        final var p = Paths.get(baseDir, file);
        final var f = p.toFile();
        if (f.exists()) {
            assertThat(f.delete()).isTrue();
        }
    }

    private void assertMarkerCreated(final String file, final @Nullable Instant when) throws IOException {
        assertMarkerCreated(file, when, markerFilesLoc);
    }

    private void assertMarkerCreated(final String file, final @Nullable Instant when, final String baseDir)
            throws IOException {
        final var p = Paths.get(baseDir, file);
        final var f = p.toFile();
        assertThat(f).exists();
        final var contents = Files.readString(p);
        assertThat(f.delete()).isTrue();

        if (file.equals(EXEC_IMMEDIATE_MARKER)) {
            assertThat(logCaptor.infoLogs())
                    .anyMatch(l -> (l.startsWith("About to unzip ")
                            && l.contains(" bytes for software update into " + baseDir)));
            assertThat(logCaptor.infoLogs())
                    .anyMatch(l -> (l.startsWith("Finished unzipping ")
                            && l.contains(" bytes for software update into " + baseDir)));
            assertThat(logCaptor.infoLogs()).anyMatch(l -> (l.contains("Wrote marker " + p)));
        } else if (file.equals(EXEC_TELEMETRY_MARKER)) {
            assertThat(logCaptor.infoLogs())
                    .anyMatch(l -> (l.startsWith("About to unzip ")
                            && l.contains(" bytes for telemetry update into " + baseDir)));
            assertThat(logCaptor.infoLogs())
                    .anyMatch(l ->
                            (l.startsWith("Finished unzipping ") && l.contains(" bytes for telemetry update into ")));
            assertThat(logCaptor.infoLogs()).anyMatch(l -> (l.contains("Wrote marker " + p)));
        } else {
            assertThat(logCaptor.infoLogs()).anyMatch(l -> (l.contains("Wrote marker " + p)));
        }
        if (when != null) {
            final var writtenEpochSecond = Long.parseLong(contents);
            assertThat(when.getEpochSecond()).isEqualTo(writtenEpochSecond);
        } else {
            assertThat(contents).isEqualTo(FreezeUpgradeActions.MARK);
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
        assertThat(new File(noiseDirLoc)).doesNotExist();
        assertThat(new File(noiseFileLoc)).doesNotExist();
        assertThat(new File(noiseSubFileLoc)).doesNotExist();
    }
}
