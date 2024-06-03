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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_GOSSIP_CAE_CERTIFICATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SERVICE_ENDPOINT;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.addressbook.NodeCreateTransactionBody;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.addressbook.impl.handlers.NodeCreateHandler;
import com.hedera.node.app.service.addressbook.impl.records.NodeCreateRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeCreateHandlerTest extends AddressBookTestBase {

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock
    private NodeCreateRecordBuilder recordBuilder;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeAccumulator feeAccumulator;

    @Mock
    private StoreMetricsService storeMetricsService;

    private TransactionBody txn;
    private NodeCreateHandler subject;

    @BeforeEach
    void setUp() {
        subject = new NodeCreateHandler();
    }

    @Test
    @DisplayName("pureChecks fail when accountId is null")
    void accountIdCannotNull() {
        txn = new NodeCreateBuilder().build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertThat(INVALID_NODE_ACCOUNT_ID).isEqualTo(msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail when accountId not set")
    void accountIdNeedSet() {
        txn = new NodeCreateBuilder().withAccountId(AccountID.DEFAULT).build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertThat(INVALID_NODE_ACCOUNT_ID).isEqualTo(msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail when accountId is alias")
    void accountIdCannotAlias() {
        txn = new NodeCreateBuilder().withAccountId(alias).build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertThat(INVALID_NODE_ACCOUNT_ID).isEqualTo(msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail when gossip_endpoint not specified")
    void gossipEndpointNeedSet() {
        txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of())
                .build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertThat(INVALID_GOSSIP_ENDPOINT).isEqualTo(msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail when service_endpoint not specified")
    void serviceEndpointNeedSet() {
        txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1))
                .withServiceEndpoint(List.of())
                .build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertThat(INVALID_SERVICE_ENDPOINT).isEqualTo(msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks fail when gossipCaCertificate not specified")
    void gossipCaCertificateNeedSet() {
        txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1))
                .withServiceEndpoint(List.of(endpoint2))
                .build();
        final var msg = assertThrows(PreCheckException.class, () -> subject.pureChecks(txn));
        assertThat(INVALID_GOSSIP_CAE_CERTIFICATE).isEqualTo(msg.responseCode());
    }

    @Test
    @DisplayName("pureChecks succeeds when expected attributes are specified")
    void pureCheckPass() {
        txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1))
                .withServiceEndpoint(List.of(endpoint2))
                .withGossipCaCertificate(Bytes.wrap("cert"))
                .build();
        assertDoesNotThrow(() -> subject.pureChecks(txn));
    }

    @Test
    void accountIdMustInState() throws PreCheckException {
        txn = new NodeCreateBuilder().withAccountId(accountId).build();
        given(accountStore.contains(accountId)).willReturn(false);
        final var payerKey = mockPayerLookup(A_COMPLEX_KEY, payerId, accountStore);
        refreshStoresWithCurrentNodeInReadable();
        final var context = new FakePreHandleContext(accountStore, txn);
        context.registerStore(ReadableNodeStore.class, readableStore);

        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_NODE_ACCOUNT_ID);
    }

    @Test
    void preHandlePass() throws PreCheckException {
        txn = new NodeCreateBuilder().withAccountId(accountId).build();
        given(accountStore.contains(accountId)).willReturn(true);
        final var payerKey = mockPayerLookup(A_COMPLEX_KEY, payerId, accountStore);
        refreshStoresWithCurrentNodeInReadable();
        final var context = new FakePreHandleContext(accountStore, txn);
        context.registerStore(ReadableNodeStore.class, readableStore);

        assertDoesNotThrow(() -> subject.preHandle(context));
    }
    @Test
    void failsWhenMaxNodesExceeds() {
        final var txn = new NodeCreateBuilder().withAccountId(accountId).build();
        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxNumber", 1L)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        assertEquals(1, writableStore.sizeOfState());
        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.MAX_NODE_HAVE_BEEN_CREATED, msg.getStatus());
        assertEquals(0, writableStore.modifiedNodes().size());
    }

    @Test
    void failsWhenDescriptionTooLarge() {
        final var txn = new NodeCreateBuilder().withDescription("Description").build();
        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.nodeMaxDescriptionUtf8Bytes", 10)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_NODE_DESCRIPTION, msg.getStatus());
    }

    @Test
    void failsWhenDescriptionContainZeroByte() {
        final var txn = new NodeCreateBuilder().withDescription("Des\0cription").build();
        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.nodeMaxDescriptionUtf8Bytes", 12)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_NODE_DESCRIPTION, msg.getStatus());
    }

    @Test
    void failsWhenGossipEndpointTooLarge() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint2, endpoint3))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenGossipEndpointNull() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(null)
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenGossipEndpointEmpty() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of())
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenGossipEndpointTooSmall() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenGossipEndpointHaveIPAndFQDN() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint4))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN, msg.getStatus());
    }

    @Test
    void failsWhenEndpointHaveEmptyIPAndFQDN() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint5))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenEndpointHaveZeroIp() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint6))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenServiceEndpointTooLarge() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint2))
                .withServiceEndpoint(List.of(endpoint1, endpoint2, endpoint3))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxServiceEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_SERVICE_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenServiceEndpointNull() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint2))
                .withServiceEndpoint(null)
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxServiceEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_SERVICE_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenServiceEndpointEmpty() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint2))
                .withServiceEndpoint(List.of())
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxServiceEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_SERVICE_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenEndpointHaveIPAndFQDN() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint2))
                .withServiceEndpoint(List.of(endpoint1, endpoint4))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .withValue("nodes.maxServiceEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT, msg.getStatus());
    }

    @Test
    void failsWhenEndpointFQDNTooLarge() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint2))
                .withServiceEndpoint(List.of(endpoint1, endpoint3))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .withValue("nodes.maxServiceEndpoint", 2)
                .withValue("nodes.maxFqdnSize", 4)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.FQDN_SIZE_TOO_LARGE, msg.getStatus());
    }


    @Test
    void failsWhenGossipCaCertificateEmptu() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withGossipEndpoint(List.of(endpoint1, endpoint2))
                .withServiceEndpoint(List.of(endpoint1, endpoint3))
                .build();

        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.maxGossipEndpoint", 2)
                .withValue("nodes.maxServiceEndpoint", 2)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.INVALID_GOSSIP_CAE_CERTIFICATE, msg.getStatus());
    }

    @Test
    void hanldeWorkAsExpected() {
        final var txn = new NodeCreateBuilder()
                .withAccountId(accountId)
                .withDescription("Description")
                .withGossipEndpoint(List.of(endpoint1, endpoint2))
                .withServiceEndpoint(List.of(endpoint1, endpoint3))
                .withGossipCaCertificate(Bytes.wrap("cert"))
                .withGrpcCertificateHash(Bytes.wrap("hash"))
                .build();
        given(handleContext.body()).willReturn(txn);
        refreshStoresWithCurrentNodeInWritable();
        final var config = HederaTestConfigBuilder.create()
                .withValue("nodes.nodeMaxDescriptionUtf8Bytes", 12)
                .withValue("nodes.maxGossipEndpoint", 4)
                .withValue("nodes.maxServiceEndpoint", 3)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableNodeStore.class)).willReturn(writableStore);
        given(handleContext.newEntityNum()).willReturn(2L);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);

        assertDoesNotThrow(() -> subject.handle(handleContext));
        final var createdNode = writableStore.get(2L);
        assertNotNull(createdNode);
        verify(recordBuilder).nodeID(2L);
        assertEquals(2, createdNode.nodeId());
        assertEquals("Description", createdNode.description());
        assertArrayEquals(
                (List.of(endpoint1, endpoint2)).toArray(),
                createdNode.gossipEndpoint().toArray());
        assertArrayEquals(
                (List.of(endpoint1, endpoint3)).toArray(),
                createdNode.serviceEndpoint().toArray());
        assertArrayEquals("cert".getBytes(), createdNode.gossipCaCertificate().toByteArray());
        assertArrayEquals("hash".getBytes(), createdNode.grpcCertificateHash().toByteArray());
    }

    private class NodeCreateBuilder {
        private AccountID accountId = null;
        private String description = null;
        private List<ServiceEndpoint> gossipEndpoint = null;

        private List<ServiceEndpoint> serviceEndpoint = null;

        private Bytes gossipCaCertificate = null;

        private Bytes grpcCertificateHash = null;

        private NodeCreateBuilder() {}

        public TransactionBody build() {
            final var txnId = TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp);
            final var txnBody = NodeCreateTransactionBody.newBuilder();
            if (accountId != null) {
                txnBody.accountId(accountId);
            }
            if (description != null) {
                txnBody.description(description);
            }
            if (gossipEndpoint != null) {
                txnBody.gossipEndpoint(gossipEndpoint);
            }
            if (serviceEndpoint != null) {
                txnBody.serviceEndpoint(serviceEndpoint);
            }
            if (gossipCaCertificate != null) {
                txnBody.gossipCaCertificate(gossipCaCertificate);
            }
            if (grpcCertificateHash != null) {
                txnBody.grpcCertificateHash(grpcCertificateHash);
            }

            return TransactionBody.newBuilder()
                    .transactionID(txnId)
                    .nodeCreate(txnBody.build())
                    .build();
        }

        public NodeCreateBuilder withAccountId(final AccountID accountId) {
            this.accountId = accountId;
            return this;
        }

        public NodeCreateBuilder withDescription(final String description) {
            this.description = description;
            return this;
        }

        public NodeCreateBuilder withGossipEndpoint(final List<ServiceEndpoint> gossipEndpoint) {
            this.gossipEndpoint = gossipEndpoint;
            return this;
        }

        public NodeCreateBuilder withServiceEndpoint(final List<ServiceEndpoint> serviceEndpoint) {
            this.serviceEndpoint = serviceEndpoint;
            return this;
        }

        public NodeCreateBuilder withGossipCaCertificate(final Bytes gossipCaCertificate) {
            this.gossipCaCertificate = gossipCaCertificate;
            return this;
        }

        public NodeCreateBuilder withGrpcCertificateHash(final Bytes grpcCertificateHash) {
            this.grpcCertificateHash = grpcCertificateHash;
            return this;
        }
    }
}
