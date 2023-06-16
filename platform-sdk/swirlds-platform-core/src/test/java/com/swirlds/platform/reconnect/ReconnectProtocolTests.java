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

package com.swirlds.platform.reconnect;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.state.signed.ReservedSignedState.createNullReservation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the {@link ReconnectProtocol}
 */
public class ReconnectProtocolTests {

    private static final NodeId PEER_ID = new NodeId(1L);

    private static Stream<Arguments> initiateParams() {
        return Stream.of(
                Arguments.of(new InitiateParams(
                        true, true, true, "Permit acquired and peer is a reconnect neighbor, initiate")),
                Arguments.of(new InitiateParams(false, true, false, "Permit not acquired, do not initiate")),
                Arguments.of(
                        new InitiateParams(true, false, false, "Peer is not a reconnect neighbor, do not initiate")),
                Arguments.of(new InitiateParams(
                        false,
                        false,
                        false,
                        "Permit not acquired and peer is not a reconnect neighbor, do not initiate")));
    }

    private record InitiateParams(
            boolean getsPermit, boolean isReconnectNeighbor, boolean shouldInitiate, String desc) {
        @Override
        public String toString() {
            return desc;
        }
    }

    private static Stream<Arguments> acceptParams() {
        final List<Arguments> arguments = new ArrayList<>();

        for (final boolean teacherIsThrottled : List.of(true, false)) {
            for (final boolean selfIsBehind : List.of(true, false)) {
                for (final boolean teacherHasValidState : List.of(true, false)) {
                    for (final boolean stateIsInitialized : List.of(true, false)) {
                        arguments.add(Arguments.of(new AcceptParams(
                                teacherIsThrottled, selfIsBehind, teacherHasValidState, stateIsInitialized)));
                    }
                }
            }
        }

        return arguments.stream();
    }

    private record AcceptParams(
            boolean teacherIsThrottled,
            boolean selfIsBehind,
            boolean teacherHasValidState,
            boolean stateIsInitialized) {

        public boolean shouldAccept() {
            return !teacherIsThrottled && !selfIsBehind && teacherHasValidState && stateIsInitialized;
        }

        @Override
        public String toString() {
            return (teacherIsThrottled ? "throttled teacher" : "un-throttled teacher") + ", "
                    + (selfIsBehind ? "teacher is behind" : "teacher not behind")
                    + ", " + (teacherHasValidState ? "teacher has valid state" : "teacher has no valid state")
                    + ", " + (stateIsInitialized ? "state is initialized" : "state is not initialized");
        }
    }

    @DisplayName("Test the conditions under which the protocol should and should not be initiated")
    @ParameterizedTest
    @MethodSource("initiateParams")
    void shouldInitiateTest(final InitiateParams params) {
        final ReconnectController reconnectController = mock(ReconnectController.class);
        when(reconnectController.acquireLearnerPermit()).thenReturn(params.getsPermit);

        final List<NodeId> neighborsForReconnect = LongStream.range(0L, 10L)
                .filter(id -> id != PEER_ID.id() || params.isReconnectNeighbor)
                .mapToObj(NodeId::new)
                .toList();

        final FallenBehindManager fallenBehindManager = mock(FallenBehindManager.class);
        when(fallenBehindManager.getNeighborsForReconnect()).thenReturn(neighborsForReconnect);
        when(fallenBehindManager.shouldReconnectFrom(any()))
                .thenAnswer(a -> neighborsForReconnect.contains(a.getArgument(0, NodeId.class)));

        final ReconnectProtocol protocol = new ReconnectProtocol(
                getStaticThreadManager(),
                PEER_ID,
                mock(ReconnectThrottle.class),
                () -> null,
                100,
                mock(ReconnectMetrics.class),
                reconnectController,
                mock(SignedStateValidator.class),
                fallenBehindManager);

        assertEquals(params.shouldInitiate, protocol.shouldInitiate(), "unexpected initiation result");
    }

