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
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.addressbook.NodeGetInfoQuery;
import com.hedera.hapi.node.addressbook.NodeGetInfoResponse;
import com.hedera.hapi.node.addressbook.NodeInfo;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl;
import com.hedera.node.app.service.addressbook.impl.handlers.NodeGetInfoHandler;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.converter.BytesConverter;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.swirlds.platform.test.fixtures.state.MapReadableKVState;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeGetInfoHandlerTest extends AddressBookTestBase {

    @Mock
    private QueryContext context;

    private NodeGetInfoHandler subject;

    @Mock
    private AssetsLoader assetsLoader;

    @Mock
    private ExchangeRateInfo exchangeRateInfo;

    @BeforeEach
    void setUp() {
        subject = new NodeGetInfoHandler(assetsLoader);
    }

    @Test
    @DisplayName("Query header is extracted correctly")
    void extractsHeader() {
        final var query = createGetNodeInfoQuery(nodeId.number());
        final var header = subject.extractHeader(query);
        final var op = query.nodeGetInfoOrThrow();
        assertEquals(op.header(), header);
    }

    @Test
    @DisplayName("Check empty query response is created correctly")
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();
        final var response = subject.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .nodeGetInfo(NodeGetInfoResponse.newBuilder().header(responseHeader))
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    @DisplayName("Check node payment requirement is correct with each response type")
    void requiresPayment() {
        assertTrue(subject.requiresNodePayment(ResponseType.ANSWER_ONLY));
        assertTrue(subject.requiresNodePayment(ResponseType.ANSWER_STATE_PROOF));
        assertFalse(subject.requiresNodePayment(ResponseType.COST_ANSWER));
        assertFalse(subject.requiresNodePayment(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    @DisplayName("Check Answer Only Cost is correct with each response type")
    void needsAnswerOnlyCostForCostAnswer() {
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.ANSWER_ONLY));
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.ANSWER_STATE_PROOF));
        assertTrue(subject.needsAnswerOnlyCost(ResponseType.COST_ANSWER));
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    @DisplayName("Validate query is good")
    void validatesQueryWhenValidNode() {
        givenValidNode();

        final var query = createGetNodeInfoQuery(nodeId.number());
        given(context.query()).willReturn(query);
        given(context.createStore(ReadableNodeStore.class)).willReturn(readableStore);

        assertThatCode(() -> subject.validate(context)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Node Id is needed during validate")
    void validatesQueryIfInvalidNode() {
        readableNodeState.reset();
        final var state =
                MapReadableKVState.<EntityNumber, Node>builder(NODES_KEY).build();
        given(readableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(state);
        final var store = new ReadableNodeStoreImpl(readableStates);

        final var query = createGetNodeInfoQuery(-5L);
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableNodeStore.class)).thenReturn(store);

        assertThatThrownBy(() -> subject.validate(context))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_NODE_ID));
    }

    @Test
    @DisplayName("Node Id in transaction is needed during validate")
    void validatesQueryIfInvalidNodeInTrans() throws Throwable {
        readableNodeState.reset();
        final var state =
                MapReadableKVState.<EntityNumber, Node>builder(NODES_KEY).build();
        given(readableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(state);
        final var store = new ReadableNodeStoreImpl(readableStates);

        final var query = createEmptyGetNodeInfoQuery();
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableNodeStore.class)).thenReturn(store);

        assertThatThrownBy(() -> subject.validate(context))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_NODE_ID));
    }

    @Test
    @DisplayName("deleted node is allowed")
    void validatesQueryIfDeletedNode() throws Throwable {
        givenValidNode(true);
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedInfo = getExpectedInfo(true);

        readableNodeState = readableNodeState();
        given(readableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(readableNodeState);
        readableStore = new ReadableNodeStoreImpl(readableStates);

        final var config =
                HederaTestConfigBuilder.create().withValue("ledger.id", "0x03").getOrCreateConfig();
        given(context.configuration()).willReturn(config);

        final var query = createGetNodeInfoQuery(nodeId.number());
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableNodeStore.class)).thenReturn(readableStore);

        final var response = subject.findResponse(context, responseHeader);
        final var nodeInfoResponse = response.nodeGetInfoOrThrow();
        assertEquals(ResponseCodeEnum.OK, nodeInfoResponse.header().nodeTransactionPrecheckCode());
        assertEquals(expectedInfo, nodeInfoResponse.nodeInfo());
    }

    @Test
    @DisplayName("OK response is correctly handled in findResponse")
    void getsResponseIfOkResponse() {
        givenValidNode();
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedInfo = getExpectedInfo(false);

        final var query = createGetNodeInfoQuery(nodeId.number());
        when(context.query()).thenReturn(query);
        when(context.createStore(ReadableNodeStore.class)).thenReturn(readableStore);

        final var config =
                HederaTestConfigBuilder.create().withValue("ledger.id", "0x03").getOrCreateConfig();
        given(context.configuration()).willReturn(config);

        final var response = subject.findResponse(context, responseHeader);
        final var nodeInfoResponse = response.nodeGetInfoOrThrow();
        assertEquals(ResponseCodeEnum.OK, nodeInfoResponse.header().nodeTransactionPrecheckCode());
        assertEquals(expectedInfo, nodeInfoResponse.nodeInfo());
    }

    @Test
    @DisplayName("check that fees are 1 for delete node trx")
    void testCalculateFeesInvocations() throws IOException {
        final var feeCtx = mock(QueryContext.class);
        final ExchangeRate exchangeRate = new ExchangeRate(1, 2, TimestampSeconds.DEFAULT);
        Map<SubType, BigDecimal> subTypeMap = new HashMap<>();
        subTypeMap.put(SubType.DEFAULT, BigDecimal.valueOf(0.001));
        Map<HederaFunctionality, Map<SubType, BigDecimal>> map = new HashMap<>();
        map.put(HederaFunctionality.NodeGetInfo, subTypeMap);

        given(feeCtx.exchangeRateInfo()).willReturn(exchangeRateInfo);
        given(exchangeRateInfo.activeRate(any())).willReturn(exchangeRate);
        given(assetsLoader.loadCanonicalPrices()).willReturn(map);

        assertThat(subject.computeFees(feeCtx)).isEqualTo(new Fees(0, 5000000, 0));
    }

    private NodeInfo getExpectedInfo(boolean deleted) {
        return NodeInfo.newBuilder()
                .nodeId(nodeId.number())
                .accountId(accountId)
                .description("description")
                .gossipEndpoint((List<ServiceEndpoint>) null)
                .serviceEndpoint((List<ServiceEndpoint>) null)
                .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                .grpcCertificateHash(Bytes.wrap(grpcCertificateHash))
                .weight(0)
                .ledgerId(new BytesConverter().convert("0x03"))
                .deleted(deleted)
                .build();
    }

    private Query createGetNodeInfoQuery(final long nodeId) {
        final var data = NodeGetInfoQuery.newBuilder()
                .nodeId(nodeId)
                .header(QueryHeader.newBuilder().build())
                .build();

        return Query.newBuilder().nodeGetInfo(data).build();
    }

    private Query createEmptyGetNodeInfoQuery() {
        final var data = NodeGetInfoQuery.newBuilder()
                .header(QueryHeader.newBuilder().build())
                .build();

        return Query.newBuilder().nodeGetInfo(data).build();
    }
}
