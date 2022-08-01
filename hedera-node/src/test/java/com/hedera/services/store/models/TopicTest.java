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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TopicTest {
    @Test
    void createsOkTopic() {
        final var id = Id.DEFAULT;
        final var autoRenew = new Account(new Id(1, 2, 3));
        final var topic =
                Topic.fromGrpcTopicCreate(
                        id,
                        TxnHandlingScenario.MISC_TOPIC_SUBMIT_KT.asJKeyUnchecked(),
                        TxnHandlingScenario.MISC_TOPIC_ADMIN_KT.asJKeyUnchecked(),
                        autoRenew,
                        "memo",
                        100,
                        Instant.MAX);
        assertNotNull(topic);
        assertEquals(new Id(1, 2, 3), topic.getAutoRenewAccountId());
        assertEquals("memo", topic.getMemo());
        assertEquals(Id.DEFAULT, topic.getId());
        assertEquals(RichInstant.fromJava(Instant.MAX), topic.getExpirationTimestamp());
        assertEquals(100, topic.getAutoRenewDurationSeconds());
        assertEquals(0, topic.getSequenceNumber());
    }

    @Test
    void objectContractWorks() {
        final var topic = new Topic(Id.DEFAULT);
        assertEquals(Id.DEFAULT, topic.getId());

        topic.setDeleted(true);
        assertTrue(topic.isDeleted());

        topic.setNew(true);
        assertTrue(topic.isNew());

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
}
