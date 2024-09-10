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

package com.swirlds.platform.state.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.swirlds.common.RosterStateId;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.merkle.singleton.WritableSingletonStateImpl;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WritableRosterStoreTest {

    private final WritableStates writableStates = mock(WritableStates.class);
    private WritableRosterStore writableRosterStore;

    @BeforeEach
    void setUp() {
        final SingletonNode<RosterState> rosterStateSingleton = new SingletonNode<>(
                RosterStateId.NAME,
                RosterStateId.ROSTER_STATES_KEY,
                0,
                RosterState.PROTOBUF,
                new RosterState(null, new LinkedList<>()));
        final WritableKVState<ProtoBytes, Roster> rosters = MapWritableKVState.<ProtoBytes, Roster>builder(
                        RosterStateId.ROSTER_KEY)
                .build();

        when(writableStates.<ProtoBytes, Roster>get(RosterStateId.ROSTER_KEY)).thenReturn(rosters);
        when(writableStates.<RosterState>getSingleton(RosterStateId.ROSTER_STATES_KEY))
                .thenReturn(new WritableSingletonStateImpl<>(RosterStateId.ROSTER_STATES_KEY, rosterStateSingleton));

        writableRosterStore = new WritableRosterStore(writableStates);
    }

    @Test
    void testSetCandidateRosterWithValidInputs() {
        Roster roster = mock(Roster.class);
        writableRosterStore.setCandidateRoster(roster);

        assertEquals(1, writableRosterStore.rosters().size());
        assertNotNull(writableRosterStore.rosterState());
        assertEquals(writableRosterStore.getCandidateRoster(), roster);

        writableRosterStore.setCandidateRoster(Roster.DEFAULT);
        assertEquals(1, writableRosterStore.rosters().size());
        assertEquals(Roster.DEFAULT, writableRosterStore.getCandidateRoster());
    }

    @Test
    void testBothActiveAndCandidateRosterWithValidInputs() {
        // two rosters with separate hashes for test
        final Roster roster1 =
                Roster.newBuilder().rosters(List.of(RosterEntry.DEFAULT)).build();
        final Roster roster2 = Roster.DEFAULT;

        writableRosterStore.setCandidateRoster(roster1);
        writableRosterStore.setActiveRoster(roster2, 1L);

        assertEquals(2, writableRosterStore.rosters().size());

        assertEquals(writableRosterStore.getCandidateRoster(), roster1);
        assertEquals(writableRosterStore.getActiveRoster(), roster2);
    }

    @Test
    void testConstructorWithValidStates() {
        assertNotNull(writableRosterStore);
    }

    @Test
    void testConstructorWithNullStates() {
        assertThrows(NullPointerException.class, () -> new WritableRosterStore(null));
    }

    @Test
    void testSetActiveRosterWhenRosterIsNull() {
        assertThrows(NullPointerException.class, () -> writableRosterStore.setActiveRoster(null, 1L));
    }

    @Test
    void testSetActiveRosterWithValidInputs() {
        Roster roster = Roster.newBuilder()
                .rosters(List.of(RosterEntry.newBuilder()
                        .gossipEndpoint(ServiceEndpoint.newBuilder().build())
                        .build()))
                .build();

        writableRosterStore.setActiveRoster(roster, 1L);

        assertEquals(1, writableRosterStore.rosters().size());
        assertNotNull(writableRosterStore.rosterState());
        assertEquals(writableRosterStore.getActiveRoster(), roster);
    }

    @Test
    void testSetCandidateRosterWhenRosterIsNull() {
        assertThrows(NullPointerException.class, () -> writableRosterStore.setCandidateRoster(null));
    }
}
