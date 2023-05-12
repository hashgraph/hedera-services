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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.merkle.synchronization.settings.ReconnectSettings;
import com.swirlds.common.merkle.synchronization.settings.ReconnectSettingsFactory;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Reconnect Throttle Tests")
class ReconnectThrottleTest {

    private ReconnectSettings buildSettings(final Duration minimumTimeBetweenReconnects) {
        return new ReconnectSettings() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public int getReconnectWindowSeconds() {
                return -1;
            }

            @Override
            public double getFallenBehindThreshold() {
                return 0.5;
            }

            @Override
            public int getAsyncStreamTimeoutMilliseconds() {
                return 0; // not used for this test
            }

            @Override
            public int getAsyncOutputStreamFlushMilliseconds() {
                return 0; // not used for this test
            }

            @Override
            public int getAsyncStreamBufferSize() {
                return 10_000;
            }

            @Override
            public int getMaxAckDelayMilliseconds() {
                return 0; // not used for this test
            }

            @Override
            public int getMaximumReconnectFailuresBeforeShutdown() {
                return 0; // not used for this test
            }

            @Override
            public Duration getMinimumTimeBetweenReconnects() {
                return minimumTimeBetweenReconnects;
            }
        };
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Simultaneous Reconnect Test")
    void simultaneousReconnectTest() {
        final ReconnectThrottle reconnectThrottle = new ReconnectThrottle(ReconnectSettingsFactory.get());

        assertTrue(reconnectThrottle.initiateReconnect(0), "reconnect should be allowed");
        assertFalse(reconnectThrottle.initiateReconnect(1), "reconnect should be blocked");
        reconnectThrottle.reconnectAttemptFinished();

        assertTrue(reconnectThrottle.initiateReconnect(1), "reconnect should be allowed");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Simultaneous Reconnect Test")
    void repeatedReconnectTest() {
        final ReconnectThrottle reconnectThrottle = new ReconnectThrottle(buildSettings(Duration.ofSeconds(1)));
        reconnectThrottle.setCurrentTime(() -> Instant.ofEpochMilli(0));

        assertTrue(reconnectThrottle.initiateReconnect(0), "reconnect should be allowed");
        reconnectThrottle.reconnectAttemptFinished();
        assertFalse(reconnectThrottle.initiateReconnect(0), "reconnect should be blocked");

        assertTrue(reconnectThrottle.initiateReconnect(1), "reconnect should be allowed");
        reconnectThrottle.reconnectAttemptFinished();
        assertFalse(reconnectThrottle.initiateReconnect(1), "reconnect should be blocked");

        reconnectThrottle.setCurrentTime(() -> Instant.ofEpochMilli(2000));

        assertTrue(reconnectThrottle.initiateReconnect(0), "reconnect should be allowed");
        reconnectThrottle.reconnectAttemptFinished();
        assertTrue(reconnectThrottle.initiateReconnect(1), "reconnect should be allowed");
        reconnectThrottle.reconnectAttemptFinished();
    }

    /**
     * As membership in the network changes, we should not keep reconnect records forever or else
     * the records will grow indefinitely.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @DisplayName("Many Node Test")
    void manyNodeTest() {

        final ReconnectThrottle reconnectThrottle = new ReconnectThrottle(buildSettings(Duration.ofSeconds(1)));
        int time = 0;
        final int now = time;
        reconnectThrottle.setCurrentTime(() -> Instant.ofEpochMilli(now));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 100; j++) {
                // Each request is for a unique node
                reconnectThrottle.initiateReconnect((i + 1000) * (j + 1));
                reconnectThrottle.reconnectAttemptFinished();

                assertTrue(
                        reconnectThrottle.getNumberOfRecentReconnects() <= 100,
                        "old requests should have been forgotten");
            }
            if (i + 1 < 3) {
                time += 2_000;
                final int later = time;
                reconnectThrottle.setCurrentTime(() -> Instant.ofEpochMilli(later));
            }
        }
    }
}
