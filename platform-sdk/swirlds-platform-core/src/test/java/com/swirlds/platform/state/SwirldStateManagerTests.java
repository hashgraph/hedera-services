/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.platform.test.fixtures.state.RandomSignedStateGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwirldStateManagerTests {

    private SwirldStateManager swirldStateManager;
    private MerkleRoot initialState;

    @BeforeEach
    void setup() {
        final SwirldsPlatform platform = mock(SwirldsPlatform.class);
        final AddressBook addressBook =
                RandomAddressBookBuilder.create(Randotron.create()).build();
        when(platform.getAddressBook()).thenReturn(addressBook);
        initialState = newState();
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        swirldStateManager = new SwirldStateManager(
                platformContext,
                addressBook,
                new NodeId(0L),
                mock(StatusActionSubmitter.class),
                new BasicSoftwareVersion(1));
        swirldStateManager.setInitialState(initialState);
    }

    @Test
    @DisplayName("Initial State - state reference counts")
    void initialStateReferenceCount() {
        assertEquals(
                1,
                initialState.getReservationCount(),
                "The initial state is copied and should be referenced once as the previous immutable state.");
        assertEquals(
                1,
                swirldStateManager.getConsensusState().getReservationCount(),
                "The consensus state should have one reference.");
    }

    @Test
    @DisplayName("Seal consensus round")
    void sealConsensusRound() {
        final var round = mock(Round.class);
        swirldStateManager.sealConsensusRound(round);
        verify(round).getRoundNum();
    }

    @Test
    @DisplayName("Load From Signed State - state reference counts")
    void loadFromSignedStateRefCount() {
        final SignedState ss1 = newSignedState();
        swirldStateManager.loadFromSignedState(ss1);

        assertEquals(
                2,
                ss1.getState().getReservationCount(),
                "Loading from signed state should increment the reference count, because it is now referenced by the "
                        + "signed state and the previous immutable state in SwirldStateManager.");
        assertEquals(
                1,
                swirldStateManager.getConsensusState().getReservationCount(),
                "The current consensus state should have a single reference count.");

        final SignedState ss2 = newSignedState();
        swirldStateManager.loadFromSignedState(ss2);

        assertEquals(
                2,
                ss2.getState().getReservationCount(),
                "Loading from signed state should increment the reference count, because it is now referenced by the "
                        + "signed state and the previous immutable state in SwirldStateManager.");
        assertEquals(
                1,
                swirldStateManager.getConsensusState().getReservationCount(),
                "The current consensus state should have a single reference count.");
        assertEquals(
                1,
                ss1.getState().getReservationCount(),
                "The previous immutable state was replaced, so the old state's reference count should have been "
                        + "decremented.");
    }

    private static MerkleRoot newState() {
        final MerkleStateRoot state =
                new MerkleStateRoot(FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major()));

        final PlatformStateModifier platformState = mock(PlatformStateModifier.class);
        when(platformState.getCreationSoftwareVersion()).thenReturn(new BasicSoftwareVersion(nextInt(1, 100)));

        state.updatePlatformState(platformState);

        assertEquals(0, state.getReservationCount(), "A brand new state should have no references.");
        return state;
    }

    private static SignedState newSignedState() {
        final SignedState ss = new RandomSignedStateGenerator().build();
        assertEquals(
                1,
                ss.getSwirldState().getReservationCount(),
                "Creating a signed state should increment the state reference count.");
        return ss;
    }
}
