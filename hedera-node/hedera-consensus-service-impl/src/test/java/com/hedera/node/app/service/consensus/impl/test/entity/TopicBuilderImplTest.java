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

package com.hedera.node.app.service.consensus.impl.test.entity;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.consensus.entity.TopicBuilder;
import com.hedera.node.app.service.consensus.impl.entity.TopicBuilderImpl;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusHandlerTestBase;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TopicBuilderImplTest extends ConsensusHandlerTestBase {
    private TopicBuilder subject;

    @BeforeEach
    void setUp() {
        subject = new TopicBuilderImpl(setUpTopicImpl());
    }

    @Test
    void constructorWorks() {
        assertNotNull(subject);
        final var topic = subject.build();

        assertEquals(topicId.getTopicNum(), topic.topicNumber());
        assertSame(hederaKey, topic.getAdminKey().get());
        assertSame(hederaKey, topic.getSubmitKey().get());
        assertEquals(memo, topic.memo());
        assertEquals(autoRenewId.getAccountNum(), topic.autoRenewAccountNumber());
        assertEquals(autoRenewSecs, topic.autoRenewSecs());
        assertTrue(topic.deleted());
        assertEquals(sequenceNumber, topic.sequenceNumber());
        assertEquals(expirationTime, topic.expiry());
    }

    @Test
    void defaultConstructorWorks() {
        subject = new TopicBuilderImpl();
        assertFalse(subject.build().deleted());
        assertEquals(Optional.empty(), subject.build().getAdminKey());
        assertEquals(Optional.empty(), subject.build().getSubmitKey());
    }

    @Test
    void negativeOrZeroValuesThrow() {
        assertThrows(IllegalArgumentException.class, () -> subject.autoRenewSecs(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.autoRenewAccountNumber(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.topicNumber(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.sequenceNumber(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.expiry(-1L));
        assertThrows(IllegalArgumentException.class, () -> subject.topicNumber(0L));
    }

    @Test
    void settersWork() {
        final var newKey = asHederaKey(A_COMPLEX_KEY).get();
        subject.sequenceNumber(1L);
        subject.expiry(2L);
        subject.autoRenewSecs(3L);
        subject.autoRenewAccountNumber(4L);
        subject.deleted(true);
        subject.memo("test2");
        subject.adminKey(newKey);
        subject.submitKey(newKey);
        subject.topicNumber(20L);

        final var topic = subject.build();

        assertEquals(20L, topic.topicNumber());
        assertSame(newKey, topic.getAdminKey().get());
        assertSame(newKey, topic.getSubmitKey().get());
        assertEquals("test2", topic.memo());
        assertEquals(4L, topic.autoRenewAccountNumber());
        assertEquals(3L, topic.autoRenewSecs());
        assertTrue(topic.deleted());
        assertEquals(1L, topic.sequenceNumber());
        assertEquals(2L, topic.expiry());
    }
}
