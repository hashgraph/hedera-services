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

package com.swirlds.platform.system.status.logic;

import static com.swirlds.platform.system.status.logic.StatusLogicTestUtils.triggerActionAndAssertException;
import static com.swirlds.platform.system.status.logic.StatusLogicTestUtils.triggerActionAndAssertNoTransition;
import static com.swirlds.platform.system.status.logic.StatusLogicTestUtils.triggerActionAndAssertTransition;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusConfig;
import com.swirlds.platform.system.status.actions.CatastrophicFailureAction;
import com.swirlds.platform.system.status.actions.DoneReplayingEventsAction;
import com.swirlds.platform.system.status.actions.FallenBehindAction;
import com.swirlds.platform.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.platform.system.status.actions.ReconnectCompleteAction;
import com.swirlds.platform.system.status.actions.SelfEventReachedConsensusAction;
import com.swirlds.platform.system.status.actions.StartedReplayingEventsAction;
import com.swirlds.platform.system.status.actions.StateWrittenToDiskAction;
import com.swirlds.platform.system.status.actions.TimeElapsedAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StartingUpStatusLogic}.
 */
class StartingUpStatusLogicTests {
    private FakeTime time;
    private StartingUpStatusLogic logic;

    @BeforeEach
    void setup() {
        time = new FakeTime();
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        logic = new StartingUpStatusLogic(configuration.getConfigData(PlatformStatusConfig.class));
    }

    @Test
    @DisplayName("Go to REPLAYING_EVENTS")
    void toReplayingEvents() {
        triggerActionAndAssertTransition(
                logic::processStartedReplayingEventsAction,
                new StartedReplayingEventsAction(),
                PlatformStatus.REPLAYING_EVENTS);
    }

    @Test
    @DisplayName("Go to CATASTROPHIC_FAILURE")
    void toCatastrophicFailure() {
        triggerActionAndAssertTransition(
                logic::processCatastrophicFailureAction,
                new CatastrophicFailureAction(),
                PlatformStatus.CATASTROPHIC_FAILURE);
    }

    @Test
    @DisplayName("Irrelevant actions shouldn't cause transitions")
    void irrelevantActions() {
        triggerActionAndAssertNoTransition(
                logic::processTimeElapsedAction, new TimeElapsedAction(time.now()), logic.getStatus());
    }

    @Test
    @DisplayName("Unexpected actions should cause exceptions")
    void unexpectedActions() {
        triggerActionAndAssertException(
                logic::processDoneReplayingEventsAction, new DoneReplayingEventsAction(time.now()), logic.getStatus());
        triggerActionAndAssertException(
                logic::processSelfEventReachedConsensusAction,
                new SelfEventReachedConsensusAction(time.now()),
                logic.getStatus());
        triggerActionAndAssertException(
                logic::processFreezePeriodEnteredAction, new FreezePeriodEnteredAction(0), logic.getStatus());
        triggerActionAndAssertException(logic::processFallenBehindAction, new FallenBehindAction(), logic.getStatus());
        triggerActionAndAssertException(
                logic::processReconnectCompleteAction, new ReconnectCompleteAction(0), logic.getStatus());
        triggerActionAndAssertException(
                logic::processStateWrittenToDiskAction, new StateWrittenToDiskAction(0, false), logic.getStatus());
        triggerActionAndAssertException(
                logic::processStateWrittenToDiskAction, new StateWrittenToDiskAction(0, true), logic.getStatus());
    }
}
