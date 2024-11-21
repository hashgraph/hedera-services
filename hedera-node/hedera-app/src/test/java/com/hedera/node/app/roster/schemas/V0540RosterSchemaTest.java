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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link V0540RosterSchema}.
 */
class V0540RosterSchemaTest {

    @LoggingSubject
    private V0540RosterSchema subject;

    private MigrationContext migrationContext;
    private WritableSingletonState<Object> rosterState;

    @BeforeEach
    void setUp() {
        subject = new V0540RosterSchema();
        migrationContext = mock(MigrationContext.class);
        rosterState = mock(WritableSingletonState.class);
    }

    @Test
    void registersExpectedRosterSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate).hasSize(2);
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(ROSTER_KEY, iter.next(), "Unexpected Roster key!");
        assertEquals(ROSTER_STATES_KEY, iter.next(), "Unexpected RosterState key!");
    }

    @Test
    @DisplayName("For this version, migrate from existing state version returns default.")
    void testMigrateFromNullRosterStateReturnsDefault() {
        when(migrationContext.newStates()).thenReturn(mock(WritableStates.class));
        when(migrationContext.newStates().getSingleton(ROSTER_STATES_KEY)).thenReturn(rosterState);

        subject.migrate(migrationContext);
        verify(rosterState, times(1)).put(RosterState.DEFAULT);
    }

    @Test
    @DisplayName("Migrate from older state version returns default.")
    void testMigrateFromPreviousStateVersion() {
        when(migrationContext.newStates()).thenReturn(mock(WritableStates.class));
        when(migrationContext.newStates().getSingleton(ROSTER_STATES_KEY)).thenReturn(rosterState);
        when(migrationContext.previousVersion())
                .thenReturn(
                        SemanticVersion.newBuilder().major(0).minor(53).patch(0).build());
        subject.migrate(migrationContext);
        verify(rosterState, times(1)).put(RosterState.DEFAULT);
    }
}
