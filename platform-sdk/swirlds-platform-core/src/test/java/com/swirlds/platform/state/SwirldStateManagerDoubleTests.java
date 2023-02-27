/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.state.DummySwirldState2;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.components.SystemTransactionHandlerImpl;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.state.signed.SignedState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SwirldStateManagerDoubleTests {

    private SwirldStateManagerDouble swirldStateManagerDouble;
    private State initialState;

    @BeforeEach
    void setup() {
        final SwirldsPlatform platform = mock(SwirldsPlatform.class);
        when(platform.getAddressBook()).thenReturn(mock(AddressBook.class));
        initialState = newState();
        swirldStateManagerDouble = new SwirldStateManagerDouble(
                new NodeId(false, 0L),
                mock(SystemTransactionHandlerImpl.class),
                mock(SwirldStateMetrics.class),
                mock(SettingsProvider.class),
                () -> false,
                initialState);
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
                swirldStateManagerDouble.getConsensusState().getReservationCount(),
                "The consensus state should have one reference.");
    }

    @Test
    @DisplayName("Load From Signed State - state reference counts")
    void loadFromSignedStateRefCount() {
        final SignedState ss1 = newSignedState();
        swirldStateManagerDouble.loadFromSignedState(ss1);

        assertEquals(
                2,
                ss1.getState().getReservationCount(),
                "Loading from signed state should increment the reference count, because it is now referenced by the "
                        + "signed state and the previous immutable state in SwirldStateManagerDouble.");
        assertEquals(
                1,
                swirldStateManagerDouble.getConsensusState().getReservationCount(),
                "The current consensus state should have a single reference count.");

        final SignedState ss2 = newSignedState();
        swirldStateManagerDouble.loadFromSignedState(ss2);

        assertEquals(
                2,
                ss2.getState().getReservationCount(),
                "Loading from signed state should increment the reference count, because it is now referenced by the "
                        + "signed state and the previous immutable state in SwirldStateManagerDouble.");
        assertEquals(
                1,
                swirldStateManagerDouble.getConsensusState().getReservationCount(),
                "The current consensus state should have a single reference count.");
        assertEquals(
                1,
                ss1.getState().getReservationCount(),
                "The previous immutable state was replaced, so the old state's reference count should have been "
                        + "decremented.");
    }

    private static State newState() {
        final State state = new State();
        state.setSwirldState(new DummySwirldState2());
        assertEquals(0, state.getReservationCount(), "A brand new state should have no references.");
        return state;
    }

    private static SignedState newSignedState() {
        final State state = newState();
        final SignedState ss = new SignedState(state);
        assertEquals(
                1, state.getReservationCount(), "Creating a signed state should increment the state reference count.");
        return ss;
    }
}
