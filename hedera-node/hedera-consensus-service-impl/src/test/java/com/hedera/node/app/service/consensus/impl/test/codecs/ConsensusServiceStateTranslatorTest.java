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

package com.hedera.node.app.service.consensus.impl.test.codecs;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.impl.codecs.ConsensusServiceStateTranslator;
import com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestBase;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.spi.WritableKVState;
import com.swirlds.platform.test.fixtures.state.MapWritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConsensusServiceStateTranslatorTest extends ConsensusTestBase {

    @BeforeEach
    void setUp() {}

    @Test
    void createMerkleTopicFromTopic() {
        final var existingTopic = readableStore.getTopic(topicId);
        assertFalse(existingTopic.deleted());

        final com.hedera.node.app.service.mono.state.merkle.MerkleTopic convertedTopic =
                ConsensusServiceStateTranslator.pbjToState(topic);

        assertMatch(convertedTopic, getExpectedMonoTopic());
    }

    @Test
    void createMerkleTopicFromTopicWithEmptyKeys() {
        final var existingTopic = readableStore.getTopic(topicId);
        assertFalse(existingTopic.deleted());

        final com.hedera.node.app.service.mono.state.merkle.MerkleTopic convertedTopic =
                ConsensusServiceStateTranslator.pbjToState(topicNoKeys);

        assertMatch(convertedTopic, getExpectedMonoTopicNoKeys());
    }

    @Test
    void createMerkleTopicFromReadableTopicStore() {
        final com.hedera.node.app.service.mono.state.merkle.MerkleTopic convertedTopic =
                ConsensusServiceStateTranslator.pbjToState(topicId, readableStore);

        assertMatch(convertedTopic, getExpectedMonoTopic());
    }

    @Test
    void createTopicFromMerkleTopic() {
        com.hedera.node.app.service.mono.state.merkle.MerkleTopic merkleTopic = getMerkleTopic(
                (com.hedera.node.app.service.mono.legacy.core.jproto.JKey)
                        com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(topic.adminKeyOrElse(Key.DEFAULT))
                                .orElse(null),
                (com.hedera.node.app.service.mono.legacy.core.jproto.JKey)
                        com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(topic.submitKeyOrElse(Key.DEFAULT))
                                .orElse(null));

        final Topic convertedTopic = ConsensusServiceStateTranslator.stateToPbj(merkleTopic);

        assertEquals(createTopic(), convertedTopic);
    }

    @Test
    void createTopicFromMerkleTopicEmptyKeys() {
        com.hedera.node.app.service.mono.state.merkle.MerkleTopic merkleTopic = getMerkleTopic(null, null);

        final Topic convertedTopic = ConsensusServiceStateTranslator.stateToPbj(merkleTopic);

        assertEquals(createTopicEmptyKeys(), convertedTopic);
    }

    @Test
    void createFileFromFileIDAndHederaFs() {
        com.swirlds.merkle.map.MerkleMap<
                        com.hedera.node.app.service.mono.utils.EntityNum,
                        com.hedera.node.app.service.mono.state.merkle.MerkleTopic>
                monoTopics = new MerkleMap<>();
        WritableKVState<TopicID, Topic> appTopics = new MapWritableKVState<>(TOPICS_KEY) {
            private final List<TopicID> keys = new ArrayList<>();

            @Override
            protected void putIntoDataSource(@NonNull TopicID key, @NonNull Topic value) {
                keys.add(key);
                super.putIntoDataSource(key, value);
            }

            @Override
            protected void removeFromDataSource(@NonNull TopicID key) {
                keys.add(key);
                super.removeFromDataSource(key);
            }
        };

        com.hedera.node.app.service.mono.state.merkle.MerkleTopic merkleTopic = getMerkleTopic(
                (com.hedera.node.app.service.mono.legacy.core.jproto.JKey)
                        com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(topic.adminKeyOrElse(Key.DEFAULT))
                                .orElse(null),
                (com.hedera.node.app.service.mono.legacy.core.jproto.JKey)
                        com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(topic.submitKeyOrElse(Key.DEFAULT))
                                .orElse(null));
        monoTopics.put(topicEntityNum, merkleTopic);
        refreshStoresWithCurrentTopicOnlyInReadable();
        ConsensusServiceStateTranslator.migrateFromMerkleToPbj(monoTopics, appTopics);

        final Topic convertedTopic = appTopics.get(topicId);

        assertEquals(createTopic(), convertedTopic);
    }

    private void assertMatch(MerkleTopic expected, MerkleTopic actual) {
        assertEquals(expected.getKey(), actual.getKey());
        assertEquals(expected.getMemo(), actual.getMemo());
        assertEquals(expected.getAdminKey(), actual.getAdminKey());
        assertEquals(expected.getSubmitKey(), actual.getSubmitKey());
        assertEquals(expected.getExpirationTimestamp(), actual.getExpirationTimestamp());
        assertEquals(expected.getAutoRenewDurationSeconds(), actual.getAutoRenewDurationSeconds());
        assertEquals(expected.getAutoRenewAccountId(), actual.getAutoRenewAccountId());
        assertEquals(expected.getAutoRenewDurationSeconds(), actual.getAutoRenewDurationSeconds());
        assertEquals(expected.isDeleted(), actual.isDeleted());
        assertEquals(expected.getSequenceNumber(), actual.getSequenceNumber());
    }

    private com.hedera.node.app.service.mono.state.merkle.MerkleTopic getExpectedMonoTopic() {
        com.hedera.node.app.service.mono.state.merkle.MerkleTopic merkleTopic =
                new com.hedera.node.app.service.mono.state.merkle.MerkleTopic();
        merkleTopic.setKey(EntityNum.fromLong(topicId.topicNum()));
        merkleTopic.setMemo(topic.memo());
        merkleTopic.setExpirationTimestamp(
                new com.hedera.node.app.service.mono.state.submerkle.RichInstant(topic.expirationSecond(), 0));
        merkleTopic.setAdminKey((com.hedera.node.app.service.mono.legacy.core.jproto.JKey)
                com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(topic.adminKeyOrElse(Key.DEFAULT))
                        .orElse(null));
        merkleTopic.setSubmitKey((com.hedera.node.app.service.mono.legacy.core.jproto.JKey)
                com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(topic.submitKeyOrElse(Key.DEFAULT))
                        .orElse(null));
        merkleTopic.setAutoRenewDurationSeconds(topic.autoRenewPeriod());
        merkleTopic.setDeleted(topic.deleted());
        merkleTopic.setSequenceNumber(topic.sequenceNumber());
        merkleTopic.setAutoRenewAccountId(new com.hedera.node.app.service.mono.state.submerkle.EntityId(
                topic.autoRenewAccountId().shardNum(),
                topic.autoRenewAccountId().realmNum(),
                topic.autoRenewAccountId().accountNum()));
        return merkleTopic;
    }

    private com.hedera.node.app.service.mono.state.merkle.MerkleTopic getExpectedMonoTopicNoKeys() {
        com.hedera.node.app.service.mono.state.merkle.MerkleTopic merkleTopic =
                new com.hedera.node.app.service.mono.state.merkle.MerkleTopic();
        merkleTopic.setKey(EntityNum.fromLong(topicId.topicNum()));
        merkleTopic.setMemo(topic.memo());
        merkleTopic.setExpirationTimestamp(
                new com.hedera.node.app.service.mono.state.submerkle.RichInstant(topic.expirationSecond(), 0));
        merkleTopic.setAutoRenewDurationSeconds(topic.autoRenewPeriod());
        merkleTopic.setDeleted(topic.deleted());
        merkleTopic.setSequenceNumber(topic.sequenceNumber());
        merkleTopic.setAutoRenewAccountId(new com.hedera.node.app.service.mono.state.submerkle.EntityId(
                topic.autoRenewAccountId().shardNum(),
                topic.autoRenewAccountId().realmNum(),
                topic.autoRenewAccountId().accountNum()));
        return merkleTopic;
    }

    private com.hedera.node.app.service.mono.state.merkle.MerkleTopic getMerkleTopic(
            com.hedera.node.app.service.mono.legacy.core.jproto.JKey adminKey,
            com.hedera.node.app.service.mono.legacy.core.jproto.JKey submitKey) {
        com.hedera.node.app.service.mono.state.merkle.MerkleTopic merkleTopic =
                new com.hedera.node.app.service.mono.state.merkle.MerkleTopic();
        merkleTopic.setKey(EntityNum.fromLong(topicId.topicNum()));
        merkleTopic.setMemo(topic.memo());
        merkleTopic.setExpirationTimestamp(
                new com.hedera.node.app.service.mono.state.submerkle.RichInstant(topic.expirationSecond(), 0));
        merkleTopic.setAdminKey(adminKey);
        merkleTopic.setSubmitKey(submitKey);
        merkleTopic.setAutoRenewDurationSeconds(topic.autoRenewPeriod());
        merkleTopic.setDeleted(true);
        merkleTopic.setSequenceNumber(topic.sequenceNumber());
        merkleTopic.setAutoRenewAccountId(
                new com.hedera.node.app.service.mono.state.submerkle.EntityId(0, 0, autoRenewId.accountNum()));
        merkleTopic.setRunningHash(runningHash);

        return merkleTopic;
    }
}
