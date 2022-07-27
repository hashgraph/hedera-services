/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.interceptors;

import static com.hedera.services.ledger.interceptors.StakeChangeScenario.FROM_ABSENT_TO_ABSENT;
import static com.hedera.services.ledger.interceptors.StakeChangeScenario.forCase;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StakeChangeScenarioTest {
    @Test
    void scenariosHaveExpectedSemantics() {
        assertSemanticsAre(StakeChangeScenario.FROM_ABSENT_TO_NODE, false, false, true, false);
        assertSemanticsAre(StakeChangeScenario.FROM_ACCOUNT_TO_NODE, false, true, true, false);
        assertSemanticsAre(StakeChangeScenario.FROM_NODE_TO_NODE, true, false, true, false);

        assertSemanticsAre(StakeChangeScenario.FROM_ABSENT_TO_ACCOUNT, false, false, false, true);
        assertSemanticsAre(StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, false, true, false, true);
        assertSemanticsAre(StakeChangeScenario.FROM_NODE_TO_ACCOUNT, true, false, false, true);

        assertSemanticsAre(FROM_ABSENT_TO_ABSENT, false, false, false, false);
        assertSemanticsAre(StakeChangeScenario.FROM_ACCOUNT_TO_ABSENT, false, true, false, false);
        assertSemanticsAre(StakeChangeScenario.FROM_NODE_TO_ABSENT, true, false, false, false);
    }

    @Test
    void returnsExpectedScenarios() {
        assertEquals(StakeChangeScenario.FROM_ABSENT_TO_NODE, forCase(0, -1));
        assertEquals(StakeChangeScenario.FROM_ACCOUNT_TO_NODE, forCase(1, -1));
        assertEquals(StakeChangeScenario.FROM_NODE_TO_NODE, forCase(-1, -1));

        assertEquals(StakeChangeScenario.FROM_ABSENT_TO_ACCOUNT, forCase(0, 1));
        assertEquals(StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT, forCase(1, 1));
        assertEquals(StakeChangeScenario.FROM_NODE_TO_ACCOUNT, forCase(-1, 1));

        assertEquals(StakeChangeScenario.FROM_ABSENT_TO_ABSENT, forCase(0, 0));
        assertEquals(StakeChangeScenario.FROM_ACCOUNT_TO_ABSENT, forCase(1, 0));
        assertEquals(StakeChangeScenario.FROM_NODE_TO_ABSENT, forCase(-1, 0));
    }

    private void assertSemanticsAre(
            final StakeChangeScenario scenario,
            final boolean withdrawsFromNode,
            final boolean withdrawsFromAccount,
            final boolean awardsToNode,
            final boolean awardsToAccount) {
        assertEquals(scenario.withdrawsFromNode(), withdrawsFromNode);
        assertEquals(scenario.withdrawsFromAccount(), withdrawsFromAccount);
        assertEquals(scenario.awardsToNode(), awardsToNode);
        assertEquals(scenario.awardsToAccount(), awardsToAccount);
    }
}
