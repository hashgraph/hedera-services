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

package com.swirlds.common.system.platformstatus.statuslogic;

import static com.swirlds.common.system.platformstatus.statuslogic.StatusLogicTestUtils.triggerActionAndAssertException;

import com.swirlds.common.system.platformstatus.PlatformStatusAction;
import com.swirlds.common.system.platformstatus.PlatformStatusConfig;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CatastrophicFailureStatusLogic}.
 */
class CatastrophicFailureStatusLogicTests {
    private CatastrophicFailureStatusLogic logic;

    @BeforeEach
    void setup() {
        final FakeTime time = new FakeTime();
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        logic = new CatastrophicFailureStatusLogic(time, configuration.getConfigData(PlatformStatusConfig.class));
    }

    @Test
    @DisplayName("Unexpected actions should cause exceptions")
    void unexpectedActions() {
        triggerActionAndAssertException(logic, PlatformStatusAction.STARTED_REPLAYING_EVENTS);
        triggerActionAndAssertException(logic, PlatformStatusAction.DONE_REPLAYING_EVENTS);
        triggerActionAndAssertException(logic, PlatformStatusAction.OWN_EVENT_REACHED_CONSENSUS);
        triggerActionAndAssertException(logic, PlatformStatusAction.FREEZE_PERIOD_ENTERED);
        triggerActionAndAssertException(logic, PlatformStatusAction.FALLEN_BEHIND);
        triggerActionAndAssertException(logic, PlatformStatusAction.RECONNECT_COMPLETE);
        triggerActionAndAssertException(logic, PlatformStatusAction.STATE_WRITTEN_TO_DISK);
        triggerActionAndAssertException(logic, PlatformStatusAction.CATASTROPHIC_FAILURE);
        triggerActionAndAssertException(logic, PlatformStatusAction.TIME_ELAPSED);
    }
}
