/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.status;

import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.platform.system.status.PlatformStatus.BEHIND;
import static com.swirlds.platform.system.status.PlatformStatus.CATASTROPHIC_FAILURE;
import static com.swirlds.platform.system.status.PlatformStatus.CHECKING;
import static com.swirlds.platform.system.status.PlatformStatus.FREEZE_COMPLETE;
import static com.swirlds.platform.system.status.PlatformStatus.FREEZING;
import static com.swirlds.platform.system.status.PlatformStatus.OBSERVING;
import static com.swirlds.platform.system.status.PlatformStatus.RECONNECT_COMPLETE;
import static com.swirlds.platform.system.status.PlatformStatus.REPLAYING_EVENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.system.status.actions.CatastrophicFailureAction;
import com.swirlds.platform.system.status.actions.DoneReplayingEventsAction;
import com.swirlds.platform.system.status.actions.FallenBehindAction;
import com.swirlds.platform.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.platform.system.status.actions.PlatformStatusAction;
import com.swirlds.platform.system.status.actions.ReconnectCompleteAction;
import com.swirlds.platform.system.status.actions.SelfEventReachedConsensusAction;
import com.swirlds.platform.system.status.actions.StartedReplayingEventsAction;
import com.swirlds.platform.system.status.actions.StateWrittenToDiskAction;
import com.swirlds.platform.system.status.actions.TimeElapsedAction;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traverses every edge of the state machine
 */
class PlatformStatusStateMachineTests {
    private FakeTime time;
    private DefaultStatusStateMachine stateMachine;