    @DisplayName("Test the conditions under which the protocol should accept protocol initiation")
    @ParameterizedTest
    @MethodSource("acceptParams")
    void testShouldAccept(final AcceptParams params) {
        final ReconnectThrottle teacherThrottle = mock(ReconnectThrottle.class);
        when(teacherThrottle.initiateReconnect(any())).thenReturn(!params.teacherIsThrottled);

        final FallenBehindManager fallenBehindManager = mock(FallenBehindManager.class);
        when(fallenBehindManager.hasFallenBehind()).thenReturn(params.selfIsBehind);

        final SignedState signedState;
        if (params.teacherHasValidState) {
            signedState = spy(new RandomSignedStateGenerator().build());
            when(signedState.isComplete()).thenReturn(true);
            if (params.stateIsInitialized) {
                signedState.getState().markAsInitialized();
            }
        } else {
            signedState = null;
        }

        final ReservedSignedState reservedSignedState =
                signedState == null ? createNullReservation() : signedState.reserve("test");

        final ReconnectProtocol protocol = new ReconnectProtocol(
                getStaticThreadManager(),
                PEER_ID,
                teacherThrottle,
                () -> reservedSignedState,
                100,
                mock(ReconnectMetrics.class),
                mock(ReconnectController.class),
                mock(SignedStateValidator.class),
                fallenBehindManager);

        assertEquals(params.shouldAccept(), protocol.shouldAccept(), "unexpected protocol acceptance");
    }

    @DisplayName("Tests if the reconnect learner permit gets released")
    @Test
    void testPermitReleased() throws InterruptedException {
        final FallenBehindManager fallenBehindManager = mock(FallenBehindManager.class);
        when(fallenBehindManager.shouldReconnectFrom(any())).thenReturn(false);

        final ReconnectController reconnectController =
                new ReconnectController(getStaticThreadManager(), mock(ReconnectHelper.class), () -> {});

        final ReconnectProtocol protocol = new ReconnectProtocol(
                getStaticThreadManager(),
                PEER_ID,
                mock(ReconnectThrottle.class),
                () -> null,
                100,
                mock(ReconnectMetrics.class),
                reconnectController,
                mock(SignedStateValidator.class),
                fallenBehindManager);

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

        assertFalse(
                protocol.shouldInitiate(),
                "we expect that a reconnect should not be initiated because of FallenBehindManager");
        assertTrue(reconnectController.acquireLearnerPermit(), "a permit should still be available for other peers");
    }

    @DisplayName("Tests if teacher throttle gets released")
    @Test
    void testTeacherThrottleReleased() {
        final FallenBehindManager fallenBehindManager = mock(FallenBehindManager.class);
        final Configuration config = new TestConfigBuilder()
                // we don't want the time based throttle to interfere
                .withValue("reconnect.minimumTimeBetweenReconnects", "0s")
                .getOrCreateConfig();
        final ReconnectThrottle reconnectThrottle = new ReconnectThrottle(config.getConfigData(ReconnectConfig.class));

        final NodeId node0 = new NodeId(0L);
        final NodeId node1 = new NodeId(1L);
        final NodeId node2 = new NodeId(2L);
        final ReconnectProtocol peer1 = new ReconnectProtocol(
                getStaticThreadManager(),
                node1,
                reconnectThrottle,
                () -> null,
                100,
                mock(ReconnectMetrics.class),
                mock(ReconnectController.class),
                mock(SignedStateValidator.class),
                fallenBehindManager);
        final SignedState signedState = spy(new RandomSignedStateGenerator().build());
        when(signedState.isComplete()).thenReturn(true);
        final State state = mock(State.class);
        when(signedState.getState()).thenReturn(state);
        when(state.isInitialized()).thenReturn(true);

        final ReservedSignedState reservedSignedState = signedState.reserve("test");

        final ReconnectProtocol peer2 = new ReconnectProtocol(
                getStaticThreadManager(),
                node2,
                reconnectThrottle,
                () -> reservedSignedState,
                100,
                mock(ReconnectMetrics.class),
                mock(ReconnectController.class),
                mock(SignedStateValidator.class),
                fallenBehindManager);

        // pretend we have fallen behind
        when(fallenBehindManager.hasFallenBehind()).thenReturn(true);
        assertFalse(peer1.shouldAccept(), "we should not accept because we have fallen behind");
        assertFalse(peer2.shouldAccept(), "we should not accept because we have fallen behind");

        // now we have not fallen behind
        when(fallenBehindManager.hasFallenBehind()).thenReturn(false);
        assertTrue(peer2.shouldAccept(), "we should accept because we have not fallen behind");
    }

