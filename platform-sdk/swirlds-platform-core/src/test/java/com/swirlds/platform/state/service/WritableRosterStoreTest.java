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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.RosterStateId;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.RosterStateAccessor;
import com.swirlds.platform.state.RosterStateModifier;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.merkle.singleton.WritableSingletonStateImpl;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WritableRosterStoreTest {

    private final WritableStates writableStates = mock(WritableStates.class);
    private RosterStateModifier rosterStateModifier;
    private RosterStateAccessor rosterStateAccessor;

    private final SoftwareVersion version = mock(SoftwareVersion.class);
    private final ReservedSignedState initialState = mock(ReservedSignedState.class);

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
        final Roster roster1 = createValidRoster(1);
        rosterStateModifier.setCandidateRoster(roster1);

        assertEquals(rosterStateAccessor.getCandidateRoster(), roster1);

        final Roster roster2 = createValidRoster(2);
        rosterStateModifier.setCandidateRoster(roster2);
        assertEquals(roster2, rosterStateAccessor.getCandidateRoster());
    }

    @Test
    void testSetCandidateRosterWhenRosterIsNull() {
        assertThrows(NullPointerException.class, () -> rosterStateModifier.setCandidateRoster(null));
    }

    @Test
    @DisplayName("Tests that adopting a candidate roster throws an exception when the candidate roster is null.")
    void testAdoptCandidateRosterWhenCandidateRosterNotFound() {
        enableSoftwareUpgradeMode(true);
        final Exception exception = assertThrows(
                NullPointerException.class, () -> rosterStateModifier.determineActiveRoster(version, initialState));
        assertEquals(
                "Candidate Roster must be present in the state before attempting to adopt a roster.",
                exception.getMessage());
    }

    @Test
    @DisplayName("Tests that adopting a candidate roster returns the candidate roster as the active roster.")
    void testAdoptCandidateRosterWithValidCandidateRoster() {
        enableSoftwareUpgradeMode(true);
        final Roster candidateRoster = createValidRoster(1);
        rosterStateModifier.setCandidateRoster(candidateRoster);
        assertEquals(rosterStateAccessor.getCandidateRoster(), candidateRoster);
        assertNull(rosterStateAccessor.getActiveRoster());

        rosterStateModifier.determineActiveRoster(version, initialState);
        assertSame(candidateRoster, rosterStateAccessor.getActiveRoster());
    }

    @Test
    @DisplayName(
            "Test determine active roster during normal restart but with no active roster present in the state throws exception")
    void testDetermineActiveRosterDuringNormalRestartWithoutActiveRoster() {
        enableSoftwareUpgradeMode(false);
        final Exception exception = assertThrows(
                NullPointerException.class, () -> rosterStateModifier.determineActiveRoster(version, initialState));
        assertEquals(
                "Active Roster must be present in the state during normal network restart.", exception.getMessage());
    }

    @Test
    @DisplayName("Test determine active roster during normal restart but with an active roster present")
    void testDetermineActiveRosterDuringNormalRestartWithActiveRoster() {
        final Roster candidateRoster = createValidRoster(2);
        rosterStateModifier.setCandidateRoster(candidateRoster);
        // enable network upgrade and adopt the candidate roster.
        enableSoftwareUpgradeMode(true);
        rosterStateModifier.determineActiveRoster(version, initialState);

        enableSoftwareUpgradeMode(false);
        // Normal restart. Assert that the active roster is the same one we adopted earlier
        assertSame(rosterStateModifier.determineActiveRoster(version, initialState), candidateRoster);
        assertSame(rosterStateModifier.getActiveRoster(), candidateRoster);
    }

    @Test
    @DisplayName("Test that the oldest roster is removed after 3 software upgrades")
    void testDetermineActiveRosterWithExisting2PreviousRosters() {
        // set a 1st candidate roster and adopt it
        enableSoftwareUpgradeMode(true);
        rosterStateModifier.setCandidateRoster(createValidRoster(3));
        rosterStateModifier.determineActiveRoster(version, initialState);

        // set a 2nd candidate roster and adopt it
        enableSoftwareUpgradeMode(true);
        rosterStateModifier.setCandidateRoster(createValidRoster(1));
        rosterStateModifier.determineActiveRoster(version, initialState);

        // set a 3rd candidate roster and adopt it
        enableSoftwareUpgradeMode(true);
        final Roster latestCandidateRoster = createValidRoster(2);
        rosterStateModifier.setCandidateRoster(latestCandidateRoster);

        assertSame(rosterStateModifier.determineActiveRoster(version, initialState), latestCandidateRoster);
        assertSame(rosterStateModifier.getActiveRoster(), latestCandidateRoster);
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

    /**
     * Fakes software upgrade mode by setting the software version to a higher version than the current version.
     *
     * @param mode the mode to enable
     */
    private void enableSoftwareUpgradeMode(final boolean mode) {
        final SignedState state = mock(SignedState.class);
        final MerkleRoot stateMerkleRoot = mock(MerkleRoot.class);
        final PlatformStateAccessor platformState = mock(PlatformStateAccessor.class);
        when(initialState.get()).thenReturn(state);
        when(state.getRound()).thenReturn(1L);
        when(state.getState()).thenReturn(stateMerkleRoot);
        when(stateMerkleRoot.getWritableRosterState()).thenReturn(rosterStateModifier);
        when(stateMerkleRoot.getReadablePlatformState()).thenReturn(platformState);
        when(platformState.getCreationSoftwareVersion()).thenReturn(version);
        when(version.compareTo(any())).thenReturn(mode ? 1 : 0);
    }
}
