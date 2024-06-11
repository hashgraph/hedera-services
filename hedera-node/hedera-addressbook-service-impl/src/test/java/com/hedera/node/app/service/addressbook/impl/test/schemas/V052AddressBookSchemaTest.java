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

import static com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl.NODES_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.service.addressbook.impl.schemas.V052AddressBookSchema;
import com.hedera.node.app.service.addressbook.impl.test.handlers.AddressBookTestBase;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.spi.info.NetworkInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class V052AddressBookSchemaTest extends AddressBookTestBase {
    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private WritableStates writableStates;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private WritableKVState writableKVState;

    @LoggingSubject
    private V052AddressBookSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V052AddressBookSchema();
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
        final var config =
                HederaTestConfigBuilder.create().withValue("nodes.maxNumber", 2).getOrCreateConfig();
        given(migrationContext.configuration()).willReturn(config);

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.infoLogs()).contains("Migrated 2 nodes from address book");
    }

    @Test
    void migrateLogWarn() {
        setupMigrationContext();
        final var config =
                HederaTestConfigBuilder.create().withValue("nodes.maxNumber", 1).getOrCreateConfig();
        given(migrationContext.configuration()).willReturn(config);

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.warnLogs()).contains("Address book contains more nodes 2 than the migrated count 1");
        assertThat(logCaptor.infoLogs()).contains("Migrated 1 nodes from address book");
    }

    private void setupMigrationContext() {
        given(migrationContext.newStates()).willReturn(writableStates);
        given(writableStates.get(NODES_KEY)).willReturn(writableKVState);

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
                Bytes.wrap(gossipCaCertificate));
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
                Bytes.wrap(grpcCertificateHash));
        given(networkInfo.addressBook()).willReturn(List.of(nodeInfo1, nodeInfo2));
        given(migrationContext.networkInfo()).willReturn(networkInfo);
    }
}
