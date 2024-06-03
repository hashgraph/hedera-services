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

package com.hedera.node.app.service.addressbook.impl.test.handlers;

import static com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl.NODES_KEY;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.protoToPbj;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AddressBookTestBase {
    protected final Key key = A_COMPLEX_KEY;
    protected final Key anotherKey = B_COMPLEX_KEY;
    protected final String payerIdLiteral = "0.0.3";
    protected final AccountID payerId = protoToPbj(asAccount(payerIdLiteral), AccountID.class);
    protected final byte[] grpcCertificateHash = "grpcCertificateHash".getBytes();
    protected final byte[] gossipCaCertificate = "gossipCaCertificate".getBytes();
    protected final EntityNumber nodeId = EntityNumber.newBuilder().number(1L).build();

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

    protected ReadableNodeStore readableStore;
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
        writableNodeState = writableNodeStateWithOneKey();
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
    protected MapWritableKVState<EntityNumber, Node> writableNodeStateWithOneKey() {
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

    @NonNull
    protected MapReadableKVState<EntityNumber, Node> emptyReadableNodeState() {
        return MapReadableKVState.<EntityNumber, Node>builder(NODES_KEY).build();
    }

    protected void givenValidNode() {
        node = new Node(
                nodeId.number(),
                payerId,
                "description",
                null,
                null,
                Bytes.wrap(gossipCaCertificate),
                Bytes.wrap(grpcCertificateHash),
                0);
    }

    protected Node createNode() {
        return new Node.Builder()
                .nodeId(nodeId.number())
                .accountId(payerId)
                .description("description")
                .gossipEndpoint((List<ServiceEndpoint>) null)
                .serviceEndpoint((List<ServiceEndpoint>) null)
                .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                .grpcCertificateHash(Bytes.wrap(grpcCertificateHash))
                .weight(0)
                .build();
    }
}
