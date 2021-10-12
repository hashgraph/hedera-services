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

package com.hedera.services.store.models;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TopicConversionTest {
	final JKey SUBMIT_KEY = TxnHandlingScenario.MISC_TOPIC_SUBMIT_KT.asJKeyUnchecked();
	final JKey ADMIN_KEY = TxnHandlingScenario.MISC_TOPIC_ADMIN_KT.asJKeyUnchecked();

	@Test
	void fromMerkle() {
		final var merkle = new MerkleTopic();
		merkle.setSequenceNumber(10);
		merkle.setMemo("memo");
		merkle.setDeleted(false);
		merkle.setSubmitKey(SUBMIT_KEY);
		merkle.setAdminKey(ADMIN_KEY);
		merkle.setAutoRenewDurationSeconds(100);
		merkle.setExpirationTimestamp(RichInstant.MISSING_INSTANT);
		merkle.setAutoRenewAccountId(new EntityId(1, 2, 3));

		final var mappedModel = TopicConversion.fromMerkle(merkle, Id.DEFAULT);
		assertEquals(Id.DEFAULT, mappedModel.getId());
		assertEquals(10, mappedModel.getSequenceNumber());
		assertEquals("memo", mappedModel.getMemo());
		assertEquals(SUBMIT_KEY, mappedModel.getSubmitKey());
		assertEquals(ADMIN_KEY, mappedModel.getAdminKey());
		assertEquals(100, mappedModel.getAutoRenewDurationSeconds());
		assertEquals(RichInstant.MISSING_INSTANT, mappedModel.getExpirationTimestamp());
		assertEquals(new EntityId(1, 2, 3), mappedModel.getAutoRenewAccountId().asEntityId());

		/* assert that empty admin keys are mapped to null on the model side */
		var emptyAdmin = new JKeyList(new ArrayList<>());
		merkle.setAdminKey(emptyAdmin);
		var mapped2 = TopicConversion.fromMerkle(merkle, Id.DEFAULT);
		assertNull(mapped2.getAdminKey());
	}

	@Test
	void fromModel() {
		final var model = new Topic(Id.DEFAULT);
		model.setSequenceNumber(10);
		model.setMemo("memo");
		model.setDeleted(false);
		model.setSubmitKey(SUBMIT_KEY);
		model.setAdminKey(ADMIN_KEY);
		model.setAutoRenewDurationSeconds(100);
		model.setExpirationTimestamp(RichInstant.MISSING_INSTANT);
		model.setAutoRenewAccountId(EntityId.MISSING_ENTITY_ID.asId());

		final var mappedMerkle = TopicConversion.fromModel(model);
		assertEquals(Id.DEFAULT.getNum(), mappedMerkle.getKey().longValue());
		assertEquals(10, mappedMerkle.getSequenceNumber());
		assertEquals("memo", mappedMerkle.getMemo());
		assertEquals(SUBMIT_KEY, mappedMerkle.getSubmitKey());
		assertEquals(ADMIN_KEY, mappedMerkle.getAdminKey());
		assertEquals(100, mappedMerkle.getAutoRenewDurationSeconds());
		assertEquals(RichInstant.MISSING_INSTANT, mappedMerkle.getExpirationTimestamp());
		assertEquals(EntityId.MISSING_ENTITY_ID, mappedMerkle.getAutoRenewAccountId());
	}

	@Test
	void modelToMerkle() {
		final var model = new Topic(Id.DEFAULT);
		model.setSequenceNumber(10);
		model.setMemo("memo");
		model.setDeleted(false);
		model.setSubmitKey(SUBMIT_KEY);
		model.setAdminKey(ADMIN_KEY);
		model.setAutoRenewDurationSeconds(100);
		model.setExpirationTimestamp(RichInstant.MISSING_INSTANT);
		model.setAutoRenewAccountId(EntityId.MISSING_ENTITY_ID.asId());

		final var merkle = new MerkleTopic();
		merkle.setSequenceNumber(15);
		merkle.setMemo("old memo");
		merkle.setDeleted(true);
		merkle.setSubmitKey(null);
		merkle.setAdminKey(null);
		merkle.setAutoRenewDurationSeconds(200);
		merkle.setExpirationTimestamp(null);
		merkle.setAutoRenewAccountId(new EntityId(3, 6, 9));

		TopicConversion.mapModelChangesToMerkle(model, merkle);

		assertEquals(model.getSequenceNumber(), merkle.getSequenceNumber());
		assertEquals(model.getMemo(), merkle.getMemo());
		assertEquals(model.isDeleted(), merkle.isDeleted());
		assertEquals(model.getSubmitKey(), merkle.getSubmitKey());
		assertEquals(model.getAdminKey(), merkle.getAdminKey());
		assertEquals(model.getAutoRenewDurationSeconds(), merkle.getAutoRenewDurationSeconds());
		assertEquals(model.getExpirationTimestamp(), merkle.getExpirationTimestamp());
		assertEquals(model.getAutoRenewAccountId().asEntityId(), merkle.getAutoRenewAccountId());
	}
}