    @Test
    @DisplayName("Aborted Learner")
    void abortedLearner() {
        final ReconnectController reconnectController = mock(ReconnectController.class);
        when(reconnectController.acquireLearnerPermit()).thenReturn(true);
        final ValueReference<Boolean> permitCancelled = new ValueReference<>(false);
        doAnswer(invocation -> {
                    assertFalse(permitCancelled.getValue(), "permit should only be cancelled once");
                    permitCancelled.setValue(true);
                    return null;
                })
                .when(reconnectController)
                .cancelLearnerPermit();

        final FallenBehindManager fallenBehindManager = mock(FallenBehindManager.class);
        when(fallenBehindManager.hasFallenBehind()).thenReturn(true);
        when(fallenBehindManager.shouldReconnectFrom(any())).thenReturn(true);

        final ReconnectProtocol protocol = new ReconnectProtocol(
                getStaticThreadManager(),
                new NodeId(0),
                mock(ReconnectThrottle.class),
                () -> null,
                100,
                mock(ReconnectMetrics.class),
                reconnectController,
                mock(SignedStateValidator.class),
                fallenBehindManager);

        assertTrue(protocol.shouldInitiate());
        protocol.initiateFailed();

        assertTrue(permitCancelled.getValue(), "permit should have been cancelled");
    }

    @Test
    @DisplayName("Aborted Teacher")
    void abortedTeacher() {
        final ReconnectThrottle reconnectThrottle = mock(ReconnectThrottle.class);
        when(reconnectThrottle.initiateReconnect(any())).thenReturn(true);
        final ValueReference<Boolean> throttleReleased = new ValueReference<>(false);
        doAnswer(invocation -> {
                    assertFalse(throttleReleased.getValue(), "throttle should not be released twice");
                    throttleReleased.setValue(true);
                    return null;
                })
                .when(reconnectThrottle)
                .reconnectAttemptFinished();

        final FallenBehindManager fallenBehindManager = mock(FallenBehindManager.class);
        when(fallenBehindManager.hasFallenBehind()).thenReturn(false);

        final SignedState signedState = spy(new RandomSignedStateGenerator().build());
        when(signedState.isComplete()).thenReturn(true);
        signedState.getState().markAsInitialized();

        final ReservedSignedState reservedSignedState = signedState.reserve("test");

        final ReconnectProtocol protocol = new ReconnectProtocol(
                getStaticThreadManager(),
                new NodeId(0),
                reconnectThrottle,
                () -> reservedSignedState,
                100,
                mock(ReconnectMetrics.class),
                mock(ReconnectController.class),
                mock(SignedStateValidator.class),
                fallenBehindManager);

        assertTrue(protocol.shouldAccept());
        protocol.acceptFailed();

        assertTrue(throttleReleased.getValue(), "throttle should be released");
        assertEquals(-1, signedState.getReservationCount(), "state should be released");
    }

    @Test
    @DisplayName("Teacher Has No Signed State")
    void teacherHasNoSignedState() {
        final ReconnectThrottle reconnectThrottle = mock(ReconnectThrottle.class);
        doAnswer(invocation -> {
                    fail("throttle should not be engaged if there is not available state");
                    return null;
                })
                .when(reconnectThrottle)
                .initiateReconnect(any());

        final FallenBehindManager fallenBehindManager = mock(FallenBehindManager.class);
        when(fallenBehindManager.hasFallenBehind()).thenReturn(false);

        final ReconnectProtocol protocol = new ReconnectProtocol(
                getStaticThreadManager(),
                new NodeId(0),
                reconnectThrottle,
                ReservedSignedState::createNullReservation,
                100,
                mock(ReconnectMetrics.class),
                mock(ReconnectController.class),
                mock(SignedStateValidator.class),
                fallenBehindManager);

        assertFalse(protocol.shouldAccept());
    }
}
