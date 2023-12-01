/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.status.actions.CatastrophicFailureAction;
import com.swirlds.common.system.status.actions.DoneReplayingEventsAction;
import com.swirlds.common.system.status.actions.FallenBehindAction;
import com.swirlds.common.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.common.system.status.actions.PlatformStatusAction;
import com.swirlds.common.system.status.actions.ReconnectCompleteAction;
import com.swirlds.common.system.status.actions.SelfEventReachedConsensusAction;
import com.swirlds.common.system.status.actions.StartedReplayingEventsAction;
import com.swirlds.common.system.status.actions.StateWrittenToDiskAction;
import com.swirlds.common.system.status.actions.TimeElapsedAction;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traverses every edge of the state machine
 */
class PlatformStatusStateMachineTests {
    private FakeTime time;
    private PlatformStatusStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        time = new FakeTime();
        final Configuration configuration = new TestConfigBuilder()
                .withValue("platformStatus.observingStatusDelay", "5s")
                .withValue("platformStatus.activeStatusDelay", "10s")
                .getOrCreateConfig();

        stateMachine = new PlatformStatusStateMachine(
                time, configuration.getConfigData(PlatformStatusConfig.class), mock(NotificationEngine.class));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> FREEZE_COMPLETE")
    void freezeCompleteAfterReplayingEvents() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FreezePeriodEnteredAction(2));
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(2, true));
        assertEquals(PlatformStatus.FREEZE_COMPLETE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> FREEZE_COMPLETE")
    void freezeCompleteAfterObserving() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(2, true));
        assertEquals(PlatformStatus.FREEZE_COMPLETE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> FREEZING -> FREEZE_COMPLETE")
    void freezeCompleteAfterFreezing() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FreezePeriodEnteredAction(2));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.FREEZING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(2, true));
        assertEquals(PlatformStatus.FREEZE_COMPLETE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> FREEZE_COMPLETE")
    void freezeCompleteAfterChecking() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(2, true));
        assertEquals(PlatformStatus.FREEZE_COMPLETE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> FREEZING")
    void freezingAfterChecking() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FreezePeriodEnteredAction(2));
        assertEquals(PlatformStatus.FREEZING, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> ACTIVE -> FREEZE_COMPLETE")
    void freezeCompleteAfterActive() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new SelfEventReachedConsensusAction(time.now()));
        assertEquals(PlatformStatus.ACTIVE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(2, true));
        assertEquals(PlatformStatus.FREEZE_COMPLETE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> ACTIVE -> CHECKING")
    void checkingAfterActive() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new SelfEventReachedConsensusAction(time.now()));
        assertEquals(PlatformStatus.ACTIVE, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(11));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> ACTIVE -> BEHIND")
    void behindAfterActive() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new SelfEventReachedConsensusAction(time.now()));
        assertEquals(PlatformStatus.ACTIVE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> ACTIVE -> FREEZING")
    void freezingAfterActive() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new SelfEventReachedConsensusAction(time.now()));
        assertEquals(PlatformStatus.ACTIVE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FreezePeriodEnteredAction(2));
        assertEquals(PlatformStatus.FREEZING, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> BEHIND")
    void behindAfterChecking() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> FREEZE_COMPLETE")
    void freezeCompleteAfterBehind() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(2, true));
        assertEquals(PlatformStatus.FREEZE_COMPLETE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> RECONNECT_COMPLETE -> FREEZE_COMPLETE")
    void freezeCompleteAfterReconnectComplete() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new ReconnectCompleteAction(5));
        assertEquals(PlatformStatus.RECONNECT_COMPLETE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(2, true));
        assertEquals(PlatformStatus.FREEZE_COMPLETE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> RECONNECT_COMPLETE -> BEHIND")
    void behindAfterReconnectComplete() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new ReconnectCompleteAction(5));
        assertEquals(PlatformStatus.RECONNECT_COMPLETE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> RECONNECT_COMPLETE -> FREEZING")
    void freezingAfterReconnectComplete() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new ReconnectCompleteAction(5));
        assertEquals(PlatformStatus.RECONNECT_COMPLETE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FreezePeriodEnteredAction(10));
        assertEquals(PlatformStatus.RECONNECT_COMPLETE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(11, false));
        assertEquals(PlatformStatus.FREEZING, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> RECONNECT_COMPLETE -> CHECKING")
    void checkingAfterReconnectComplete() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new ReconnectCompleteAction(5));
        assertEquals(PlatformStatus.RECONNECT_COMPLETE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StateWrittenToDiskAction(11, false));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("STARTING_UP -> CATASTROPHIC_FAILURE")
    void startingUpToCatastrophicFailure() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new CatastrophicFailureAction());
        assertEquals(PlatformStatus.CATASTROPHIC_FAILURE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("REPLAYING_EVENTS -> CATASTROPHIC_FAILURE")
    void replayingEventsToCatastrophicFailure() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new CatastrophicFailureAction());
        assertEquals(PlatformStatus.CATASTROPHIC_FAILURE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("OBSERVING -> CATASTROPHIC_FAILURE")
    void observingToCatastrophicFailure() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new CatastrophicFailureAction());
        assertEquals(PlatformStatus.CATASTROPHIC_FAILURE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("CHECKING -> CATASTROPHIC_FAILURE")
    void checkingToCatastrophicFailure() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new CatastrophicFailureAction());
        assertEquals(PlatformStatus.CATASTROPHIC_FAILURE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("RECONNECT_COMPLETE -> CATASTROPHIC_FAILURE")
    void reconnectCompleteToCatastrophicFailure() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new ReconnectCompleteAction(5));
        assertEquals(PlatformStatus.RECONNECT_COMPLETE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new CatastrophicFailureAction());
        assertEquals(PlatformStatus.CATASTROPHIC_FAILURE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("ACTIVE -> CATASTROPHIC_FAILURE")
    void activeToCatastrophicFailure() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.CHECKING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new SelfEventReachedConsensusAction(time.now()));
        assertEquals(PlatformStatus.ACTIVE, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new CatastrophicFailureAction());
        assertEquals(PlatformStatus.CATASTROPHIC_FAILURE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("BEHIND -> CATASTROPHIC_FAILURE")
    void behindToCatastrophicFailure() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.BEHIND, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new CatastrophicFailureAction());
        assertEquals(PlatformStatus.CATASTROPHIC_FAILURE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("FREEZING -> CATASTROPHIC_FAILURE")
    void freezingToCatastrophicFailure() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new StartedReplayingEventsAction());
        assertEquals(PlatformStatus.REPLAYING_EVENTS, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new DoneReplayingEventsAction(time.now()));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FreezePeriodEnteredAction(2));
        assertEquals(PlatformStatus.OBSERVING, stateMachine.getCurrentStatus());
        time.tick(Duration.ofSeconds(6));
        stateMachine.processStatusAction(new TimeElapsedAction(time.now()));
        assertEquals(PlatformStatus.FREEZING, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new CatastrophicFailureAction());
        assertEquals(PlatformStatus.CATASTROPHIC_FAILURE, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("Illegal action")
    void illegalAction() {
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        stateMachine.processStatusAction(new FallenBehindAction());
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
    }

    @Test
    @DisplayName("Unknown action")
    void unknownAction() {
        class UnknownAction implements PlatformStatusAction {}

        final UnknownAction unknownAction = new UnknownAction();
        assertEquals(PlatformStatus.STARTING_UP, stateMachine.getCurrentStatus());
        assertThrows(IllegalArgumentException.class, () -> stateMachine.processStatusAction(unknownAction));
    }
}