    @BeforeEach
    void setup() {
        time = new FakeTime();
        final Configuration configuration = new TestConfigBuilder()
                .withValue(PlatformStatusConfig_.OBSERVING_STATUS_DELAY, "5s")
                .withValue(PlatformStatusConfig_.ACTIVE_STATUS_DELAY, "10s")
                .getOrCreateConfig();

        final PlatformContext platformContextBuilder = TestPlatformContextBuilder.create()
                .withTime(time)
                .withConfiguration(configuration)
                .build();

        stateMachine = new DefaultStatusStateMachine(platformContextBuilder);
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> FREEZE_COMPLETE")
    void freezeCompleteAfterReplayingEvents() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertNull(stateMachine.submitStatusAction(new FreezePeriodEnteredAction(2)));
        assertEquals(FREEZE_COMPLETE, stateMachine.submitStatusAction(new StateWrittenToDiskAction(2, true)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> FREEZE_COMPLETE")
    void freezeCompleteAfterObserving() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(FREEZE_COMPLETE, stateMachine.submitStatusAction(new StateWrittenToDiskAction(2, true)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> FREEZING -> FREEZE_COMPLETE")
    void freezeCompleteAfterFreezing() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertNull(stateMachine.submitStatusAction(new FreezePeriodEnteredAction(2)));
        time.tick(Duration.ofSeconds(6));
        assertEquals(FREEZING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(FREEZE_COMPLETE, stateMachine.submitStatusAction(new StateWrittenToDiskAction(2, true)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> FREEZE_COMPLETE")
    void freezeCompleteAfterChecking() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(FREEZE_COMPLETE, stateMachine.submitStatusAction(new StateWrittenToDiskAction(2, true)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> FREEZING")
    void freezingAfterChecking() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(FREEZING, stateMachine.submitStatusAction(new FreezePeriodEnteredAction(2)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> ACTIVE -> FREEZE_COMPLETE")
    void freezeCompleteAfterActive() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(ACTIVE, stateMachine.submitStatusAction(new SelfEventReachedConsensusAction(time.now())));
        assertEquals(FREEZE_COMPLETE, stateMachine.submitStatusAction(new StateWrittenToDiskAction(2, true)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> ACTIVE -> CHECKING")
    void checkingAfterActive() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(ACTIVE, stateMachine.submitStatusAction(new SelfEventReachedConsensusAction(time.now())));
        time.tick(Duration.ofSeconds(11));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> ACTIVE -> BEHIND")
    void behindAfterActive() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(ACTIVE, stateMachine.submitStatusAction(new SelfEventReachedConsensusAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> ACTIVE -> FREEZING")
    void freezingAfterActive() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(ACTIVE, stateMachine.submitStatusAction(new SelfEventReachedConsensusAction(time.now())));
        assertEquals(FREEZING, stateMachine.submitStatusAction(new FreezePeriodEnteredAction(2)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> CHECKING -> BEHIND")
    void behindAfterChecking() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> FREEZE_COMPLETE")
    void freezeCompleteAfterBehind() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
        assertEquals(FREEZE_COMPLETE, stateMachine.submitStatusAction(new StateWrittenToDiskAction(2, true)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> RECONNECT_COMPLETE -> FREEZE_COMPLETE")
    void freezeCompleteAfterReconnectComplete() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
        assertEquals(RECONNECT_COMPLETE, stateMachine.submitStatusAction(new ReconnectCompleteAction(5)));
        assertEquals(FREEZE_COMPLETE, stateMachine.submitStatusAction(new StateWrittenToDiskAction(2, true)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> RECONNECT_COMPLETE -> BEHIND")
    void behindAfterReconnectComplete() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
        assertEquals(RECONNECT_COMPLETE, stateMachine.submitStatusAction(new ReconnectCompleteAction(5)));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> RECONNECT_COMPLETE -> FREEZING")
    void freezingAfterReconnectComplete() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
        assertEquals(RECONNECT_COMPLETE, stateMachine.submitStatusAction(new ReconnectCompleteAction(5)));
        assertNull(stateMachine.submitStatusAction(new FreezePeriodEnteredAction(10)));
        assertEquals(FREEZING, stateMachine.submitStatusAction(new StateWrittenToDiskAction(11, false)));
    }

    @Test
    @DisplayName("STARTING_UP -> REPLAYING_EVENTS -> OBSERVING -> BEHIND -> RECONNECT_COMPLETE -> CHECKING")
    void checkingAfterReconnectComplete() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
        assertEquals(RECONNECT_COMPLETE, stateMachine.submitStatusAction(new ReconnectCompleteAction(5)));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new StateWrittenToDiskAction(11, false)));
    }

    @Test
    @DisplayName("STARTING_UP -> CATASTROPHIC_FAILURE")
    void startingUpToCatastrophicFailure() {
        assertEquals(CATASTROPHIC_FAILURE, stateMachine.submitStatusAction(new CatastrophicFailureAction()));
    }

    @Test
    @DisplayName("REPLAYING_EVENTS -> CATASTROPHIC_FAILURE")
    void replayingEventsToCatastrophicFailure() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(CATASTROPHIC_FAILURE, stateMachine.submitStatusAction(new CatastrophicFailureAction()));
    }

    @Test
    @DisplayName("OBSERVING -> CATASTROPHIC_FAILURE")
    void observingToCatastrophicFailure() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(CATASTROPHIC_FAILURE, stateMachine.submitStatusAction(new CatastrophicFailureAction()));
    }

    @Test
    @DisplayName("CHECKING -> CATASTROPHIC_FAILURE")
    void checkingToCatastrophicFailure() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(CATASTROPHIC_FAILURE, stateMachine.submitStatusAction(new CatastrophicFailureAction()));
    }

    @Test
    @DisplayName("RECONNECT_COMPLETE -> CATASTROPHIC_FAILURE")
    void reconnectCompleteToCatastrophicFailure() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
        assertEquals(RECONNECT_COMPLETE, stateMachine.submitStatusAction(new ReconnectCompleteAction(5)));
        assertEquals(CATASTROPHIC_FAILURE, stateMachine.submitStatusAction(new CatastrophicFailureAction()));
    }

    @Test
    @DisplayName("ACTIVE -> CATASTROPHIC_FAILURE")
    void activeToCatastrophicFailure() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        time.tick(Duration.ofSeconds(6));
        assertEquals(CHECKING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(ACTIVE, stateMachine.submitStatusAction(new SelfEventReachedConsensusAction(time.now())));
        assertEquals(CATASTROPHIC_FAILURE, stateMachine.submitStatusAction(new CatastrophicFailureAction()));
    }

    @Test
    @DisplayName("BEHIND -> CATASTROPHIC_FAILURE")
    void behindToCatastrophicFailure() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertEquals(BEHIND, stateMachine.submitStatusAction(new FallenBehindAction()));
        assertEquals(CATASTROPHIC_FAILURE, stateMachine.submitStatusAction(new CatastrophicFailureAction()));
    }

    @Test
    @DisplayName("FREEZING -> CATASTROPHIC_FAILURE")
    void freezingToCatastrophicFailure() {
        assertEquals(REPLAYING_EVENTS, stateMachine.submitStatusAction(new StartedReplayingEventsAction()));
        assertEquals(OBSERVING, stateMachine.submitStatusAction(new DoneReplayingEventsAction(time.now())));
        assertNull(stateMachine.submitStatusAction(new FreezePeriodEnteredAction(2)));
        time.tick(Duration.ofSeconds(6));
        assertEquals(FREEZING, stateMachine.submitStatusAction(new TimeElapsedAction(time.now())));
        assertEquals(CATASTROPHIC_FAILURE, stateMachine.submitStatusAction(new CatastrophicFailureAction()));
    }

    @Test
    @DisplayName("Illegal action")
    void illegalAction() {
        // state machine must be robust to unexpected actions
        assertNull(stateMachine.submitStatusAction(new FallenBehindAction()));
    }

    @Test
    @DisplayName("Unknown action")
    void unknownAction() {
        class UnknownAction implements PlatformStatusAction {}

        final UnknownAction unknownAction = new UnknownAction();
        assertThrows(IllegalArgumentException.class, () -> stateMachine.submitStatusAction(unknownAction));
    }
}
