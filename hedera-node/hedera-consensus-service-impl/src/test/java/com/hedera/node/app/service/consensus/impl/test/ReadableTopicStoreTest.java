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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.TopicStore.TopicMetadata;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusHandlerTestBase;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadableTopicStoreTest extends ConsensusHandlerTestBase {
    private ReadableTopicStore subject;

    @BeforeEach
    void setUp() {
        subject = new ReadableTopicStore(readableStates);
    }

    @Test
    void getsTopicMetadataIfTopicExists() throws PreCheckException {
        givenValidTopic();
        final var topicMeta = subject.getTopicMetadata(WELL_KNOWN_TOPIC_ID);

        assertNotNull(topicMeta);

        assertEquals(topicEntityNum.longValue(), topicMeta.key());
        assertEquals(adminKey.toString(), topicMeta.adminKey().toString());
        assertEquals(adminKey.toString(), topicMeta.submitKey().toString());
        assertEquals(topic.sequenceNumber(), topicMeta.sequenceNumber());
        assertEquals(topic.autoRenewPeriod(), topicMeta.autoRenewDurationSeconds());
        assertEquals(OptionalLong.of(autoRenewId.accountNum()), topicMeta.autoRenewAccountId());
        assertEquals(Optional.of(memo), topicMeta.memo());
        assertFalse(topicMeta.isDeleted());
        assertArrayEquals(runningHash, topicMeta.runningHash());
    }

    @Test
    void objectMethodsWorks() {
        final var meta = new TopicMetadata(
                Optional.of(memo),
                adminKey,
                adminKey,
                100L,
                OptionalLong.of(autoRenewId.accountNum()),
                Timestamp.newBuilder().seconds(100L).build(),
                1L,
                new byte[48],
                topicEntityNum.longValue(),
                false);

        final var expectedString =
                "TopicMetadata{memo=Optional[test memo], adminKey=Key[key=OneOf[kind=THRESHOLD_KEY, value=ThresholdKey[threshold=2, keys=KeyList[keys=[Key[key=OneOf[kind=ED25519, value=Bytes[97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97]]], Key[key=OneOf[kind=ED25519, value=Bytes[98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98]]], Key[key=OneOf[kind=THRESHOLD_KEY, value=ThresholdKey[threshold=2, keys=KeyList[keys=[Key[key=OneOf[kind=ED25519, value=Bytes[97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97]]], Key[key=OneOf[kind=ED25519, value=Bytes[98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98]]], Key[key=OneOf[kind=ED25519, value=Bytes[99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99]]]]]]]]]]]]], submitKey=Key[key=OneOf[kind=THRESHOLD_KEY, value=ThresholdKey[threshold=2, keys=KeyList[keys=[Key[key=OneOf[kind=ED25519, value=Bytes[97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97]]], Key[key=OneOf[kind=ED25519, value=Bytes[98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98]]], Key[key=OneOf[kind=THRESHOLD_KEY, value=ThresholdKey[threshold=2, keys=KeyList[keys=[Key[key=OneOf[kind=ED25519, value=Bytes[97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97,97]]], Key[key=OneOf[kind=ED25519, value=Bytes[98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98,98]]], Key[key=OneOf[kind=ED25519, value=Bytes[99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99]]]]]]]]]]]]], autoRenewDurationSeconds=100, autoRenewAccountId=OptionalLong[4], expirationTimestamp=Timestamp[seconds=100, nanos=0], sequenceNumber=1, runningHash=[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], key=1, isDeleted=false}";
        assertEquals(expectedString, meta.toString());

        final var metaCopy = meta;
        assertEquals(meta, metaCopy);
        assertEquals(meta.hashCode(), metaCopy.hashCode());

        final var meta2 = new TopicMetadata(
                Optional.of(memo),
                adminKey,
                adminKey,
                100L,
                OptionalLong.of(autoRenewId.accountNum()),
                WELL_KNOWN_EXPIRY,
                1L,
                new byte[48],
                topicEntityNum.longValue(),
                true);
        assertNotEquals(meta, meta2);
        assertNotEquals(meta.hashCode(), meta2.hashCode());
    }

    @Test
    void getsTopicMetadataIfTopicExistsWithNoAutoRenewAccount() throws PreCheckException {
        givenValidTopic(0L);
        readableTopicState = readableTopicState();
        given(readableStates.<EntityNum, com.hedera.hapi.node.state.consensus.Topic>get(TOPICS))
                .willReturn(readableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        subject = new ReadableTopicStore(readableStates);

        final var topicMeta = subject.getTopicMetadata(WELL_KNOWN_TOPIC_ID);

        assertNotNull(topicMeta);

        assertEquals(topicEntityNum.longValue(), topicMeta.key());
        assertEquals(adminKey.toString(), topicMeta.adminKey().toString());
        assertEquals(adminKey.toString(), topicMeta.submitKey().toString());
        assertEquals(topic.sequenceNumber(), topicMeta.sequenceNumber());
        assertEquals(topic.autoRenewPeriod(), topicMeta.autoRenewDurationSeconds());
        assertEquals(OptionalLong.empty(), topicMeta.autoRenewAccountId());
        assertEquals(Optional.of(memo), topicMeta.memo());
        assertFalse(topicMeta.isDeleted());
        assertArrayEquals(runningHash, topicMeta.runningHash());
    }

    @Test
    void missingTopicIsNull() {
        readableTopicState.reset();
        final var state =
                MapReadableKVState.<Long, MerkleTopic>builder("TOPICS").build();
        given(readableStates.<Long, MerkleTopic>get(TOPICS)).willReturn(state);
        subject = new ReadableTopicStore(readableStates);

        assertThat(subject.getTopicMetadata(WELL_KNOWN_TOPIC_ID)).isNull();
    }

    @Test
    void constructorCreatesTopicState() {
        final var store = new ReadableTopicStore(readableStates);
        assertNotNull(store);
    }

    @Test
    void nullArgsFail() {
        assertThrows(NullPointerException.class, () -> new ReadableTopicStore(null));
    }
}
