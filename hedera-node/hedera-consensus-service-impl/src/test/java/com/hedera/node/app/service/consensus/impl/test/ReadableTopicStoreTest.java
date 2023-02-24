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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore.TopicMetadata;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusHandlerTestBase;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadableTopicStoreTest extends ConsensusHandlerTestBase {
    private ReadableTopicStore subject;

    @BeforeEach
    void setUp() {
        subject = new ReadableTopicStore(readableStates);
    }

    @Test
    void getsTopicMetadataIfTopicExists() {
        givenValidTopic();
        final var topicMeta = subject.getTopicMetadata(topicId);

        assertNotNull(topicMeta);
        assertNotNull(topicMeta.metadata());
        assertFalse(topicMeta.failed());
        assertNull(topicMeta.failureReason());

        final var meta = topicMeta.metadata();
        assertEquals(topicNum, meta.key());
        assertEquals(Optional.of(adminKey), meta.adminKey());
        assertEquals(Optional.of(adminKey), meta.submitKey());
        assertEquals(1L, meta.sequenceNumber());
        assertEquals(100L, meta.autoRenewDurationSeconds());
        assertEquals(Optional.of(autoRenewId.getAccountNum()), meta.autoRenewAccountId());
        assertEquals(Optional.of(memo), meta.memo());
        assertFalse(meta.isDeleted());
        assertArrayEquals(new byte[48], meta.runningHash());
    }

    @Test
    void objectMethodsWorks() {
        final var meta = new TopicMetadata(
                Optional.of(memo),
                Optional.of(adminKey),
                Optional.of(adminKey),
                100L,
                Optional.of(autoRenewId.getAccountNum()),
                Timestamp.newBuilder().setSeconds(100L).build(),
                1L,
                new byte[48],
                topicNum,
                false);

        final var expectedString =
                "TopicMetadata{memo=Optional[test memo], adminKey=Optional[<JThresholdKey: thd=2, keys=<JKeyList: keys=[<JEd25519Key: ed25519 hex=6161616161616161616161616161616161616161616161616161616161616161>, <JEd25519Key: ed25519 hex=6262626262626262626262626262626262626262626262626262626262626262>, <JThresholdKey: thd=2, keys=<JKeyList: keys=[<JEd25519Key: ed25519 hex=6161616161616161616161616161616161616161616161616161616161616161>, <JEd25519Key: ed25519 hex=6262626262626262626262626262626262626262626262626262626262626262>, <JEd25519Key: ed25519 hex=6363636363636363636363636363636363636363636363636363636363636363>]>>]>>], submitKey=Optional[<JThresholdKey: thd=2, keys=<JKeyList: keys=[<JEd25519Key: ed25519 hex=6161616161616161616161616161616161616161616161616161616161616161>, <JEd25519Key: ed25519 hex=6262626262626262626262626262626262626262626262626262626262626262>, <JThresholdKey: thd=2, keys=<JKeyList: keys=[<JEd25519Key: ed25519 hex=6161616161616161616161616161616161616161616161616161616161616161>, <JEd25519Key: ed25519 hex=6262626262626262626262626262626262626262626262626262626262626262>, <JEd25519Key: ed25519 hex=6363636363636363636363636363636363636363636363636363636363636363>]>>]>>], autoRenewDurationSeconds=100, autoRenewAccountId=Optional[4], expirationTimestamp=seconds: 100\n"
                        + ", sequenceNumber=1, runningHash=[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], key=1, isDeleted=false}";
        assertEquals(expectedString, meta.toString());

        final var metaCopy = meta;
        assertEquals(meta, metaCopy);
        assertEquals(meta.hashCode(), metaCopy.hashCode());

        final var meta2 = new TopicMetadata(
                Optional.of(memo),
                Optional.of(adminKey),
                Optional.of(adminKey),
                100L,
                Optional.of(autoRenewId.getAccountNum()),
                Timestamp.newBuilder().setSeconds(100L).build(),
                1L,
                new byte[48],
                topicNum,
                true);
        assertNotEquals(meta, meta2);
        assertNotEquals(meta.hashCode(), meta2.hashCode());
    }

    @Test
    void getsTopicMetadataIfTopicExistsWithNoAutoRenewAccount() {
        givenValidTopic();
        given(topic.getAutoRenewAccountId()).willReturn(EntityId.MISSING_ENTITY_ID);
        final var topicMeta = subject.getTopicMetadata(topicId);

        assertNotNull(topicMeta);
        assertNotNull(topicMeta.metadata());
        assertFalse(topicMeta.failed());
        assertNull(topicMeta.failureReason());

        final var meta = topicMeta.metadata();
        assertEquals(topicNum, meta.key());
        assertEquals(Optional.of(adminKey), meta.adminKey());
        assertEquals(Optional.of(adminKey), meta.submitKey());
        assertEquals(1L, meta.sequenceNumber());
        assertEquals(100L, meta.autoRenewDurationSeconds());
        assertEquals(Optional.empty(), meta.autoRenewAccountId());
        assertEquals(Optional.of(memo), meta.memo());
        assertFalse(meta.isDeleted());
        assertArrayEquals(new byte[48], meta.runningHash());
    }

    @Test
    void failsIfTopicDoesntExist() {
        given(readableTopicState.get(topicNum)).willReturn(null);
        final var topicMeta = subject.getTopicMetadata(topicId);

        assertNotNull(topicMeta);
        assertNull(topicMeta.metadata());
        assertTrue(topicMeta.failed());
        assertEquals(INVALID_TOPIC_ID, topicMeta.failureReason());
    }

    @Test
    void constructorCreatesTopicState() {
        final var store = new ReadableTopicStore(readableStates);
        assertNotNull(store);
    }

    @Test
    void nullArgsFail() {
        assertThrows(NullPointerException.class, () -> new ReadableTopicStore(null));
        assertThrows(NullPointerException.class, () -> subject.getTopicMetadata(null));
    }
}
