/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.addressbook.impl.test;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.asBytes;
import static com.hedera.node.app.service.addressbook.AddressBookHelper.NODES_KEY;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.addressbook.impl.test.handlers.AddressBookTestBase;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import java.util.Set;
import org.assertj.core.util.Streams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadableNodeStoreImplTest extends AddressBookTestBase {
    private ReadableNodeStore subject;

    @BeforeEach
    void setUp() {
        subject = new ReadableNodeStoreImpl(readableStates);
    }

    @Test
    void getsNodeIfNodeExists() {
        givenValidNode();
        final var node = subject.get(nodeId.number());

        assertNotNull(node);

        assertEquals(1L, node.nodeId());
        assertEquals(accountId, node.accountId());
        assertEquals("description", node.description());
        assertArrayEquals(gossipCaCertificate, asBytes(node.gossipCaCertificate()));
        assertArrayEquals(grpcCertificateHash, asBytes(node.grpcCertificateHash()));
    }

    @Test
    void missingNodeIsNull() {
        readableNodeState.reset();
        final var state =
                MapReadableKVState.<EntityNumber, Node>builder(NODES_KEY).build();
        given(readableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(state);
        subject = new ReadableNodeStoreImpl(readableStates);

        assertThat(subject.get(nodeId.number())).isNull();
    }

    @Test
    void constructorCreatesNodeState() {
        final var store = new ReadableNodeStoreImpl(readableStates);
        assertNotNull(store);
    }

    @Test
    void nullArgsFail() {
        assertThrows(NullPointerException.class, () -> new ReadableNodeStoreImpl(null));
    }

    @Test
    void getSizeOfState() {
        final var store = new ReadableNodeStoreImpl(readableStates);
        assertEquals(readableStates.get(NODES_KEY).size(), store.sizeOfState());
    }

    @Test
    void keysWorks() {
        final var stateBuilder = emptyReadableNodeStateBuilder();
        stateBuilder
                .value(new EntityNumber(2), mock(Node.class))
                .value(new EntityNumber(4), mock(Node.class))
                .value(new EntityNumber(5), mock(Node.class))
                .value(new EntityNumber(1), mock(Node.class));
        readableNodeState = stateBuilder.build();
        given(readableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(readableNodeState);
        subject = new ReadableNodeStoreImpl(readableStates);
        final var keys = subject.keys();

        assertTrue(keys.hasNext());
        final var keySet = Streams.stream(keys).collect(toSet());
        assertEquals(
                keySet, Set.of(new EntityNumber(1), new EntityNumber(2), new EntityNumber(4), new EntityNumber(5)));
    }

    @Test
    @DisplayName("Constructing a new roster includes all of the latest nodes defined in state")
    void snapshotOfFutureRosterIncludesAllUndeletedDefinitions() {
        final ReadableKVState<EntityNumber, Node> nodesState = emptyReadableNodeStateBuilder()
                .value(EntityNumber.newBuilder().number(1).build(), NODE_1)
                .value(EntityNumber.newBuilder().number(2).build(), NODE_2)
                .value(EntityNumber.newBuilder().number(3).build(), NODE_3)
                .value(
                        EntityNumber.newBuilder().number(4).build(),
                        Node.newBuilder().nodeId(4).weight(40).deleted(true).build())
                .build();
        given(readableStates.<EntityNumber, Node>get(anyString())).willReturn(nodesState);

        subject = new ReadableNodeStoreImpl(readableStates);
        final var result = subject.snapshotOfFutureRoster();
        org.assertj.core.api.Assertions.assertThat(result.rosterEntries())
                .containsExactlyInAnyOrder(ROSTER_NODE_1, ROSTER_NODE_2, ROSTER_NODE_3);
    }

    private static final Node NODE_1 = Node.newBuilder().nodeId(1).weight(10).build();
    private static final RosterEntry ROSTER_NODE_1 = RosterEntry.newBuilder()
            .nodeId(NODE_1.nodeId())
            .weight(NODE_1.weight())
            .build();
    private static final Node NODE_2 = Node.newBuilder().nodeId(2).weight(20).build();
    private static final RosterEntry ROSTER_NODE_2 = RosterEntry.newBuilder()
            .nodeId(NODE_2.nodeId())
            .weight(NODE_2.weight())
            .build();
    private static final Node NODE_3 = Node.newBuilder().nodeId(3).weight(30).build();
    private static final RosterEntry ROSTER_NODE_3 = RosterEntry.newBuilder()
            .nodeId(NODE_3.nodeId())
            .weight(NODE_3.weight())
            .build();
}
