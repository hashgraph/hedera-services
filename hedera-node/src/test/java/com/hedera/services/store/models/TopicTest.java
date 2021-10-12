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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicTest {


	@Test
	void createsOkTopic() {
		final var id = Id.DEFAULT;
		final var autoRenew = new Account(new Id(1, 2, 3));
		final var topic = Topic.fromGrpcTopicCreate(
				id,
				TxnHandlingScenario.MISC_TOPIC_SUBMIT_KT.asJKeyUnchecked(),
				TxnHandlingScenario.MISC_TOPIC_ADMIN_KT.asJKeyUnchecked(),
				autoRenew,
				"memo",
				100,
				Instant.MAX);
		assertNotNull(topic);
		assertEquals(topic.getAutoRenewAccountId(), new Id(1, 2, 3));
		assertEquals(topic.getMemo(), "memo");
		assertEquals(topic.getId(), Id.DEFAULT);
		assertEquals(topic.getExpirationTimestamp(), RichInstant.fromJava(Instant.MAX));
		assertEquals(topic.getAutoRenewDurationSeconds(), 100);
		assertEquals(topic.getSequenceNumber(), 0);
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

		topic.setNew(true);
		assertTrue(topic.isNew());
	}

	@Test
	void updatesAsExpected() {
		final var oldAdminKey = TxnHandlingScenario.MISC_TOPIC_ADMIN_KT.asJKeyUnchecked();
		final var newAdminKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asKey();
		final var submitKey = TxnHandlingScenario.MISC_TOPIC_SUBMIT_KT.asKey();

		final var subject = new Topic(Id.DEFAULT);
		subject.setExpirationTimestamp(RichInstant.fromJava(Instant.MIN));
		subject.setAutoRenewDurationSeconds(Instant.MIN.getEpochSecond());
		subject.setSubmitKey(null);
		subject.setAdminKey(oldAdminKey);
		subject.setAutoRenewAccountId(new Id(1, 2, 3));
		subject.update(
				Optional.of(Timestamp.newBuilder().setSeconds(Instant.MAX.getEpochSecond()).setNanos(0).build()),
				Optional.of(newAdminKey),
				Optional.of(submitKey),
				Optional.of("memo"),
				Optional.of(Duration.newBuilder().setSeconds(Instant.MAX.getEpochSecond()).build()),// auto-renew-period
				Optional.of(new Account(Id.DEFAULT)), // ar - account
				false,
				false
		);

		assertEquals("memo", subject.getMemo());
		assertNotEquals(asFcKeyUnchecked(newAdminKey), oldAdminKey);
		assertEquals(Id.DEFAULT, subject.getAutoRenewAccountId());
		assertEquals(Instant.MAX.getEpochSecond(), subject.getAutoRenewDurationSeconds());
	}
}