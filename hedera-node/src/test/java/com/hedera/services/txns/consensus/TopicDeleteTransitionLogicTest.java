package com.hedera.services.txns.consensus;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.TopicStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Topic;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopicDeleteTransitionLogicTest {
	private TransactionBody transactionBody;
	final private String TOPIC_ID = "8.6.75309";
	final private Id topicId = Id.fromGrpcTopic(asTopic(TOPIC_ID));
	final private Instant consensusTime = Instant.ofEpochSecond(1546304461);
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private Topic topic = new Topic(Id.fromGrpcTopic(asTopic(TOPIC_ID)));

	@Mock
	private TransactionContext transactionContext;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private TopicStore topicStore;

	private TopicDeleteTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new TopicDeleteTransitionLogic(topicStore, transactionContext);
	}

	@Test
	void followsHappyPath() throws Exception {
		givenValidTransactionContext();
		infrastructureSetup();
		subject.doStateTransition();
		verify(topicStore).loadTopic(topicId);
	}

	@Test
	void unauthorizedTopic() {
		givenUnauthorizedTransactionContext();
		infrastructureSetup();
		assertThrows(InvalidTransactionException.class, () -> subject.doStateTransition());
		verify(topicStore).loadTopic(topicId);
	}

	@Test
	void rubberstampsSyntax() {
		// expect:
		assertEquals(OK, subject.semanticCheck().apply(null));
	}

	@Test
	void hasCorrectApplicability() throws Exception {
		givenValidTransactionContext();

		// expect:
		assertTrue(subject.applicability().test(transactionBody));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	private ConsensusDeleteTopicTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
		return ConsensusDeleteTopicTransactionBody.newBuilder()
				.setTopicID(asTopic(TOPIC_ID));
	}

	private void givenTransaction(ConsensusDeleteTopicTransactionBody.Builder body) {
		transactionBody = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setConsensusDeleteTopic(body.build())
				.build();
	}

	private void infrastructureSetup() {
		given(transactionContext.accessor()).willReturn(accessor);
		given(accessor.getTxn()).willReturn(transactionBody);
		given(topicStore.loadTopic(topicId)).willReturn(topic);
	}

	private void givenValidTransactionContext() throws Exception {
		topic.setAdminKey(MISC_ACCOUNT_KT.asJKey());
		givenTransaction(getBasicValidTransactionBodyBuilder());
	}

	private void givenUnauthorizedTransactionContext() {
		topic.setAdminKey(null);
		givenTransaction(getBasicValidTransactionBodyBuilder());
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
