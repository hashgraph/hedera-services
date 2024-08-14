/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.roster.schemas;

import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_STATES_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.swirlds.state.spi.StateDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class V0540RosterSchemaTest {

    @LoggingSubject
    private V0540RosterSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0540RosterSchema();
    }

    @Test
    void registersExpectedRosterSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate).hasSize(2);
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(ROSTER_KEY, iter.next());
        assertEquals(ROSTER_STATES_KEY, iter.next());
    }
}
