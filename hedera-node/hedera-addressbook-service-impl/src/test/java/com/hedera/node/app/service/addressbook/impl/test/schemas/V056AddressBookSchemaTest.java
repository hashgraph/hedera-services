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

package com.hedera.node.app.service.addressbook.impl.test.schemas;

import static com.hedera.node.app.service.addressbook.AddressBookHelper.NODES_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.ACCOUNTS_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.FILES_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.endpointFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddress;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.service.addressbook.impl.schemas.V056AddressBookSchema;
import com.hedera.node.app.service.addressbook.impl.test.handlers.AddressBookTestBase;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import com.swirlds.state.test.fixtures.MapReadableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import com.swirlds.state.test.fixtures.MapWritableStates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class V056AddressBookSchemaTest extends AddressBookTestBase {
    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private SemanticVersion version;

    @LoggingSubject
    private V056AddressBookSchema subject;

    private final Map<AccountID, Account> accounts = new HashMap<>();
    private final MapReadableKVState<AccountID, Account> readableAccounts =
            new MapReadableKVState<>(ACCOUNTS_KEY, accounts);

    private final Map<EntityNumber, Node> nodes = new HashMap<>();
    private final MapWritableKVState<EntityNumber, Node> writableNodes = new MapWritableKVState<>(NODES_KEY, nodes);

    private final Map<FileID, File> files = new HashMap<>();
    private final MapReadableKVState<FileID, File> readableFiles = new MapReadableKVState<>(FILES_KEY, files);

    private MapWritableStates writableStates = null;
    private MapReadableStates readableStates = null;

    @BeforeEach
    void setUp() {
        subject = new V056AddressBookSchema();
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate).isEmpty();
    }

    @Test
    void migrateAsExpected() {
        setupMigrationContext();
        setupNodeDetailsFile();
        setupReadableStates();

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.infoLogs()).contains("Migrated 3 nodes from address book");
        assertWritableNodes();
    }

    @Test
    void migrateFailedWithNotGoodNodeDetailFile() {
        setupMigrationContext();
        setupNotGoodNodeDetailFile();
        setupReadableStates();

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.errorLogs()).hasSize(1);
        assertThat(logCaptor.errorLogs()).matches(logs -> logs.getFirst()
                .contains("Can not parse file 102 com.hedera.pbj.runtime.ParseException: "));
        assertThat(logCaptor.infoLogs()).contains("Migrated 3 nodes from address book");

        assertNotGoodNodeDetailFile();
    }

    @Test
    void failedNullNetworkInfo() {
        given(migrationContext.previousVersion()).willReturn(version);
        given(migrationContext.genesisNetworkInfo()).willReturn(null);
        assertThatCode(() -> subject.migrate(migrationContext))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Genesis network info is not found");
    }

    private void assertWritableNodes() {
        assertEquals(3, writableNodes.size());
        assertEquals(
                Node.newBuilder()
                        .nodeId(1)
                        .accountId(payerId)
                        .description("node2")
                        .gossipEndpoint(List.of(endpointFor("23.45.34.245", 22), endpointFor("127.0.0.1", 123)))
                        .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                        .weight(0)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(1).build()));
        assertEquals(
                Node.newBuilder()
                        .nodeId(2)
                        .accountId(accountId)
                        .description("node3")
                        .gossipEndpoint(List.of(endpointFor("23.45.34.240", 23), endpointFor("127.0.0.2", 123)))
                        .gossipCaCertificate(Bytes.wrap(grpcCertificateHash))
                        .weight(1)
                        .adminKey(anotherKey)
                        .grpcCertificateHash(Bytes.wrap("grpcCertificateHash1"))
                        .serviceEndpoint(List.of(endpointFor("127.1.0.1", 1234), endpointFor("127.1.0.2", 1234)))
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(2).build()));
        assertEquals(
                Node.newBuilder()
                        .nodeId(3)
                        .accountId(accountId)
                        .description("node4")
                        .gossipEndpoint(List.of(endpointFor("23.45.34.243", 45), endpointFor("127.0.0.3", 124)))
                        .gossipCaCertificate(Bytes.wrap(grpcCertificateHash))
                        .weight(10)
                        .adminKey(anotherKey)
                        .grpcCertificateHash(Bytes.wrap("grpcCertificateHash2"))
                        .serviceEndpoint(
                                List.of(endpointFor("domain.test1.com", 1234), endpointFor("domain.test2.com", 5678)))
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(3).build()));
    }

    void assertNotGoodNodeDetailFile() {
        assertEquals(3, writableNodes.size());
        assertEquals(
                Node.newBuilder()
                        .nodeId(1)
                        .accountId(payerId)
                        .description("node2")
                        .gossipEndpoint(List.of(endpointFor("23.45.34.245", 22), endpointFor("127.0.0.1", 123)))
                        .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                        .weight(0)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(1).build()));
        assertEquals(
                Node.newBuilder()
                        .nodeId(2)
                        .accountId(accountId)
                        .description("node3")
                        .gossipEndpoint(List.of(endpointFor("23.45.34.240", 23), endpointFor("127.0.0.2", 123)))
                        .gossipCaCertificate(Bytes.wrap(grpcCertificateHash))
                        .weight(1)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(2).build()));
        assertEquals(
                Node.newBuilder()
                        .nodeId(3)
                        .accountId(accountId)
                        .description("node4")
                        .gossipEndpoint(List.of(endpointFor("23.45.34.243", 45), endpointFor("127.0.0.3", 124)))
                        .gossipCaCertificate(Bytes.wrap(grpcCertificateHash))
                        .weight(10)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(3).build()));
    }

    private void setupMigrationContext() {
        given(migrationContext.previousVersion()).willReturn(version);

        final var nodeInfo1 = new NodeInfoImpl(
                1,
                payerId,
                0,
                List.of(endpointFor("23.45.34.245", 22), endpointFor("127.0.0.1", 123)),
                Bytes.wrap(gossipCaCertificate));
        final var nodeInfo2 = new NodeInfoImpl(
                2,
                accountId,
                1,
                List.of(endpointFor("23.45.34.240", 23), endpointFor("127.0.0.2", 123)),
                Bytes.wrap(grpcCertificateHash));
        final var nodeInfo3 = new NodeInfoImpl(
                3,
                accountId,
                10,
                List.of(endpointFor("23.45.34.243", 45), endpointFor("127.0.0.3", 124)),
                Bytes.wrap(grpcCertificateHash));
        given(networkInfo.addressBook()).willReturn(List.of(nodeInfo1, nodeInfo2, nodeInfo3));
        given(migrationContext.genesisNetworkInfo()).willReturn(networkInfo);

        writableStates = MapWritableStates.builder().state(writableNodes).build();
        given(migrationContext.newStates()).willReturn(writableStates);

        setupConfig();
        accounts.put(
                AccountID.newBuilder().accountNum(55).build(),
                Account.newBuilder().key(anotherKey).build());
    }

    private void setupConfig() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("accounts.addressBookAdmin", "55")
                .withValue("files.nodeDetails", "102")
                .getOrCreateConfig();
        given(migrationContext.configuration()).willReturn(config);
    }

    private void setupNodeDetailsFile() {
        final var nodeDetails = new ArrayList<NodeAddress>();
        nodeDetails.addAll(List.of(
                NodeAddress.newBuilder()
                        .nodeId(2)
                        .nodeCertHash(Bytes.wrap("grpcCertificateHash1"))
                        .serviceEndpoint(List.of(endpointFor("127.1.0.1", 1234), endpointFor("127.1.0.2", 1234)))
                        .build(),
                NodeAddress.newBuilder()
                        .nodeId(3)
                        .nodeCertHash(Bytes.wrap("grpcCertificateHash2"))
                        .serviceEndpoint(
                                List.of(endpointFor("domain.test1.com", 1234), endpointFor("domain.test2.com", 5678)))
                        .build()));
        final Bytes fileContent = NodeAddressBook.PROTOBUF.toBytes(
                NodeAddressBook.newBuilder().nodeAddress(nodeDetails).build());

        files.put(
                FileID.newBuilder().fileNum(102).build(),
                File.newBuilder().contents(fileContent).build());
    }

    private void setupNotGoodNodeDetailFile() {
        files.put(
                FileID.newBuilder().fileNum(102).build(),
                File.newBuilder().contents(Bytes.wrap("NotGoodNodeDetailFile")).build());
    }

    private void setupReadableStates() {
        readableStates = MapReadableStates.builder()
                .state(readableAccounts)
                .state(readableFiles)
                .build();
        given(migrationContext.previousStates()).willReturn(readableStates);
    }
}
