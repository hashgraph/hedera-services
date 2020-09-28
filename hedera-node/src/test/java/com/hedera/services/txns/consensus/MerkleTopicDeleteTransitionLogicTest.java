package com.hedera.services.txns.consensus;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.time.Instant;

import static com.hedera.services.state.merkle.MerkleEntityId.fromTopicId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asTopic;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
class MerkleTopicDeleteTransitionLogicTest {
	final private String TOPIC_ID = "8.6.75309";
	final private MerkleEntityId topicFcKey = fromTopicId(asTopic(TOPIC_ID));
	private Instant consensusTime;
	private TransactionBody transactionBody;
	private TransactionContext transactionContext;
	private PlatformTxnAccessor accessor;
	private FCMap<MerkleEntityId, MerkleTopic> topics = new FCMap<>(new MerkleEntityId.Provider(), new MerkleTopic.Provider());
	private OptionValidator validator;
	private TopicDeleteTransitionLogic subject;
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();

	MerkleTopic deletableTopic;

	@BeforeEach
	private void setup() {
		consensusTime = Instant.ofEpochSecond(1546304461);

		transactionContext = mock(TransactionContext.class);
		given(transactionContext.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		topics.clear();

		subject = new TopicDeleteTransitionLogic(() -> topics, validator, transactionContext);
	}

	@Test
	public void rubberstampsSyntax() {
		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(null));
	}

	@Test
	public void hasCorrectApplicability() throws Throwable {
		givenValidTransactionContext();

		// expect:
		assertTrue(subject.applicability().test(transactionBody));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void followsHappyPath() throws Throwable {
		// setup:
		givenMocksForHappyPath();
		// and:
		InOrder inOrder = inOrder(topics, deletableTopic, transactionContext);

		// when:
		subject.doStateTransition();

		// then:
		inOrder.verify(deletableTopic).setTopicDeleted(true);
		inOrder.verify(topics).put(topicFcKey, deletableTopic);
		inOrder.verify(transactionContext).setStatus(SUCCESS);
	}

	private void givenMocksForHappyPath() {
		deletableTopic = mock(MerkleTopic.class);
		given(deletableTopic.hasAdminKey()).willReturn(true);
		given(validator.queryableTopicStatus(any(), any())).willReturn(OK);
		givenTransaction(getBasicValidTransactionBodyBuilder());

		topics = (FCMap<MerkleEntityId, MerkleTopic>)mock(FCMap.class);

		given(topics.get(topicFcKey)).willReturn(deletableTopic);
		given(topics.getForModify(topicFcKey)).willReturn(deletableTopic);

		subject = new TopicDeleteTransitionLogic(() -> topics, validator, transactionContext);
	}

	@Test
	public void failsForTopicWithoutAdminKey() {
		// given:
		givenTransactionContextNoAdminKey();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(fromTopicId(asTopic(TOPIC_ID)));
		assertNotNull(topic);
		assertFalse(topic.isTopicDeleted());
		verify(transactionContext).setStatus(UNAUTHORIZED);
	}

	@Test
	public void failsForInvalidTopic() {
		// given:
		givenTransactionContextInvalidTopic();

		// when:
		subject.doStateTransition();

		// then:
		assertTrue(topics.isEmpty());
		verify(transactionContext).setStatus(INVALID_TOPIC_ID);
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
		given(validator.queryableTopicStatus(asTopic(TOPIC_ID), topics)).willReturn(INVALID_TOPIC_ID);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
