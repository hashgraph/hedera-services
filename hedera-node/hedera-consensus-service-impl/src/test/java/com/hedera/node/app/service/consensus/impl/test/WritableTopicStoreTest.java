/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableTopicStoreTest extends ConsensusTestBase {
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
        assertFalse(writableTopicKVState.contains(topicId));

        writableStore.put(topic);

        assertTrue(writableTopicKVState.contains(topicId));
        final var writtenTopic = writableTopicKVState.get(topicId);
        assertEquals(topic, writtenTopic);
    }

    @Test
    void testWritableStoreSorted() {
        topic = createTopic(4L);
        writableStore.put(topic);
        topic = createTopic(3L);
        writableStore.put(topic);
        topic = createTopic(6L);
        writableStore.put(topic);

        final var topicIds = writableStore.modifiedTopics();
        assertThat(topicIds)
                .containsSequence(
                        TopicID.newBuilder().topicNum(3L).build(),
                        TopicID.newBuilder().topicNum(4L).build(),
                        TopicID.newBuilder().topicNum(6L).build());
    }
}
