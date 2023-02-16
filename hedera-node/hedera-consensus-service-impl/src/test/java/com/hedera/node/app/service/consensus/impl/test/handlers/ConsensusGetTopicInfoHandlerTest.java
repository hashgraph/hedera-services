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

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusGetTopicInfoHandlerTest extends ConsensusHandlerTestBase {
    private ConsensusGetTopicInfoHandler subject;

    @BeforeEach
    void setUp() {
        subject = new ConsensusGetTopicInfoHandler();
    }

    @Test
    void extractsHeader() throws Throwable {
        final var query = createGetTopicInfoQuery(topicNum.intValue());
        final var header = subject.extractHeader(query);
        assertEquals(query.getConsensusGetTopicInfo().getHeader(), header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .setNodeTransactionPrecheckCode(FAIL_FEE)
                .build();
        final var response = subject.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .setConsensusGetTopicInfo(ConsensusGetTopicInfoResponse.newBuilder()
                        .setHeader(responseHeader)
                        .build())
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    void requiresPayment() {
        assertTrue(subject.requiresNodePayment(ANSWER_ONLY));
        assertTrue(subject.requiresNodePayment(ANSWER_STATE_PROOF));
        assertFalse(subject.requiresNodePayment(COST_ANSWER));
        assertFalse(subject.requiresNodePayment(COST_ANSWER_STATE_PROOF));
    }

    @Test
    void needsAnswerOnlyCostForCostAnswer() {
        assertFalse(subject.needsAnswerOnlyCost(ANSWER_ONLY));
        assertFalse(subject.needsAnswerOnlyCost(ANSWER_STATE_PROOF));
        assertTrue(subject.needsAnswerOnlyCost(COST_ANSWER));
        assertFalse(subject.needsAnswerOnlyCost(COST_ANSWER_STATE_PROOF));
    }

    @Test
    void validatesQueryWhenValidTopic() throws Throwable {
        givenValidTopic();

        final var query = createGetTopicInfoQuery(topicNum.intValue());
        final var response = subject.validate(query, store);
        assertEquals(OK, response);
    }

    @Test
    void validatesQueryIfInvalidTopic() throws Throwable {
        given(topics.get(topicNum)).willReturn(null);

        final var query = createGetTopicInfoQuery(topicNum.intValue());
        final var response = subject.validate(query, store);
        assertEquals(INVALID_TOPIC_ID, response);
    }

    @Test
    void validatesQueryIfDeletedTopic() throws Throwable {
        givenValidTopic();
        given(topic.isDeleted()).willReturn(true);

        final var query = createGetTopicInfoQuery(topicNum.intValue());
        final var response = subject.validate(query, store);
        assertEquals(INVALID_TOPIC_ID, response);
    }

    private void givenValidTopic() {
        given(topics.get(topicNum)).willReturn(topic);
        given(topic.getMemo()).willReturn("topic memo");
        given(topic.getAdminKey()).willReturn((JKey) adminKey);
        given(topic.getAutoRenewDurationSeconds()).willReturn(100L);
        given(topic.getAutoRenewAccountId()).willReturn(EntityId.fromGrpcAccountId(autoRenewId));
        given(topic.getExpirationTimestamp()).willReturn(RichInstant.MISSING_INSTANT);
        given(topic.getSequenceNumber()).willReturn(1L);
        given(topic.getRunningHash()).willReturn(new byte[48]);
        given(topic.getKey()).willReturn(EntityNum.fromLong(topicNum));
        given(topic.isDeleted()).willReturn(false);
    }

    private Query createGetTopicInfoQuery(int topicId) throws Throwable {
        final var payment = payerSponsoredTransfer(payerId, COMPLEX_KEY_ACCOUNT_KT, beneficiaryIdStr, paymentAmount);
        final var data = ConsensusGetTopicInfoQuery.newBuilder()
                .setTopicID(TopicID.newBuilder().setTopicNum(topicId).build())
                .setHeader(QueryHeader.newBuilder().setPayment(payment).build())
                .build();

        return Query.newBuilder().setConsensusGetTopicInfo(data).build();
    }
}
