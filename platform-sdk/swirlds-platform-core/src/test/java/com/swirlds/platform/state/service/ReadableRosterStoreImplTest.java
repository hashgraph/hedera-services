/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableRosterStoreImplTest {
    @Mock
    private ReadableStates readableStates;

    @Mock
    private ReadableSingletonState<RosterState> rosterState;

    private ReadableRosterStoreImpl subject;

    @BeforeEach
    void setUp() {
        given(readableStates.<RosterState>getSingleton(WritableRosterStore.ROSTER_STATES_KEY))
                .willReturn(rosterState);
        subject = new ReadableRosterStoreImpl(readableStates);
    }

    @Test
    void nullCandidateRosterCasesPass() {
        assertNull(subject.getCandidateRosterHash());
        given(rosterState.get()).willReturn(RosterState.DEFAULT);
        assertNull(subject.getCandidateRosterHash());
    }

    @Test
    void nonNullCandidateRosterIsReturned() {
        final var fakeHash = Bytes.wrap("PRETEND");
        given(rosterState.get())
                .willReturn(
                        RosterState.newBuilder().candidateRosterHash(fakeHash).build());
        assertEquals(fakeHash, subject.getCandidateRosterHash());
    }
}
