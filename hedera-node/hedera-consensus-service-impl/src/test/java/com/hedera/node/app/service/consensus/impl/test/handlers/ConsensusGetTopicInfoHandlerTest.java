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

import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredPbjTransfer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.consensus.ConsensusGetTopicInfoQuery;
import com.hedera.hapi.node.consensus.ConsensusGetTopicInfoResponse;
import com.hedera.hapi.node.consensus.ConsensusTopicInfo;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusGetTopicInfoHandlerTest extends ConsensusHandlerTestBase {

    @Mock
    private NetworkInfo networkInfo;

    private ConsensusGetTopicInfoHandler subject;

    @BeforeEach
    void setUp() {
        subject = new ConsensusGetTopicInfoHandler(networkInfo);
    }

    @Test
    void extractsHeader() {
        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var header = subject.extractHeader(query);
        final var op = query.consensusGetTopicInfoOrThrow();
        assertEquals(op.header(), header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();
        final var response = subject.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .consensusGetTopicInfo(
                        ConsensusGetTopicInfoResponse.newBuilder().header(responseHeader))
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    void requiresPayment() {
        assertTrue(subject.requiresNodePayment(ResponseType.ANSWER_ONLY));
        assertTrue(subject.requiresNodePayment(ResponseType.ANSWER_STATE_PROOF));
        assertFalse(subject.requiresNodePayment(ResponseType.COST_ANSWER));
        assertFalse(subject.requiresNodePayment(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void needsAnswerOnlyCostForCostAnswer() {
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.ANSWER_ONLY));
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.ANSWER_STATE_PROOF));
        assertTrue(subject.needsAnswerOnlyCost(ResponseType.COST_ANSWER));
        assertFalse(subject.needsAnswerOnlyCost(ResponseType.COST_ANSWER_STATE_PROOF));
    }

    @Test
    void validatesQueryWhenValidTopic() throws Throwable {
        givenValidTopic();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.validate(query, readableStore);
        assertEquals(ResponseCodeEnum.OK, response);
    }

    @Test
    void validatesQueryIfInvalidTopic() throws Throwable {
        readableTopicState.reset();
        final var state =
                MapReadableKVState.<Long, MerkleTopic>builder("TOPICS").build();
        given(readableStates.<Long, MerkleTopic>get(TOPICS)).willReturn(state);
        final var store = new ReadableTopicStore(readableStates);

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        assertThrowsPreCheck(() -> subject.validate(query, store), ResponseCodeEnum.INVALID_TOPIC_ID);
    }

    @Test
    void validatesQueryIfDeletedTopic() throws Throwable {
        givenValidTopic(autoRenewId.accountNum(), true);
        readableTopicState = readableTopicState();
        given(readableStates.<EntityNum, Topic>get(TOPICS)).willReturn(readableTopicState);
        readableStore = new ReadableTopicStore(readableStates);

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.validate(query, readableStore);
        assertEquals(ResponseCodeEnum.INVALID_TOPIC_ID, response);
    }

    @Test
    void getsResponseIfFailedResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.FAIL_FEE)
                .build();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.findResponse(query, responseHeader, readableStore);
        final var op = response.consensusGetTopicInfoOrThrow();
        assertEquals(ResponseCodeEnum.FAIL_FEE, op.header().nodeTransactionPrecheckCode());
        assertNull(op.topicInfo());
    }

    @Test
    void getsResponseIfOkResponse() {
        givenValidTopic();
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        final var responseHeader = ResponseHeader.newBuilder()
                .nodeTransactionPrecheckCode(ResponseCodeEnum.OK)
                .build();
        final var expectedInfo = getExpectedInfo();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.findResponse(query, responseHeader, readableStore);
        final var topicInfoResponse = response.consensusGetTopicInfoOrThrow();
        assertEquals(ResponseCodeEnum.OK, topicInfoResponse.header().nodeTransactionPrecheckCode());
        assertEquals(expectedInfo, topicInfoResponse.topicInfo());
    }

    private ConsensusTopicInfo getExpectedInfo() {
        return ConsensusTopicInfo.newBuilder()
                .memo(topic.memo())
                .adminKey(key)
                .runningHash(Bytes.wrap("runningHash"))
                .sequenceNumber(topic.sequenceNumber())
                .expirationTime(Timestamp.newBuilder().seconds(topic.expiry()))
                .submitKey(key)
                .autoRenewAccount(AccountID.newBuilder().accountNum(topic.autoRenewAccountNumber()))
                .autoRenewPeriod(WELL_KNOWN_AUTO_RENEW_PERIOD)
                .ledgerId(ledgerId)
                .build();
    }

    private Query createGetTopicInfoQuery(final int topicId) {
        final var payment =
                payerSponsoredPbjTransfer(payerIdLiteral, COMPLEX_KEY_ACCOUNT_KT, beneficiaryIdStr, paymentAmount);
        final var data = ConsensusGetTopicInfoQuery.newBuilder()
                .topicID(TopicID.newBuilder().topicNum(topicId).build())
                .header(QueryHeader.newBuilder().payment(payment).build())
                .build();

        return Query.newBuilder().consensusGetTopicInfo(data).build();
    }
}
