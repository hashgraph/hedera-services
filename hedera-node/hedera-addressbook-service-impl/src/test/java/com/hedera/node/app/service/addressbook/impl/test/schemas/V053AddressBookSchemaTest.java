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
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.endpointFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema;
import com.hedera.node.app.service.addressbook.impl.test.handlers.AddressBookTestBase;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class V053AddressBookSchemaTest extends AddressBookTestBase {
    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private NetworkInfo networkInfo;

    @LoggingSubject
    private V053AddressBookSchema subject;

    private final Map<AccountID, Account> accounts = new HashMap<>();
    private final MapWritableKVState<AccountID, Account> writableAccounts =
            new MapWritableKVState<>(ACCOUNTS_KEY, accounts);

    private final Map<EntityNumber, Node> nodes = new HashMap<>();
    private final MapWritableKVState<EntityNumber, Node> writableNodes = new MapWritableKVState<>(NODES_KEY, nodes);

    private MapWritableStates writableStates = null;

    @BeforeEach
    void setUp() {
        subject = new V053AddressBookSchema();
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate).hasSize(1);
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(NODES_KEY, iter.next());
    }

    @Test
    void migrateAsExpected() {
        setupMigrationContext();

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.infoLogs()).contains("AccountStore is not found, can be ignored.");
        assertThat(logCaptor.infoLogs()).contains("Migrated 2 nodes from address book");
    }

    @Test
    void migrateAsExpected2() {
        setupMigrationContext2();

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.infoLogs()).contains("Migrated 2 nodes from address book");
        assertEquals(
                Node.newBuilder()
                        .nodeId(1)
                        .accountId(payerId)
                        .description("memo1")
                        .gossipEndpoint(List.of(endpointFor("127.0.0.1", 123), endpointFor("23.45.34.245", 22)))
                        .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                        .weight(0)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(1).build()));
        assertEquals(
                Node.newBuilder()
                        .nodeId(2)
                        .accountId(accountId)
                        .description("memo2")
                        .gossipEndpoint(List.of(endpointFor("127.0.0.2", 123), endpointFor("23.45.34.240", 23)))
                        .gossipCaCertificate(Bytes.wrap(grpcCertificateHash))
                        .weight(1)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(2).build()));
    }

    private void setupMigrationContext() {
        writableStates = MapWritableStates.builder().state(writableNodes).build();
        given(migrationContext.newStates()).willReturn(writableStates);

        final var nodeInfo1 = new NodeInfoImpl(
                1,
                payerId,
                0,
                "23.45.34.245",
                22,
                "127.0.0.1",
                123,
                "pubKey1",
                "memo1",
                Bytes.wrap(gossipCaCertificate),
                "memo1");
        final var nodeInfo2 = new NodeInfoImpl(
                2,
                accountId,
                1,
                "23.45.34.240",
                23,
                "127.0.0.2",
                123,
                "pubKey2",
                "memo2",
                Bytes.wrap(grpcCertificateHash),
                "memo2");
        given(networkInfo.addressBook()).willReturn(List.of(nodeInfo1, nodeInfo2));
        given(migrationContext.networkInfo()).willReturn(networkInfo);
        final var config = HederaTestConfigBuilder.create()
                .withValue("bootstrap.genesisPublicKey", defauleAdminKeyBytes)
                .getOrCreateConfig();
        given(migrationContext.configuration()).willReturn(config);
    }

    private void setupMigrationContext2() {
        setupMigrationContext();
        accounts.put(
                AccountID.newBuilder().accountNum(55).build(),
                Account.newBuilder().key(anotherKey).build());
        writableStates = MapWritableStates.builder()
                .state(writableAccounts)
                .state(writableNodes)
                .build();
        given(migrationContext.newStates()).willReturn(writableStates);

        final var config = HederaTestConfigBuilder.create()
                .withValue("bootstrap.genesisPublicKey", defauleAdminKeyBytes)
                .withValue("accounts.addressBookAdmin", "55")
                .getOrCreateConfig();
        given(migrationContext.configuration()).willReturn(config);
    }
}
