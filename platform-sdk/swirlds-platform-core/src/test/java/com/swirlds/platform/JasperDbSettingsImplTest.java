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

package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class JasperDbSettingsImplTest {
    @Test
    void defaultsAreAsExpected() throws IOException {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        assertEquals(3 * 1024, subject.getSmallMergeCutoffMb(), "Default small merge cutoff should be 3GB");
        assertEquals(10 * 1024, subject.getMediumMergeCutoffMb(), "Default medium merge cutoff should be 10GB");

        assertEquals(ChronoUnit.MINUTES, subject.getMergePeriodUnit(), "Default merge unit should be MINUTES");
        assertEquals(1440L, subject.getFullMergePeriod(), "Default full merge period should be 1440 (MINUTES) 24h");
        assertEquals(60L, subject.getMediumMergePeriod(), "Default medium merge period should be 60 (MINUTES) 1h");
        assertEquals(1L, subject.getMergeActivatePeriod(), "Default merge activation period should be 1 (SECONDS)");
        assertEquals(8L, subject.getMinNumberOfFilesInMerge(), "Default min number of files to merge should be 8");
        assertEquals(64L, subject.getMaxNumberOfFilesInMerge(), "Default max number of files to merge should be 64");

        assertEquals(
                64L * 1024 * 1024 * 1024, subject.getMaxDataFileBytes(), "Default max data file size should be 64GB");
        assertEquals(500_000, subject.getMoveListChunkSize(), "Default move list chunk size should be half a million");
        assertEquals(10, subject.getMaxRamUsedForMergingGb(), "Default max RAM used for merging should be 10GB");
    }

    @Test
    void canSetMergeCutoffs() {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        final int smallCutoff = 30 * 1024;
        final int mediumCutoff = 100 * 1024;

        subject.setSmallMergeCutoffMb(smallCutoff);
        subject.setMediumMergeCutoffMb(mediumCutoff);

        assertEquals(smallCutoff, subject.getSmallMergeCutoffMb(), "Small merge cutoff should be configurable");
        assertEquals(mediumCutoff, subject.getMediumMergeCutoffMb(), "Medium merge cutoff should be configurable");
    }

    @Test
    void canSetMergeUnits() {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMergePeriodUnit("NOT_A_TIME_UNIT"),
                "Should require merge unit to be a valid ChronoUnit");

        subject.setMergePeriodUnit("HOURS");

        assertEquals(ChronoUnit.HOURS, subject.getMergePeriodUnit(), "Should be able to override merge period units");
    }

    @Test
    void canSetPeriods() {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setFullMergePeriod(-1),
                "Should throw on negative merge periods");
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMediumMergePeriod(-1),
                "Should throw on negative merge periods");
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMergeActivatedPeriod(-1),
                "Should throw on negative merge periods");
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMaxNumberOfFilesInMerge(-1),
                "Should throw on negative max number of files in merge");
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMinNumberOfFilesInMerge(-1),
                "Should throw on negative min number of files in merge");

        final long overrideFullPeriod = 1230;
        final long overrideMediumPeriod = 123;
        final long overrideSmallPeriod = 12;
        final int overrideInitialMaxFiles = 100;
        final int overrideInitialMinFiles = 10;

        subject.setFullMergePeriod(overrideFullPeriod);
        subject.setMediumMergePeriod(overrideMediumPeriod);
        subject.setMergeActivatedPeriod(overrideSmallPeriod);
        subject.setMaxNumberOfFilesInMerge(overrideInitialMaxFiles);
        subject.setMinNumberOfFilesInMerge(overrideInitialMinFiles);
        assertEquals(overrideFullPeriod, subject.getFullMergePeriod(), "Should allow overriding full merge periods");
        assertEquals(
                overrideMediumPeriod, subject.getMediumMergePeriod(), "Should allow overriding medium merge periods");
        assertEquals(
                overrideSmallPeriod, subject.getMergeActivatePeriod(), "Should allow overriding small merge periods");
        assertEquals(
                overrideInitialMaxFiles, subject.getMaxNumberOfFilesInMerge(), "Should allow overriding max num files");
        assertEquals(
                overrideInitialMinFiles, subject.getMinNumberOfFilesInMerge(), "Should allow overriding min num files");
    }

    @Test
    void canSetMaxDataFileSize() {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMaxDataFileBytes(-1),
                "Should throw on negative max moves per file");

        final long overrideMaxDataFileBytes = 1024 * 1023;

        subject.setMaxDataFileBytes(overrideMaxDataFileBytes);

        assertEquals(
                overrideMaxDataFileBytes, subject.getMaxDataFileBytes(), "Should allow overriding max data file bytes");
    }

    @Test
    void canSetMoveListChunkSize() {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMoveListChunkSize(0),
                "Should throw on non-positive chunk size");

        final int overrideMoveListChunkSize = 123_000;

        subject.setMoveListChunkSize(overrideMoveListChunkSize);

        assertEquals(
                overrideMoveListChunkSize,
                subject.getMoveListChunkSize(),
                "Should allow overriding move list chunk size");
    }

    @Test
    void canSetMaxRamUsedForMerging() {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMaxRamUsedForMergingGb(-1),
                "Should throw on negative RAM used for merging");

        final int overrideMaxRam = 20;

        subject.setMaxRamUsedForMergingGb(overrideMaxRam);

        assertEquals(
                overrideMaxRam,
                subject.getMaxRamUsedForMergingGb(),
                "Should allow overriding max RAM used for merging");
    }

    @Test
    void canSetIteratorInputBufferBytes() {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setIteratorInputBufferBytes(0),
                "Should throw on non-positive iterator input buffer bytes");

        final int overrideInputBufferBytes = 1024;

        subject.setIteratorInputBufferBytes(overrideInputBufferBytes);

        assertEquals(
                overrideInputBufferBytes,
                subject.getIteratorInputBufferBytes(),
                "Should allow overriding iterator input buffer bytes");
    }

    @Test
    void canSetWriterOutputBufferBytes() {
        final JasperDbSettingsImpl subject = new JasperDbSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setWriterOutputBufferBytes(0),
                "Should throw on non-positive iterator output buffer bytes");

        final int overrideOutputBufferBytes = 1024;

        subject.setWriterOutputBufferBytes(overrideOutputBufferBytes);

        assertEquals(
                overrideOutputBufferBytes,
                subject.getWriterOutputBufferBytes(),
                "Should allow overriding writer output buffer bytes");
    }
}
