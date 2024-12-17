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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestBase;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableTopicStoreTest extends ConsensusTestBase {

    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();

    @Mock
    private StoreMetricsService storeMetricsService;

    private Topic topic;

    @Test
    void throwsIfNullValuesAsArgs() {
        assertThrows(
                NullPointerException.class, () -> new WritableTopicStore(null, CONFIGURATION, storeMetricsService));
        assertThrows(
                NullPointerException.class, () -> new WritableTopicStore(writableStates, null, storeMetricsService));
        assertThrows(NullPointerException.class, () -> new WritableTopicStore(writableStates, CONFIGURATION, null));
        assertThrows(NullPointerException.class, () -> writableStore.put(null));
    }

    @Test
    void constructorCreatesTopicState() {
        final var store = new WritableTopicStore(writableStates, CONFIGURATION, storeMetricsService);
        assertNotNull(store);
    }

    @Test
    void commitsTopicChanges() {
        topic = createTopic();
        assertFalse(writableTopicState.contains(topicId));

        writableStore.put(topic);

        assertTrue(writableTopicState.contains(topicId));
        final var writtenTopic = writableTopicState.get(topicId);
        assertEquals(topic, writtenTopic);
    }

    @Test
    void getReturnsTopic() {
        topic = createTopic();
        writableStore.put(topic);

        final var maybeReadTopic = writableStore.getTopic(
                TopicID.newBuilder().topicNum(topicEntityNum).build());

        assertNotNull(maybeReadTopic);
        assertEquals(topic, maybeReadTopic);
    }
}
