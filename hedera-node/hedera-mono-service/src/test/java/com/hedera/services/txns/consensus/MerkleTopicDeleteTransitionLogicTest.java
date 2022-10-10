/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.consensus;

import static com.hedera.services.utils.EntityNum.fromTopicId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MerkleTopicDeleteTransitionLogicTest {
    private final String TOPIC_ID = "0.0.75309";
    private final EntityNum topicFcKey = fromTopicId(asTopic(TOPIC_ID));
    private Instant consensusTime;
    private TransactionBody transactionBody;
    private TransactionContext transactionContext;
    private SignedTxnAccessor accessor;
    private MerkleMap<EntityNum, MerkleTopic> topics = new MerkleMap<>();
    private OptionValidator validator;
    private TopicDeleteTransitionLogic subject;
    private SigImpactHistorian sigImpactHistorian;
    private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();

    MerkleTopic deletableTopic;

    @BeforeEach
    void setup() {
        consensusTime = Instant.ofEpochSecond(1546304461);

        transactionContext = mock(TransactionContext.class);
        given(transactionContext.consensusTime()).willReturn(consensusTime);
        accessor = mock(SignedTxnAccessor.class);
        validator = mock(OptionValidator.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);
        topics.clear();

        subject =
                new TopicDeleteTransitionLogic(
                        () -> topics, validator, sigImpactHistorian, transactionContext);
    }

    @Test
    void rubberstampsSyntax() {
        // expect:
        assertEquals(OK, subject.semanticCheck().apply(null));
    }

    @Test
    void hasCorrectApplicability() throws Throwable {
        givenValidTransactionContext();

        // expect:
        assertTrue(subject.applicability().test(transactionBody));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void followsHappyPath() {
        // setup:
        givenMocksForHappyPath();
        // and:
        InOrder inOrder = inOrder(topics, deletableTopic, transactionContext, sigImpactHistorian);

        // when:
        subject.doStateTransition();

        // then:
        inOrder.verify(deletableTopic).setDeleted(true);
        inOrder.verify(transactionContext).setStatus(SUCCESS);
        inOrder.verify(sigImpactHistorian).markEntityChanged(topicFcKey.longValue());
    }

    private void givenMocksForHappyPath() {
        deletableTopic = mock(MerkleTopic.class);
        given(deletableTopic.hasAdminKey()).willReturn(true);
        given(validator.queryableTopicStatus(any(), any())).willReturn(OK);
        givenTransaction(getBasicValidTransactionBodyBuilder());

        topics = (MerkleMap<EntityNum, MerkleTopic>) mock(MerkleMap.class);

        given(topics.get(topicFcKey)).willReturn(deletableTopic);
        given(topics.getForModify(topicFcKey)).willReturn(deletableTopic);

        subject =
                new TopicDeleteTransitionLogic(
                        () -> topics, validator, sigImpactHistorian, transactionContext);
    }

    @Test
    void failsForTopicWithoutAdminKey() {
        // given:
        givenTransactionContextNoAdminKey();

        // when:
        subject.doStateTransition();

        // then:
        var topic = topics.get(fromTopicId(asTopic(TOPIC_ID)));
        assertNotNull(topic);
        assertFalse(topic.isDeleted());
        verify(transactionContext).setStatus(UNAUTHORIZED);
    }

    @Test
    void failsForInvalidTopic() {
        // given:
        givenTransactionContextInvalidTopic();

        // when:
        subject.doStateTransition();

        // then:
        assertTrue(topics.isEmpty());
        verify(transactionContext).setStatus(INVALID_TOPIC_ID);
    }

    private ConsensusDeleteTopicTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
        return ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(asTopic(TOPIC_ID));
    }

    private void givenTransaction(ConsensusDeleteTopicTransactionBody.Builder body) {
        transactionBody =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setConsensusDeleteTopic(body.build())
                        .build();
        given(accessor.getTxn()).willReturn(transactionBody);
        given(transactionContext.accessor()).willReturn(accessor);
    }

    private void givenValidTransactionContext() throws Throwable {
        givenTransaction(getBasicValidTransactionBodyBuilder());
        given(validator.queryableTopicStatus(asTopic(TOPIC_ID), topics)).willReturn(OK);
        var topicWithAdminKey = new MerkleTopic();
        topicWithAdminKey.setAdminKey(MISC_ACCOUNT_KT.asJKey());
        topics.put(fromTopicId(asTopic(TOPIC_ID)), topicWithAdminKey);
    }

    private void givenTransactionContextNoAdminKey() {
        givenTransaction(getBasicValidTransactionBodyBuilder());
        given(validator.queryableTopicStatus(asTopic(TOPIC_ID), topics)).willReturn(OK);
        topics.put(fromTopicId(asTopic(TOPIC_ID)), new MerkleTopic());
    }

    private void givenTransactionContextInvalidTopic() {
        givenTransaction(getBasicValidTransactionBodyBuilder());
        given(validator.queryableTopicStatus(asTopic(TOPIC_ID), topics))
                .willReturn(INVALID_TOPIC_ID);
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
                .build();
    }
}
