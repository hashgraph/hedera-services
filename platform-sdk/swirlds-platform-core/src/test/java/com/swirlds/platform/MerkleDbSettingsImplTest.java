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

import static org.junit.jupiter.api.Assertions.*;

import com.swirlds.merkledb.settings.DefaultMerkleDbSettings;
import org.junit.jupiter.api.Test;

public class MerkleDbSettingsImplTest {
    @Test
    void defaultSettingsTest() {
        final MerkleDbSettingsImpl settings = new MerkleDbSettingsImpl();

        assertEquals(
                DefaultMerkleDbSettings.DEFAULT_PERCENT_HALFDISKHASHMAP_FLUSH_THREADS,
                settings.getPercentHalfDiskHashMapFlushThreads());
        assertEquals(
                DefaultMerkleDbSettings.getNumHalfDiskHashMapFlushThreads(
                        DefaultMerkleDbSettings.DEFAULT_PERCENT_HALFDISKHASHMAP_FLUSH_THREADS),
                settings.getNumHalfDiskHashMapFlushThreads());
    }

    @Test
    void canOverridePercentHalfDiskHashMapFlushThreads() {
        final MerkleDbSettingsImpl settings = new MerkleDbSettingsImpl();

        settings.setPercentHalfDiskHashMapFlushThreads(10.0);

        assertEquals(10.0, settings.getPercentHalfDiskHashMapFlushThreads());
        assertEquals(
                DefaultMerkleDbSettings.getNumHalfDiskHashMapFlushThreads(10.0),
                settings.getNumHalfDiskHashMapFlushThreads());
    }

    @Test
    void canOverrideNumHalfDiskHashMapFlushThreads() {
        final MerkleDbSettingsImpl settings = new MerkleDbSettingsImpl();

        settings.setPercentHalfDiskHashMapFlushThreads(10.0);
        settings.setNumHalfDiskHashMapFlushThreads(2);

        assertEquals(10.0, settings.getPercentHalfDiskHashMapFlushThreads());
        assertEquals(2, settings.getNumHalfDiskHashMapFlushThreads());
    }
}
