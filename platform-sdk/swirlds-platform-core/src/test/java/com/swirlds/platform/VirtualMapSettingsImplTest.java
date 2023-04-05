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

import org.junit.jupiter.api.Test;

class VirtualMapSettingsImplTest {
    @Test
    void defaultsAreAsExpected() {
        final int expectedDefaultNumHashThreads = (int) (Runtime.getRuntime().availableProcessors() * 0.5);

        final VirtualMapSettingsImpl subject = new VirtualMapSettingsImpl();

        assertEquals(50.0, subject.getPercentHashThreads(), "Default percent hash threads should be 50%");
        assertEquals(
                expectedDefaultNumHashThreads,
                subject.getNumHashThreads(),
                "Default num hash threads should come from calculation");
        assertEquals(
                (long) Integer.MAX_VALUE,
                subject.getMaximumVirtualMapSize(),
                "Default maximum virtual map size should be " + Integer.MAX_VALUE);
        assertEquals(
                5_000_000L,
                subject.getVirtualMapWarningThreshold(),
                "Default virtual map warning threshold should be 5,000,000");
        assertEquals(
                100_000L,
                subject.getVirtualMapWarningInterval(),
                "Default virtual map warning interval should be 100,000");
    }

    @Test
    void overridingPercentHashThreadsWorksAsExpected() {
        final VirtualMapSettingsImpl subject = new VirtualMapSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setPercentHashThreads(-1.0),
                "Should not be able to set a negative percentage of hash threads");
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setPercentHashThreads(101.0),
                "Should not be able to set a percentage of hash threads above 100%");

        final double customPer = 12.3;
        subject.setPercentHashThreads(customPer);
        assertEquals(customPer, subject.getPercentHashThreads(), "Should be able to override percentHashThreads");
    }

    @Test
    void overridingNumHashThreadsWorksAsExpected() {
        final VirtualMapSettingsImpl subject = new VirtualMapSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setNumHashThreads(-1),
                "Should not be able to set a negative number of hash threads");

        final int customNum = 12;
        subject.setNumHashThreads(customNum);
        assertEquals(customNum, subject.getNumHashThreads(), "Should be able to override numHashThreads");
    }

    @Test
    void overridingMaximumVirtualMapSizeWorksAsExpected() {
        final VirtualMapSettingsImpl subject = new VirtualMapSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMaximumVirtualMapSize(-1L),
                "Should not be able to set a negative maximum virtual map size");
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMaximumVirtualMapSize(0L),
                "Should not be able to set a zero maximum virtual map size");
        long tooBig = Integer.MAX_VALUE + 1L;
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setMaximumVirtualMapSize(tooBig),
                "Should not be able to set a too-large maximum virtual map size");

        final long customMax = 42;
        subject.setMaximumVirtualMapSize(customMax);
        assertEquals(customMax, subject.getMaximumVirtualMapSize(), "Should be able to override maximumVirtualMapSize");
    }

    @Test
    void overridingVirtualMapWarningThresholdWorksAsExpected() {
        final VirtualMapSettingsImpl subject = new VirtualMapSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setVirtualMapWarningThreshold(-1L),
                "Should not be able to set a negative virtual map warning threshold");
        long tooBig = Integer.MAX_VALUE + 1L;
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setVirtualMapWarningThreshold(tooBig),
                "Should not be able to set a too-large virtual map warning threshold");

        final long customThreshold = 42;
        subject.setVirtualMapWarningThreshold(customThreshold);
        assertEquals(
                customThreshold,
                subject.getVirtualMapWarningThreshold(),
                "Should be able to override virtualMapWarningThreshold");
    }

    @Test
    void overridingVirtualMapWarningIntervalWorksAsExpected() {
        final VirtualMapSettingsImpl subject = new VirtualMapSettingsImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setVirtualMapWarningInterval(-1L),
                "Should not be able to set a negative virtual map warning interval");
        long tooBig = Integer.MAX_VALUE + 1L;
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.setVirtualMapWarningInterval(tooBig),
                "Should not be able to set a too-large virtual map warning interval");

        final long customInterval = 42;
        subject.setVirtualMapWarningInterval(customInterval);
        assertEquals(
                customInterval,
                subject.getVirtualMapWarningInterval(),
                "Should be able to override virtualMapWarningInterval");
    }
}
