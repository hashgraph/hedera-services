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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
    private WritableRosterStore rosterStore;

    @Mock
    private TssMetrics tssMetrics;

    private TssBaseService subject;

    @BeforeEach
    void setUp() {
        given(appContext.gossip()).willReturn(mock(AppContext.Gossip.class));

        final var executor = mock(Executor.class);
        subject = new TssBaseServiceImpl(
                appContext, mock(ExecutorService.class), executor, new PlaceholderTssLibrary(), executor);
    }

    @Test
    void nameIsAsExpected() {
        final var subject = mock(TssBaseService.class);
        doCallRealMethod().when(subject).getServiceName();
        assertEquals(TssBaseService.NAME, subject.getServiceName());
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
        subject.setCandidateRoster(inputRoster, handleContext, tssMetrics);
        verify(rosterStore).putCandidateRoster(inputRoster);
    }

    private WritableRosterStore mockWritableRosterStore() {
        // Mock retrieval of the roster store
        given(handleContext.storeFactory()).willReturn(storeFactory);
        given(storeFactory.writableStore(WritableRosterStore.class)).willReturn(rosterStore);

        // Mock the candidate and active rosters
        lenient().when(rosterStore.getCandidateRoster()).thenReturn(TssBaseServiceTest.CURRENT_CANDIDATE_ROSTER);
        lenient().when(rosterStore.getActiveRoster()).thenReturn(TssBaseServiceTest.ACTIVE_ROSTER);

        return rosterStore;
    }

    private static final Bytes BYTES_1_2_3 = Bytes.wrap("1, 2, 3");
    public static final Node NODE_1 = Node.newBuilder()
            .nodeId(1)
            .weight(10)
            .gossipCaCertificate(BYTES_1_2_3)
            .gossipEndpoint(ServiceEndpoint.newBuilder()
                    .ipAddressV4(Bytes.wrap("1, 1"))
                    .port(11)
                    .build())
            .build();
    private static final RosterEntry ROSTER_NODE_1 = RosterEntry.newBuilder()
            .nodeId(NODE_1.nodeId())
            .weight(NODE_1.weight())
            .gossipCaCertificate(NODE_1.gossipCaCertificate())
            .gossipEndpoint(NODE_1.gossipEndpoint())
            .build();
    public static final Node NODE_2 = Node.newBuilder()
            .nodeId(2)
            .weight(20)
            .gossipCaCertificate(BYTES_1_2_3)
            .gossipEndpoint(ServiceEndpoint.newBuilder()
                    .ipAddressV4(Bytes.wrap("2, 2"))
                    .port(22)
                    .build())
            .build();
    private static final RosterEntry ROSTER_NODE_2 = RosterEntry.newBuilder()
            .nodeId(NODE_2.nodeId())
            .weight(NODE_2.weight())
            .gossipCaCertificate(NODE_2.gossipCaCertificate())
            .gossipEndpoint(NODE_2.gossipEndpoint())
            .build();
    public static final Node NODE_3 = Node.newBuilder()
            .nodeId(3)
            .weight(30)
            .gossipCaCertificate(BYTES_1_2_3)
            .gossipEndpoint(ServiceEndpoint.newBuilder()
                    .ipAddressV4(Bytes.wrap("3, 3"))
                    .port(33)
                    .build())
            .build();
    private static final RosterEntry ROSTER_NODE_3 = RosterEntry.newBuilder()
            .nodeId(NODE_3.nodeId())
            .weight(NODE_3.weight())
            .gossipCaCertificate(NODE_3.gossipCaCertificate())
            .gossipEndpoint(NODE_3.gossipEndpoint())
            .build();
    public static final Node NODE_4 = Node.newBuilder()
            .nodeId(4)
            .weight(40)
            .gossipCaCertificate(BYTES_1_2_3)
            .gossipEndpoint(ServiceEndpoint.newBuilder()
                    .ipAddressV4(Bytes.wrap("4, 4"))
                    .port(44)
                    .build())
            .build();
    private static final RosterEntry ROSTER_NODE_4 = RosterEntry.newBuilder()
            .nodeId(NODE_4.nodeId())
            .weight(NODE_4.weight())
            .gossipCaCertificate(NODE_4.gossipCaCertificate())
            .gossipEndpoint(NODE_4.gossipEndpoint())
            .build();

    public static final Roster CURRENT_CANDIDATE_ROSTER = Roster.newBuilder()
            .rosterEntries(List.of(ROSTER_NODE_1, ROSTER_NODE_2))
            .build();
    public static final Roster ACTIVE_ROSTER = Roster.newBuilder()
            .rosterEntries(ROSTER_NODE_1, ROSTER_NODE_2, ROSTER_NODE_3, ROSTER_NODE_4)
            .build();
}
