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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.RosterStateId;
import com.swirlds.platform.roster.RosterUtils;
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
                PlatformStateService.NAME,
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
        final Roster roster1 = createValidRoster(1);
        writableRosterStore.setCandidateRoster(roster1);

        assertEquals(writableRosterStore.getCandidateRoster(), roster1);

        final Roster roster2 = createValidRoster(2);
        writableRosterStore.setCandidateRoster(roster2);
        assertEquals(roster2, writableRosterStore.getCandidateRoster());
    }

    @Test
    void testConstructorWithNullStates() {
        assertThrows(NullPointerException.class, () -> new WritableRosterStore(null));
    }

    @Test
    void testSetCandidateRosterWhenRosterIsNull() {
        assertThrows(NullPointerException.class, () -> writableRosterStore.setCandidateRoster(null));
    }

    @Test
    void testAdoptCandidateRosterWhenCandidateRosterNotFound() {
        assertThrows(IllegalStateException.class, () -> writableRosterStore.adoptCandidateRoster(1L));
    }

    @Test
    void testAdoptCandidateRosterWithValidCandidateRoster() {
        final Roster candidateRoster = createValidRoster(1);
        writableRosterStore.setCandidateRoster(candidateRoster);
        assertEquals(writableRosterStore.getCandidateRoster(), candidateRoster);
        assertNull(writableRosterStore.getActiveRoster());
        assertEquals(
                RosterUtils.hashOf(candidateRoster).getBytes(),
                writableRosterStore.rosterStateOrThrow().candidateRosterHash());

        writableRosterStore.adoptCandidateRoster(1L);
        // This should be asserting the active roster is the (previous) candidate roster,
        // but the state is uncommitted for testing, so we just assert the candidate roster should now be removed.
        // In turn, this test doubles as both adoptCandidateRoster and removeCandidateRoster tests.
        assertEquals(Bytes.EMPTY, writableRosterStore.rosterStateOrThrow().candidateRosterHash());
    }

    /**
     * Creates a valid roster with the given number of entries for testing.
     *
     * @param entries the number of entries
     * @return a valid roster
     */
    private Roster createValidRoster(final int entries) {
        final List<RosterEntry> entriesList = new LinkedList<>();
        for (int i = 0; i < entries; i++) {
            entriesList.add(RosterEntry.newBuilder()
                    .nodeId(i)
                    .weight(i + 1) // weight must be > 0
                    .gossipCaCertificate(Bytes.wrap("test" + i))
                    .tssEncryptionKey(Bytes.wrap("test" + i))
                    .gossipEndpoint(ServiceEndpoint.newBuilder()
                            .domainName("domain.com" + i)
                            .port(666)
                            .build())
                    .build());
        }
        return Roster.newBuilder().rosterEntries(entriesList).build();
    }
}
