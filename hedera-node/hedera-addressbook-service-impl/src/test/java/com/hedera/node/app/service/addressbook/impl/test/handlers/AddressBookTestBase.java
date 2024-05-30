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
import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
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
    protected final AccountID accountId = AccountID.newBuilder().accountNum(3).build();

    protected final AccountID payerId = AccountID.newBuilder().accountNum(2).build();
    protected final AccountID invalidId =
            AccountID.newBuilder().accountNum(Long.MAX_VALUE).build();
    protected final byte[] grpcCertificateHash = "grpcCertificateHash".getBytes();
    protected final byte[] gossipCaCertificate = "gossipCaCertificate".getBytes();
    protected final long WELL_KNOWN_NODE_ID = 1L;
    protected final EntityNumber nodeId = EntityNumber.newBuilder().number(WELL_KNOWN_NODE_ID).build();
    protected final EntityNumber nodeId2 = EntityNumber.newBuilder().number(3L).build();
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();

    protected static final Key aPrimitiveKey = Key.newBuilder()
            .ed25519(Bytes.wrap("01234567890123456789012345678901"))
            .build();
    protected static final ProtoBytes edKeyAlias = new ProtoBytes(Bytes.wrap(asBytes(Key.PROTOBUF, aPrimitiveKey)));
    protected final AccountID alias =
            AccountID.newBuilder().alias(edKeyAlias.value()).build();
    protected final byte[] evmAddress = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e26");

    protected final ServiceEndpoint endpoint1 = new ServiceEndpoint(Bytes.wrap("127.0.0.1"), 1234, null);

    protected final ServiceEndpoint endpoint2 = new ServiceEndpoint(Bytes.wrap("127.0.0.2"), 2345, null);

    protected final ServiceEndpoint endpoint3 = new ServiceEndpoint(Bytes.EMPTY, 3456, "test.domain.com");

    protected final ServiceEndpoint endpoint4 = new ServiceEndpoint(Bytes.wrap("127.0.0.2"), 2345, "test.domain.com");

    protected final ServiceEndpoint endpoint5 = new ServiceEndpoint(Bytes.EMPTY, 2345, null);

    protected final ServiceEndpoint endpoint6 = new ServiceEndpoint(Bytes.EMPTY, 0, null);

    protected Node node;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    @Mock
    private StoreMetricsService storeMetricsService;

    protected MapReadableKVState<EntityNumber, Node> readableNodeState;
    protected MapWritableKVState<EntityNumber, Node> writableNodeState;

    protected ReadableNodeStore readableStore;
    protected WritableNodeStore writableStore;

    @BeforeEach
    void commonSetUp() {
        givenValidNode();
        refreshStoresWithCurrentNodeInReadable();
    }

    protected void refreshStoresWithCurrentNodeInReadable() {
        readableNodeState = readableNodeState();
        writableNodeState = emptyWritableNodeState();
        given(readableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(readableNodeState);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(writableNodeState);
        readableStore = new ReadableNodeStoreImpl(readableStates);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableNodeStore(writableStates, configuration, storeMetricsService);
    }

    protected void refreshStoresWithCurrentNodeInWritable() {
        writableNodeState = writableNodeStateWithOneKey();
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(writableNodeState);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableNodeStore(writableStates, configuration, storeMetricsService);
    }

    protected void refreshStoresWithMoreNodeInWritable() {
        writableNodeState = writableNodeStateWithMoreKeys();
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(writableNodeState);
        final var configuration = HederaTestConfigBuilder.createConfig();
        writableStore = new WritableNodeStore(writableStates, configuration, storeMetricsService);
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
    protected MapWritableKVState<EntityNumber, Node> writableNodeStateWithMoreKeys() {
        return MapWritableKVState.<EntityNumber, Node>builder(NODES_KEY)
                .value(nodeId, node)
                .value(nodeId2, mock(Node.class))
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
        givenValidNode(false);
    }

    protected void givenValidNode(boolean deleted) {
        node = new Node(
                nodeId.number(),
                accountId,
                "description",
                null,
                null,
                Bytes.wrap(gossipCaCertificate),
                Bytes.wrap(grpcCertificateHash),
                0,
                deleted);
    }

    protected Node createNode() {
        return new Node.Builder()
                .nodeId(nodeId.number())
                .accountId(accountId)
                .description("description")
                .gossipEndpoint((List<ServiceEndpoint>) null)
                .serviceEndpoint((List<ServiceEndpoint>) null)
                .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                .grpcCertificateHash(Bytes.wrap(grpcCertificateHash))
                .weight(0)
                .build();
    }

    static Key mockPayerLookup(Key key, AccountID accountId, ReadableAccountStore accountStore)
            throws PreCheckException {
        final var account = mock(Account.class);
        lenient().when(accountStore.getAccountById(accountId)).thenReturn(account);
        lenient().when(account.key()).thenReturn(key);
        return key;
    }
}
