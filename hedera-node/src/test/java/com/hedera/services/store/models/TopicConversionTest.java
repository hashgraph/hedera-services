/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.Test;

class TopicConversionTest {
    private static final JKey SUBMIT_KEY =
            TxnHandlingScenario.MISC_TOPIC_SUBMIT_KT.asJKeyUnchecked();
    private static final JKey ADMIN_KEY = TxnHandlingScenario.MISC_TOPIC_ADMIN_KT.asJKeyUnchecked();

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
        merkle.setAutoRenewAccountId(EntityId.MISSING_ENTITY_ID);

        final var mappedModel = TopicConversion.fromMerkle(merkle, Id.DEFAULT);
        assertEquals(Id.DEFAULT, mappedModel.getId());
        assertEquals(10, mappedModel.getSequenceNumber());
        assertEquals("memo", mappedModel.getMemo());
        assertEquals(SUBMIT_KEY, mappedModel.getSubmitKey());
        assertEquals(ADMIN_KEY, mappedModel.getAdminKey());
        assertEquals(100, mappedModel.getAutoRenewDurationSeconds());
        assertEquals(RichInstant.MISSING_INSTANT, mappedModel.getExpirationTimestamp());
        assertEquals(EntityId.MISSING_ENTITY_ID, mappedModel.getAutoRenewAccountId().asEntityId());
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
        assertEquals(Id.DEFAULT.num(), mappedMerkle.getKey().longValue());
        assertEquals(10, mappedMerkle.getSequenceNumber());
        assertEquals("memo", mappedMerkle.getMemo());
        assertEquals(SUBMIT_KEY, mappedMerkle.getSubmitKey());
        assertEquals(ADMIN_KEY, mappedMerkle.getAdminKey());
        assertEquals(100, mappedMerkle.getAutoRenewDurationSeconds());
        assertEquals(RichInstant.MISSING_INSTANT, mappedMerkle.getExpirationTimestamp());
        assertEquals(EntityId.MISSING_ENTITY_ID, mappedMerkle.getAutoRenewAccountId());
    }
}
