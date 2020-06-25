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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.domain.topic.Topic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.core.MapKey;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hedera.test.utils.IdUtils.asTopic;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
class SubmitMessageTransitionLogicTest {
	final private String TOPIC_ID = "8.6.75";

	private Instant consensusTime;
	private TransactionBody transactionBody;
	private TransactionContext transactionContext;
	private PlatformTxnAccessor accessor;
	private OptionValidator validator;
	private SubmitMessageTransitionLogic subject;
	private FCMap<MapKey, Topic> topics = new FCMap<>(MapKey::deserialize, Topic::deserialize);;
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();

	@BeforeEach
	private void setup() {
		consensusTime = Instant.ofEpochSecond(1546304461);

		transactionContext = mock(TransactionContext.class);
		given(transactionContext.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		topics.clear();

		subject = new SubmitMessageTransitionLogic(topics, validator, transactionContext);
	}

	@Test
	public void rubberstampsSyntax() {
		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(null));
	}

	@Test
	public void hasCorrectApplicability() {
		// given:
		givenValidTransactionContext();

		// expect:
		assertTrue(subject.applicability().test(transactionBody));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void followsHappyPath() {
		// given:
		givenValidTransactionContext();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(MapKey.getMapKey(asTopic(TOPIC_ID)));
		assertNotNull(topic);
		assertEquals(1L, topic.getSequenceNumber()); // Starts at 0.

		// Hash depends on prior state of topic (default topic object has 0s for runningHash and 0L for seqNum),
		// consensus timestamp, message.
		assertEquals("bd9ec4df57667d55b922c3b1ad7dee2566bb018efe13d68cd0696fb4181694cc4d7aa2186cd8dff6e22849d663f94bf1",
				MiscUtils.commonsBytesToHex(topic.getRunningHash()));

		verify(transactionContext).setStatus(SUCCESS);
	}

	@Test
	public void failsWithEmptyMessage() {
		// given:
		givenTransactionContextNoMessage();

		// when:
		subject.doStateTransition();

		// then:
		assertUnchangedTopics();
		verify(transactionContext).setStatus(INVALID_TOPIC_MESSAGE);
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

	@Test
	public void failsForInvalidChunkNumber() {
		// given:
		givenChunkMessage(2, 3);

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(INVALID_CHUNK_NUMBER);
	}

	private void assertUnchangedTopics() {
		var topic = topics.get(MapKey.getMapKey(asTopic(TOPIC_ID)));
		assertEquals(0L, topic.getSequenceNumber());
		assertArrayEquals(new byte[48], topic.getRunningHash());
	}

	private ConsensusSubmitMessageTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
		return ConsensusSubmitMessageTransactionBody.newBuilder()
				.setTopicID(asTopic(TOPIC_ID))
				.setMessage(ByteString.copyFrom("valid message".getBytes()));
	}

	private void givenTransaction(ConsensusSubmitMessageTransactionBody.Builder body) {
		transactionBody = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setConsensusSubmitMessage(body.build())
				.build();
		given(accessor.getTxn()).willReturn(transactionBody);
		given(transactionContext.accessor()).willReturn(accessor);
	}

	private void givenValidTransactionContext() {
		givenTransaction(getBasicValidTransactionBodyBuilder());
		given(validator.queryableTopicStatus(asTopic(TOPIC_ID), topics)).willReturn(OK);
		topics.put(MapKey.getMapKey(asTopic(TOPIC_ID)), new Topic());
	}

	private void givenTransactionContextNoMessage() {
		givenTransaction(ConsensusSubmitMessageTransactionBody.newBuilder()
				.setTopicID(asTopic(TOPIC_ID)).setTopicID(asTopic(TOPIC_ID)));
		given(validator.queryableTopicStatus(asTopic(TOPIC_ID), topics)).willReturn(OK);
		topics.put(MapKey.getMapKey(asTopic(TOPIC_ID)), new Topic());
	}

	private void givenTransactionContextInvalidTopic() {
		givenTransaction(getBasicValidTransactionBodyBuilder());
		given(validator.queryableTopicStatus(asTopic(TOPIC_ID), topics)).willReturn(INVALID_TOPIC_ID);
	}

	private void givenChunkMessage(int totalChunks, int chunkNumber) {
		ConsensusMessageChunkInfo chunkInfo = ConsensusMessageChunkInfo
				.newBuilder()
				.setInitialTransactionID(ourTxnId())
				.setTotal(totalChunks)
				.setNumber(chunkNumber)
				.build();
		givenTransaction(getBasicValidTransactionBodyBuilder()
				.setChunkInfo(chunkInfo));
		given(validator.queryableTopicStatus(asTopic(TOPIC_ID), topics)).willReturn(OK);
		topics.put(MapKey.getMapKey(asTopic(TOPIC_ID)), new Topic());
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
