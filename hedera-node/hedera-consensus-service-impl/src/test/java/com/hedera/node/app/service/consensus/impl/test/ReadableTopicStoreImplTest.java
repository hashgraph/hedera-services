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

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStoreImpl;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestBase;
import com.swirlds.state.test.fixtures.MapReadableKVState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadableTopicStoreImplTest extends ConsensusTestBase {
    private ReadableTopicStore subject;

    @BeforeEach
    void setUp() {
        subject = new ReadableTopicStoreImpl(readableStates);
    }

    @Test
    void getsTopicIfTopicExists() {
        givenValidTopic();
        final var topic = subject.getTopic(topicId);

        assertNotNull(topic);

        assertEquals(topicEntityNum, topic.topicId().topicNum());
        assertEquals(adminKey.toString(), topic.adminKey().toString());
        assertEquals(adminKey.toString(), topic.submitKey().toString());
        assertEquals(this.topic.sequenceNumber(), topic.sequenceNumber());
        assertEquals(this.topic.autoRenewPeriod(), topic.autoRenewPeriod());
        assertEquals(autoRenewId, topic.autoRenewAccountId());
        assertEquals(memo, topic.memo());
        assertFalse(topic.deleted());
        assertArrayEquals(runningHash, CommonPbjConverters.asBytes(topic.runningHash()));
    }

    @Test
    void getsTopicIfTopicExistsWithNoAutoRenewAccount() {
        final var accountId = AccountID.newBuilder().accountNum(0L).build();
        givenValidTopic(accountId);
        readableTopicState = readableTopicState();
        given(readableStates.<TopicID, Topic>get(TOPICS_KEY)).willReturn(readableTopicState);
        readableStore = new ReadableTopicStoreImpl(readableStates);
        subject = new ReadableTopicStoreImpl(readableStates);

        final var topic = subject.getTopic(topicId);

        assertNotNull(topic);

        assertEquals(topicEntityNum, topic.topicId().topicNum());
        assertEquals(adminKey.toString(), topic.adminKey().toString());
        assertEquals(adminKey.toString(), topic.submitKey().toString());
        assertEquals(this.topic.sequenceNumber(), topic.sequenceNumber());
        assertEquals(this.topic.autoRenewPeriod(), topic.autoRenewPeriod());
        assertEquals(accountId, topic.autoRenewAccountId());
        assertEquals(memo, topic.memo());
        assertFalse(topic.deleted());
        assertArrayEquals(runningHash, CommonPbjConverters.asBytes(topic.runningHash()));
    }

    @Test
    void missingTopicIsNull() {
        readableTopicState.reset();
        final var state = MapReadableKVState.<Long, Topic>builder(TOPICS_KEY).build();
        given(readableStates.<Long, Topic>get(TOPICS_KEY)).willReturn(state);
        subject = new ReadableTopicStoreImpl(readableStates);

        assertThat(subject.getTopic(topicId)).isNull();
    }

    @Test
    void constructorCreatesTopicState() {
        final var store = new ReadableTopicStoreImpl(readableStates);
        assertNotNull(store);
    }

    @Test
    void nullArgsFail() {
        assertThrows(NullPointerException.class, () -> new ReadableTopicStoreImpl(null));
    }

    @Test
    void getSizeOfState() {
        final var store = new ReadableTopicStoreImpl(readableStates);
        assertEquals(readableStates.get(TOPICS_KEY).size(), store.sizeOfState());
    }
}
