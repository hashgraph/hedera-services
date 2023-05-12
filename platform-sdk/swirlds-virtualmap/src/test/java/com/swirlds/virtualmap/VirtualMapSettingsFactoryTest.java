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

package com.swirlds.virtualmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class VirtualMapSettingsFactoryTest {
    @Test
    void canConfigureSettings() {
        final VirtualMapSettings testSettings = new DefaultVirtualMapSettings();

        VirtualMapSettingsFactory.configure(testSettings);

        assertSame(testSettings, VirtualMapSettingsFactory.get(), "Should return configured settings");
    }

    @Test
    void withoutConfigurationJustReturnsDefaults() {
        final VirtualMapSettings defaultSettings = VirtualMapSettingsFactory.get();
        final int expectedNumHashThreads =
                Math.max(1, (int) (Runtime.getRuntime().availableProcessors() * 0.5));

        assertEquals(50.0, defaultSettings.getPercentHashThreads(), "Default percent hash threads should be 50%");
        assertEquals(
                expectedNumHashThreads,
                defaultSettings.getNumHashThreads(),
                "Default num hash threads should be based on calculation");
        assertEquals(
                (long) Integer.MAX_VALUE,
                defaultSettings.getMaximumVirtualMapSize(),
                "Default maximum virtual map size should be " + Integer.MAX_VALUE);
        assertEquals(
                5_000_000L,
                defaultSettings.getVirtualMapWarningThreshold(),
                "Default virtual map warning threshold should be 5,000,000");
        assertEquals(
                100_000L,
                defaultSettings.getVirtualMapWarningInterval(),
                "Default virtual map warning interval should be 100,000");
    }
}
