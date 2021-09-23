package com.hedera.services.store.models;

/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicTest {
	
	private ConsensusCreateTopicTransactionBody grpc;
	
	@Test
	void fromGrpcTopicCreate() {
		withValidGrpc();
		final var id = Id.DEFAULT;
		final var created = Topic.fromGrpcTopicCreate(grpc, id, Instant.MAX);
		assertNotNull(created.getMemo());
		assertEquals("memo", created.getMemo());
		assertNotNull(created.getAdminKey());
		assertNotNull(created.getAutoRenewAccountId());
		assertEquals(Id.DEFAULT, created.getAutoRenewAccountId());
		assertEquals(0, created.getSequenceNumber());
	}
	
	@Test
	void throwsOnInvalidKeys() {
		grpc = ConsensusCreateTopicTransactionBody.newBuilder()
				.setAdminKey(Key.getDefaultInstance())
				.build();
		final var id = Id.DEFAULT;
		assertFailsWith(() -> Topic.fromGrpcTopicCreate(grpc, id, Instant.MAX), ResponseCodeEnum.BAD_ENCODING);
	}
	
	@Test
	void objectContractWorks() {
		final var topic = new Topic(Id.DEFAULT);
		assertEquals(Id.DEFAULT, topic.getId());
		
		topic.setDeleted(true);
		assertTrue(topic.isDeleted());
		
		topic.setAutoRenewAccountId(Id.DEFAULT);
		assertEquals(Id.DEFAULT, topic.getAutoRenewAccountId());
		
		topic.setMemo("memo");
		assertEquals("memo", topic.getMemo());
		
		topic.setExpirationTimestamp(RichInstant.MISSING_INSTANT);
		assertEquals(RichInstant.MISSING_INSTANT, topic.getExpirationTimestamp());
		
		assertEquals(0, topic.getSequenceNumber());
		topic.setSequenceNumber(10);
		assertEquals(10, topic.getSequenceNumber());
		
		final var submitKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
		topic.setSubmitKey(submitKey);
		assertEquals(submitKey, topic.getSubmitKey());
		
		topic.setAutoRenewDurationSeconds(10L);
		assertEquals(10L, topic.getAutoRenewDurationSeconds());
	}
	
	private void withValidGrpc() {
		grpc = ConsensusCreateTopicTransactionBody.newBuilder()
				.setMemo("memo")
				.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey())
				.setAutoRenewAccount(Id.DEFAULT.asGrpcAccount())
				.setSubmitKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey())
				.build();
	}
}