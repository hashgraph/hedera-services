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

package com.hedera.node.app.tss;

import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.ACTIVE_ROSTER;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.CURRENT_CANDIDATE_ROSTER;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.ROSTER_NODE_1;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.ROSTER_NODE_2;
import static com.hedera.node.app.workflows.handle.steps.NodeStakeUpdatesTest.RosterCase.ROSTER_NODE_3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.tss.api.TssLibrary;
import com.swirlds.platform.state.service.WritableRosterStore;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TssBaseServiceTest {
    @Mock
    private HandleContext handleContext;

    @Mock
    private AppContext appContext;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private WritableRosterStore rosterStore;

    @Mock
    private TssLibrary tssLibrary;

    private TssBaseService subject;

    @BeforeEach
    void setUp() {
        given(appContext.gossip()).willReturn(mock(AppContext.Gossip.class));

        subject = new TssBaseServiceImpl(
                appContext, mock(ExecutorService.class), mock(Executor.class), tssLibrary, mock(Executor.class));
    }

    @Test
    void nameIsAsExpected() {
        final var subject = mock(TssBaseService.class);
        doCallRealMethod().when(subject).getServiceName();
        assertEquals(TssBaseService.NAME, subject.getServiceName());
    }

    @Test
    @DisplayName("Service won't set the current candidate roster as the new candidate roster")
    void doesntSetSameCandidateRoster() {
        // Simulate CURRENT_CANDIDATE_ROSTER and ACTIVE_ROSTER
        mockWritableRosterStore();

        // Attempt to set the same candidate roster
        subject.setCandidateRoster(CURRENT_CANDIDATE_ROSTER, handleContext);
        verify(rosterStore, never()).putCandidateRoster(any());
    }

    @Test
    @DisplayName("Service won't set the active roster as the new candidate roster")
    void doesntSetActiveRosterAsCandidateRoster() {
        // Simulate CURRENT_CANDIDATE_ROSTER and ACTIVE_ROSTER
        final var rosterStore = mockWritableRosterStore();
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.writableStore(WritableRosterStore.class)).willReturn(rosterStore);

        // Attempt to set the active roster as the new candidate roster
        subject.setCandidateRoster(ACTIVE_ROSTER, handleContext);
        verify(rosterStore, never()).putCandidateRoster(any());
    }

    @Test
    @DisplayName("Service appropriately sets a new roster as the new candidate roster")
    void setsCandidateRoster() {
        // Simulate the _current_ candidate roster and active roster
        final var rosterStore = mockWritableRosterStore();

        // Test setting a _new_ candidate roster with nodes 1, 2, and 3
        final var inputRoster = Roster.newBuilder()
                .rosterEntries(List.of(ROSTER_NODE_1, ROSTER_NODE_2, ROSTER_NODE_3))
                .build();
        subject.setCandidateRoster(inputRoster, handleContext);
        verify(rosterStore).putCandidateRoster(inputRoster);
    }

    private WritableRosterStore mockWritableRosterStore() {
        // Mock retrieval of the roster store
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.writableStore(WritableRosterStore.class)).willReturn(rosterStore);

        // Mock the candidate and active rosters
        lenient().when(rosterStore.getCandidateRoster()).thenReturn(CURRENT_CANDIDATE_ROSTER);
        lenient().when(rosterStore.getActiveRoster()).thenReturn(ACTIVE_ROSTER);

        return rosterStore;
    }
}
