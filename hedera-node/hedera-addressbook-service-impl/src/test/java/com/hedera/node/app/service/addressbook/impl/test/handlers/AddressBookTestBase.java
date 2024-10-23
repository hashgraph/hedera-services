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

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.asBytes;
import static com.hedera.node.app.service.addressbook.AddressBookHelper.NODES_KEY;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Key.Builder;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Random;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AddressBookTestBase {
    private static final String A_NAME = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B_NAME = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String C_NAME = "cccccccccccccccccccccccccccccccc";
    private static final Function<String, Builder> KEY_BUILDER =
            value -> Key.newBuilder().ed25519(Bytes.wrap(value.getBytes()));
    private static final Key A_THRESHOLD_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    KEY_BUILDER.apply(C_NAME).build())
                            .build()))
            .build();
    private static final Key A_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    A_THRESHOLD_KEY)))
            .build();
    private static final Key B_COMPLEX_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(2)
                    .keys(KeyList.newBuilder()
                            .keys(
                                    KEY_BUILDER.apply(A_NAME).build(),
                                    KEY_BUILDER.apply(B_NAME).build(),
                                    A_COMPLEX_KEY)))
            .build();
    protected final Key key = A_COMPLEX_KEY;
    protected final Key anotherKey = B_COMPLEX_KEY;

    protected final Bytes defauleAdminKeyBytes =
            Bytes.wrap("0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92");

    final Key invalidKey = Key.newBuilder()
            .ecdsaSecp256k1((Bytes.fromHex("0000000000000000000000000000000000000000")))
            .build();
    protected final AccountID accountId = AccountID.newBuilder().accountNum(3).build();

    protected final AccountID payerId = AccountID.newBuilder().accountNum(2).build();
    protected final byte[] grpcCertificateHash = "grpcCertificateHash".getBytes();
    protected final byte[] gossipCaCertificate = "gossipCaCertificate".getBytes();
    protected final long WELL_KNOWN_NODE_ID = 1L;
    protected final EntityNumber nodeId =
            EntityNumber.newBuilder().number(WELL_KNOWN_NODE_ID).build();
    protected final EntityNumber nodeId2 = EntityNumber.newBuilder().number(3L).build();
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();

    protected static final Key aPrimitiveKey = Key.newBuilder()
            .ed25519(Bytes.wrap("01234567890123456789012345678901"))
            .build();
    protected static final ProtoBytes edKeyAlias = new ProtoBytes(Bytes.wrap(asBytes(Key.PROTOBUF, aPrimitiveKey)));
    protected final AccountID alias =
            AccountID.newBuilder().alias(edKeyAlias.value()).build();

    protected final ServiceEndpoint endpoint1 = V053AddressBookSchema.endpointFor("127.0.0.1", 1234);

    protected final ServiceEndpoint endpoint2 = V053AddressBookSchema.endpointFor("127.0.0.2", 2345);

    protected final ServiceEndpoint endpoint3 = V053AddressBookSchema.endpointFor("test.domain.com", 3456);

    protected final ServiceEndpoint endpoint4 = V053AddressBookSchema.endpointFor("test.domain.com", 2345)
            .copyBuilder()
            .ipAddressV4(endpoint1.ipAddressV4())
            .build();

    protected final ServiceEndpoint endpoint5 = new ServiceEndpoint(Bytes.EMPTY, 2345, null);

    protected final ServiceEndpoint endpoint6 = new ServiceEndpoint(Bytes.EMPTY, 0, null);
    protected final ServiceEndpoint endpoint7 = new ServiceEndpoint(null, 123, null);

    protected final ServiceEndpoint endpoint8 = new ServiceEndpoint(Bytes.wrap("345.0.0.1"), 1234, null);
    protected final ServiceEndpoint endpoint9 = new ServiceEndpoint(Bytes.wrap("1.0.0.0"), 1234, null);

    private final byte[] invalidIPBytes = {49, 46, 48, 46, 48, 46, 48};
    protected final ServiceEndpoint endpoint10 = new ServiceEndpoint(Bytes.wrap(invalidIPBytes), 1234, null);

    private static final Bytes BYTES_1_2_3 = Bytes.wrap(new byte[] {1, 2, 3});

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

    protected void refreshStoresWithCurrentNodeInBothReadableAndWritable() {
        readableNodeState = readableNodeState();
        writableNodeState = writableNodeStateWithOneKey();
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
    protected MapReadableKVState.Builder<EntityNumber, Node> emptyReadableNodeStateBuilder() {
        return MapReadableKVState.builder(NODES_KEY);
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
                deleted,
                key,
                BYTES_1_2_3);
    }

    protected void givenValidNodeWithAdminKey(Key adminKey) {
        node = new Node(
                nodeId.number(),
                accountId,
                "description",
                null,
                null,
                Bytes.wrap(gossipCaCertificate),
                Bytes.wrap(grpcCertificateHash),
                0,
                false,
                adminKey,
                BYTES_1_2_3);
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
                .adminKey(key)
                .tssEncryptionKey(BYTES_1_2_3)
                .build();
    }

    public static List<X509Certificate> generateX509Certificates(final int n) {
        final var randomAddressBook = RandomAddressBookBuilder.create(new Random())
                .withSize(n)
                .withRealKeysEnabled(true)
                .build();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(randomAddressBook.iterator(), 0), false)
                .map(Address::getSigCert)
                .collect(Collectors.toList());
    }
}
