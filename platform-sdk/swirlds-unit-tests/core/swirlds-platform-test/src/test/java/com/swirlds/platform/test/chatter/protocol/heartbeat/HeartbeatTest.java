/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.protocol.heartbeat;

import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.platform.chatter.protocol.heartbeat.HeartbeatMessage;
import com.swirlds.platform.chatter.protocol.heartbeat.HeartbeatSendReceive;
import com.swirlds.platform.chatter.protocol.heartbeat.HeartbeatSender;
import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeartbeatTest {
    private final long peerId = 1;
    private final PingChecker pingChecker = new PingChecker(peerId);
    private final Duration heartbeatInterval = Duration.ofSeconds(1);
    private final FakeTime time = new FakeTime();

    private final HeartbeatSendReceive heartbeat =
            new HeartbeatSendReceive(time, peerId, pingChecker::checkPing, heartbeatInterval);

    @BeforeEach
    void reset() {
        heartbeat.clear();
    }

    /**
     * Sends a heartbeat after 1 second, receives a response, updates the ping time
     */
    @Test
    void sendHeartbeat() {
        // initial state assertions
        Assertions.assertNull(heartbeat.getMessage(), "no messages expected until the interval runs out");
        Assertions.assertNull(heartbeat.getLastRoundTripNanos(), "no available time expected");

        // Advance time such that a heartbeat request is sent
        time.tick(Duration.ofSeconds(2));
        final HeartbeatMessage request = (HeartbeatMessage) heartbeat.getMessage();
        Assertions.assertNotNull(request, "a heartbeat is expected after 2 seconds");

        // Setup heartbeat response
        final Duration responseTime = Duration.ofMillis(100);
        time.tick(responseTime);
        pingChecker.setExpectedPing(responseTime);
        pingChecker.assertNumUpdates(0);

        // Handle heartbeat response
        heartbeat.handleMessage(HeartbeatMessage.response(request.getHeartbeatId()));

        // Verify heartbeat response handling
        pingChecker.assertNumUpdates(1);
        Assertions.assertNotNull(heartbeat.getLastRoundTripNanos(), "round trip time expected");
        Assertions.assertEquals(
                responseTime, Duration.ofNanos(heartbeat.getLastRoundTripNanos()), "round trip time is not correct");
    }

    @Test
    void getLastRoundTripNanosBeforeResponseReceived() {
        Assertions.assertNull(heartbeat.getMessage(), "no messages expected until the interval runs out");
        Assertions.assertNull(heartbeat.getLastRoundTripNanos(), "no available time expected");
        time.tick(Duration.ofSeconds(2));
        final HeartbeatMessage request = (HeartbeatMessage) heartbeat.getMessage();
        Assertions.assertNotNull(request, "a heartbeat is expected after 2 seconds");

        // We have sent the first heartbeat request, but not gotten a response yet, so this should return null
        Assertions.assertNull(heartbeat.getLastRoundTripNanos(), "no round trip time expected");

        final Duration responseTime = Duration.ofMillis(100);
        time.tick(responseTime);
        pingChecker.setExpectedPing(responseTime);
        pingChecker.assertNumUpdates(0);
        heartbeat.handleMessage(HeartbeatMessage.response(request.getHeartbeatId()));

        // We have sent the first heartbeat request and a response, so there should be a round trip time available
        Assertions.assertNotNull(heartbeat.getLastRoundTripNanos(), "round trip time expected");

        Assertions.assertEquals(
                responseTime.toNanos(),
                heartbeat.getLastRoundTripNanos(),
                "round trip time should be the last heartbeat response time");

        time.tick(Duration.ofSeconds(2));
        final HeartbeatMessage request2 = (HeartbeatMessage) heartbeat.getMessage();
        Assertions.assertNotNull(request2, "a heartbeat is expected after 2 seconds");

        final Duration responseWaitTime = Duration.ofMillis(200);
        time.tick(responseWaitTime);

        // We have sent a second heartbeat, not gotten a response yet, and we've waited for a response longer than the
        // duration of the last ping time, so the round trip time should be the time since the second heartbeat was sent
        Assertions.assertNotNull(heartbeat.getLastRoundTripNanos(), "round trip time expected");
        Assertions.assertEquals(
                responseWaitTime.toNanos(),
                heartbeat.getLastRoundTripNanos(),
                "round trip time should be time since last unanswered heartbeat");
    }

    /**
     * Receives a heartbeat request and sends a response
     */
    @Test
    void receiveHeartbeat() {
        final long id = 10;
        Assertions.assertNull(heartbeat.getMessage(), "no messages expected");
        heartbeat.handleMessage(HeartbeatMessage.request(id));
        final HeartbeatMessage response = (HeartbeatMessage) heartbeat.getMessage();
        Assertions.assertNotNull(response, "a response is expected for the heartbeat sent");
        Assertions.assertEquals(id, response.getHeartbeatId());
        Assertions.assertTrue(response.isResponse());
        pingChecker.assertNumUpdates(0);
    }

    /**
     * Sends a heartbeat after 1 second, does not receive a response. After the first heartbeat times out, another
     * request is sent
     */
    @Test
    void heartbeatTimeout() {
        Assertions.assertNull(heartbeat.getMessage(), "no messages expected until the interval runs out");
        time.tick(Duration.ofSeconds(2));
        Assertions.assertNotNull(heartbeat.getMessage(), "a heartbeat is expected after 2 seconds");
        time.tick(HeartbeatSender.HEARTBEAT_TIMEOUT.minusMillis(1));
        Assertions.assertNull(
                heartbeat.getMessage(), "until the timeout runs out, we should not send any more heartbeats");
        time.tick(Duration.ofSeconds(1));
        final HeartbeatMessage request = (HeartbeatMessage) heartbeat.getMessage();
        Assertions.assertNotNull(request, "a heartbeat is expected after the timeout runs out");
        final Duration responseTime = Duration.ofMillis(5);
        time.tick(responseTime);
        pingChecker.setExpectedPing(responseTime);
        pingChecker.assertNumUpdates(0);
        heartbeat.handleMessage(HeartbeatMessage.response(request.getHeartbeatId()));
        pingChecker.assertNumUpdates(1);
    }
}
