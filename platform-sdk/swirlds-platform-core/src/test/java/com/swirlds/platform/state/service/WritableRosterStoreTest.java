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

import static com.swirlds.platform.state.service.WritableRosterStore.MAXIMUM_ROSTER_HISTORY_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RosterState.Builder;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.RosterStateId;
import com.swirlds.platform.roster.InvalidRosterException;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.RosterStateAccessor;
import com.swirlds.platform.state.RosterStateModifier;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.merkle.singleton.WritableSingletonStateImpl;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WritableRosterStoreTest {

    private final WritableStates writableStates = mock(WritableStates.class);
    private RosterStateModifier rosterStateModifier;
    private RosterStateAccessor rosterStateAccessor;

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

        rosterStateAccessor = new ReadableRosterStore(writableStates);
        rosterStateModifier = new WritableRosterStore(writableStates);
    }

    @Test
    @DisplayName("Test that a stored candidate roster can be successfully retrieved")
    void testSetCandidateRosterReturnsSame() {
        final Roster roster1 = createValidTestRoster(1);
        rosterStateModifier.setCandidateRoster(roster1);
        assertEquals(rosterStateAccessor.getCandidateRoster(), roster1);

        final Roster roster2 = createValidTestRoster(2);
        rosterStateModifier.setCandidateRoster(roster2);
        assertEquals(roster2, rosterStateAccessor.getCandidateRoster());
    }

    @Test
    void testInvalidRosterThrowsException() {
        assertThrows(NullPointerException.class, () -> rosterStateModifier.setCandidateRoster(null));
        assertThrows(InvalidRosterException.class, () -> rosterStateModifier.setCandidateRoster(Roster.DEFAULT));
        assertThrows(InvalidRosterException.class, () -> rosterStateModifier.setActiveRoster(Roster.DEFAULT, 1));
    }

    @Test
    @DisplayName("Tests that setting an active roster returns the active roster when getActiveRoster is called.")
    void testGetCandidateRosterWithValidCandidateRoster() {
        final Roster activeRoster = createValidTestRoster(1);
        assertNull(rosterStateAccessor.getActiveRoster());
        rosterStateModifier.setActiveRoster(activeRoster, 2);
        assertSame(rosterStateAccessor.getActiveRoster(), activeRoster);
    }

    @Test
    @DisplayName("Test that the oldest roster is removed when a third roster is set")
    void testOldestActiveRosterRemoved() {
        Roster roster = createValidTestRoster(3);
        rosterStateModifier.setActiveRoster(roster, 1);
        assertSame(rosterStateAccessor.getActiveRoster(), roster);

        roster = createValidTestRoster(1);
        rosterStateModifier.setActiveRoster(roster, 2);
        assertSame(rosterStateAccessor.getActiveRoster(), roster);

        // set a 3rd candidate roster and adopt it
        roster = createValidTestRoster(2);
        rosterStateModifier.setActiveRoster(roster, 3);
        assertSame(rosterStateAccessor.getActiveRoster(), roster);
    }

    @Test
    @DisplayName("Test that an exception is thrown if stored active rosters are ever > MAXIMUM_ROSTER_HISTORY_SIZE")
    void testMaximumRostersMoreThan2ThrowsException() throws NoSuchFieldException, IllegalAccessException {
        final List<RoundRosterPair> activeRosters = Lists.newArrayList();
        activeRosters.add(new RoundRosterPair(
                1, RosterUtils.hash(createValidTestRoster(1)).getBytes()));
        activeRosters.add(new RoundRosterPair(
                2, RosterUtils.hash(createValidTestRoster(2)).getBytes()));
        activeRosters.add(new RoundRosterPair(
                3, RosterUtils.hash(createValidTestRoster(3)).getBytes()));

        final Builder rosterStateBuilder =
                RosterState.newBuilder().candidateRosterHash(Bytes.EMPTY).roundRosterPairs(activeRosters);

        final Field field = WritableRosterStore.class.getDeclaredField("rosterState");
        field.setAccessible(true);
        final WritableSingletonState<RosterState> rosterState =
                (WritableSingletonState<RosterState>) field.get(rosterStateModifier);
        rosterState.put(rosterStateBuilder.build());

        final Exception exception = assertThrows(
                IllegalStateException.class, () -> rosterStateModifier.setActiveRoster(createValidTestRoster(4), 4));
        assertEquals(
                "Active rosters in the Roster state cannot be more than  " + MAXIMUM_ROSTER_HISTORY_SIZE,
                exception.getMessage());
    }

    @Test
    @DisplayName(
            "Test that when a roster hash collision occurs between a newly set active roster "
                    + "and another active roster in history, the other roster isn't removed from the state when remove is called")
    void testRosterHashCollisions() {
        final Roster roster1 = createValidTestRoster(3);
        rosterStateModifier.setActiveRoster(roster1, 1);
        assertSame(rosterStateAccessor.getActiveRoster(), roster1);

        final Roster roster2 = createValidTestRoster(1);
        rosterStateModifier.setActiveRoster(roster2, 2);
        assertSame(rosterStateAccessor.getActiveRoster(), roster2);

        rosterStateModifier.setActiveRoster(roster1, 3);
        assertSame(rosterStateAccessor.getActiveRoster(), roster1);
    }

    /**
     * Creates a valid test roster with the given number of entries.
     *
     * @param entries the number of entries
     * @return a valid roster
     */
    private Roster createValidTestRoster(final int entries) {
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
