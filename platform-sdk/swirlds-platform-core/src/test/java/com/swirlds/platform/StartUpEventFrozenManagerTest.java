/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.system.EventCreationRuleResponse.DONT_CREATE;
import static com.swirlds.common.system.EventCreationRuleResponse.PASS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.test.metrics.NoOpMetrics;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StartUpEventFrozenManagerTest {
    private static final int TIME_DIFF = 1;

    @Test
    void startUpFreezeEndNotSetTest() {
        final Metrics metrics = new NoOpMetrics();
        StartUpEventFrozenManager startUpEventFrozenManager =
                new StartUpEventFrozenManager(metrics, InstantProvider::get);
        assertFalse(
                startUpEventFrozenManager.isEventCreationPausedAfterStartUp(),
                "when startUpEventFrozenEndTime is not set, this method should return false");
    }

    @Test
    void isEventCreationPausedAfterStartUpTest() {
        final Metrics metrics = mock(Metrics.class);
        StartUpEventFrozenManager startUpEventFrozenManager =
                new StartUpEventFrozenManager(metrics, InstantProvider::get);
        Instant startUpEventFrozenEndTime = Instant.now();
        // set startUpEventFrozenEndTime
        startUpEventFrozenManager.setStartUpEventFrozenEndTime(startUpEventFrozenEndTime);
        // set current time to be before this end time
        InstantProvider.set(startUpEventFrozenEndTime.minusSeconds(TIME_DIFF));
        assertTrue(
                startUpEventFrozenManager.isEventCreationPausedAfterStartUp(),
                "when current time is before startUpEventFrozenEndTime, this method should return true");
        verify(metrics, never()).resetAll();

        // set current time to be after this end time
        InstantProvider.set(startUpEventFrozenEndTime.plusSeconds(TIME_DIFF));
        assertFalse(
                startUpEventFrozenManager.isEventCreationPausedAfterStartUp(),
                "when current time is after startUpEventFrozenEndTime, this method should return false");
        verify(metrics).resetAll();
    }

    @Test
    void shouldNotCreateEventTest() {
        final Metrics metrics = new NoOpMetrics();
        StartUpEventFrozenManager startUpEventFrozenManager =
                spy(new StartUpEventFrozenManager(metrics, InstantProvider::get));

        doReturn(true).when(startUpEventFrozenManager).isEventCreationPausedAfterStartUp();
        assertEquals(
                DONT_CREATE,
                startUpEventFrozenManager.shouldCreateEvent(),
                "should not create events during startUp freeze");

        doReturn(false).when(startUpEventFrozenManager).isEventCreationPausedAfterStartUp();
        assertEquals(
                PASS,
                startUpEventFrozenManager.shouldCreateEvent(),
                "shouldCreateEvent() should return PASS when it is not in startUp freeze");
    }

    @Test
    void shouldSyncTest() {
        final Metrics metrics = new NoOpMetrics();
        StartUpEventFrozenManager startUpEventFrozenManager =
                spy(new StartUpEventFrozenManager(metrics, InstantProvider::get));

        doReturn(true).when(startUpEventFrozenManager).isEventCreationPausedAfterStartUp();
        assertTrue(startUpEventFrozenManager.shouldSync(), "should sync during startUp freeze");

        doReturn(false).when(startUpEventFrozenManager).isEventCreationPausedAfterStartUp();
        assertFalse(
                startUpEventFrozenManager.shouldSync(),
                "shouldSync() should return false when it is not in startUp freeze");
    }

    private static class InstantProvider {
        static Instant instant;

        static void set(final Instant time) {
            instant = time;
        }

        static Instant get() {
            if (instant == null) {
                return Instant.now();
            }
            return instant;
        }
    }
}
