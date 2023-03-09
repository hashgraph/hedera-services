/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import com.hedera.hapi.node.consensus.ConsensusGetTopicInfoResponse;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.pbj.runtime.io.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredPbjTransfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ConsensusGetTopicInfoHandlerTest extends ConsensusHandlerTestBase {
    private ConsensusGetTopicInfoHandler subject;

    @BeforeEach
    void setUp() {
        subject = new ConsensusGetTopicInfoHandler();
    }

    @Test
    void emptyConstructor() {
        assertNotNull(new ConsensusGetTopicInfoHandler());
    }

    @Test
    void extractsHeader() {
        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var header = subject.extractHeader(query);
        assertEquals(query.consensusGetTopicInfo().get().header(), header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = com.hedera.hapi.node.base.ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_FEE)
                .build();
        final var response = subject.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .consensusGetTopicInfo(ConsensusGetTopicInfoResponse.newBuilder().header(responseHeader))
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    void requiresPayment() {
        assertTrue(subject.requiresNodePayment(com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY));
        assertTrue(subject.requiresNodePayment(com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF));
        assertFalse(subject.requiresNodePayment(com.hedera.hapi.node.base.ResponseType.COST_ANSWER));
        assertFalse(subject.requiresNodePayment(com.hedera.hapi.node.base.ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void needsAnswerOnlyCostForCostAnswer() {
        assertFalse(subject.needsAnswerOnlyCost(com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY));
        assertFalse(subject.needsAnswerOnlyCost(com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF));
        assertTrue(subject.needsAnswerOnlyCost(com.hedera.hapi.node.base.ResponseType.COST_ANSWER));
        assertFalse(subject.needsAnswerOnlyCost(com.hedera.hapi.node.base.ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void validatesQueryWhenValidTopic() throws Throwable {
        givenValidTopic();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.validate(query, readableStore);
        assertEquals(com.hedera.hapi.node.base.ResponseCodeEnum.OK, response);
    }

    @Test
    void validatesQueryIfInvalidTopic() throws Throwable {
        readableTopicState.reset();
        final var state =
                MapReadableKVState.<Long, MerkleTopic>builder("TOPICS").build();
        given(readableStates.<Long, MerkleTopic>get(TOPICS)).willReturn(state);
        final var store = new ReadableTopicStore(readableStates);

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.validate(query, store);
        assertEquals(com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID, response);
    }

    @Test
    void validatesQueryIfDeletedTopic() throws Throwable {
        givenValidTopic(autoRenewId.accountNum().get(), true);
        readableTopicState = readableTopicState();
        given(readableStates.<EntityNum, com.hedera.hapi.node.state.consensus.Topic>get(TOPICS))
                .willReturn(readableTopicState);
        readableStore = new ReadableTopicStore(readableStates);

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.validate(query, readableStore);
        assertEquals(com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID, response);
    }

    @Test
    void getsResponseIfFailedResponse() {
        final var responseHeader = com.hedera.hapi.node.base.ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_FEE)
                .build();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.findResponse(query, responseHeader, readableStore, queryContext);
        assertEquals(com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_FEE, response.consensusGetTopicInfo().get().header().nodeTransactionPrecheckCode());
        assertEquals(
                com.hedera.hapi.node.consensus.ConsensusTopicInfo.newBuilder().build(),
                response.consensusGetTopicInfo().get().topicInfo());
    }

    @Test
    void getsResponseIfOkResponse() {
        givenValidTopic();
        given(queryContext.getLedgerId()).willReturn(ledgerId);
        final var responseHeader =
                com.hedera.hapi.node.base.ResponseHeader.newBuilder()
                        .nodeTransactionPrecheckCode(com.hedera.hapi.node.base.ResponseCodeEnum.OK)
                        .build();
        final var expectedInfo = getExpectedInfo();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.findResponse(
                query,
                responseHeader, readableStore, queryContext);
        final var topicInfoResponse = response.consensusGetTopicInfo().get();
        assertEquals(com.hedera.hapi.node.base.ResponseCodeEnum.OK, topicInfoResponse.header().nodeTransactionPrecheckCode());
        assertEquals(expectedInfo, topicInfoResponse.topicInfo());
    }

    private com.hedera.hapi.node.consensus.ConsensusTopicInfo getExpectedInfo() {
        return com.hedera.hapi.node.consensus.ConsensusTopicInfo.newBuilder()
                .memo(topic.memo())
                .adminKey(key)
                .runningHash(Bytes.wrap("runningHash"))
                .sequenceNumber(topic.sequenceNumber())
                .expirationTime(com.hedera.hapi.node.base.Timestamp.newBuilder().seconds(topic.expiry()))
                .submitKey(key)
                .autoRenewAccount(com.hedera.hapi.node.base.AccountID.newBuilder()
                        .accountNum(topic.autoRenewAccountNumber()))
                .autoRenewPeriod(WELL_KNOWN_AUTO_RENEW_PERIOD)
                .ledgerId(ledgerId)
                .build();
    }

    private com.hedera.hapi.node.transaction.Query createGetTopicInfoQuery(final int topicId) {
        final var payment = payerSponsoredPbjTransfer(
                payerIdLiteral,
                COMPLEX_KEY_ACCOUNT_KT, beneficiaryIdStr, paymentAmount);
        final var data = com.hedera.hapi.node.consensus.ConsensusGetTopicInfoQuery.newBuilder()
                .topicID(com.hedera.hapi.node.base.TopicID.newBuilder().topicNum(topicId).build())
                .header(com.hedera.hapi.node.base.QueryHeader.newBuilder().payment(payment).build())
                .build();

        return com.hedera.hapi.node.transaction.Query.newBuilder()
                .consensusGetTopicInfo(data)
                .build();
    }
}
