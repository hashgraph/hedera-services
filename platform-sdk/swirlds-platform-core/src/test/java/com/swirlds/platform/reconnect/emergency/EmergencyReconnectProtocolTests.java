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

package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.Connection;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectSettingsImpl;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.state.EmergencyRecoveryFile;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.signed.SignedStateManager;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for P
 */
public class EmergencyReconnectProtocolTests {

    private static final NodeId PEER_ID = new NodeId(false, 1L);

    private static Stream<Arguments> initiateParams() {
        return Stream.of(
                Arguments.of(new InitiateParams(
                        true, true, true, "Emergency state is required and permit is acquired, initiate")),
                Arguments.of(new InitiateParams(
                        false, true, false, "Emergency state is not required and permit is acquired, do not initiate")),
                Arguments.of(new InitiateParams(
                        true, false, false, "Emergency state is required and permit is not acquired, do not initiate")),
                Arguments.of(new InitiateParams(
                        false,
                        false,
                        false,
                        "Emergency state is not required and permit is not acquired, do not initiate")));
    }

    private record InitiateParams(
            boolean emergencyStateRequired, boolean getsPermit, boolean shouldInitiate, String desc) {
        @Override
        public String toString() {
            return desc;
        }
    }

    @DisplayName("Test the conditions under which the protocol should and should not be initiated")
    @ParameterizedTest
    @MethodSource("initiateParams")
    void shouldInitiateTest(final InitiateParams initiateParams) {
        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        if (initiateParams.emergencyStateRequired) {
            when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
            final EmergencyRecoveryFile file = new EmergencyRecoveryFile(1L, RandomUtils.randomHash(), Instant.now());
            when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(file);
        } else {
            when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(false);
        }

        final ReconnectController reconnectController = mock(ReconnectController.class);
        when(reconnectController.acquireLearnerPermit()).thenReturn(initiateParams.getsPermit);

        final EmergencyReconnectProtocol protocol = new EmergencyReconnectProtocol(
                getStaticThreadManager(),
                mock(NotificationEngine.class),
                PEER_ID,
                emergencyRecoveryManager,
                mock(ReconnectThrottle.class),
                mock(SignedStateManager.class),
                100,
                mock(ReconnectMetrics.class),
                reconnectController);

        assertEquals(initiateParams.shouldInitiate, protocol.shouldInitiate(), "unexpected initiation result");
    }

    @DisplayName("Test the conditions under which the protocol should accept protocol initiation")
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testShouldAccept(final boolean teacherIsThrottled) {
        final ReconnectThrottle teacherThrottle = mock(ReconnectThrottle.class);
        when(teacherThrottle.initiateReconnect(anyLong())).thenReturn(!teacherIsThrottled);

        final EmergencyReconnectProtocol protocol = new EmergencyReconnectProtocol(
                getStaticThreadManager(),
                mock(NotificationEngine.class),
                PEER_ID,
                mock(EmergencyRecoveryManager.class),
                teacherThrottle,
                mock(SignedStateManager.class),
                100,
                mock(ReconnectMetrics.class),
                mock(ReconnectController.class));

        assertEquals(!teacherIsThrottled, protocol.shouldAccept(), "unexpected protocol acceptance");
    }

    @DisplayName("Tests if the reconnect learner permit gets released")
    @Test
    void testPermitReleased() throws InterruptedException {
        final ReconnectThrottle teacherThrottle = mock(ReconnectThrottle.class);
        when(teacherThrottle.initiateReconnect(anyLong())).thenReturn(true);

        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);

        final ReconnectController reconnectController =
                new ReconnectController(getStaticThreadManager(), mock(ReconnectHelper.class), () -> {});

        final EmergencyReconnectProtocol protocol = new EmergencyReconnectProtocol(
                getStaticThreadManager(),
                mock(NotificationEngine.class),
                PEER_ID,
                emergencyRecoveryManager,
                teacherThrottle,
                mock(EmergencyStateFinder.class),
                100,
                mock(ReconnectMetrics.class),
                reconnectController);

        // the ReconnectController must be running in order to provide permits
        getStaticThreadManager()
                .createThreadFactory("test", "test")
                .newThread(reconnectController)
                .start();

        // wait for the background thread to start waiting for the reconnect connection
        while (!reconnectController.acquireLearnerPermit()) {
            Thread.sleep(10);
        }
        assertFalse(
                reconnectController.acquireLearnerPermit(),
                "the while loop should have acquired the permit, so it should not be available");
        reconnectController.cancelLearnerPermit();

        assertTrue(protocol.shouldInitiate(), "protocol should be initiated");

        final Connection throwingConnection = mock(Connection.class);
        when(throwingConnection.getDos()).thenThrow(new RuntimeException());
        assertThrows(
                RuntimeException.class,
                () -> protocol.runProtocol(throwingConnection),
                "expected an exception to be thrown");

        assertTrue(reconnectController.acquireLearnerPermit(), "a permit should still be available for other peers");
    }

    @DisplayName("Tests if teacher throttle gets released")
    @Test
    void testTeacherThrottleReleased() {
        final ReconnectSettingsImpl reconnectSettings = new ReconnectSettingsImpl();
        // we don't want the time based throttle to interfere
        reconnectSettings.minimumTimeBetweenReconnects = Duration.ZERO;
        final ReconnectThrottle teacherThrottle = new ReconnectThrottle(reconnectSettings);

        final EmergencyReconnectProtocol protocol = new EmergencyReconnectProtocol(
                getStaticThreadManager(),
                mock(NotificationEngine.class),
                PEER_ID,
                mock(EmergencyRecoveryManager.class),
                teacherThrottle,
                mock(EmergencyStateFinder.class),
                100,
                mock(ReconnectMetrics.class),
                mock(ReconnectController.class));

        assertTrue(protocol.shouldAccept(), "expected protocol to accept initiation");

        final Connection throwingConnection = mock(Connection.class);
        when(throwingConnection.getDos()).thenThrow(new RuntimeException());

        assertThrows(
                RuntimeException.class,
                () -> protocol.runProtocol(throwingConnection),
                "expected an exception to be thrown");

        assertTrue(teacherThrottle.initiateReconnect(PEER_ID.getId()), "Teacher throttle should be released");
    }
}
