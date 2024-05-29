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

import static com.hedera.node.app.service.mono.pbj.PbjConverter.protoToPbj;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.test.fixtures.state.MapReadableKVState;
import com.swirlds.platform.test.fixtures.state.MapWritableKVState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NodeTestBase {
    protected static final String NODES_KEY = "NODES";

    protected final String payerIdLiteral = "0.0.3";
    protected final String description = "Test node";
    protected final AccountID payerId = protoToPbj(asAccount(payerIdLiteral), AccountID.class);
    protected final byte[] gossipCaCertificate = "gossipCaCertificate".getBytes();
    protected final byte[] grpcCertificateHash = "grpcCertificateHash".getBytes();


    protected final long WELL_KNOWN_NODE_ID = 1L;
    protected final EntityNumber nodeId = EntityNumber.newBuilder().number(WELL_KNOWN_NODE_ID).build();

    protected Node node;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    @Mock(strictness = LENIENT)
    protected HandleContext handleContext;

    @Mock
    private StoreMetricsService storeMetricsService;

    protected MapReadableKVState<EntityNumber, Node> readableNodeState;
    protected MapWritableKVState<EntityNumber, Node> writableNodeState;

    protected ReadableNodeStoreImpl readableStore;
    protected WritableNodeStore writableStore;

    @BeforeEach
    void commonSetUp() {
        givenValidNode();
        refreshStoresWithCurrentNodeOnlyInReadable();
    }

    protected void refreshStoresWithCurrentNodeOnlyInReadable() {
        readableNodeState = readableNodeState();
        writableNodeState = emptyWritableNodeState();
        given(readableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(readableNodeState);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(writableNodeState);
        readableStore = new ReadableNodeStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableNodeStore(writableStates, configuration, storeMetricsService);

        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);
    }

    protected void refreshStoresWithCurrentNodeInBothReadableAndWritable() {
        readableNodeState = readableNodeState();
        writableNodeState = writableNodeState();
        given(readableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(readableNodeState);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(writableNodeState);
        readableStore = new ReadableNodeStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableNodeStore(writableStates, configuration, storeMetricsService);

        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);
    }

    @NonNull
    protected MapWritableKVState<EntityNumber, Node> emptyWritableNodeState() {
        return MapWritableKVState.<EntityNumber, Node>builder(NODES_KEY).build();
    }


    @NonNull
    protected MapWritableKVState<EntityNumber, Node> writableNodeState() {
        return MapWritableKVState.<EntityNumber, Node>builder(NODES_KEY)
                .value(nodeId, node)
                .build();
    }

    @NonNull
    protected MapWritableKVState<EntityNumber, Node> writableFileStateWithoutKey(Node node) {
        return MapWritableKVState.<EntityNumber, Node>builder(NODES_KEY)
                .value(nodeId, node)
                .build();
    }

    @NonNull
    protected MapReadableKVState<EntityNumber, Node> readableNodeState() {
        return MapReadableKVState.<EntityNumber, Node>builder(NODES_KEY)
                .value(nodeId, node)
                .build();

    }

    protected void givenValidNode() {
        givenValidNode(false);
    }

    protected void givenValidNode(boolean deleted) {
        node = new Node(WELL_KNOWN_NODE_ID, payerId, description,null,null, Bytes.wrap(gossipCaCertificate), Bytes.wrap(grpcCertificateHash), 30, deleted);
    }

    protected Node createNode() {
        return new Node.Builder()
                .nodeId(WELL_KNOWN_NODE_ID)
                .accountId(payerId)
                .description(description)
                .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                .grpcCertificateHash(Bytes.wrap(grpcCertificateHash))
                .weight(30)
                .build();
    }
}
