/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class MerkleDbSettingsFactoryTest {

    @Test
    void canConfigureSettings() {
        final MerkleDbSettings testSettings = new DefaultMerkleDbSettings();

        MerkleDbSettingsFactory.configure(testSettings);

        assertSame(testSettings, MerkleDbSettingsFactory.get(), "Should return configured settings");
    }

    @Test
    void withoutConfigurationJustReturnsTestDefaults() {
        final MerkleDbSettings defaultTestSettings = MerkleDbSettingsFactory.get();

        assertEquals(
                500_000_000, defaultTestSettings.getMaxNumOfKeys(), "Default max num of keys should be 500 million");
        assertEquals(
                0,
                defaultTestSettings.getHashesRamToDiskThreshold(),
                "Default internal hashes ram to disk threshold should be 0");
        assertEquals(3 * 1024, defaultTestSettings.getSmallMergeCutoffMb(), "Default small merge cutoff should be 3GB");
        assertEquals(
                10 * 1024, defaultTestSettings.getMediumMergeCutoffMb(), "Default medium merge cutoff should be 10GB");
        assertEquals(
                ChronoUnit.MINUTES, defaultTestSettings.getMergePeriodUnit(), "Default merge unit should be MINUTES");
        assertEquals(
                1440L,
                defaultTestSettings.getFullMergePeriod(),
                "Default full merge period should be 1440 (MINUTES) 24h");
        assertEquals(
                60L,
                defaultTestSettings.getMediumMergePeriod(),
                "Default medium merge period should be 60 (MINUTES) 1h");
        assertEquals(
                1L,
                defaultTestSettings.getMergeActivatePeriod(),
                "Default merge activation period should be 1 (SECONDS)");
        assertEquals(
                1024L,
                defaultTestSettings.getMaxNumberOfFilesInMerge(),
                "Default max number of files in merge should be 1024");
        assertEquals(
                8L,
                defaultTestSettings.getMinNumberOfFilesInMerge(),
                "Default max number of files in merge should be 8");
        assertEquals(
                64L * 1024 * 1024 * 1024,
                defaultTestSettings.getMaxDataFileBytes(),
                "Default max data file size should be 16GB");
        assertEquals(
                1000,
                defaultTestSettings.getMoveListChunkSize(),
                "Default move list chunk size should be half a million");
        assertEquals(
                10, defaultTestSettings.getMaxRamUsedForMergingGb(), "Default max RAM used for merging should be 10GB");
    }
}
