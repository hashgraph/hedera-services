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

package com.hedera.node.app.service.consensus.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.consensus.entity.Topic;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.entity.TopicImpl;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusHandlerTestBase;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableTopicStoreTest extends ConsensusHandlerTestBase {
    private Topic topic;

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(NullPointerException.class, () -> new WritableTopicStore(null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
    }

    @Test
    void constructorCreatesTopicState() {
        final var store = new WritableTopicStore(writableStates);
        assertNotNull(store);
    }

    @Test
    void commitsTopicChanges() {
        topic = createTopic();
        assertFalse(writableTopicState.contains(topicEntityNum));

        writableStore.put(topic);

        assertTrue(writableTopicState.contains(topicEntityNum));
        final var merkleTopic = writableTopicState.get(topicEntityNum);

        assertEquals(topic.getAdminKey().get(), merkleTopic.getAdminKey());
        assertEquals(topic.getSubmitKey().get(), merkleTopic.getSubmitKey());
        assertEquals(topic.autoRenewSecs(), merkleTopic.getAutoRenewDurationSeconds());
        assertEquals(
                topic.autoRenewAccountNumber(),
                merkleTopic.getAutoRenewAccountId().num());
        assertEquals(topic.expiry(), merkleTopic.getExpirationTimestamp().getSeconds());
        assertEquals(topic.sequenceNumber(), merkleTopic.getSequenceNumber());
        assertEquals(topic.memo(), merkleTopic.getMemo());
        assertEquals(topic.deleted(), merkleTopic.isDeleted());

        assertTrue(writableTopicState.modifiedKeys().contains(topicEntityNum));

        final var expectedTopic = new TopicImpl(
                topicEntityNum.longValue(),
                topic.getAdminKey().get(),
                topic.getSubmitKey().get(),
                topic.memo(),
                topic.autoRenewAccountNumber(),
                topic.autoRenewSecs(),
                topic.expiry(),
                topic.deleted(),
                topic.sequenceNumber());

        assertEquals(Optional.of(expectedTopic), writableStore.get(topicEntityNum.longValue()));
    }

    @Test
    void getReturnsTopic() {
        topic = createTopic();
        writableStore.put(topic);

        final var topicFromStore = writableStore.get(topicEntityNum.longValue());

        assertTrue(topicFromStore.isPresent());
        final var actualTopic = topicFromStore.get();
        assertEquals(actualTopic.memo(), topic.memo());
        assertEquals(actualTopic.autoRenewSecs(), topic.autoRenewSecs());
        assertEquals(actualTopic.autoRenewAccountNumber(), topic.autoRenewAccountNumber());
        assertEquals(actualTopic.getAdminKey().get(), topic.getAdminKey().get());
        assertEquals(actualTopic.getSubmitKey().get(), topic.getSubmitKey().get());
        assertEquals(actualTopic.sequenceNumber(), topic.sequenceNumber());
    }
}
